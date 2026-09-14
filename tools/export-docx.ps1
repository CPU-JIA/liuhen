<#
.SYNOPSIS
    把指定实验 04-最终版 目录下的 Markdown 批量导出为 Word 文档。

.DESCRIPTION
    依赖本机 Pandoc。输出到同目录下的 docx/ 子目录，文件名与 Markdown 同名。
    默认使用 docs/templates/reference.docx 作为样式模板；不存在则用 Pandoc 默认样式。

.PARAMETER Exp
    两位实验编号，例如 01、02、12。

.PARAMETER All
    导出所有实验的 04-最终版。

.EXAMPLE
    pwsh tools/export-docx.ps1 -Exp 01
    pwsh tools/export-docx.ps1 -All
#>
[CmdletBinding()]
param(
    [ValidatePattern('^\d{2}$')]
    [string]$Exp,
    [switch]$All
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command pandoc -ErrorAction SilentlyContinue)) {
    Write-Error '未找到 pandoc，请先安装：https://pandoc.org/installing.html'
    exit 1
}

if (-not $Exp -and -not $All) {
    Write-Error '请指定 -Exp <两位编号> 或 -All'
    exit 1
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$docsRoot = Join-Path $repoRoot 'docs'
$reference = Join-Path $docsRoot 'templates\reference.docx'

$pattern = if ($All) { 'Exp*' } else { "Exp$Exp-*" }
$expDirs = Get-ChildItem -Path $docsRoot -Directory -Filter $pattern

if (-not $expDirs) {
    Write-Error "未找到匹配 $pattern 的实验目录"
    exit 1
}

$exported = 0
foreach ($dir in $expDirs) {
    $finalDir = Join-Path $dir.FullName '04-最终版'
    if (-not (Test-Path $finalDir)) { continue }

    $mdFiles = Get-ChildItem -Path $finalDir -Filter '*.md' -File
    if (-not $mdFiles) {
        Write-Host "[$($dir.Name)] 04-最终版 下没有 Markdown，跳过"
        continue
    }

    $outDir = Join-Path $finalDir 'docx'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    foreach ($md in $mdFiles) {
        $out = Join-Path $outDir ($md.BaseName + '.docx')
        $args = @('-f', 'markdown', '-t', 'docx', '--toc', '--toc-depth=2', '-o', $out, $md.FullName)
        if (Test-Path $reference) { $args += @('--reference-doc', $reference) }

        try {
            & pandoc @args
            Write-Host "[$($dir.Name)] $($md.Name) -> docx/$($md.BaseName).docx"
            $exported++
        }
        catch {
            Write-Warning "[$($dir.Name)] 导出失败：$($md.Name)。$($_.Exception.Message)"
        }
    }
}

Write-Host "完成，共导出 $exported 个文件。"
