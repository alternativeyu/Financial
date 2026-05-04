# 在未配置 PATH 中的 mvn 时，尝试自动找到 IntelliJ 自带的 Maven 并启动网关。
# 用法：在 gateway 目录执行  .\run.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Find-MvnCmd {
    $inPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($inPath) {
        return $inPath.Source
    }
    $roots = @(
        (Join-Path $env:LOCALAPPDATA "JetBrains\Toolbox\apps"),
        (Join-Path $env:ProgramFiles "JetBrains"),
        (${env:ProgramFiles(x86)} + "\JetBrains")
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $hit = Get-ChildItem -Path $root -Filter "mvn.cmd" -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\maven3\\bin\\mvn\.cmd$' } |
            Select-Object -First 1
        if ($hit) {
            return $hit.FullName
        }
    }
    return $null
}

$mvn = Find-MvnCmd
if (-not $mvn) {
    Write-Host "未找到 mvn（PATH 与常见 IntelliJ 安装路径均未发现 maven3\bin\mvn.cmd）。" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "可选方案：" -ForegroundColor Cyan
    Write-Host "  1) 安装 Apache Maven，并把 bin 加入系统 PATH：https://maven.apache.org/download.cgi"
    Write-Host "  2) 在 IntelliJ IDEA 中打开项目 D:\Financial\gateway，运行主类 com.financial.gateway.FinancialApiGatewayApplication"
    Write-Host "  3) 使用 Chocolatey：choco install maven"
    exit 1
}

Write-Host "使用 Maven: $mvn" -ForegroundColor Green
& $mvn spring-boot:run @args
