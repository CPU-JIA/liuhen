<#
.SYNOPSIS
  为 Sprint 1 在 GitHub 上建任务板：标签、20 张卡的 Issue（正文是任务勾选清单）、1 个横切 Issue。
.DESCRIPTION
  依据 docs/Exp04-Sprint1任务拆分与工时估算/04-最终版/Sprint1任务清单与工时估算表.md。
  需要已登录 gh（gh auth status）。重复运行会跳过已存在的同名 Issue。
.EXAMPLE
  pwsh tools/create-sprint1-issues.ps1
  pwsh tools/create-sprint1-issues.ps1 -Repo CPU-JIA/liuhen -DryRun
#>
param(
    [string]$Repo = "CPU-JIA/liuhen",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# 参数不能叫 $Args：与 PowerShell 自动变量 $args 撞名，展开为空，gh 会只打印帮助并以 0 退出，脚本误以为成功
function Invoke-Gh {
    param([string[]]$GhArgs)
    if ($DryRun) { Write-Host "gh $($GhArgs -join ' ')"; return "" }
    # 本地代理偶发把 api.github.com 连接掐断（EOF），重试三次再放弃
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $out = & gh @GhArgs 2>&1
        if ($LASTEXITCODE -eq 0) { return $out }
        if ($attempt -lt 3) { Start-Sleep -Seconds 2 }
    }
    throw "gh 失败：$out"
}

# 标签
$labels = @(
    @{ name = "sprint-1"; color = "0E8A16"; desc = "Sprint 1（第 5 到 8 周）" },
    @{ name = "must";     color = "B60205"; desc = "Must 故事" },
    @{ name = "should";   color = "FBCA04"; desc = "Should 故事，有余力再做" },
    @{ name = "cross";    color = "5319E7"; desc = "横切任务" }
)
foreach ($l in $labels) {
    $exists = (Invoke-Gh @("label", "list", "-R", $Repo, "--search", $l.name, "--json", "name", "-q", ".[].name")) -contains $l.name
    if (-not $exists) { Invoke-Gh @("label", "create", $l.name, "-R", $Repo, "--color", $l.color, "--description", $l.desc) | Out-Null }
}

