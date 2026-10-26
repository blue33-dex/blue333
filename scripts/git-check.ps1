# git-check.ps1 — 只读 Git 仓库安全检查
# 用途：执行安全检查并生成报告，不修改任何 Git 历史或工作区文件
# 用法：powershell -ExecutionPolicy Bypass -File .\scripts\git-check.ps1

$ErrorActionPreference = "Continue"
$report = New-Object System.Collections.ArrayList
function Add-Report($msg, $level = "INFO") {
    [void]$report.Add(("[$level] " + $msg))
    $color = switch ($level) {
        "OK"    { "Green" }
        "WARN"  { "Yellow" }
        "FAIL"  { "Red" }
        "INFO"  { "Cyan" }
        default { "White" }
    }
    Write-Host ("[$level] " + $msg) -ForegroundColor $color
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Git 安全检查 · git-check.ps1" -ForegroundColor Cyan
Write-Host (" 时间：" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss")) -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ============ 1. Git 可用性 ============
Add-Report "检查 Git 可用性..."
$gitVer = & git --version 2>&1
if ($LASTEXITCODE -eq 0) { Add-Report "Git 可用，版本 $gitVer" "OK" }
else { Add-Report "Git 不可用：$gitVer" "FAIL" }

$inRepo = & git rev-parse --is-inside-work-tree 2>&1
if ($inRepo -eq "true") { Add-Report "当前目录是 Git 仓库" "OK" }
else { Add-Report "当前目录不是 Git 仓库（退出）" "FAIL" ; return }

# ============ 2. 远程仓库地址 ============
Add-Report "" ; Add-Report "检查远程仓库..."
$remotes = & git remote -v 2>&1
if ($remotes -match "https://github\.com/blue33-dex/blue333") {
    Add-Report "远程 origin 地址匹配目标仓库" "OK"
    Add-Report "  $remotes" "INFO"
} elseif ($remotes -match "^origin\s") {
    Add-Report "远程 origin 存在但地址不匹配目标仓库，需人工确认" "WARN"
    Add-Report "  $remotes" "INFO"
} else {
    Add-Report "未配置远程仓库 origin" "WARN"
}

# ============ 3. 分支与提交 ============
Add-Report "" ; Add-Report "检查分支与提交..."
$branches = & git branch -v --no-abbrev 2>&1
Add-Report $branches "INFO"

$last = & git log --oneline -5 2>&1
if ($LASTEXITCODE -eq 0 -and $last) {
    Add-Report "最近提交：" "INFO"
    $last -split "`n" | ForEach-Object { Add-Report "  $_" "INFO" }
}

# ============ 4. 工作区状态 ============
Add-Report "" ; Add-Report "检查工作区状态..."
$status = & git status --porcelain 2>&1
$modified = @()
$untracked = @()
$staged = @()

if ($status) {
    $status | ForEach-Object {
        $code = $_.Substring(0,2)
        $path = $_.Substring(3)
        if ($code.StartsWith("M") -or $code.StartsWith("A") -or $code.StartsWith("D") -or $code.StartsWith("R")) {
            $staged += $path
        }
        if ($code.EndsWith("M") -or $code.EndsWith("D")) {
            $modified += $path
        }
        if ($code.StartsWith("??")) {
            $untracked += $path
        }
    }
}

Add-Report "已暂存：$($staged.Count) 项" $(if($staged.Count -eq 0){"OK"}else{"WARN"})
Add-Report "已跟踪修改：$($modified.Count) 项" $(if($modified.Count -eq 0){"OK"}else{"WARN"})
Add-Report "未跟踪：$($untracked.Count) 项" $(if($untracked.Count -eq 0){"OK"}else{"WARN"})

if ($untracked.Count -gt 0) {
    Add-Report "未跟踪文件/目录：" "INFO"
    $untracked | ForEach-Object { Add-Report "  $_" "INFO" }
}

# ============ 5. 敏感文件扫描 ============
Add-Report "" ; Add-Report "扫描敏感文件..."
$sensitivePatterns = @(
    "\.env$", "\.env\.", "^\.env$",
    "\.key$", "\.pem$", "\.crt$", "\.p12$", "\.jks$", "\.keystore$",
    "id_rsa", "id_ecdsa", "id_ed25519",
    "\.token$", "secrets?/", "certificates?/"
)
$sensitiveFound = @()
Get-ChildItem -Recurse -Force -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -notmatch "\\node_modules\\" -and `
    $_.FullName -notmatch "\\\.git\\" -and `
    $_.FullName -notmatch "\\dist\\" -and `
    $_.FullName -notmatch "\\target\\" -and `
    $_.FullName -notmatch "\\build\\"
} | ForEach-Object {
    foreach ($pat in $sensitivePatterns) {
        if ($_.Name -match $pat -or $_.FullName -match $pat) {
            $sensitiveFound += $_.FullName
            break
        }
    }
}
if ($sensitiveFound.Count -eq 0) {
    Add-Report "未发现敏感文件" "OK"
} else {
    Add-Report "发现 $($sensitiveFound.Count) 个敏感文件（以下文件不得提交）：" "FAIL"
    $sensitiveFound | ForEach-Object { Add-Report "  $_" "FAIL" }
}

# ============ 6. .gitignore 检查 ============
Add-Report "" ; Add-Report "检查 .gitignore..."
if (Test-Path ".gitignore") {
    Add-Report "根目录 .gitignore 存在" "OK"
} else {
    Add-Report "根目录 .gitignore 缺失（建议创建）" "FAIL"
}
if (Test-Path "frontend/.gitignore") { Add-Report "frontend/.gitignore 存在" "OK" }

# ============ 7. 构建/测试命令检查 ============
Add-Report "" ; Add-Report "检查构建/测试命令..."
if (Test-Path "frontend/package.json") {
    $pkg = Get-Content "frontend/package.json" -Raw | ConvertFrom-Json
    Add-Report "frontend scripts:" "INFO"
    foreach ($k in $pkg.scripts.PSObject.Properties.Name) {
        Add-Report ("  {0} : {1}" -f $k, $pkg.scripts.$k) "INFO"
    }
    # 实际运行 build 验证
    Push-Location frontend
    Write-Host "  运行 npm run build ..."
    $buildOut = & npm.cmd run build 2>&1
    if ($LASTEXITCODE -eq 0) { Add-Report "frontend npm run build 通过" "OK" }
    else { Add-Report "frontend npm run build 失败" "FAIL" }
    Pop-Location
} else {
    Add-Report "frontend/package.json 不存在（前端尚未初始化）" "INFO"
}

$hasMaven = Test-Path "backend/pom.xml"; $hasGradle = Test-Path "backend/build.gradle"
if ($hasMaven -or $hasGradle) {
    Add-Report "后端构建文件存在，建议运行对应编译命令验证" "INFO"
} else {
    Add-Report "后端构建文件不存在（backend 尚未初始化）" "INFO"
}

# ============ 8. 与远程同步状态 ============
Add-Report "" ; Add-Report "检查远程同步状态（只读 fetch）..."
try {
    & git fetch --quiet origin 2>&1 | Out-Null
    $aheadBehind = & git rev-list --left-right --count HEAD...@{u} 2>&1
    if ($aheadBehind -match "^(\d+)\s+(\d+)$") {
        $ahead = $Matches[1]; $behind = $Matches[2]
        Add-Report "本地领先远程 $ahead 提交，落后远程 $behind 提交" $(if($ahead -gt 0 -or $behind -gt 0){"WARN"}else{"OK"})
    }
} catch {
    Add-Report "远程同步检查跳过：$($_.Exception.Message)" "WARN"
}

# ============ 9. 汇总 ============
Add-Report "" ; Add-Report "========================================" -ForegroundColor Cyan
Add-Report " 检查完成 · 汇总" -ForegroundColor Cyan
Add-Report "========================================" -ForegroundColor Cyan

$failCount = ($report | Where-Object { $_ -match "\[FAIL\]" }).Count
$warnCount = ($report | Where-Object { $_ -match "\[WARN\]" }).Count
$okCount   = ($report | Where-Object { $_ -match "\[OK\]"   }).Count

Add-Report "OK: $okCount   WARN: $warnCount   FAIL: $failCount" $(if($failCount -gt 0){"FAIL"}elseif($warnCount -gt 0){"WARN"}else{"OK"})

if ($failCount -gt 0) {
    Add-Report "" ; Add-Report "下一步：修复 FAIL 项后重新运行 git-check.ps1" "FAIL"
} elseif ($warnCount -gt 0) {
    Add-Report "" ; Add-Report "下一步：确认 WARN 项影响后运行 git-publish.ps1" "WARN"
} else {
    Add-Report "" ; Add-Report "下一步：可安全运行 git-publish.ps1 执行发布" "OK"
}

Write-Host ""
