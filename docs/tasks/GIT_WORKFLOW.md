# Git 工作流 · 单笔宣传物料订单管控系统

> 适用：Windows + PowerShell + Git 2.54 + GitHub HTTPS
> 远程：origin = https://github.com/blue33-dex/blue333
> 禁止：force push、推送 main、覆盖远程历史、自动修复 Git 状态

---

## 快速入口

```powershell
# 检查当前仓库状态（只读，不修改任何文件）
powershell -ExecutionPolicy Bypass -File .\scripts\git-check.ps1

# 执行安全发布（分支 = feature/xxx，经过人工确认）
powershell -ExecutionPolicy Bypass -File .\scripts\git-publish.ps1 -Branch feature/xxx
```

---

## 分支策略

| 分支 | 用途 | 允许 |
|---|---|---|
| `main` | 主分支，已合并的稳定代码 | ✅ 只接收 PR 合并，**禁止直接 push** |
| `feature/*` | 功能分支（如 `feature/p1-docker-setup`） | ✅ 本脚本允许 |
| `bugfix/*` | Bug 修复分支 | ✅ 本脚本允许 |
| `hotfix/*` | 紧急修复分支 | ⚠️ 需人工评估风险，本脚本默认不支持 |

**禁止**：直接 push main / master、force push、rebase 公共分支历史。

---

## 发布流程

### Step 1：运行 git-check.ps1（每次发布前必做）

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\git-check.ps1
```

检查内容：

1. Git 可用性 + 仓库存在
2. 远程地址匹配目标仓库
3. 当前分支 + 最近提交
4. 工作区状态（已跟踪修改 / 已暂存 / 未跟踪）
5. 敏感文件扫描（.env / .key / .pem / 私钥 / 证书 / secrets 目录）
6. .gitignore 根目录存在性
7. 构建命令实际运行（`npm run build` 等）
8. 远程同步状态（只读 fetch，不修改本地）

**FAIL 项必须全部修复后才能继续**。WARN 项需人工确认影响。

### Step 2：运行 git-publish.ps1

```powershell
# 示例：Phase 1 Batch 1 发布
powershell -ExecutionPolicy Bypass -File .\scripts\git-publish.ps1 -Branch feature/p1-batch1

# 示例：自定义提交信息
powershell -ExecutionPolicy Bypass -File .\scripts\git-publish.ps1 -Branch feature/p1-batch1 -Message "feat: P1-T22 Vue 3 脚手架"
```

**git-publish.ps1 内置安全机制**：

| 检查点 | 行为 |
|---|---|
| 目标分支是 main/master | ❌ 直接退出（红线）|
| 远程 origin 不匹配目标仓库 | ⚠️ 要求人工输入 OK 确认才能继续 |
| 暂存区含敏感文件 | ❌ 直接退出 + 列出文件 |
| 构建未通过 | ❌ 直接退出 |
| 未人工确认 | ❌ 退出 |
| 推送失败 | ❌ 不自动重试、不自动修复，给出下一步建议 |

### Step 3：GitHub 上创建 Pull Request

push 成功后，在 GitHub 仓库创建 PR：

- Source: `feature/p1-batch1` → Target: `main`
- 描述中注明包含的 Task 编号（如 "含 P1-T22 Vue 3 脚手架"）
- 关联 docs/tasks/TASKS_PHASE_1.md 中的验收标准

### Step 4：PR Review & Merge

PR 合并由人工执行（本脚本不自动 merge）。

---

## 不应该提交的文件

| 类型 | 覆盖规则 |
|---|---|
| 环境变量与密钥 | `.env*`（`.env.example` 除外）、`*.key`、`*.pem`、`*.crt`、`*.p12`、`*.jks`、`*.keystore`、`*.token`、`secrets/`、`certificates/` |
| 私钥 | `id_rsa`、`id_ecdsa`、`id_ed25519` |
| IDE 配置 | `.idea/`、`.vscode/*`（`.vscode/extensions.json` 除外）|
| 构建产物 | `dist/`、`target/`、`build/`、`node_modules/` |
| 本地数据库 | `*.db`、`*.sqlite`、`*.mdb` |
| 本地 Docker 数据 | `ordermgmt-mysql-data/`、`ordermgmt-minio-data/`、`data/` |
| 日志 | `*.log`、`logs/` |
| 临时文件 | `*.tmp`、`*.bak`、`*.swp`、`*~` |

根目录 `.gitignore` 已覆盖以上规则。`frontend/.gitignore` 覆盖 node_modules / dist 等。

---

## 紧急场景处理

### 场景 1：远程推送被拒（403 / non-fast-forward）

```powershell
# 只读 fetch 更新远程引用
git fetch origin
# 查看差异
git log HEAD...origin/main --oneline
# 不强制 rebase，优先 create new commit on top
# 如果必须 rebase（且确认风险），先 stash 本地改动：
git stash
git rebase origin/main
git stash pop
```

### 场景 2：本地误提交敏感文件

```powershell
# 历史中移除但不丢失工作区文件
git filter-branch --force --index-filter `
  "git rm --cached --ignore-unmatch path/to/secret" `
  --prune-empty --tag-name-filter cat -- --all
# 加入 .gitignore 防止再次提交
echo ".env" >> .gitignore
git add .gitignore
git commit -m "chore: add .env to gitignore"
# 强制推送（⚠️ 仅限紧急敏感数据泄露，需人工评估）
git push --force --all
```

### 场景 3：忘记用 git-publish.ps1 直接 push 了

```powershell
# git-publish.ps1 禁止 force push
# 手动 push 后如需回退：
git revert HEAD~1..HEAD  # 创建 revert 提交（安全、不重写历史）
git push origin feature/xxx
# 不要用 git reset --hard + force push（重写远程历史）
```

---

## PowerShell 注意事项

| 规则 | 说明 |
|---|---|
| 不用 `&&` / `||` | PowerShell 5 不支持 bash 链式连接符。用分号或拆分为多次命令 |
| 不用 `rm -rf` | 用 `Remove-Item -Recurse -Force` |
| 执行策略 | 运行脚本需加 `-ExecutionPolicy Bypass`（或先 Set-ExecutionPolicy） |
| 路径引号 | 含空格的路径必须加引号 `"D:\path with spaces\file.txt"` |

---

## 环境快照（截至 2026-10-26）

| 组件 | 版本 |
|---|---|
| Git | 2.54.0.windows.1 |
| Node.js | v24.21.0 |
| npm | 11.19.0 |
| Java | 17.0.5 LTS |
| Maven | 未安装（后续评估 Gradle Wrapper）|
| Docker | 未安装（后续评估 Docker Desktop）|
| 当前分支 | main（跟踪 origin/main，无 ahead/behind）|
| 远程仓库 | https://github.com/blue33-dex/blue333 |
| 根目录 .gitignore | ✅ 已创建 |
| backend/ | 尚未初始化（Phase 1 后续）|