# 卡与任务：标题 | 标签 | 主责 | 任务清单（编号 任务 谁 小时 周）
$issues = @(
    @{ t = "[X] 项目骨架、接口约定、测试环境、文档、评审"; l = "cross"; o = "孟甲"; tasks = @(
        "T-X-1 项目骨架：Spring Boot、Vue、Compose、统一响应格式 · 孟甲 · 6h · 第5周",
        "T-X-2 数据库脚本接入与初始数据 · 杨子航 · 2h · 第5周",
        "T-X-3 前后端接口约定（OpenAPI） · 杨子航 · 3h · 第5周",
        "T-X-4 测试环境与测试数据 · 袁飞翔 · 3h · 第5周",
        "T-X-5 实验 5、6、7 材料与站会记录 · 张家森 · 6h · 第5-8周",
        "T-X-6 Sprint 评审演示脚本与数据 · 孟甲 · 4h · 第8周",
        "T-X-7 代码审查 · 孟甲 · 6h · 第7周",
        "T-X-8 全部手测用例编写 · 张家森 · 4h · 第5周") },
    @{ t = "[ACC-01] 老师开课、发作业"; l = "must"; o = "杨子航"; tasks = @(
        "T-ACC-01-1 课程与作业实体接口，课程码生成，截止校验 · 杨子航 · 4h · 第5周",
        "T-ACC-01-2 建课发作业页面 · 丁浩然 · 4h · 第6周",
        "T-ACC-01-3 手测 4 条 · 袁飞翔 · 1.5h · 第6周") },
    @{ t = "[ACC-02] 学生用课程码进课"; l = "must"; o = "杨子航"; tasks = @(
        "T-ACC-02-1 加入课程接口，重复与大小写 · 杨子航 · 2h · 第6周",
        "T-ACC-02-2 输码进课页面 · 张家森 · 2h · 第6周",
        "T-ACC-02-3 手测 4 条 · 袁飞翔 · 1h · 第6周") },
    @{ t = "[ACC-04] 本地账号与名单导入"; l = "must"; o = "杨子航"; tasks = @(
        "T-ACC-04-1 本地登录、JWT、失败计数与锁定 · 杨子航 · 6h · 第5周",
        "T-ACC-04-2 名单导入 xlsx 与 csv，重复学号整份拒绝 · 杨子航 · 4h · 第6周",
        "T-ACC-04-3 登录、首次改密、导入页；路由守卫与登录态过期 · 丁浩然 · 5h · 第6周",
        "T-ACC-04-4 集成测试 4 条 · 袁飞翔 · 2h · 第6周") },
    @{ t = "[RULE-01] 老师定这门课的 AI 规则"; l = "must"; o = "杨子航"; tasks = @(
        "T-RULE-01-1 规则版本与场景写入，新作业继承 · 杨子航 · 4h · 第6周",
        "T-RULE-01-2 规则引擎 current、check、needsReack（手写） · 杨子航 · 5h · 第6周",
        "T-RULE-01-3 三档与场景勾选页 · 丁浩然 · 4h · 第6周",
        "T-RULE-01-4 手测 5 条加集成 1 条 · 袁飞翔 · 2h · 第6周") },
    @{ t = "[RULE-02] 学生一进作业先看到规则"; l = "must"; o = "丁浩然"; tasks = @(
        "T-RULE-02-1 确认阅读接口 · 杨子航 · 1.5h · 第6周",
        "T-RULE-02-2 规则拦截页，编辑器焦点控制 · 丁浩然 · 3h · 第6周",
        "T-RULE-02-3 手测 3 条 · 袁飞翔 · 1h · 第6周") },
    @{ t = "[WRK-01] 在系统里写，自动保存"; l = "must"; o = "丁浩然"; tasks = @(
        "T-WRK-01-1 保存接口、字符上限、累计改动量 · 周浩然 · 3h · 第5周",
        "T-WRK-01-2 编辑器、5 秒防抖、断网本地缓存与补传 · 丁浩然 · 7h · 第6周",
        "T-WRK-01-3 手测 4 条，含拔网线 · 袁飞翔 · 2h · 第7周") },
    @{ t = "[WRK-02] 版本自动留存"; l = "must"; o = "周浩然"; tasks = @(
        "T-WRK-02-1 MyersDiff 字符级 diff 与 apply（手写） · 周浩然 · 8h · 第5周",
        "T-WRK-02-2 DeltaCodec、关键帧策略、reconstruct · 周浩然 · 5h · 第6周",
        "T-WRK-02-3 触发判断与快照写入，接哈希链 · 周浩然 · 3h · 第6周",
        "T-WRK-02-4 HashChainService append 与 head（手写） · 杨子航 · 4h · 第5周",
        "T-WRK-02-5 MyersDiff 与重建单测，50 快照存储占用 · 袁飞翔、周浩然 · 3h · 第7周") },
    @{ t = "[WRK-03] 大段粘贴时选来源"; l = "must"; o = "丁浩然"; tasks = @(
        "T-WRK-03-1 粘贴监听、100 字符阈值、来源弹窗、稍后 · 丁浩然 · 4h · 第6周",
        "T-WRK-03-2 粘贴事件接口与 PENDING 查询 · 孟甲 · 2h · 第6周",
        "T-WRK-03-3 手测 4 条加单测 1 条 · 袁飞翔 · 1h · 第7周") },
    @{ t = "[WRK-05] 学生查看自己的时间线"; l = "must"; o = "丁浩然"; tasks = @(
        "T-WRK-05-1 学生端时间线页，复用组件 · 丁浩然 · 3h · 第7周",
        "T-WRK-05-2 权限放行本人 · 杨子航 · 1h · 第7周",
        "T-WRK-05-3 两端一致性集成测试 · 袁飞翔 · 1.5h · 第8周") },
    @{ t = "[REG-01] 登记一次 AI 使用"; l = "must"; o = "周浩然"; tasks = @(
        "T-REG-01-1 登记接口、枚举、工具字典、自定义名 · 周浩然 · 4h · 第6周",
        "T-REG-01-2 登记表单，6 次点选内 · 丁浩然 · 5h · 第6周",
        "T-REG-01-3 手测 5 条 · 袁飞翔 · 1.5h · 第7周") },
    @{ t = "[DECL-01] 提交时自动生成 AI 使用声明"; l = "must"; o = "周浩然"; tasks = @(
        "T-DECL-01-1 DeclarationGenerator：汇总、规则对照、逾期、哈希 · 周浩然 · 6h · 第7周",
        "T-DECL-01-2 提交流程：PENDING 阻断、SUBMIT 快照、状态切换 · 周浩然 · 3h · 第7周",
        "T-DECL-01-3 提交页与声明预览 · 丁浩然 · 4h · 第7周",
        "T-DECL-01-4 声明模板文案，七要素对齐南大备案表 · 张家森 · 3h · 第5周",
        "T-DECL-01-5 集成测试 6 条 · 袁飞翔 · 2h · 第8周") },
    @{ t = "[DECL-02] 未使用 AI 的承诺"; l = "must"; o = "孟甲"; tasks = @(
        "T-DECL-02-1 未使用承诺分支 · 孟甲 · 1h · 第7周",
        "T-DECL-02-2 承诺勾选 · 张家森 · 1h · 第7周",
        "T-DECL-02-3 手测 2 条 · 袁飞翔 · 0.5h · 第8周") },
    @{ t = "[TCH-01] 查看作业时间线"; l = "must"; o = "周浩然"; tasks = @(
        "T-TCH-01-1 TimelineAssembler 合并三类条目 · 周浩然 · 4h · 第7周",
        "T-TCH-01-2 时间线组件 · 丁浩然 · 5h · 第7周",
        "T-TCH-01-3 50 快照 3 秒性能测加空态 · 袁飞翔 · 2h · 第8周") },
    @{ t = "[TCH-02] 两个版本对比高亮"; l = "must"; o = "周浩然"; tasks = @(
        "T-TCH-02-1 lineDiff 行级差异（手写） · 周浩然 · 6h · 第7周",
        "T-TCH-02-2 单栏行内高亮组件 · 丁浩然 · 4h · 第7周",
        "T-TCH-02-3 手测 2 条加 50,000 字符单测 · 袁飞翔 · 2h · 第8周") },
    @{ t = "[TCH-03] 只呈现事实"; l = "must"; o = "袁飞翔"; tasks = @(
        "T-TCH-03-1 ForbiddenWords 常量与文案扫描脚本 · 袁飞翔 · 3h · 第6周",
        "T-TCH-03-2 三端文案审查 · 张家森 · 2h · 第8周") },
    @{ t = "[PERM-01] 数据权限分层"; l = "must"; o = "杨子航"; tasks = @(
        "T-PERM-01-1 AccessControl 矩阵、拦截器与注解 · 杨子航 · 6h · 第6周",
        "T-PERM-01-2 五条 403 集成测试 · 袁飞翔 · 3h · 第7周") },
    @{ t = "[RULE-03] 老师写明评分态度（有余力）"; l = "should"; o = "杨子航"; tasks = @("T-RULE-03-1 评分态度字段与显示 · 杨子航 1h、丁浩然 1h · 2h") },
    @{ t = "[REG-02] 修改登记保留历史（有余力）"; l = "should"; o = "周浩然"; tasks = @("T-REG-02-1 登记追加式修改与作废 · 周浩然 3h、丁浩然 2h、袁飞翔 1h · 6h") },
    @{ t = "[REG-03] 登记关联到段落（有余力）"; l = "should"; o = "周浩然"; tasks = @("T-REG-03-1 登记关联段落 · 周浩然 3h、丁浩然 4h、袁飞翔 1h · 8h") },
    @{ t = "[DECL-03] 声明导出 PDF（有余力）"; l = "should"; o = "周浩然"; tasks = @("T-DECL-03-1 声明导出 PDF · 周浩然 4h、丁浩然 1h、袁飞翔 1h · 6h") }
)

