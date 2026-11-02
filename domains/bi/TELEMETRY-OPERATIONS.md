# 空调、用电 BI 与原始记录冷归档

## 功能与统计口径

空调与用电页面已接入真实统计，组件预览地址为 /previews/telemetry-analysis，可在无需后端的情况下切换两种分析及完整、缺口、空数据状态。

Web 接口：
- GET /api/bi/air-condition/dashboard
- GET /api/bi/energy/dashboard
- 参数 range=TODAY、CURRENT_MONTH 或 CUSTOM；CUSTOM 同时传 startDate、endDate（YYYY-MM-DD，含首尾，最多一年，不含未来）。
- buildingNames、orgNames、laboratoryIds、deviceIds 均为可重复参数；空列表表示该维度全部可见值，同一维度取并集，不同维度取交集。设备使用 ID 筛选，避免同名内机 / 空开混淆。分别校验 air_condition_data_analysis、circuit_break_data_analysis 权限。
- calculatedAt、日期边界均为 Asia/Shanghai；时间区间内部采用半开区间。

业务筛选与明细：
- GET /api/bi/air-condition/options 或 /api/bi/energy/options 返回授权范围内的楼栋、单位、实验室、设备选项；实验室按楼栋 / 单位收窄，设备再按实验室收窄。
- options 和 dashboard 使用同一种 TelemetryQuery，校验相同分析权限。权限范围、楼栋 / 单位组织和设备过滤均由后端处理；筛选失败或空交集不会回退到全部数据。
- dashboard.rows 为完整筛选结果：deviceId、deviceName、groupId、laboratoryId、laboratoryName、orgName、buildingName、value、incomplete、coverage。空调 value 为小时，能耗 value 为 kWh。总计与 rows 的 value 合计一致；前端分页只改变显示，合计覆盖全部筛选记录。
- 机组当前只有 group_id，没有独立名称表；如实显示机组编号，未设置时显示“—”。实验室名称、楼栋、单位取当前授权实验室元数据，统计归属仍按汇总记录的 laboratory_id。
- 页面默认全部可见设备。修改筛选为草稿，点击“查询统计”后应用；更换楼栋 / 单位会清除依赖的实验室和设备选择，更换实验室会清除依赖设备选择。
- /previews/telemetry-analysis 使用真实组件和固定本地数据，可演示多选、条件联动、总计、分页及完整 / 缺口 / 空 / 加载 / 失败状态，不需要业务服务。
- 图表使用按需加载的 ECharts：趋势支持柱状 / 折线切换，超过 32 个时间桶时可缩放；设备排名展示前 8 台，占比可按实验室 / 单位 / 楼栋切换。排名和占比从完整筛选明细聚合，不受表格分页影响；占比超过 6 组时保留前 5 组，其余合并为“其他”。
- 缺口趋势不连线补零，分摊 / 不完整数据使用不同颜色及纹理提示；无有效用量时显示空状态。图表支持窄屏重排、尺寸变化和减少动态效果偏好，明细表保留完整数值。

指标：
- 空调开启时长和模式时长按有效上报区间计算；连续断报超过 BI_STALE_MINUTES（默认 15）后的时间未知。温度按开启时长加权，未观测到运行时不返回伪造的平均温度。
- energy 为累计 kWh；功率原值为 W，负载展示 kW。负差不计入电量，按新读数重新建立基线，清零事件归属新读数时间。
- 跨小时或长间隔电量按时间比例分摊并标记。没有前后两个读数的区间无电量覆盖，不能解释成零用电。
- 设备峰值功率指范围内最高的单设备实测功率，不代表所有回路同一时刻的合计峰值。
- coverage 是已观测区间 / 设备有效存续区间；缺少历史汇总会额外提示。当前状态独立于历史统计，过期状态不计入当前开启及负载。
- 原始记录没有历史实验室字段。首次投影采用设备当时的 belong_to，已冻结汇总保留该归属；不能从现有数据重建设备迁移前的真实归属。

已结束日期使用 bi_device_hourly，当日直接计算原始记录，避免重叠。历史按日期原子替换；未归档日期可反复回填，已冻结日期不会因原始记录移走而被覆盖为空。归档锚点保留跨日期所需的上一条有效原始读数。

