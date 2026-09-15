<#
.SYNOPSIS
把仓库里带 sprint-1 标签的 open Issue 挂到 GitHub Project 看板，并把 Status 置为"待办"。

.DESCRIPTION
create-sprint1-issues.ps1 只建 Issue 和标签，这个脚本负责第二步：挂板。
可以重复运行：已经在看板上的 Issue 不会重复添加，Status 会被重新置为 -TodoName。
需要 gh 已登录且令牌带 project 权限（gh auth refresh -s project,read:project）。

.PARAMETER ProjectNumber
用户级 Project 的编号，即看板地址 /users/<owner>/projects/<编号> 里的数字。

.PARAMETER TodoName
Status 字段里"待办"那一列的名字，默认 待办。

.PARAMETER DryRun
只打印将要执行的 gh 命令，不真的调用。
#>
param(
    [string]$Owner = "CPU-JIA",
    [string]$Repo = "liuhen",
    [int]$ProjectNumber = 2,
    [string]$TodoName = "待办",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
# gh 输出 UTF-8，Windows 控制台默认按本地代码页解码会把中文列名读坏，先声明再解析
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 参数不能叫 $Args：与 PowerShell 自动变量 $args 撞名，展开为空
function Invoke-Gh {
    param([string[]]$GhArgs)
    # 本地代理偶发把 api.github.com 连接掐断（EOF），重试三次再放弃
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $out = & gh @GhArgs 2>&1
        if ($LASTEXITCODE -eq 0) { return ($out -join "`n") }
        if ($attempt -lt 3) { Start-Sleep -Seconds 2 }
    }
    throw "gh 失败：$out"
}

# 1. 查看板的 id、Status 字段 id、待办列的 option id（都是查询，DryRun 也照跑）
$project = Invoke-Gh @("project", "view", "$ProjectNumber", "--owner", $Owner, "--format", "json") | ConvertFrom-Json
$fields = Invoke-Gh @("project", "field-list", "$ProjectNumber", "--owner", $Owner, "--format", "json") | ConvertFrom-Json
$statusField = $fields.fields | Where-Object { $_.name -eq "Status" }
if (-not $statusField) { throw "看板里没有 Status 字段" }
$todoOption = $statusField.options | Where-Object { $_.name -eq $TodoName }
if (-not $todoOption) { throw "Status 字段里没有名为 $TodoName 的列，现有列：$($statusField.options.name -join '、')" }
Write-Host "看板：$($project.title)（$($project.id)），Status 字段 $($statusField.id)，$TodoName 列 $($todoOption.id)"

# 2. 取带 sprint-1 标签的 open Issue
$issues = Invoke-Gh @("api", "repos/$Owner/$Repo/issues?state=open&labels=sprint-1&per_page=100") | ConvertFrom-Json
$issues = $issues | Sort-Object number
Write-Host "待挂板 Issue：$($issues.Count) 条"

# 3. 逐条挂板并置待办
foreach ($issue in $issues) {
    $addArgs = @("project", "item-add", "$ProjectNumber", "--owner", $Owner, "--url", $issue.html_url, "--format", "json")
    if ($DryRun) {
        Write-Host "gh $($addArgs -join ' ')"
        Write-Host "gh project item-edit --project-id $($project.id) --id <item id> --field-id $($statusField.id) --single-select-option-id $($todoOption.id)"
        continue
    }
    $item = Invoke-Gh $addArgs | ConvertFrom-Json
    $editArgs = @("project", "item-edit", "--project-id", $project.id, "--id", $item.id,
        "--field-id", $statusField.id, "--single-select-option-id", $todoOption.id)
    Invoke-Gh $editArgs | Out-Null
    Write-Host ("#{0} {1} -> {2}" -f $issue.number, $issue.title, $TodoName)
}
if (-not $DryRun) { Write-Host "完成。看板地址：$($project.url)" }
