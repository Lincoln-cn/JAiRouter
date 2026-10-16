#!/usr/bin/env bash
# =============================================================================
#  branch-cleanup.sh —— 清理已合并分支 + 梳理「合并得不干净」的分支
#  （与 scripts/test/branch-cleanup.ps1 同一套判据；本仓惯例 .ps1/.sh 双份）
#
#  为什么不能直接用 `git branch --merged`：
#    本仓 PR 用 squash 合并 ⇒ 分支提交**从未成为基线祖先**。实测 80 个本地分支里
#    ancestry 只认出 6 个，而内容早已进主线的有 67 个。据此删除会漏掉九成。
#
#  两条内容判据（由强到弱）：
#    A. `git merge-tree --write-tree --merge-base=<mb> <base> <branch>` 得到的树
#       是否**等于**基线树（不动工作区/索引；需 git ≥ 2.38）。
#    B. A 不成立时，算「分支相对 merge-base 的改动」在基线里的**存活率**
#       （双向：新增行要在基线里、删除行要不在基线里），并在 blob 相同时走快路径。
#       只有「整份文件零存活」或「分支新建的文件在基线里不存在」才算内容没落地，
#       否则记为 churn（改动已进基线、后续被改写）。
#
#  安全默认：**dry-run**。只有 `--apply` 才真删；删除用 `git branch -D`
#  （必需：squash 合并的分支 `-d` 会被 git 拒绝），且当前分支/worktree 占用/
#  基线/受保护模式一律不动。远端服务端删除需 `--apply --delete-remote`。
#
#  用法：
#    scripts/test/branch-cleanup.sh                      # 只分析，打印命令
#    scripts/test/branch-cleanup.sh --include-remote     # 连远端跟踪分支一起分析
#    scripts/test/branch-cleanup.sh --format markdown    # 出可贴 issue 的报告
#    scripts/test/branch-cleanup.sh --out-dir .tmp-bc    # 报告落盘 md+json
#    scripts/test/branch-cleanup.sh --apply              # 执行本地删除
#    scripts/test/branch-cleanup.sh --include-remote --apply --delete-remote
#
#  退出码：0=无待删；2=有可删候选；3=存在需人工确认项；1=前置条件不满足
# =============================================================================
set -uo pipefail

BASE=""
REMOTE=""
INCLUDE_REMOTE=0
APPLY=0
DELETE_REMOTE=0
NO_FETCH=0
FORMAT="text"
OUT_DIR=""
REVIEW_CHURN=0
OLDER_THAN_DAYS=0
PROTECT_PATTERNS=()

IGNORE_RE='(^|/)package-lock\.json$|(^|/)pnpm-lock\.yaml$|(^|/)yarn\.lock$|(^|/)pom\.xml$|(^|/)docs-versions\.json$|(^|/)sitemap\.xml$|^docs/.*versions?\.json$|(^|/)target/|(^|/)node_modules/'

die() { echo "❌ $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base) BASE="$2"; shift 2 ;;
    --base=*) BASE="${1#*=}"; shift ;;
    --remote) REMOTE="$2"; shift 2 ;;
    --remote=*) REMOTE="${1#*=}"; shift ;;
    --include-remote) INCLUDE_REMOTE=1; shift ;;
    --apply) APPLY=1; shift ;;
    --delete-remote) DELETE_REMOTE=1; shift ;;
    --no-fetch) NO_FETCH=1; shift ;;
    --format) FORMAT="$2"; shift 2 ;;
    --format=*) FORMAT="${1#*=}"; shift ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --out-dir=*) OUT_DIR="${1#*=}"; shift ;;
    --review-churn) REVIEW_CHURN=1; shift ;;
    --older-than-days) OLDER_THAN_DAYS="$2"; shift 2 ;;
    --older-than-days=*) OLDER_THAN_DAYS="${1#*=}"; shift ;;
    --protect) PROTECT_PATTERNS+=("$2"); shift 2 ;;
    --protect=*) PROTECT_PATTERNS+=("${1#*=}"); shift ;;
    --repo-root) REPO_ROOT_OVERRIDE="$2"; shift 2 ;;
    --repo-root=*) REPO_ROOT_OVERRIDE="${1#*=}"; shift ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) die "未知参数：$1（--help 看用法）" ;;
  esac