$existing = @()
if (-not $DryRun) {
    $existing = (Invoke-Gh @("issue", "list", "-R", $Repo, "--state", "all", "--limit", "200", "--json", "title", "-q", ".[].title"))
}

$created = 0
foreach ($i in $issues) {
    if ($existing -contains $i.t) { Write-Host "已存在，跳过：$($i.t)"; continue }
    $body = "主责：$($i.o)`n`n依据：docs/Exp04-Sprint1任务拆分与工时估算/04-最终版/Sprint1任务清单与工时估算表.md`n`n## 任务`n`n"
    foreach ($task in $i.tasks) { $body += "- [ ] $task`n" }
    $body += "`n完成时在对应行后追加'实际 Nh'。"
    $bodyFile = New-TemporaryFile
    Set-Content -Path $bodyFile -Value $body -Encoding UTF8
    Invoke-Gh @("issue", "create", "-R", $Repo, "--title", $i.t, "--label", "sprint-1,$($i.l)", "--body-file", $bodyFile) | Out-Null
    Remove-Item $bodyFile
    $created++
    Write-Host "已建：$($i.t)"
}
Write-Host "完成，新建 $created 个 Issue。接着在 GitHub Projects 建看板，三列：待办、进行中、完成，把 Issue 拖进待办后截图。"
