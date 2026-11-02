# 统一 API 异常契约

响应为 `{code, ok, data, msg, traceId?}`。成功码为 200。HTTP 状态独立存储，通过 `DiyResponseEntity` 设置，不出现在 JSON 中。

MQTT 设备超时：HTTP 504 / code 130001 / msg「设备响应超时，请检查设备连接」。RPC 传输超时为 100006，浏览器超时为 100010；不得根据消息字符串判断。

## 维护与升级

- 唯一错误目录：`contracts/errors.json`。运行 `python3 scripts/generate-errors.py` 同时生成 Java 与相邻前端仓库的 TypeScript 定义；`--check` 校验重复、生成文件漂移、未知枚举引用和生产代码的裸 HTTP 业务异常。
- 后端 CI 自动执行检查；仅后端检出时跳过不存在的前端目录，在联合工作区执行会同时检查两端。业务码发布后不复用。新增错误优先复用已有语义。
- 本次 RPC code 从字符串变为数字，批量设备结果新增 code；前后端及所有 RPC 服务必须同步部署，不能混用旧提供者/消费者。
- `exception-inventory.json` 记录迁移前抛出位置和触发信息与新码映射；`exception-scan.json` 记录扫描到的业务、内部、配置、后台与前端错误处理点，可运行 `python3 scripts/scan-errors.py` 更新。内部/configuration 异常不按抛出点增加业务码；边界统一映射 100002，认证配置错误映射 110003。
- 普通设备等待 5 秒；单设备浏览器预算 15 秒；批量最多 20 台串行等待，Dubbo 预算 120 秒，浏览器 125 秒。仓库 Vite 代理无更短的显式请求超时；部署环境的外部网关也应至少覆盖对应预算。

## 前端处理

请求默认 `errorPolicy: 'global'`，在应用根部显示真实的全局提示组件。同 code/message 的并发提示合并，8 秒后关闭。API 模块只解包数据，不重新包装普通 Error。

登录请求使用 `local` 保留登录页提示；初始会话探测使用 `silent`，由启动流程清理会话。字段表单需要局部展示时在请求配置中显式指定 `local`，读取 `ApiError.data.fieldErrors`。通用组件通过 `localErrorMessage` 避免重复展示已经全局处理的 API 错误；加载、回滚、重试入口和批量逐设备结果继续由业务组件维护。设备写操作不自动重试。

预览地址 `/previews/api-errors`，导航「组件预览 → API 异常预览」。页面使用根部真实组件，不依赖服务端，显示最近触发的 code、HTTP 状态、data、traceId 与提示策略。

## Dubbo 与 MQTT 边界

Dubbo 的默认 ExceptionFilter 可能将跨 JAR 的未声明自定义异常转换为只有文本的 RuntimeException。内层 `RpcResultValueDubboFilter` 在它之前将业务失败变成值；外层 `RpcResultDubboFilter` 在 Trace 范围内捕获身份上下文等过滤器失败。异步回调也必须经过同样转换。

MQTT 自身等待超时与工作线程 `completeExceptionally(TimeoutException)` 是两条路径，两者统一使用 130001；规则引擎异步调用也返回相同的错误值。

## 链路验证与诊断边界

`ApiErrorContractTests` 覆盖真实 Dubbo 激活过滤器链及 fastjson2 编解码 → RpcClient → Advice → HTTP JSON。它确认应用响应链可以保留 504 的消息；不能证明某个已部署网关是否替换了响应。若线上仍无 msg，应核对浏览器原始响应、Web traceId 日志和网关错误页配置。

## 业务码目录

