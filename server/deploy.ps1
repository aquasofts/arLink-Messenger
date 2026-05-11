# NearLink Server 一键部署（PowerShell 包装）
#
# Windows / WSL 都能用。脚本本身只是把 deploy.sh 通过 git-bash 或 WSL 调起来。
# 用法（PowerShell）:
#   .\deploy.ps1                # 默认 up
#   .\deploy.ps1 up
#   .\deploy.ps1 down
#   .\deploy.ps1 logs
#   .\deploy.ps1 status
#   .\deploy.ps1 rebuild
#   .\deploy.ps1 nuke

param(
    [string]$Cmd = "up"
)

$ErrorActionPreference = "Stop"

function Resolve-Bash {
    # 优先 Git Bash（Windows 上 Docker Desktop 用户常装）
    $candidates = @(
        "$env:ProgramFiles\Git\bin\bash.exe",
        "$env:ProgramFiles(x86)\Git\bin\bash.exe",
        "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
    )
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    # 退而求其次：WSL
    $wsl = Get-Command wsl.exe -ErrorAction SilentlyContinue
    if ($wsl) { return "wsl" }
    return $null
}

$bash = Resolve-Bash
if (-not $bash) {
    Write-Host "找不到 Git Bash 或 WSL。请先安装 Docker Desktop（自带 Git Bash 不是必然），或手动安装 Git for Windows。" -ForegroundColor Red
    Write-Host "也可以直接在 WSL/Linux 终端里运行 ./deploy.sh"
    exit 1
}

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

if ($bash -eq "wsl") {
    & wsl.exe -e bash ./deploy.sh $Cmd
} else {
    & $bash ./deploy.sh $Cmd
}
exit $LASTEXITCODE
