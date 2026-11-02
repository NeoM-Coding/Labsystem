# BI 模块

`bi-api` 定义稳定的 Dubbo 看板契约，`bi-service` 基于 MySQL
`bi_edu_course_occurrence` View 提供只读统计，不复制教务数据。

## 教务时间维度

- `TODAY`：当天课程，时间序列按开课时间聚合。
- `CURRENT_MONTH`：当前自然月，时间序列按日期聚合并补齐无课程日期。
- `CURRENT_SEMESTER`：当前日期所在学期，时间序列按学期周次聚合并补齐空周。

接口统一返回课程数和学时数、已结束/进行中/待开始状态、正在上课的实验室数量、
星期分布、节次分布以及当前课程明细。学时按 `end_section - start_section + 1`
计算；状态使用一次请求内相同的 `calculatedAt` 和半开区间 `[startAt, endAt)` 计算。

用户传入的实验室 ID 只用于收窄 Dubbo 透传 `UserContext` 中的可见范围。
查询需要 `app:global#edu_data_analysis` 权限。

Web 入口：

```text
GET /api/bi/edu/dashboard?range=TODAY&laboratoryIds=...
```

## 空调、用电与冷归档

空调与用电看板、设备小时汇总、MinIO 公共存储和五类记录冷归档已接入。
热记录默认保留 90 天并额外保护设备最新状态，物理清理默认关闭。
部署、指标口径、维护 API、演练、恢复及验证见 [运维说明](TELEMETRY-OPERATIONS.md)。