增量任务每 30 秒检测最新读数边界。首次发现历史数据自动排队回填；断报后新读数跨越多日时，从旧边界所在日期重新计算。每天 02:10 额外回填前两日。历史查询按实验室收窄并流式读取汇总。

## 部署顺序

1. 配置 .env 中 MINIO_ROOT_PASSWORD、MINIO_SECRET_KEY，使用不同的管理员与业务凭据。配置 MINIO_PUBLIC_ENDPOINT 为浏览器、下位机可访问的地址；签名生成时使用此地址，不能生成后替换域名。内部服务使用 http://minio:9000。
2. 执行已有库迁移 sql/migrations/20261102_bi_telemetry_archive.sql。表与索引创建均可重复执行；统一部署脚本已接入该迁移，新建数据库也可安全重跑。
3. 统一入口 scripts/deploy.sh 已纳入 MinIO、Tempo 与 BI 迁移；先等待 MinIO 健康及 minio-init 以 0 退出，再部署 BI 和 Web。构建包含新增 shared/object-storage 模块。Compose 固定到实际测试的 MinIO 镜像摘要，初始化复用同一个镜像中自带的 mc。
4. 启动新版前端，等待自动回填，或用下述管理员接口显式提交回填并检查状态。
5. 执行归档演练；下载一组对象及清单，用恢复工具恢复到临时表并核对。
6. 演练通过后将 BI_ARCHIVE_DELETE_ENABLED=true，重新创建 BI 服务。其他服务无需获得清理权限。

示例（在 lab-system-cloud 目录，仓库命令通过交互式 Zsh 执行）：

~~~sh
docker compose up -d minio minio-init
./mvnw -pl domains/bi/service,web -am package
docker compose --profile app up -d bi-service web-service
~~~

如果外部镜像仓库当前不可用，可通过 MINIO_IMAGE 指定可信的本地镜像或内部镜像仓库。此项同时用于服务端与初始化容器；替换镜像需保留 /bin/sh、mc、curl，以支持初始化和健康检查。

配置：

| 参数 | 默认 | 用途 |
| --- | --- | --- |
| MINIO_ENABLED | 本地 false、Compose true | 对象存储开关 |
| MINIO_ENDPOINT | 本地 http://localhost:9000 | 服务内部地址 |
| MINIO_PUBLIC_ENDPOINT | http://localhost:9000 | 下位机 / 浏览器下载地址 |
| MINIO_ARCHIVE_BUCKET | lab-telemetry-archive | 私有原始记录桶 |
| MINIO_DEVICE_BUCKET | lab-device-files | 私有设备文件桶 |
| BI_STALE_MINUTES | 15 | 状态可信时长 |
| BI_ARCHIVE_RETENTION_DAYS | 90 | 热记录保留天数 |
| BI_ARCHIVE_BATCH_SIZE | 500，最大 2000 | 每批记录数 |
| BI_ARCHIVE_DELETE_ENABLED | false | 物理清理开关 |
| BI_JOB_POLL_MS | 30000 | 持久化任务轮询周期 |
| BI_DAILY_CRON | 0 10 2 * * * | 每日任务，上海时区 |

MinIO 数据位于 minio-data 卷，控制台默认只绑定本机 9001，S3 端口默认 9000。两个桶均禁止匿名访问；业务账号只允许指定桶的读取与写入，没有对象删除权限。对象长期保留，需将 MinIO 卷纳入部署环境的持久化及备份管理。

## 管理员接口与归档状态

以下维护和设备文件接口均要求当前登录用户具有 app:global 的 super_admin 关系。分析权限不能代替维护权限。请求使用已有会话 cookie，示例 cookie 文件为 session.cookies。

~~~sh
# 返回配置、各表旧记录数量、近期任务及归档批次
curl -b session.cookies http://localhost:8989/api/bi/maintenance

# 返回持久化任务 ID，不在 HTTP 请求中执行长任务
curl -b session.cookies -H 'Content-Type: application/json'   -d '{"startDate":"2026-08-01","endDate":"2026-08-31"}'   http://localhost:8989/api/bi/maintenance/backfill

# 默认只演练：上传、回读校验、生成清单，不删除原表
curl -b session.cookies -X POST   'http://localhost:8989/api/bi/maintenance/archive?dryRun=true'

