# git-publish.ps1 — 安全 Git 发布脚本（需人工确认）
# 用途：显示准备提交的差异 → 人工输入确认 → 提交到 feature 分支 → 推送
# 安全红线：禁止 force push、禁止推送 main 分支、禁止覆盖远程历史
# 用法：powershell -ExecutionPolicy Bypass -File .\scripts\git-publish.ps1 -Branch feature/xxx

param(
    [Parameter(Mandatory=$true, HelpMessage="目标分支名（必须是 feature/* 或其他非 main 分支）")]
    [string]$Branch,
    [Parameter(HelpMessage="提交信息；不填则自动生成")]
    [string]$Message
)

$ErrorActionPreference = "Stop"

# ============= 安全红线 1：禁止推送 main =============
if ($Branch -eq "main" -or $Branch -eq "master") {
    Write-Host "[FAIL] 禁止推送 main/master 分支。请创建 feature/* 或 bugfix/* 分支。" -ForegroundColor Red
    Write-Host "       用法：git checkout -b feature/xxx 然后再运行本脚本" -ForegroundColor Yellow
    exit 1
}

# ============= 前置检查（与 git-check.ps1 不同——这里要严格）=============
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Git 发布安全检查 · git-publish.ps1" -ForegroundColor Cyan
Write-Host (" 目标分支：" + $Branch) -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Git 可用性 + 仓库检查
if (-not (& git rev-parse --is-inside-work-tree 2>&1) -eq "true") {
    Write-Host "[FAIL] 当前目录不是 Git 仓库" -ForegroundColor Red ; exit 1
}

# 2. 远程仓库地址检查
$remoteAddr = (& git remote get-url origin 2>&1)
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] 未配置远程 origin，发布无法继续" -ForegroundColor Red ; exit 1
}
if ($remoteAddr -notmatch "github\.com/blue33-dex/blue333") {
    Write-Host "[WARN] 远程 origin 地址与目标仓库不匹配：$remoteAddr" -ForegroundColor Yellow
    Write-Host "       请确认是否应修改远程地址，或取消本次发布" -ForegroundColor Yellow
    Write-Host ""
    $confirm = Read-Host "输入 OK 继续 / 其他字符取消"
    if ($confirm -ne "OK") { Write-Host "已取消" ; exit 0 }
}
Write-Host "[OK] 远程 origin：$remoteAddr" -ForegroundColor Green

# 3. 暂存区敏感文件检查
Write-Host "" ; Write-Host "--- 暂存区检查 ---"
$gitignorePaths = @()
if (Test-Path ".gitignore") {
    $gitignorePaths += (Get-Content ".gitignore" -Raw)
}
$cachedList = & git diff --cached --name-only 2>&1
if ($cachedList -and $cachedList.Count -gt 0) {
    # 检查暂存区中是否有敏感文件
    $sensitiveInCache = @()
    foreach ($f in $cachedList) {
        if ($f -match "\.env$|\.key$|\.pem$|\.crt$|\.p12$|\.jks$|\.keystore$|id_rsa|id_ecdsa|\.token$") {
            $sensitiveInCache += $f
        }
    }
    if ($sensitiveInCache.Count -gt 0) {
        Write-Host "[FAIL] 暂存区中发现敏感文件（禁止提交）：" -ForegroundColor Red
        $sensitiveInCache | ForEach-Object { Write-Host "  $($_)" -ForegroundColor Red }
        Write-Host "       请先 git reset -- <file> 取消暂存，或加入 .gitignore" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "[OK] 暂存区敏感文件检查通过（$($cachedList.Count) 项已暂存）" -ForegroundColor Green
} else {
    Write-Host "[INFO] 暂存区为空，需先 git add 相关文件" -ForegroundColor Cyan
}

# 4. 未跟踪敏感文件提示
$untracked = & git ls-files --others --exclude-standard 2>&1
if ($untracked.Count -gt 0) {
    Write-Host "" ; Write-Host "--- 未跟踪文件 ---" -ForegroundColor Cyan
    $untracked | ForEach-Object { Write-Host "  [untracked]  $_" -ForegroundColor Gray }
}

# ============= 构建验证 =============
Write-Host "" ; Write-Host "--- 构建验证 ---" -ForegroundColor Cyan
$buildPass = $true
if (Test-Path "frontend/package.json") {
    Push-Location frontend
    $buildOut = & npm.cmd run build 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] frontend npm run build 失败" -ForegroundColor Red ; $buildPass = $false }
    else { Write-Host "[OK] frontend 构建通过" -ForegroundColor Green }
    Pop-Location
}
if (-not $buildPass) {
    Write-Host "" ; Write-Host "[FAIL] 构建未通过，禁止发布。请修复后重试。" -ForegroundColor Red
    exit 1
}