done

REPO_ROOT="${REPO_ROOT_OVERRIDE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
[[ -d "$REPO_ROOT/.git" ]] || die "不是 git 仓库（或 .git 不可见）：$REPO_ROOT"
cd "$REPO_ROOT" || die "无法进入仓库：$REPO_ROOT"

git_() { git "$@" 2>/dev/null; }

# ⚠️ 必须在 set -u 下显式初始化所有聚合数组：旧 bash 对 `${arr[@]}` 的 `unbound variable` 判定
#    相当严格（实测 Git Bash 直接在 `"${DUP_GROUPS[@]}"` 上炸），`:-` 默认值在这里不可靠。
OUT_ROWS=()
EVIDENCE_ROWS=()
DEL_LOCAL=()
DEL_REMOTE=()
REV_ITEMS=()
CHURN_ITEMS=()
DUP_GROUPS=()
PROTECT_PATTERNS=()
SHA_OF=()

# ---------------------------------------------------------------- 解析基线/远端
if [[ -z "$REMOTE" ]]; then
  if git remote | grep -qx github; then REMOTE=github
  elif git remote | grep -qx origin; then REMOTE=origin
  else REMOTE="$(git remote | head -1)"
  fi
fi

resolve_ref() { git rev-parse --verify --quiet "$1^{commit}" 2>/dev/null; }

if [[ -z "$BASE" ]]; then
  for cand in "$REMOTE/master" "$REMOTE/main" origin/master origin/main master main; do
    if resolve_ref "$cand" >/dev/null; then BASE="$cand"; break; fi
  done
fi
[[ -n "$BASE" ]] || die "无法解析基线分支；可先 git fetch --prune，或用 --base <远端/分支> 指定"
resolve_ref "$BASE" >/dev/null || die "基线不存在：$BASE"

if [[ "$NO_FETCH" -eq 0 && -n "$REMOTE" ]]; then
  for rm in $(printf '%s\n' "$REMOTE" origin | awk 'NF && !seen[$0]++'); do
    git remote | grep -qx "$rm" || continue
    echo "  (git fetch $rm --prune)" >&2
    git_ fetch "$rm" --prune --quiet
  done
fi
BASE_SHA="$(resolve_ref "$BASE")"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"

# worktree 占用分支（git branch -D 会拒绝）
WORKTREE_BRANCHES=""
while IFS= read -r line; do
  case "$line" in
    "branch refs/heads/"*) WORKTREE_BRANCHES="$WORKTREE_BRANCHES ${line#branch refs/heads/}" ;;
  esac
done < <(git worktree list --porcelain 2>/dev/null)

