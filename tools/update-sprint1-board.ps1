<#
.SYNOPSIS
按周推进 Sprint 1 任务板：勾选 Issue 里已完成的任务并写实际小时，卡片全部完成就关闭 Issue 并移到"完成"，其余按开工情况置"进行中"或"待办"。

.DESCRIPTION
create-sprint1-issues.ps1 建 Issue，add-sprint1-issues-to-board.ps1 挂板并置待办，这个脚本负责之后每周的移列。
-Week N 表示"第 N 周结束时"的状态：实际完成周 <= N 的任务勾选，卡片全勾则移到完成，卡片里有任务已完成或本周开工则进行中。
第 8 周即评审前的最终状态。任务的实际完成周与实际小时写在下面的 $Actuals 表里，来源是各周站会记录（docs/Exp07 回顾改进项附表）。
可以重复运行，也可以从第 5 周依次跑到第 8 周，每跑一周截一张图（docs/Exp04 任务板截图/任务板-第N周.png）。
需要 gh 已登录且令牌带 project 权限。

.PARAMETER Week
5 到 8。

.PARAMETER DryRun
只打印将要执行的 gh 命令，不真的调用。
#>
param(
    [Parameter(Mandatory = $true)][ValidateRange(5, 8)][int]$Week,
    [string]$Owner = "CPU-JIA",
    [string]$Repo = "liuhen",
    [int]$ProjectNumber = 2,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 任务编号 | 计划周 | 实际完成周（0 = 未做） | 实际小时。Should 卡里没进 Sprint 的两项计划周记 99。
$Actuals = @"
T-X-1|5|5|7
T-X-2|5|5|2
T-X-3|5|6|1.5
T-X-4|5|5|3
T-X-5|5|8|7
T-X-6|8|8|5
T-X-7|7|7|8
T-X-8|5|7|4
T-ACC-01-1|5|5|4
T-ACC-01-2|6|6|4
T-ACC-01-3|6|7|1
T-ACC-02-1|6|6|1.5
T-ACC-02-2|6|6|3
T-ACC-02-3|6|6|0.5
T-ACC-04-1|5|5|7
T-ACC-04-2|6|6|5
T-ACC-04-3|6|6|6
T-ACC-04-4|6|7|3
T-RULE-01-1|6|6|4
T-RULE-01-2|6|6|5
T-RULE-01-3|6|6|4
T-RULE-01-4|6|7|2
T-RULE-02-1|6|6|1
T-RULE-02-2|6|6|3.5
T-RULE-02-3|6|7|0.5
T-WRK-01-1|5|5|2.5
T-WRK-01-2|6|6|9
T-WRK-01-3|7|8|2
T-WRK-02-1|5|5|10
T-WRK-02-2|6|6|5
T-WRK-02-3|6|6|3
T-WRK-02-4|5|5|4
T-WRK-02-5|7|7|4
T-WRK-03-1|6|6|4
T-WRK-03-2|6|6|2
T-WRK-03-3|7|7|1
T-WRK-05-1|7|7|2
T-WRK-05-2|7|7|0.5
T-WRK-05-3|8|7|1.5
T-REG-01-1|6|6|4
T-REG-01-2|6|6|5
T-REG-01-3|7|7|1
T-DECL-01-1|7|6|5
T-DECL-01-2|7|6|3
T-DECL-01-3|7|7|4
T-DECL-01-4|5|5|3
T-DECL-01-5|8|7|2
T-DECL-02-1|7|7|1
T-DECL-02-2|7|7|1
T-DECL-02-3|8|8|0.5
T-TCH-01-1|7|6|3.5
T-TCH-01-2|7|7|6
T-TCH-01-3|8|8|2
T-TCH-02-1|7|6|5
T-TCH-02-2|7|7|4
T-TCH-02-3|8|8|2
T-TCH-03-1|6|6|2.5
T-TCH-03-2|8|8|2
T-PERM-01-1|6|6|6
T-PERM-01-2|7|7|3
T-RULE-03-1|6|6|2
T-REG-02-1|6|7|5
T-REG-03-1|99|0|0
T-DECL-03-1|99|0|0
"@

$tasks = @{}
foreach ($line in $Actuals -split "`n") {
    $line = $line.Trim()
    if (-not $line) { continue }
    $p = $line -split "\|"
    $tasks[$p[0]] = @{ planned = [int]$p[1]; actual = [int]$p[2]; hours = $p[3] }
}

# 参数不能叫 $Args：与 PowerShell 自动变量撞名
function Invoke-Gh {
    param([string[]]$GhArgs)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $out = & gh @GhArgs 2>&1
        if ($LASTEXITCODE -eq 0) { return ($out -join "`n") }
        if ($attempt -lt 3) { Start-Sleep -Seconds 2 }
    }
    throw "gh 失败：$out"
}

