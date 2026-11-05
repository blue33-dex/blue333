# 本机启动后端：MySQL / SeaweedFS 由 Docker 提供（MySQL 映射在 13306）。
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File backend\run-local.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# 从 infra\.env 读取演示配置（跳过注释与空行；键名允许包含数字，如 S3_ACCESS_KEY）
Get-Content (Join-Path $root 'infra\.env') |
  Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    Set-Item -Path "env:$key" -Value $value.Trim()
  }

# 本机运行时覆盖容器内的服务名
$env:SPRING_PROFILES_ACTIVE = 'docker'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13306/ordermgmt?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:S3_ENDPOINT = 'http://127.0.0.1:8333'

Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force -Path logs | Out-Null
java -jar target\ordermgmt-backend-0.0.1-SNAPSHOT.jar *>> logs\backend.log
