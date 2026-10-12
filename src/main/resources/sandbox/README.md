# 数据沙箱数据库与场景管理

5C已完成逐环节测试和review：结果页可进入独立逐轮地图，支持手动选轮、同轮车辆/运输段/货物/事件/评价、关联POI与全部POI切换，以及0.5/1/2秒记录播放。沿用普通地图高德开发配置，不接普通地图业务回调；行驶位置不插值，端点虚线不冒充道路。见[5C使用与契约](phase5c-map.md)、[阶段性验收/review](phase5c-acceptance.md)。控制结构仍v9，无迁移，不使用当前业务库拼装历史地图。

5B-2 已在 Vue3 `/sandbox` 接通“准备并运行”、持久化启动幂等、任务进度和安全取消；使用前需升级控制结构到v9。普通仿真保留。自2026-10-04起本机沙箱管理默认启用，密码仍需一次性配置，可显式关闭；见[默认启用与接入检查](default-management-enablement.md)。此前阶段记录中的“默认关闭”是当时的配置，不代表当前默认值。见 [运行入口说明](phase5b-2-execution.md) 和 [5B-2验收记录](phase5b-2-acceptance.md)。此前的场景/规格配置发布规则见 [配置入口说明](phase5b-1-configuration.md)，历史验证见 [5B-1验收记录](phase5b-1-acceptance.md)。

当前准备链路包含三个阶段：

1. 从正式 `baseline-v1.json` 恢复静态基础数据（包括司机偏好与车辆绑定），状态为 `BASE_DATA_READY`；
2. 编译、发布并恢复一个不可变场景 Revision，状态为 `SCENARIO_DATA_READY`。
3. 编译、发布并恢复确定性运行规格和车辆初态，状态为 `RUN_SPEC_READY`。

`RUN_SPEC_READY`表示场景数据、统一随机协议、运行规格和车辆初态已准备完成。司机行为启用时使用同一根种子按 `loopIndex + driverId` 派生独立随机流；固定司机偏好只参与稳定排序。没有空闲司机的车辆不可派单，运输任务也不允许无司机启动或继续。普通主应用激活 `sandbox-runtime` profile 仍会以 `SANDBOX_NOT_RUN_READY` 拒绝启动；沙箱只能使用独立受控入口，不复用普通定时/Web入口。

4B按[分阶段计划](phase4b-plan.md)开发，三个小阶段已验收并review：[4B-1受控执行](phase4b-1-acceptance.md)、[4B-2永久账本](phase4b-2-acceptance.md)、[4B-3完整过程与逐轮复现](phase4b-3-acceptance.md)。最终联合验收114项测试全部通过，完整矩阵15次运行、1056个业务轮次；完成后停止开发，不继续前端接入。
内部执行、事实采集合同、control-schema/v5/v6升级及报文容量前提见[运行与逐轮账本](execution-journal.md)。4B验收当时未接入前端；当前用户配置/任务/结果/地图入口分别见后续5B/5C记录。

4B-3的字段级比较、跨JVM复跑、最小生产场景、种子及损耗覆盖验收规则见[重新执行验收规则](reexecution-acceptance.md)。验收采用固定测试路径响应，不代表真实高德路径已冻结，也不代表所有自动交通事件分支均已验收。

后续[自动事件补充验收](automatic-events-acceptance.md)已完成：最终联合158项测试全部通过，新增完整矩阵12次运行、672轮，覆盖两条调度路径的拥堵、维修、成功换车、跨JVM/A→关闭B→A复现及无备用车辆的截止等待状态。生产默认开关不变；不扩称所有罕见分支已验收。该次验收未推进5A/5B/5C；其后5A进展见本文末尾。

阶段4A以 V2 为新运行规格入口，V1保留为历史编译、导出和校验格式。路径事实冻结按当前决定暂缓；后续允许重新请求高德时，相同起终点和参数不能保证返回完全相同的路径。4A当时的验收只覆盖数据准备和受控随机过程，不能单独证明完整逐轮复现。收尾范围和验证记录见 [阶段4A收尾记录](phase4a-closeout.md)。