# 开关开启后才允许真实归档
curl -b session.cookies -X POST   'http://localhost:8989/api/bi/maintenance/archive?dryRun=false'

# 重试状态为 FAILED 的任务，已完成日期不重跑
curl -b session.cookies -X POST   http://localhost:8989/api/bi/maintenance/JOB_ID/retry
~~~

任务状态为 QUEUED → RUNNING → COMPLETE / FAILED。记录开始、结束、结果及失败原因；服务中断后由新租约持有者恢复 RUNNING 任务。数据库时钟控制租约，每个写事务都检查持有者及期限，过期工作者不能删除数据。

归档批次状态为 PREPARING → VERIFIED → DELETING → COMPLETE。演练终态 DRY_RUN；校验前中断标记 ABANDONED，失败标记 FAILED。只有数据库同时记录了对象与清单校验值，批次才允许进入删除流程。

每次任务最多处理 20 批，真实清理后仍有积压会自动排队后续任务，持续排空而非每天只清固定数量。默认保留最近 90 天，加每台设备最多一条更早的最新有效状态。旧记录数量包含这条保留状态，因此旧记录统计可以非零。

归档对象位于 表名/日期/批次ID.ndjson.gz，旁边保存 .manifest.json。清单含 schemaVersion、起止时间、记录 ID、update_at、逐行 SHA-256、记录数和压缩对象 SHA-256。删除前重新验证对象及清单，并锁定源记录逐字段比较；版本变化会保留源记录并使任务失败。已删除的记录在重试时跳过，不重复累计 deleted_count。

日期冻结后，迟到或修改过的旧记录不会被自动纳入删除。遇到 Late or modified raw records 或 Source version changed，应关闭删除开关，使用归档与热数据核对后安排人工修复；不要手动解除冻结并对残缺原表做覆盖回填。

## 恢复工具

先用管理员 mc 别名下载压缩对象及对应清单，再执行：

~~~sh
python3 tools/bi/restore-archive.py   --archive batch.ndjson.gz --manifest batch.ndjson.gz.manifest.json   --output restore.sql
~~~

工具检查压缩文件、逐行身份与版本、逐行摘要、重复 ID、数量和字段名。仅生成创建 bi_restore_批次ID 独立表并写入数据的 SQL；输出文件已存在时拒绝覆盖。SQL 使用十六进制字面量，避免文本转义造成数据变化。将此 SQL 导入同结构的验证数据库后逐字段核对。工具不会自动执行 SQL，也不会写回生产 record 表。

原始记录物理删除后，InnoDB 会复用释放页面；这不保证已有数据文件立即缩小。

## 设备文件公共能力

- POST /api/device-files，multipart 字段 file，大小 1 字节至 20 MB，返回服务端生成的 key。
- GET /api/device-files/download-url?key=...&expiresSeconds=300，最长 3600 秒。
- 只访问设备文件桶，禁止任意路径及归档对象 key；不返回 MinIO 凭据。
- 后续人脸同步应保存该 key，在下发前生成下位机可访问的临时 URL。本次不实现人员映射、设备协议或人脸同步业务。

## 验证与回滚

新增测试覆盖跨小时 / 跨日、电表清零、同时间戳、缺少基线、断报时长、五类表保留最新状态、演练、上传 / 校验失败、源版本冲突、删除中断重试、租约隔离、权限、汇总幂等、当日去重，以及恢复到独立表后的逐字段一致性。

BI_TEST_JDBC_URL 仅用于独立 bi_test 数据库；MINIO_TEST_ENDPOINT 仅用于独立测试 MinIO（测试账号 testadmin / bi-test-only-password）。未设置时跳过对应外部集成测试，不连接业务数据库。

~~~sh
# 先核实实际环境；不要改写 JAVA_HOME
whence -a java
java -version
./mvnw -pl domains/bi/service,web -am test
~~~

前端执行 typecheck、lint、test、build。回滚时先将 BI_ARCHIVE_DELETE_ENABLED=false 并重启 BI；保留新增表、MinIO 卷与归档对象。已完成清理的数据需要按归档恢复流程处理，不能通过回滚应用版本找回。