function Invoke-GhWrite {
    param([string[]]$GhArgs)
    if ($DryRun) { Write-Host "  gh $($GhArgs -join ' ')"; return "" }
    return Invoke-Gh $GhArgs
}

# 1. 看板、Status 字段、三列的 option id，以及每个 Issue 对应的看板条目
$project = Invoke-Gh @("project", "view", "$ProjectNumber", "--owner", $Owner, "--format", "json") | ConvertFrom-Json
$fields = Invoke-Gh @("project", "field-list", "$ProjectNumber", "--owner", $Owner, "--format", "json") | ConvertFrom-Json
$statusField = $fields.fields | Where-Object { $_.name -eq "Status" }
$option = @{}
foreach ($o in $statusField.options) { $option[$o.name] = $o.id }
foreach ($name in "待办", "进行中", "完成") {
    if (-not $option.ContainsKey($name)) { throw "Status 字段缺少列：$name" }
}
$items = Invoke-Gh @("project", "item-list", "$ProjectNumber", "--owner", $Owner, "--format", "json", "--limit", "100") | ConvertFrom-Json
$itemByNumber = @{}
foreach ($it in $items.items) { if ($it.content.number) { $itemByNumber[[int]$it.content.number] = $it } }

# 2. 带 sprint-1 标签的全部 Issue（含已关闭的，重复运行时要能再处理）
$issues = Invoke-Gh @("api", "repos/$Owner/$Repo/issues?state=all&labels=sprint-1&per_page=100") | ConvertFrom-Json
$issues = $issues | Sort-Object number
Write-Host "第 $Week 周结束时的状态，Issue $($issues.Count) 条"

$summary = @{ "待办" = 0; "进行中" = 0; "完成" = 0 }
foreach ($issue in $issues) {
    $lines = $issue.body -split "`r?`n"
    $doneCount = 0; $total = 0; $started = $false
    $newLines = foreach ($l in $lines) {
        if ($l -match '^- \[[ x]\] (T-[A-Z0-9-]+) (.*?)( · 实际 .*)?$') {
            $id = $Matches[1]; $rest = $Matches[2]
            if (-not $tasks.ContainsKey($id)) { throw "表里没有任务 $id" }
            $t = $tasks[$id]
            $total++
            $done = ($t.actual -gt 0 -and $t.actual -le $Week)
            if ($done) { $doneCount++; "- [x] $id $rest · 实际 $($t.hours)h，第 $($t.actual) 周完成" }
            else { "- [ ] $id $rest" }
            if ($done -or $t.planned -le $Week) { $started = $true }
        }
        else { $l }
    }
    $newBody = ($newLines -join "`n")
    if ($newBody -ne ($issue.body -replace "`r`n", "`n")) {
        $bodyFile = New-TemporaryFile
        Set-Content -Path $bodyFile -Value $newBody -Encoding UTF8 -NoNewline
        Invoke-GhWrite @("issue", "edit", "$($issue.number)", "-R", "$Owner/$Repo", "--body-file", $bodyFile) | Out-Null
        Remove-Item $bodyFile
    }

    if ($total -gt 0 -and $doneCount -eq $total) { $target = "完成" }
    elseif ($started) { $target = "进行中" }
    else { $target = "待办" }
    $summary[$target]++

    # 卡片全部完成：关 Issue；否则保证是打开的（重复运行或回退时用）
    if ($target -eq "完成" -and $issue.state -eq "open") {
        $note = if ($Week -eq 8) { "全部任务完成，评审前确认。" } else { "全部任务完成，第 $($Week + 1) 周站会确认。" }
        Invoke-GhWrite @("issue", "close", "$($issue.number)", "-R", "$Owner/$Repo", "-c", $note) | Out-Null
    }
    elseif ($target -ne "完成" -and $issue.state -eq "closed") {
        Invoke-GhWrite @("issue", "reopen", "$($issue.number)", "-R", "$Owner/$Repo") | Out-Null
    }

    $item = $itemByNumber[[int]$issue.number]
    if (-not $item) { throw "Issue #$($issue.number) 不在看板上，先跑 add-sprint1-issues-to-board.ps1" }
    if ($item.status -ne $target) {
        Invoke-GhWrite @("project", "item-edit", "--project-id", $project.id, "--id", $item.id,
            "--field-id", $statusField.id, "--single-select-option-id", $option[$target]) | Out-Null
    }
    Write-Host ("#{0,-3} {1,-4} {2}/{3} -> {4}  {5}" -f $issue.number, $item.status, $doneCount, $total, $target, $issue.title)
}
Write-Host "待办 $($summary['待办'])、进行中 $($summary['进行中'])、完成 $($summary['完成'])。看板：$($project.url)?layout=board"