## 一次性预置或升级

在进程环境中设置 `SANDBOX_DB_PASSWORD`。管理员密码非空时另设
`MYSQL_ADMIN_PASSWORD`，然后运行：

```powershell
.\scripts\provision-sandbox.ps1
```

脚本幂等创建或升级 `vehicle_scheduler_sandbox`、安全标记、场景和运行规格控制表，并创建最小权限账号
`road_sandbox_runtime@localhost`。该账号只拥有 `vehicle_scheduler_sandbox.*` 权限。准备程序通过
`information_schema`核对当前账号授权；若发现全局权限或任何`vehicle_scheduler`权限便拒绝执行，且不会向源库业务表发送查询，因此不能用`root`运行准备命令。

沙箱准备会直接使用版本化的 `sandbox-schema-v1.sql` 重建业务表，不执行增量迁移。
普通 `vehicle_scheduler` 库若需要从旧司机表结构升级，可由管理员人工审查并执行
`schema/migrations/20260928-driver-baseline-v1.sql`；该脚本不会被应用启动或沙箱准备流程自动执行。

## 命令环境

```powershell
$env:SANDBOX_DB_USER = 'road_sandbox_runtime'
$env:SANDBOX_DB_PASSWORD = '<仅在本机进程中设置>'
$env:SANDBOX_DB_URL = 'jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL'
```

准备/管理命令使用独立 Java 入口，不启动普通 Spring Boot 应用。受控执行入口会启动必要的真实业务组件，但禁止普通定时/Web入口、启动数据导入及旧随机初始化。

## 基础数据准备

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=prepare'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=verify'
```

默认基线为 `classpath:sandbox/baseline/baseline-v1.json`，第一阶段只接受
`ALL_ELIGIBLE_V1`。可用 `--baseline=绝对路径` 指定外部基线文件。

## 场景编译、版本发布和恢复

默认模板位于 `classpath:sandbox/scenarios/default-all-eligible-v1.json`。编译只读文件和基线，不连接数据库，因此不要求数据库密码：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=compile-scenario --scenario=classpath:sandbox/scenarios/default-all-eligible-v1.json'
```

保存草稿、显式发布、恢复指定 Revision 和只读核验。将 `$scenarioRevision` 设置为发布结果中的实际 `revision`，示例中的1不是固定默认版本：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=save-draft --scenario=classpath:sandbox/scenarios/default-all-eligible-v1.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=publish-scenario --scenario-key=all-eligible'
$scenarioRevision = 1 # 改为上一步返回的 revision；保留历史版本时可能大于1
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=prepare-scenario --scenario-key=all-eligible --revision=$scenarioRevision"
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=verify-scenario'
```

导出已发布 Revision（JSON 输出到标准输出，可由调用方保存为文件）：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=export-scenario --scenario-key=all-eligible --revision=$scenarioRevision"
```

场景准备总是从正式基线重新计算并重建业务表，不以上一个场景的数据库内容为修改基础。全部沙箱控制表会保留，运行期业务表会清空。相同定义重复发布复用原 Revision；显示名称和说明不参与数据版本指纹。

## 运行规格编译、发布和准备

默认模板位于 `classpath:sandbox/runs/default-production-original-v2.json`。运行规格只允许
`PRODUCTION`需求、关闭启动预生成，并选择已注册的`ORIGINAL`或`HEURISTIC`算法 Profile。

V2同时定义天气和交通事件。天气可以使用根种子生成覆盖完整仿真时长的时间线，也可以提供显式时间线；拥堵和故障参数、故障策略、司机行为及算法 Profile 均纳入校验和相应指纹。

运行规格引用的是不可变场景 Revision。模板中的 Revision 1只是示例引用，不代表工作库的当前版本。使用 `resolve-run-spec` 显式指定场景 Key 和 Revision，命令从已发布版本读取两个哈希，并结合正式基线编译校验。不会自动选择最新版本，也不会保存草稿、发布版本或改变工作库标记。指定版本不存在、已归档或与基线不兼容时拒绝。

