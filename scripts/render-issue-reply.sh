#!/usr/bin/env bash
set -euo pipefail

kind="${1:-}"
language="${2:-}"
if [[ -n "$kind" ]]; then shift; fi
if [[ -n "$language" ]]; then shift; fi

pr_reference=""
branch=""
branch_url=""
version=""
release_url=""
changes=()
tests=()
assets=()

usage() {
  cat <<'EOF'
Usage:
  scripts/render-issue-reply.sh branch zh|en --branch NAME --branch-url URL \
    --change TEXT [--change TEXT ...] --test TEXT [--test TEXT ...]
  scripts/render-issue-reply.sh implementation zh|en --pr REF \
    --change TEXT [--change TEXT ...] --test TEXT [--test TEXT ...]
  scripts/render-issue-reply.sh release zh|en --version X.Y.Z[-PRERELEASE] --release-url URL \
    --asset TEXT [--asset TEXT ...] --test TEXT [--test TEXT ...]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pr)
      pr_reference="${2:-}"
      shift 2
      ;;
    --branch)
      branch="${2:-}"
      shift 2
      ;;
    --branch-url)
      branch_url="${2:-}"
      shift 2
      ;;
    --version)
      version="${2:-}"
      shift 2
      ;;
    --release-url)
      release_url="${2:-}"
      shift 2
      ;;
    --change)
      changes+=("${2:-}")
      shift 2
      ;;
    --test)
      tests+=("${2:-}")
      shift 2
      ;;
    --asset)
      assets+=("${2:-}")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$kind" != "branch" && "$kind" != "implementation" && "$kind" != "release" ]]; then
  echo "Reply kind must be branch, implementation, or release." >&2
  usage >&2
  exit 2
fi
if [[ "$language" != "zh" && "$language" != "en" ]]; then
  echo "Reply language must be zh or en." >&2
  usage >&2
  exit 2
fi
if [[ ${#tests[@]} -eq 0 ]]; then
  echo "At least one --test is required." >&2
  exit 2
fi

print_items() {
  local item
  for item in "$@"; do
    printf -- '- %s\n' "$item"
  done
}

if [[ "$kind" == "branch" ]]; then
  if [[ -z "$branch" || -z "$branch_url" || ${#changes[@]} -eq 0 ]]; then
    echo "Branch replies require --branch, --branch-url, and at least one --change." >&2
    exit 2
  fi

  if [[ "$language" == "zh" ]]; then
    printf '已在分支 [`%s`](%s) 完成修复，等待合并和发布。\n\n改动：\n\n' "$branch" "$branch_url"
    print_items "${changes[@]}"
    printf '\n验证：\n\n'
    print_items "${tests[@]}"
    printf '\n该修复尚未合并或发布。Issue 将保持开启，待包含此修复的 Release 发布并验证后关闭。\n'
  else
    printf 'The fix is available on branch [`%s`](%s) and is awaiting merge and release.\n\nChanges:\n\n' "$branch" "$branch_url"
    print_items "${changes[@]}"
    printf '\nVerification:\n\n'
    print_items "${tests[@]}"
    printf '\nThis fix has not been merged or released yet. The issue will remain open until a published Release includes the fix and the release is verified.\n'
  fi
  exit 0
fi

if [[ "$kind" == "implementation" ]]; then
  if [[ -z "$pr_reference" || ${#changes[@]} -eq 0 ]]; then
    echo "Implementation replies require --pr and at least one --change." >&2
    exit 2
  fi

  if [[ "$language" == "zh" ]]; then
    printf '已在 PR %s 修复。\n\n改动：\n\n' "$pr_reference"
    print_items "${changes[@]}"
    printf '\n验证：\n\n'
    print_items "${tests[@]}"
    printf '\n该修复尚未发布。Issue 将保持开启，待包含此修复的 Release 发布并验证后关闭。\n'
  else
    printf 'Fixed in PR %s.\n\nChanges:\n\n' "$pr_reference"
    print_items "${changes[@]}"
    printf '\nVerification:\n\n'
    print_items "${tests[@]}"
    printf '\nThis fix has not been released yet. The issue will remain open until a published Release includes the fix and the release is verified.\n'
  fi
  exit 0
fi

if [[ -z "$version" || -z "$release_url" || ${#assets[@]} -eq 0 ]]; then
  echo "Release replies require --version, --release-url, and at least one --asset." >&2
  exit 2
fi

if [[ "$language" == "zh" ]]; then
  printf '修复已随 [Komelia v%s](%s) 发布。\n\n可用安装包：\n\n' "$version" "$release_url"
  print_items "${assets[@]}"
  printf '\n已验证：\n\n'
  print_items "${tests[@]}"
  printf '\n如果该版本仍可复现，请附上复现步骤、平台和错误日志，我们会重新打开此 Issue。\n'
else
  printf 'The fix is available in [Komelia v%s](%s).\n\nAvailable packages:\n\n' "$version" "$release_url"
  print_items "${assets[@]}"
  printf '\nVerified:\n\n'
  print_items "${tests[@]}"
  printf '\nIf the issue still occurs in this version, add the reproduction steps, platform, and error log. We will reopen the issue.\n'
fi