# ============= 暂存所有需要的文件 =============
Write-Host "" ; Write-Host "--- 准备暂存 ---" -ForegroundColor Cyan
Write-Host "提示：即将执行 git add -A（添加所有已跟踪修改 + 未跟踪文件）" -ForegroundColor Yellow
Write-Host "      如果有文件不应该提交，请 Ctrl+C 中断并手动 git add 指定文件" -ForegroundColor Yellow
$confirmAdd = Read-Host "输入 yes 继续 git add -A"
if ($confirmAdd -ne "yes") { Write-Host "已取消（未执行 git add）" ; exit 0 }

& git add -A 2>&1 | Out-Null
Write-Host "[OK] git add -A 完成" -ForegroundColor Green

# ============= 显示差异摘要 =============
Write-Host "" ; Write-Host "--- 准备提交的差异 ---" -ForegroundColor Cyan
Write-Host "按文件统计："
& git diff --cached --stat 2>&1
Write-Host ""
Write-Host "完整差异（仅前 200 行）："
$diffLines = (& git diff --cached 2>&1)
$diffLines | Select-Object -First 200 | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
if ($diffLines.Count -gt 200) { Write-Host "  ... 省略 $($diffLines.Count - 200) 行，运行 git diff --cached 查看完整" -ForegroundColor Yellow }

# ============= 人工二次确认 =============
Write-Host "" ; Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  人工确认阶段" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "即将执行：" -ForegroundColor Yellow
Write-Host "  git checkout -b $Branch          # 创建/切换到新分支" -ForegroundColor White
Write-Host "  git commit -m \"$CommitMsg\"      # 提交" -ForegroundColor White
Write-Host "  git push origin $Branch           # 推送到远程（仅 feature 分支）" -ForegroundColor White
Write-Host ""
Write-Host "红线：" -ForegroundColor Red
Write-Host "  - 禁止 git push --force" -ForegroundColor Red
Write-Host "  - 禁止推送 main/master" -ForegroundColor Red
Write-Host "  - 推送失败不自动重试覆盖" -ForegroundColor Red
Write-Host ""
$finalConfirm = Read-Host "输入 EXACTLY 'PUBLISH' 确认执行上述三步"
if ($finalConfirm -ne "PUBLISH") { Write-Host "已取消发布" ; exit 0 }

# ============= 执行分支切换 + 提交 + 推送 =============
Write-Host "" ; Write-Host "--- 创建分支 $Branch ---" -ForegroundColor Cyan
$exist = & git branch --list $Branch 2>&1
if ($exist -match "\S") {
    & git checkout $Branch 2>&1
} else {
    & git checkout -b $Branch 2>&1
}
if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] git checkout 失败" -ForegroundColor Red ; exit 1 }

Write-Host "" ; Write-Host "--- 提交 ---" -ForegroundColor Cyan
if (-not $Message) {
    # 自动生成简洁提交信息
    $statusCount = (& git diff --cached --name-only 2>&1 | Measure-Object -Line).Lines
    $Message = "feat: Batch $(Get-Date -Format 'yyyyMMdd-HHmm') · $($statusCount) files changed"
}
& git commit -m $Message 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] git commit 失败" -ForegroundColor Red ; exit 1 }

Write-Host "" ; Write-Host "--- 推送到 origin/$Branch ---" -ForegroundColor Cyan
$pushResult = & git push origin $Branch 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] git push 失败" -ForegroundColor Red
    Write-Host "  $pushResult" -ForegroundColor Red
    Write-Host ""
    Write-Host "下一步操作（供人工选择）：" -ForegroundColor Yellow
    Write-Host "  1. 检查远程仓库是否已存在 $Branch（可能需要 git push --set-upstream origin $Branch）"
    Write-Host "  2. 检查 Git 认证状态（可能需要重新登录 GitHub 凭据）"
    Write-Host "  3. 联系仓库所有者确认推送权限"
    exit 1
} else {
    Write-Host "[OK] git push 成功 → origin/$Branch" -ForegroundColor Green
    Write-Host ""
    Write-Host "发布完成。请在 GitHub 上创建 Pull Request 合并到 main。" -ForegroundColor Green
}