`--run-spec` 可省略，此时使用默认V2模板；自定义运行参数时传入自己的V2 JSON。模板的旧场景引用被显式选定版本替换，其它参数保留并规范化校验。`--output` 输出可再次导入的纯 JSON 文件，避免 Maven 日志混入。目标父目录必须存在，文件必须不存在，已有文件不覆盖；省略 `--output` 时只打印 JSON。

```powershell
# target 已由项目编译创建；再次执行时使用新的输出文件名。
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=resolve-run-spec --scenario-key=all-eligible --scenario-revision=$scenarioRevision --output=target/run-spec-resolved-v2.json"
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=compile-run-spec --run-spec=target/run-spec-resolved-v2.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=save-run-draft --run-spec=target/run-spec-resolved-v2.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=publish-run-spec --run-spec-key=production-weather-v2'
$runRevision = 1 # 改为 publish-run-spec 返回的实际 revision
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=prepare-run --run-spec-key=production-weather-v2 --revision=$runRevision"
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=verify-run'
```

导出Revision或核对一个派生子种子：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=export-run-spec --run-spec-key=production-weather-v2 --revision=$runRevision"
$keyJson = '{"vehicleId":1}'
$keyBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($keyJson)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=derive-seed --run-spec-key=production-weather-v2 --revision=$runRevision --domain=VEHICLE_INITIAL_POI --key=base64url:$keyBase64"
```

`--key`也可在不会吞掉引号的调用环境中直接接收JSON对象。PowerShell经Maven调用时推荐使用上面的`base64url:`传输形式；解码后的JSON对象才参与规范化和种子派生，编码形式不参与随机协议。

同一运行规格Revision会物化同一组车辆初始POI；固定POI优先，未固定车辆按`vehicleId`派生独立随机流。`driverBehavior.enabled=false`时不消费司机行为随机流；启用时仅允许`MARKOV_V1 + DERIVED_FROM_ROOT`。`RUN_SPEC_READY`仍不是正式仿真可运行状态。

运行服务已补充从发布的V2读取天气时间线和事件配置的接入逻辑，并拒绝沙箱临时覆盖及天气窗口外回退。
普通仿真行为保留，沙箱普通启动阻断器仍有效。此处描述4A随机接入；完整执行器见4B账本说明，同轮重试/去重仍不在范围内。
具体规则见 [随机协议运行接入补充](random-runtime-integration.md)。

## 集成验证

Testcontainers 是标准集成方式；以下基础准备测试在Docker不可用时明确跳过，不会回退到源数据库，跳过不视为验收通过。4B完整执行验收要求Docker真正可用，不采用自动跳过：

```powershell
.\mvnw.cmd '-DforkCount=1' '-Dtest=SandboxWorkspacePreparerIT,SandboxScenarioWorkspacePreparerIT,SandboxRunWorkspacePreparerIT' test
```

如需显式使用 XAMPP，必须先完成预置并设置以上沙箱账号环境变量：

```powershell
$env:SANDBOX_XAMPP_IT = 'true'
.\mvnw.cmd '-DforkCount=1' '-Dtest=SandboxWorkspaceXamppIT,SandboxScenarioWorkspaceXamppIT,SandboxRunWorkspaceXamppIT,SandboxJpaSchemaValidationXamppIT' test
```

项目正式编译目标仍为 Java 17，本机执行环境为 JDK 25。当前验证使用 `forkCount=1`；随机协议的新 JVM 测试依赖 Surefire 测试 classpath，`forkCount=0` 会导致其子进程找不到测试探针类，不能据此判断随机协议失败。
# 5A 后台管理基础闭环

本机管理配置、异步任务、独立JVM执行和记录查询见 [phase5a-management.md](phase5a-management.md)。管理功能默认关闭，需管理员增量升级控制结构到v7并配置独立沙箱连接；不改变普通仿真数据源。取消、暂停、恢复及前端接入不在基础闭环范围内。

[5A基础闭环验收](phase5a-acceptance.md)：27类156项定向联合测试全部通过，Boot打包工作进程启动检查通过；尚未升级或启用本机XAMPP沙箱。

5A运行管理补全的操作规则、v8增量升级、取消接口和显式异常收尾命令见
[phase5a-lifecycle.md](phase5a-lifecycle.md)。该阶段不实现前端、暂停或恢复；本机升级需另行执行管理员预置，不能在活动任务期间执行。

[5A运行管理补全验收](phase5a-lifecycle-acceptance.md)：28类170项定向联合测试全部通过，包含取消、原子收尾、异常检查和正式48轮回归；打包工作进程启动检查通过。本机XAMPP仍未在本阶段升级或启用。

## 5B-0 用户接入准备

新增只读页面契约、Vue3类型定义及取消后收尾失败的阶段判定见
[phase5b-0-page-contract.md](phase5b-0-page-contract.md)。既有接口保留，页面轮询不自动执行全量核验。

## 5B-3 结果与导出

Vue3任务列表／详情可按executionId查看结果；冻结配置、逐轮指标、默认200轮趋势、显式完整核验和全量JSON/CSV导出见 [结果说明](phase5b-3-results.md)、[验收记录](phase5b-3-acceptance.md)。无需手动核验即可查看／导出；导出校验manifest及全部评价载荷，不等价于完整业务事实核验。继续使用控制结构v9，不新增迁移，不读取普通仿真的最新评价。

2026-10-02本机XAMPP已在线增量升级到控制结构v8，保留原有业务数据和场景/运行版本，管理开关仍默认关闭。升级前沙箱SQL备份及校验报告在`E:/mysql_backup/sandbox-before-v8-20261002-101616/`。

`mysql.db`升级后普通/扩展检查均返回OK，但有索引文件尺寸警告；尚未认定根因、未执行修复、不能视为完全无警告的系统表健康验收。详细验收与后续边界见[phase5b-0-acceptance.md](phase5b-0-acceptance.md)。

## 5C地图与6A稳定性收尾

只读逐轮地图、端点关系、车辆详情和播放规则见[5C说明](phase5c-map.md)。播放只切换已记录轮次，不暂停或推进后台仿真；底图失败可独立重载，已有普通地图SDK不重置。

[6A实施范围](phase6a-stability.md)、[6A验收记录](phase6a-acceptance.md)：五阶段顺序检查完成，包含正式基线两策略40/75/100轮及100轮重复，共8次、630轮；逐轮事实与评价容差比较通过。另完成司机/自动事件分支、取消/失败前缀、历史隔离、浏览器配置到地图闭环与打包专用入口检查。完整过程只用Testcontainers，未操作本机正式库或XAMPP。

100轮是验收规模，不是硬限制。完整账本约随轮次增长且完整核验有较高内存/时间开销，不随页面轮询触发；全部2602 POI同步绘制也有明显开销，默认只显示关联点位。性能测量的范围、原始样本与限制见验收记录，不承诺生产SLA。真实高德路径事实未冻结，6B重新执行复现产品工具仍不在本阶段范围。

## 同页模式导航

主仿真与沙箱通过同一标签页切换；入口样式与实时评价一致，沙箱顶部提供系统返回按钮。运行/操作中禁止跨模式导航，普通暂停空闲后允许，沙箱内部导航不受影响。状态读取、失败关闭与浏览器验收边界见[模式导航说明](mode-navigation.md)。这是界面保护，不替代后端执行互斥，也不新增数据库迁移。

## 逐轮读取兼容性修复

2026-10-04修复MySQL Connector/J返回`LocalDateTime`时地图、评价分页及导出的时间误判，并限定沙箱异常处理优先级，保留普通接口契约。后端63项、Vue3 21项回归通过，已有48轮历史执行通过只读服务核对；在线页面需加载新后端后再检查，未重新运行实验。范围与验收边界见[读取兼容性修复记录](result-read-compatibility-fix.md)。