| code | 符号名 | HTTP | 默认消息 | 分类 |
| --- | --- | --- | --- | --- |
| 100001 | VALIDATION_FAILED | 400 | 请求参数不合法 | VALIDATION |
| 100002 | INTERNAL_ERROR | 500 | 服务器内部错误 | INTERNAL |
| 100003 | RESOURCE_NOT_FOUND | 404 | 资源不存在 | NOT_FOUND |
| 100004 | BUSINESS_CONFLICT | 409 | 操作与当前状态冲突 | CONFLICT |
| 100005 | DEPENDENCY_UNAVAILABLE | 503 | 下游服务暂不可用 | DEPENDENCY |
| 100006 | RPC_TIMEOUT | 504 | 下游服务调用超时 | DEPENDENCY |
| 100007 | INVALID_RPC_RESPONSE | 502 | 下游服务返回无效响应 | DEPENDENCY |
| 100008 | BUSINESS_FAILED | 422 | 业务处理失败 | BUSINESS |
| 100009 | NETWORK_ERROR | 客户端 | 网络连接失败，请检查网络 | DEPENDENCY |
| 100010 | REQUEST_TIMEOUT | 客户端 | 请求超时，请稍后重试 | DEPENDENCY |
| 100011 | REQUEST_CANCELLED | 客户端 | 请求已取消 | BUSINESS |
| 110001 | AUTHENTICATION_REQUIRED | 401 | 登录已过期，请重新登录 | AUTHENTICATION |
| 110002 | PERMISSION_DENIED | 403 | 无权执行此操作 | AUTHORIZATION |
| 110003 | AUTH_CONFIGURATION_ERROR | 500 | 权限配置异常 | INTERNAL |
| 130001 | MQTT_DEVICE_TIMEOUT | 504 | 设备响应超时，请检查设备连接 | DEPENDENCY |
| 130002 | MQTT_REQUEST_INTERRUPTED | 503 | 设备请求被中断 | DEPENDENCY |
| 120101 | LABORATORY_NOT_FOUND | 404 | 实验室不存在 | NOT_FOUND |
| 120102 | LABORATORY_MEMBER_NOT_FOUND | 400 | 可见成员中包含不存在的用户 | VALIDATION |
| 120103 | LABORATORY_ID_REQUIRED | 400 | 实验室 ID 不能为空 | VALIDATION |
| 120104 | USER_NOT_FOUND | 404 | 用户不存在 | NOT_FOUND |
| 120105 | USER_DISABLED | 403 | 该用户不允许使用系统，请联系管理员 | AUTHORIZATION |
| 120106 | INVALID_CREDENTIALS | 403 | 用户名或密码错误 | AUTHORIZATION |
| 120107 | USER_ID_REQUIRED | 400 | 用户 ID 不能为空 | VALIDATION |
| 120108 | USER_NAME_CONFLICT | 403 | 该姓名已被使用 | AUTHORIZATION |
| 120109 | CONTACT_LOGIN_ASSIGNMENT_FORBIDDEN | 400 | 联系人不能通过用户更新接口分配登录信息或权限 | VALIDATION |
| 120110 | SELF_DELETION_FORBIDDEN | 400 | 不能删除当前登录用户 | VALIDATION |
| 120111 | CONTACT_NOT_FOUND | 404 | 用户或联系人不存在 | NOT_FOUND |
| 160101 | BI_TIME_DIMENSION_REQUIRED | 400 | 统计时间维度不能为空 | VALIDATION |
| 160102 | BI_CURRENT_SEMESTER_NOT_FOUND | 404 | 当前日期不在任何有效学期内 | NOT_FOUND |
| 150101 | SEMESTER_NAME_CONFLICT | 409 | 学期名称已存在 | CONFLICT |
| 150102 | SEMESTER_NOT_FOUND | 404 | 学期不存在 | NOT_FOUND |
| 150103 | SEMESTER_HAS_TIMETABLES | 409 | 该学期仍有课表记录，请先清理课表 | CONFLICT |
| 150104 | SEMESTER_NAME_INVALID | 400 | 学期名称格式应为 YYYY-YYYY 第N学期 | VALIDATION |
| 150105 | SEMESTER_DATES_REQUIRED | 400 | 学期开始日期和结束日期不能为空 | VALIDATION |
| 150106 | SEMESTER_DATE_RANGE_INVALID | 400 | 学期开始日期必须早于结束日期 | VALIDATION |
| 150107 | TIMETABLE_NOT_FOUND | 404 | 课表不存在 | NOT_FOUND |
| 150108 | TIMETABLE_WEEK_TYPE_REQUIRED | 400 | 周次类型不能为空 | VALIDATION |
| 150109 | TIMETABLE_WEEK_RANGE_INVALID | 400 | 周次范围不合法 | VALIDATION |
| 150110 | TIMETABLE_PERIOD_RANGE_INVALID | 400 | 节次超出允许范围 | VALIDATION |
| 150111 | TIMETABLE_TIME_RANGE_INVALID | 400 | 上课开始时间必须早于结束时间 | VALIDATION |
| 150112 | TIMETABLE_WEEKDAY_INVALID | 400 | weekday 必须在 1 到 7 之间 | VALIDATION |
| 150113 | TIMETABLE_CONFLICT | 409 | 课程安排冲突 | CONFLICT |
| 150114 | TIMETABLE_IMPORT_PARSE_FAILED | 400 | Excel 文件无法解析 | VALIDATION |
| 150115 | TIMETABLE_IMPORT_CELL_INVALID | 400 | 格式应为：课程名称<>时间信息<>教师名称 | VALIDATION |
| 150116 | TIMETABLE_COURSE_TEACHER_REQUIRED | 400 | 课程名称和教师名称不能为空 | VALIDATION |
| 150117 | TIMETABLE_IMPORT_TIME_INVALID | 400 | 时间格式应为 X-Y周[节次或时间] | VALIDATION |
| 150118 | TIMETABLE_IMPORT_WEEK_INVALID | 400 | 周次数字不合法 | VALIDATION |
| 150119 | TIMETABLE_IMPORT_TIME_RANGE_INVALID | 400 | 开始时间必须早于结束时间 | VALIDATION |
| 150120 | TIMETABLE_IMPORT_TIME_VALUE_INVALID | 400 | 时间值不合法 | VALIDATION |
| 150121 | TIMETABLE_IMPORT_TIME_DETAIL_INVALID | 400 | 时间详情应为 X-Y节 或 HH:mm-HH:mm | VALIDATION |
| 150122 | TIMETABLE_IMPORT_EXTENSION_INVALID | 400 | 仅支持 .xlsx 或 .xls 文件 | VALIDATION |
| 150123 | TIMETABLE_IMPORT_EMPTY | 400 | Excel 文件不能为空 | VALIDATION |
| 150124 | TIMETABLE_IMPORT_TOO_LARGE | 400 | Excel 文件超过大小限制 | VALIDATION |
| 150125 | TIMETABLE_IMPORT_FORMAT_INVALID | 400 | 文件内容不是有效的 Excel 格式 | VALIDATION |
| 150126 | LABORATORY_ACCESS_DENIED | 403 | 无权访问该实验室 | AUTHORIZATION |
| 130101 | MQTT_DEVICE_TYPE_IMMUTABLE | 400 | 设备类型不能修改 | VALIDATION |
| 130102 | MQTT_DEVICE_ID_REQUIRED | 400 | 设备 ID 不能为空 | VALIDATION |
| 130103 | MQTT_DEVICE_NOT_FOUND | 404 | 设备不存在 | NOT_FOUND |
| 130104 | MQTT_DEVICE_TYPE_REQUIRED | 400 | 设备类型不能为空 | VALIDATION |
| 130105 | MQTT_GATEWAY_ID_REQUIRED | 400 | 网关 ID 不能为空 | VALIDATION |
| 130107 | MQTT_DEVICE_NAME_REQUIRED | 400 | 设备名称不能为空 | VALIDATION |
| 130108 | MQTT_DEVICE_MODEL_MISMATCH | 400 | 设备类型与型号不匹配 | VALIDATION |
| 130109 | MQTT_DEVICE_ADDRESS_INVALID | 400 | 设备地址超出允许范围 | VALIDATION |
| 130110 | MQTT_DEVICE_SELF_ID_INVALID | 400 | 设备内部 ID 不能为负数 | VALIDATION |
| 130111 | MQTT_GATEWAY_HAS_DEVICES | 409 | 网关下仍有设备，不能删除 | CONFLICT |
| 130112 | MQTT_GATEWAY_NOT_FOUND | 404 | 网关不存在 | NOT_FOUND |
| 130113 | MQTT_GATEWAY_REQUIRED | 400 | 网关不能为空 | VALIDATION |
| 130114 | MQTT_GATEWAY_TOPICS_REQUIRED | 400 | 网关主题不能为空 | VALIDATION |
| 130116 | MQTT_TARGETS_REQUIRED | 400 | 至少需要选择一台设备 | VALIDATION |
| 130117 | MQTT_TOO_MANY_TARGETS | 400 | 单次批量控制最多支持 20 台设备 | VALIDATION |
| 130119 | MQTT_DEVICE_ACCESS_DENIED | 403 | 无权控制该实验室设备 | AUTHORIZATION |
| 130120 | MQTT_DEVICE_TYPE_MISMATCH | 400 | 设备类型与实际设备不匹配 | VALIDATION |
| 140101 | STRATEGY_ALREADY_EXISTS | 409 | 策略已存在 | CONFLICT |
| 140102 | STRATEGY_NOT_FOUND | 404 | 策略不存在 | NOT_FOUND |
| 130121 | MQTT_POLL_TASK_INVALID | 400 | 无法为该设备创建轮询任务 | VALIDATION |
| 130122 | MQTT_POLL_UPDATE_FAILED | 500 | 设备轮询状态更新失败 | INTERNAL |

## 本次验证记录（2026-10-31）

- 后端全量 Maven 测试通过：349 tests，0 failures，0 errors。随后再次通过涉及异步 trace 传播的过滤器、RPC 客户端及真实 Dubbo → HTTP 契约测试。
- 前端 typecheck、lint、production build 通过；完整测试为 101 passed / 4 failed。新增异常与提示预览相关 10 个测试全部通过。
- 4 个前端失败来自门禁指令标签：`DeviceControlDialog.test.tsx` 2 项、`commandCatalog.test.ts` 1 项、`StrategyRevisionForm.test.tsx` 1 项。在临时基线副本中使用原有未提交的 commandCatalog 标签改动，复现了相同 4 个失败；未覆盖这些用户改动。
- 共享目录检查与两仓库 diff whitespace 检查通过。部署环境的真实 MQTT/网关链路尚未执行，本地测试使用确定性设备响应和 Dubbo 实际过滤器、序列化器。
