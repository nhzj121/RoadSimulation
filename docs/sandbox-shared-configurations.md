# 数据沙箱共享配置使用说明

仓库中的 `src/main/resources/sandbox/shared-config/v1/` 包含 3 个场景和 8 个运行规格。拉取代码后，将它们发布到本机沙箱数据库，就能从现有页面选择和启动。

## 一次导入全部配置

1. 启动本机 MySQL 或 MariaDB。首次使用时，按项目的沙箱初始化流程配置受保护的 `vehicle_scheduler_sandbox` 库，可使用仓库的 `scripts/provision-sandbox.ps1`。运行全局优化需要控制结构 v10。
2. 启动项目后端。默认沙箱账号为 `road_sandbox_runtime`，在后端运行配置中提供 `SANDBOX_DB_PASSWORD`。若本机设置不同，通过 `SANDBOX_DB_URL` 和 `SANDBOX_DB_USER` 配置后端。
3. 使用 Node.js 20 或更新版本，在仓库根目录运行：

   ```powershell
   node scripts/import-sandbox-config.mjs
   ```

   默认连接 `http://localhost:8080/api/sandbox`。若后端使用其他本机端口，传入 `--api-base=http://localhost:端口/api/sandbox`，把 `端口` 换成实际端口。
4. 导入成功后，终端会列出本机发布的场景和规格 Revision。打开数据沙箱的「配置与发布」，可从「查看场景列表」和「查看规格列表」加载草稿。运行时在「运行规格与发布」输入规格 Key 和终端返回的 Revision，点击「读取 v2 发布版本」，再点「准备并运行」。只导入配置时到第 3 步即可。

导入会核对基线与文件指纹，先发布场景，再按本机返回的修订号和指纹绑定运行规格。重复导入会复用相同的发布结果。遇到同 Key 的不同草稿会报 `EXISTING_DRAFT_DIFFERS`，请先检查本机配置。若要同时保留两套内容，可将共用配置用新 Key 手动导入。发生冲突前已成功发布的场景会保留，也可以在解决冲突后重新执行导入。

## 使用页面单独导入

1. 在「场景配置」页点击「导入 JSON」，粘贴 `scenarios/` 下对应文件的内容。应用到本地草稿后，保存并发布场景。
2. 打开「运行规格与发布」，点击「导入 JSON」，粘贴 `runs/` 下对应文件的内容。运行 JSON 会在导入时校验绑定。若本机发布的场景 Revision 或指纹不同，先生成一次绑定该场景的运行模板、导出 JSON，再将模板中的 `scenario` 字段整体替换到待导入的运行 JSON 中。
3. 保存、发布运行规格。使用该规格自己的已发布 Revision 启动。

已有同 Key 时，页面保存前需要加载并核对该 Key 的现有草稿。要保留已有配置，导入前先给新定义换一个 Key。

## 使用后端 API 启动

使用批量导入输出中的 `runSpecKey` 和本机 `revision`。例如在 PowerShell 里启动货物级重插实验，先把 `$revision` 设为本机输出的实际编号：

```powershell
$revision = 1  # 替换为导入输出的 revision
$body = @{
    runSpecKey = 'demo-v1-a-reference-ga'
    revision = $revision
    clientRequestId = [guid]::NewGuid().ToString()
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/sandbox/start-requests' `
    -Headers @{ 'X-Sandbox-Request' = '1' } -ContentType 'application/json' -Body $body
```

用回执的 `jobId` 到任务页查看进度。批量导入只保存和发布配置，这条启动请求才开始实验。

## 配置来源

三个场景均取源库 Rev 1。`demo-v1-full` 是全部合格车辆，`demo-v1-matched` 是等额定载重大车车队，`demo-v1-small` 是小载重车队。

| 运行规格 Key | 来源及用途 |
| --- | --- |
| `demo-v1-a-reference` | 源库 Rev 1，48 轮，原始需求配置，`ORIGINAL` |
| `demo-v1-a-reference-ga` | 源库 Rev 4，48 轮，全局多目标优化（货物级重插） |
| `demo-v1-a-reference-thing` | 源库 Rev 1，导入包将 `events.autoEnabled` 设为 `true` |
| `demo-v1-b-strategy` | 源库 Rev 1，`HEURISTIC` |
| `demo-v1-c-rain` | 源库 Rev 1，持续降雨 |
| `demo-v1-d-small` | 源库 Rev 1，小载重资源受限 |
| `demo-v1-e-matched` | 源库 Rev 1，等额定载重大车 |
| `demo-v1-f-events` | 源库 Rev 1，自动事件 |

`thing` 和 F 组的实验参数相同，各自保留独立的 Key 和名称。上表的 Revision 用于追溯来源。本机从空库首次发布这些配置时，编号通常从 1 开始。共享包只提供配置，结果和逐轮账本由本机执行产生。
