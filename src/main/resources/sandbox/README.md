# 数据沙箱数据库与场景管理

当前准备链路包含三个阶段：

1. 从正式 `baseline-v1.json` 恢复静态基础数据，状态为 `BASE_DATA_READY`；
2. 编译、发布并恢复一个不可变场景 Revision，状态为 `SCENARIO_DATA_READY`。
3. 编译、发布并恢复确定性运行规格和车辆初态，状态为 `RUN_SPEC_READY`。

`RUN_SPEC_READY`表示场景数据、统一随机协议、运行规格和车辆初态已准备完成，但路径事实和完整仿真执行仍未实现。激活 `sandbox-runtime` profile 时，应用仍会以 `SANDBOX_NOT_RUN_READY` 主动拒绝正式仿真启动。

## 一次性预置或升级

在进程环境中设置 `SANDBOX_DB_PASSWORD`。管理员密码非空时另设
`MYSQL_ADMIN_PASSWORD`，然后运行：

```powershell
.\scripts\provision-sandbox.ps1
```

脚本幂等创建或升级 `vehicle_scheduler_sandbox`、安全标记、场景和运行规格控制表，并创建最小权限账号
`road_sandbox_runtime@localhost`。该账号只拥有 `vehicle_scheduler_sandbox.*` 权限。准备程序通过
`information_schema`核对当前账号授权；若发现全局权限或任何`vehicle_scheduler`权限便拒绝执行，且不会向源库业务表发送查询，因此不能用`root`运行准备命令。

## 命令环境

```powershell
$env:SANDBOX_DB_USER = 'road_sandbox_runtime'
$env:SANDBOX_DB_PASSWORD = '<仅在本机进程中设置>'
$env:SANDBOX_DB_URL = 'jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL'
```

所有命令都使用独立 Java 入口，不启动普通 Spring Boot 组件、定时循环、数据导入器或仿真初始化器。

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

保存草稿、显式发布、恢复指定 Revision 和只读核验：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=save-draft --scenario=classpath:sandbox/scenarios/default-all-eligible-v1.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=publish-scenario --scenario-key=all-eligible'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=prepare-scenario --scenario-key=all-eligible --revision=1'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=verify-scenario'
```

导出已发布 Revision（JSON 输出到标准输出，可由调用方保存为文件）：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=export-scenario --scenario-key=all-eligible --revision=1'
```

场景准备总是从正式基线重新计算并重建业务表，不以上一个场景的数据库内容为修改基础。全部沙箱控制表会保留，运行期业务表会清空。相同定义重复发布复用原 Revision；显示名称和说明不参与数据版本指纹。

## 运行规格编译、发布和准备

默认模板位于 `classpath:sandbox/runs/default-production-original-v1.json`。运行规格只允许
`PRODUCTION`需求、关闭启动预生成，并选择已注册的`ORIGINAL`或`HEURISTIC`算法 Profile。

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=compile-run-spec --run-spec=classpath:sandbox/runs/default-production-original-v1.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=save-run-draft --run-spec=classpath:sandbox/runs/default-production-original-v1.json'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=publish-run-spec --run-spec-key=production-baseline-seed-20260927'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=prepare-run --run-spec-key=production-baseline-seed-20260927 --revision=1'
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=verify-run'
```

导出Revision或核对一个派生子种子：

```powershell
.\mvnw.cmd -Psandbox-workspace-cli exec:java '-Dexec.args=export-run-spec --run-spec-key=production-baseline-seed-20260927 --revision=1'
$keyJson = '{"vehicleId":1}'
$keyBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($keyJson)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
.\mvnw.cmd -Psandbox-workspace-cli exec:java "-Dexec.args=derive-seed --run-spec-key=production-baseline-seed-20260927 --revision=1 --domain=VEHICLE_INITIAL_POI --key=base64url:$keyBase64"
```

`--key`也可在不会吞掉引号的调用环境中直接接收JSON对象。PowerShell经Maven调用时推荐使用上面的`base64url:`传输形式；解码后的JSON对象才参与规范化和种子派生，编码形式不参与随机协议。

同一运行规格Revision会物化同一组车辆初始POI；固定POI优先，未固定车辆按`vehicleId`派生独立随机流。`RUN_SPEC_READY`仍不是正式仿真可运行状态。

## 集成验证

Testcontainers 是标准集成方式；Docker 不可用时测试明确跳过，不会回退到源数据库：

```powershell
.\mvnw.cmd '-DforkCount=0' '-Dtest=SandboxWorkspacePreparerIT,SandboxScenarioWorkspacePreparerIT,SandboxRunWorkspacePreparerIT' test
```

如需显式使用 XAMPP，必须先完成预置并设置以上沙箱账号环境变量：

```powershell
$env:SANDBOX_XAMPP_IT = 'true'
.\mvnw.cmd '-DforkCount=0' '-Dtest=SandboxWorkspaceXamppIT,SandboxScenarioWorkspaceXamppIT,SandboxRunWorkspaceXamppIT,SandboxJpaSchemaValidationXamppIT' test
```

本机 JDK 25 下 Maven Surefire 的分叉进程可能因工作区位于不同盘符而产生 classpath 问题，因此示例显式使用 `-DforkCount=0`；项目正式编译目标仍为 Java 17。