is_protected() {
  local name="$1"
  case "$name" in master|main|develop|release/*|hotfix/*) return 0 ;; esac
  local p
  for p in "${PROTECT_PATTERNS[@]:-}"; do
    [[ -z "$p" ]] && continue
    # shellcheck disable=SC2254
    case "$name" in $p) return 0 ;; esac
  done
  return 1
}

is_worktree_branch() {
  local name="$1"
  [[ " $WORKTREE_BRANCHES " == *" $name "* ]]
}

# ---------------------------------------------------------------- 判据 A / B
# 输出（全局变量，避免子 shell）：
#   V_VERDICT = contained|patch|churn|review|tree-conflict
#   V_RATIO   存活率（0..1，awk 计算）
#   V_ZERO    整份文件零存活的数量
#   V_NEWMISS 分支新建但基线没有的文件数量
#   V_EVIDENCE 明细行（制表符分隔：kind|path|added|addedFound|removed|removedGone）
classify() {
  local base="$1" branch="$2" mb="$3"
  V_VERDICT="tree-conflict"; V_RATIO=""; V_ZERO=0; V_NEWMISS=0; V_EVIDENCE=""

  if [[ "$mb" == "$branch" ]]; then V_VERDICT="contained"; V_RATIO="1.000"; return; fi

  local merged_tree
  merged_tree="$(git_ merge-tree --write-tree --merge-base="$mb" "$base" "$branch" | head -1)"
  local residual=()
  if [[ "$merged_tree" =~ ^[0-9a-f]{40,64}$ ]]; then
    if [[ "$merged_tree" == "$(git rev-parse "$base^{tree}")" ]]; then
      V_VERDICT="contained"; V_RATIO="1.000"; return
    fi
    while IFS= read -r f; do [[ -n "$f" ]] && residual+=("$f"); done < <(git_ diff --name-only "$base" "$merged_tree")
  else
    while IFS= read -r f; do [[ -n "$f" ]] && residual+=("$f"); done < <(git_ diff --name-only "$mb..$branch")
  fi

  # 过滤生成物
  local filtered=() f
  for f in "${residual[@]:-}"; do
    [[ -z "$f" ]] && continue
    [[ "$f" =~ $IGNORE_RE ]] && continue
    filtered+=("$f")
  done
  residual=("${filtered[@]:-}")

  if [[ ${#residual[@]} -eq 0 || -z "${residual[0]:-}" ]]; then
    V_VERDICT="patch"; V_RATIO="1.000"; return
  fi

  local total_chg=0 total_ok=0 zero=0 newmiss=0 partial=0
  local diff_all added removed base_blob mb_blob a r line text_file text
  for f in "${residual[@]}"; do
    if ! git cat-file -e "$base:$f" 2>/dev/null; then
      if git cat-file -e "$mb:$f" 2>/dev/null; then
        V_EVIDENCE+="deleted-in-base|$f|0|0|0|0"$'\n'
      else
        newmiss=$((newmiss+1))
        V_EVIDENCE+="new-in-branch|$f|0|0|0|0"$'\n'
      fi
      continue
    fi

    base_blob="$(git rev-parse "$base:$f" 2>/dev/null)"
    mb_blob="$(git rev-parse "$mb:$f" 2>/dev/null)"

    diff_all="$(git_ diff "$mb..$branch" -- "$f")"
    # 行长阈值 3：自检实测「>8」会把 F-branch 这类短行整条滤掉，导致零存活信号丢失
    added="$(printf '%s\n' "$diff_all" | grep '^+' | grep -v '^+++' | cut -c2- | awk 'length($0)>=3' | sort -u)"
    removed="$(printf '%s\n' "$diff_all" | grep '^-' | grep -v '^---' | cut -c2- | awk 'length($0)>=3' | sort -u)"
    local n_add n_del
    n_add="$(printf '%s\n' "$added" | grep -c . || true)"
    n_del="$(printf '%s\n' "$removed" | grep -c . || true)"

    if [[ -n "$base_blob" && "$base_blob" == "$mb_blob" ]]; then
      total_chg=$((total_chg + n_add + n_del)); total_ok=$((total_ok + n_add + n_del))
      continue
    fi
    [[ $((n_add + n_del)) -eq 0 ]] && continue

    # 基线该文件的当前内容（每文件一次 git show；原 PS 版是每行一次 git grep，跑不完）。
    # 用 awk 一趟数出「新增行仍在基线」「删除行已不在基线」——避免逐行 fork grep。
    text_file="$(mktemp)"
    git_ show "$base:$f" > "$text_file" 2>/dev/null || true
    a_found="$(printf '%s\n' "$added"   | awk 'NR==FNR{b[$0]=1;next} NF&&($0 in b){c++} END{print c+0}' "$text_file" -)"
    r_gone="$( printf '%s\n' "$removed" | awk 'NR==FNR{b[$0]=1;next} NF&&!($0 in b){c++} END{print c+0}' "$text_file" -)"
    rm -f "$text_file"

    total_chg=$((total_chg + n_add + n_del)); total_ok=$((total_ok + a_found + r_gone))
    if [[ $((a_found + r_gone)) -eq 0 ]]; then
      zero=$((zero+1))
      V_EVIDENCE+="lost|$f|$n_add|$a_found|$n_del|$r_gone"$'\n'
    elif [[ $((a_found + r_gone)) -lt $((n_add + n_del)) ]]; then
      partial=$((partial+1))
      V_EVIDENCE+="partial|$f|$n_add|$a_found|$n_del|$r_gone"$'\n'
    fi
  done

  V_ZERO=$zero; V_NEWMISS=$newmiss
  if [[ $total_chg -gt 0 ]]; then
    V_RATIO="$(awk -v ok="$total_ok" -v t="$total_chg" 'BEGIN{printf "%.3f", ok/t}')"
  else
    V_RATIO="1.000"
  fi

  if [[ $zero -gt 0 || $newmiss -gt 0 ]]; then V_VERDICT="review"
  elif [[ $partial -gt 0 ]]; then V_VERDICT="churn"
  else V_VERDICT="patch"; fi
}

# ---------------------------------------------------------------- PR 台账（gh，可选）
declare -A PR_OPEN PR_NUMS
if command -v gh >/dev/null 2>&1; then
  url="$(git remote get-url "$REMOTE" 2>/dev/null || true)"
  if [[ "$url" =~ github\.com[:/]([^/]+)/([^/.]+) ]]; then
    repo="${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
    if json="$(gh pr list --repo "$repo" --state all --limit 500 --json number,headRefName,state 2>/dev/null)"; then
      while IFS=$'\t' read -r name num state; do
        [[ -z "$name" ]] && continue
        PR_NUMS["$name"]="${PR_NUMS[$name]:-}${PR_NUMS[$name]:+,}#$num"
        [[ "$state" == "OPEN" ]] && PR_OPEN["$name"]=1
      done < <(printf '%s' "$json" | python3 -c '
import json,sys
try: prs=json.load(sys.stdin)
except Exception: sys.exit(0)
for p in prs: print("%s\t%s\t%s" % (p.get("headRefName",""), p.get("number",""), p.get("state","")))
' 2>/dev/null)
      echo "  （已读取 GitHub PR 台账：${#PR_NUMS[@]} 个 head 分支）" >&2
    fi
  fi
fi

# ---------------------------------------------------------------- 扫描
scan_kind() {
  local kind="$1"   # local | remote
  local fmt='%(refname:short)%09%(refname)'
  local pattern="refs/heads/"
  [[ "$kind" == remote ]] && pattern="refs/remotes/$REMOTE/"
  while IFS=$'\t' read -r short full; do
    [[ -z "$full" ]] && continue
    [[ "$full" == "refs/remotes/$REMOTE/HEAD" ]] && continue
    local name="$short"
    if [[ "$kind" == remote && "$short" == "$REMOTE/"* ]]; then name="${short#"$REMOTE/"}"; fi
    [[ "$kind" == local && "$name" == "$CURRENT_BRANCH" ]] && { OUT_ROWS+=("local|$name|protected-current|0|0|1.000|0|0"); continue; }

    local sha mb ahead date_iso
    sha="$(resolve_ref "$full")" || continue
    [[ -z "$sha" ]] && continue
    mb="$(git merge-base "$BASE" "$sha" 2>/dev/null)"
    [[ -z "$mb" ]] && continue
    ahead="$(git rev-list --count "$BASE..$sha" 2>/dev/null || echo 0)"

    local ancestor=0
    git merge-base --is-ancestor "$sha" "$BASE" 2>/dev/null && ancestor=1

    V_VERDICT="contained"; V_RATIO="1.000"; V_ZERO=0; V_NEWMISS=0; V_EVIDENCE=""
    if [[ $ancestor -eq 0 && "$ahead" -gt 0 ]]; then
      classify "$BASE" "$sha" "$mb"
    fi

    local bucket
    if is_worktree_branch "$name"; then bucket="protected-worktree"
    elif is_protected "$name"; then bucket="protected-pattern"
    elif [[ -n "${PR_OPEN[$name]:-}" ]]; then bucket="has-open-pr"
    elif [[ "$ahead" -eq 0 && $ancestor -eq 1 ]]; then bucket="contained"
    elif [[ "$V_VERDICT" == contained ]]; then bucket="contained"
    elif [[ "$V_VERDICT" == patch ]]; then bucket="patch"
    elif [[ "$V_VERDICT" == churn ]]; then bucket="churn"
    else
      date_iso="$(git show -s --format=%cI "$sha" 2>/dev/null || true)"
      local age=-1
      [[ -n "$date_iso" ]] && age=$(( ( $(date +%s) - $(date -d "$date_iso" +%s 2>/dev/null || echo "$(date +%s)") ) / 86400 ))
      if [[ $age -ge 14 ]]; then bucket="orphan"; else bucket="review"; fi
    fi

    if [[ "$OLDER_THAN_DAYS" -gt 0 && -n "${date_iso:-}" ]]; then
      local d=$(( ( $(date +%s) - $(date -d "$date_iso" +%s 2>/dev/null || echo "$(date +%s)") ) / 86400 ))
      [[ $d -lt "$OLDER_THAN_DAYS" ]] && continue
    fi

    OUT_ROWS+=("$kind|$name|$bucket|$ahead|$ancestor|$V_RATIO|$V_ZERO|$V_NEWMISS")
    if [[ -n "$V_EVIDENCE" ]]; then
      while IFS= read -r ev; do
        [[ -n "$ev" ]] && EVIDENCE_ROWS+=("$kind|$name|$ev")
      done <<< "$V_EVIDENCE"
    fi
    echo "  · $name → $bucket" >&2
  done < <(git for-each-ref --format="$fmt" "$pattern" 2>/dev/null)
}

OUT_ROWS=()
EVIDENCE_ROWS=()
echo "分支清理分析中…（基线 $BASE @ ${BASE_SHA:0:8}；模式 $([[ $APPLY -eq 1 ]] && echo 'APPLY（会真删）' || echo 'dry-run')）" >&2
scan_kind local
[[ $INCLUDE_REMOTE -eq 1 ]] && scan_kind remote

# ---------------------------------------------------------------- 汇总
row_field() { printf '%s' "$1" | cut -d'|' -f"$2"; }

for row in "${OUT_ROWS[@]}"; do
  kind="$(row_field "$row" 1)"; name="$(row_field "$row" 2)"; bucket="$(row_field "$row" 3)"
  case "$bucket" in
    contained|patch|churn)
      if [[ "$kind" == local ]]; then DEL_LOCAL+=("$name"); else DEL_REMOTE+=("$name"); fi
      [[ "$bucket" == churn && $REVIEW_CHURN -eq 1 ]] && CHURN_ITEMS+=("$row") ;;
    review|orphan) REV_ITEMS+=("$row") ;;
  esac
done

# 重复分支：同一 sha 的多个 heads（用 name→sha 映射）
SHA_OF=()
for row in "${OUT_ROWS[@]}"; do
  kind="$(row_field "$row" 1)"; name="$(row_field "$row" 2)"
  [[ "$kind" == local ]] || continue
  s="$(git rev-parse "refs/heads/$name" 2>/dev/null || true)"
  [[ -n "$s" ]] && SHA_OF["$s"]="${SHA_OF[$s]:-}${SHA_OF[$s]:+,}$name"
done
for s in "${!SHA_OF[@]}"; do
  [[ "${SHA_OF[$s]}" == *,* ]] && DUP_GROUPS+=("${s:0:8}|${SHA_OF[$s]}")
done

count_bucket() {
  local want="$1" kind="$2" n=0 row
  for row in "${OUT_ROWS[@]}"; do
    [[ "$(row_field "$row" 1)" != "$kind" ]] && continue
    [[ "$(row_field "$row" 3)" == "$want" ]] && n=$((n+1))
  done
  echo "$n"
}

emit_text() {
  echo ""
  echo "================ 分支清理分析 ================"
  echo "仓库      ：$REPO_ROOT"
  echo "基线      ：$BASE @ ${BASE_SHA:0:8}"
  echo "期望远端  ：$REMOTE    判据：A=树等价；B=改动存活率（整份零存活 / 新文件缺失 才算没落地）"
  echo ""
  echo "▶ 本地分支分布"
  printf '   %-30s %s\n' '已合并(判据A)        可删' "$(count_bucket contained local)"
  printf '   %-30s %s\n' '内容已在主线(判据B)  可删' "$(count_bucket patch local)"
  printf '   %-30s %s\n' '已在内(后续被改写)   可删' "$(count_bucket churn local)"
  printf '   %-30s %s\n' '需人工确认           禁删' "$(count_bucket review local)"
  printf '   %-30s %s\n' '孤儿提交             禁删' "$(count_bucket orphan local)"
  printf '   %-30s %s\n' '有开放 PR            保留' "$(count_bucket has-open-pr local)"
  printf '   %-30s %s\n' '受保护               保留' "$(count_bucket protected-current local)"
  echo ""
  echo "▶ ⚠️ 需人工确认 / 孤儿（不要直接删，先看证据）"
  [[ ${#REV_ITEMS[@]} -eq 0 ]] && echo "   （无）"
  for row in "${REV_ITEMS[@]}"; do
    [[ -z "$row" ]] && continue
    name="$(row_field "$row" 2)"
    echo "   [$(row_field "$row" 3)] $name   ahead=$(row_field "$row" 4) 存活率=$(row_field "$row" 6) PR=${PR_NUMS[$name]:-无}"
    for ev in "${EVIDENCE_ROWS[@]}"; do
      [[ "$ev" == *"|$name|"* ]] || continue
      k="$(printf '%s' "$ev" | cut -d'|' -f3)"; p="$(printf '%s' "$ev" | cut -d'|' -f4)"
      [[ "$k" == partial ]] && continue
      case "$k" in
        new-in-branch) echo "        - $p（**分支新建的文件，基线里没有**）" ;;
        lost) echo "        - $p（整份改动零存活）" ;;
        deleted-in-base) echo "        - $p（基线已删除，视为被取代）" ;;
      esac
    done
  done
  echo ""
  echo "▶ 重复分支（heads 指向同一 commit）"
  [[ ${#DUP_GROUPS[@]} -eq 0 ]] && echo "   （无）"
  for d in "${DUP_GROUPS[@]}"; do
    [[ -z "$d" ]] && continue
    echo "   ${d%%|*}  ← ${d#*|}"
  done
  echo ""
  echo "================ 执行命令（确认后再跑） ================"
  echo "   # 本地（-D 必需：squash 合并的分支用 -d 会被 git 拒绝）"
  [[ ${#DEL_LOCAL[@]} -eq 0 ]] && echo "   # （无）"
  for n in "${DEL_LOCAL[@]}"; do [[ -n "$n" ]] && echo "   git branch -D $n"; done
  if [[ $INCLUDE_REMOTE -eq 1 && ${#DEL_REMOTE[@]} -gt 0 ]]; then
    echo ""
    echo "   # 远端（在服务端删除分支）"
    for n in "${DEL_REMOTE[@]}"; do [[ -n "$n" ]] && echo "   git push $REMOTE --delete $n"; done
  fi
}

emit_markdown() {
  echo "# 分支清理报告（dry-run）"
  echo ""
  echo "- 生成时间：$(date '+%Y-%m-%d %H:%M:%S')"
  echo "- 仓库：\`$REPO_ROOT\`"
  echo "- 基线：\`$BASE\` @ \`${BASE_SHA:0:8}\`"
  echo ""
  echo "## 概览（本地）"
  echo ""
  echo "| 分类 | 数量 |"
  echo "| --- | --- |"
  echo "| 已合并（判据 A，可删） | $(count_bucket contained local) |"
  echo "| 内容已在主线（判据 B，可删） | $(count_bucket patch local) |"
  echo "| 已在内、后续被改写（可删） | $(count_bucket churn local) |"
  echo "| 需人工确认 | $(count_bucket review local) |"
  echo "| 孤儿提交 | $(count_bucket orphan local) |"
  echo ""
  echo "## 需人工确认（**不要直接删**）"
  echo ""
  [[ ${#REV_ITEMS[@]} -eq 0 ]] && echo "无。"
  for row in "${REV_ITEMS[@]}"; do
    [[ -z "$row" ]] && continue
    name="$(row_field "$row" 2)"
    echo "### [$(row_field "$row" 3)] \`$name\`"
    echo ""
    echo "- ahead=$(row_field "$row" 4)；存活率=$(row_field "$row" 6)；PR=${PR_NUMS[$name]:-（无）}"
    echo "- 证据文件："
    for ev in "${EVIDENCE_ROWS[@]}"; do
      [[ "$ev" == *"|$name|"* ]] || continue
      k="$(printf '%s' "$ev" | cut -d'|' -f3)"; p="$(printf '%s' "$ev" | cut -d'|' -f4)"
      [[ "$k" == partial ]] && continue
      echo "    - \`$p\`（$k）"
    done
    echo ""
  done
  echo "## 执行命令"
  echo ""
  echo '```bash'
  for n in "${DEL_LOCAL[@]}"; do [[ -n "$n" ]] && echo "git branch -D $n"; done
  echo '```'
}

emit_json() {
  # 手写 JSON（不依赖 jq）：字段与 PS 版对齐
  echo "{"
  echo "  \"generatedAt\": \"$(date -Iseconds)\","
  echo "  \"repoRoot\": \"${REPO_ROOT//\\/\\\\}\","
  echo "  \"base\": \"$BASE\","
  echo "  \"baseSha\": \"$BASE_SHA\","
  echo "  \"remoteName\": \"$REMOTE\","
  echo "  \"counts\": {\"localTotal\": $(count_bucket contained local), \"needReview\": ${#REV_ITEMS[@]}, \"deletableLocal\": ${#DEL_LOCAL[@]}, \"deletableRemote\": ${#DEL_REMOTE[@]}},"
  echo "  \"local\": ["
  local first=1 row
  for row in "${OUT_ROWS[@]}"; do
    [[ "$(row_field "$row" 1)" == local ]] || continue
    [[ $first -eq 0 ]] && echo ","
    first=0
    printf '    {"name": "%s", "bucket": "%s", "ahead": %s, "ancestorOfBase": %s, "survivedRatio": %s, "zeroSurvivorFiles": %s, "newFilesMissing": %s}' \
      "$(row_field "$row" 2)" "$(row_field "$row" 3)" "$(row_field "$row" 4)" "$(row_field "$row" 5)" "$(row_field "$row" 6)" "$(row_field "$row" 7)" "$(row_field "$row" 8)"
  done
  echo ""
  echo "  ]"
  echo "}"
}

case "$FORMAT" in
  text) emit_text ;;
  markdown|md) emit_markdown ;;
  json) emit_json ;;
  *) die "未知 --format：$FORMAT（text|markdown|json）" ;;
esac

if [[ -n "$OUT_DIR" ]]; then
  mkdir -p "$OUT_DIR"
  stamp="$(date '+%Y%m%d-%H%M%S')"
  FORMAT=markdown emit_markdown > "$OUT_DIR/branch-cleanup-$stamp.md"
  FORMAT=json emit_json > "$OUT_DIR/branch-cleanup-$stamp.json"
  echo "报告已写入：$OUT_DIR/branch-cleanup-$stamp.{md,json}"
fi

# ---------------------------------------------------------------- 执行删除
if [[ $APPLY -eq 1 ]]; then
  echo ""
  echo "== 执行删除 =="
  failed=0
  for n in "${DEL_LOCAL[@]}"; do
    [[ -z "$n" ]] && continue
    if git branch -D "$n" >/dev/null 2>&1; then echo "  [OK]   已删除本地分支 $n"
    else echo "  [FAIL] 删除失败 $n"; failed=$((failed+1)); fi
  done
  if [[ $DELETE_REMOTE -eq 1 && $INCLUDE_REMOTE -eq 1 ]]; then
    for n in "${DEL_REMOTE[@]}"; do
      [[ -z "$n" ]] && continue
      if git push "$REMOTE" --delete "$n" >/dev/null 2>&1; then echo "  [OK]   已删除远端分支 $REMOTE/$n"
      else echo "  [FAIL] 远端删除失败 $n"; failed=$((failed+1)); fi
    done
  fi
  [[ $failed -gt 0 ]] && echo "  失败 $failed 个" >&2
elif [[ ${#DEL_LOCAL[@]} -gt 0 ]]; then
  echo ""
  echo "（dry-run：以上均为建议，未改动任何分支；确认后加 --apply）"
fi

if [[ ${#REV_ITEMS[@]} -gt 0 ]]; then exit 3; fi
if [[ ${#DEL_LOCAL[@]} -gt 0 || ${#DEL_REMOTE[@]} -gt 0 ]]; then exit 2; fi
exit 0
