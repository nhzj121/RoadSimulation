# 加工链 DAG 数据兼容迁移说明

## 1. 迁移目的

当前数据库中已经存在 4 条旧加工链和 15 个加工阶段，但这些数据创建于 DAG 生产模型之前，缺少：

- 稳定的 `stage_key`；
- 具名阶段输入 `processing_stage_input`；
- 显式阶段连接 `processing_stage_edge`；
- 部分有效加工时间与首尾 SKU。

本次迁移复用现有加工链和阶段主键，不创建重复加工链或重复阶段，使旧定义可被新的生产计划与执行流模型读取。

## 2. 执行前数据状态

目标数据库为 `vehicle_scheduler`。执行前只读检查结果为：

| 数据 | 数量 |
| --- | ---: |
| `processing_chain` | 4 |
| `processing_stage` | 15 |
| `processing_stage_input` | 0 |
| `processing_stage_edge` | 0 |
| `production_plan` | 0 |
| `production_batch` | 0 |
| `processing_stage_execution` | 0 |
| `production_plan_flow` | 0 |
| `processing_execution_flow` | 0 |

数据库中存在执行前完整备份 `vehicle_scheduler_phase10`。该备份用于必要时核对和恢复旧加工链定义。

## 3. 数据调整范围

迁移脚本为项目根目录下的 `migrate_existing_processing_chains_to_dag_compat.sql`。

它只执行以下操作：

1. 更新现有 15 个 `processing_stage`的：
   - `stage_key`；
   - `input_goods_id` / `input_goods_sku`；
   - `output_goods_id` / `output_goods_sku`；
   - 当原值为空或不大于 0 时，将 `processing_time_minutes` 设为 60；
   - `updated_at`。
2. 新增 15 条 `processing_stage_input`，每个阶段一条 `input`，`input_share = 1.0`。
3. 新增 11 条 `processing_stage_edge`，连接各链中相邻阶段。

脚本不会：

- 新增或删除 `processing_chain`；
- 新增或删除 `processing_stage`；
- 新增 `Goods` 主数据；
- 修改运单、货物项、运输任务、车辆、路线或分配数据；
- 修改评价快照、等待账本、能耗账本或评价公式；
- 创建生产计划、批次或执行流。

该脚本定位为一次性旧数据规范化迁移。输入和边使用防重插入，但阶段更新会刷新 `updated_at`；后续如果人工调整了阶段定义，不应无条件重复执行。

## 4. SKU 映射

| 加工链 | 阶段流 |
| --- | --- |
| 木材家具 | `LOG → LOG → PLANK → PANEL → FURNITURE` |
| 金属家具 | `IRON_ORE → IRON_ORE → STEEL_BILLET → STEEL_PRODUCT → FURNITURE` |
| 金属汽车 | `IRON_ORE → IRON_ORE → STEEL_BILLET → STEEL_PRODUCT → AUTO_PARTS` |
| 橡胶汽车 | `RUBBER_RAW → RUBBER_SEMI → TIRE → AUTO_PARTS` |

`FURNITURE` 和 `AUTO_PARTS` 当前只作为最终产品文本 SKU，不自动新增 `Goods` 记录。这避免在没有明确重量、体积和货物分类参数时伪造运输主数据。

## 5. 对评价体系的影响

本次操作只调整生产定义层，不产生新运单，因此执行迁移本身不会改变当前运行的评价数值。

后续释放生产计划时，生产物料流仍通过现有 `TransportDemandService` 创建标准 `Shipment` 和 `ShipmentItem`，因此会作为真实运输需求进入现有：

- 货物需求达成率；
- 运输任务完成率和等待指标；
- 车辆里程与载重指标；
- 能耗和碳排放指标；
- 四个全局优化目标。

本次没有为生产运输需求增加评价过滤或单独口径。

## 6. 验证标准

迁移后必须同时满足：

- 4 条目标加工链仍为 4 条；
- 目标阶段仍为 15 个；
- 15 个阶段的 `stage_key` 全部非空且链内唯一；
- 目标阶段共有 15 条具名输入；
- 4 条链共有 11 条显式边；
- 链内 `stage_order` 不重复；
- 所有加工时间大于 0；
- 每条边的上游输出 SKU 等于下游输入 SKU；
- 生产运行表仍为空；
- 评价和运输业务表行数不因迁移改变。

## 7. 恢复方式

如果迁移验证失败，应先停止后端，然后以 `vehicle_scheduler_phase10` 中的 `processing_chain`、`processing_stage` 为旧定义权威来源进行核对。恢复时还需清理本次新增的 `processing_stage_edge` 和 `processing_stage_input`。

禁止为了恢复加工链定义而整库覆盖当前运单、评价快照或其他已增长的业务数据。

## 8. 实际执行结果

2026-09-17 已在本地 `vehicle_scheduler` 执行迁移。执行时后端未监听常用端口，MySQL 中也没有其他使用该数据库的连接。

执行前额外导出了四张受影响表的本地快照：

```text
.phase10/processing_dag_before_20260917.sql
```

该快照仅用于本地恢复，不纳入 Git。

执行结果：

| 校验项 | 结果 |
| --- | --- |
| 目标加工链 | 4，数量未改变 |
| 目标阶段 | 15，数量未改变 |
| 具名阶段输入 | 15 |
| 显式阶段边 | 11 |
| 空或重复 `stage_key` | 0 |
| 非正加工时间 | 0 |
| 无效输入或空 `goods_id` | 0 |
| 边的上下游 SKU 不一致 | 0 |
| 生产计划/批次/执行/流记录 | 全部仍为 0 |

迁移 SQL 只访问 `processing_stage`、`processing_stage_input`、`processing_stage_edge`、`processing_chain` 和 `goods`，其中 `processing_chain` 和 `goods` 只用于关联查询。运输与评价相关表未被写入。
