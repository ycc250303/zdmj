#!/bin/sh
# 打印本次提交在后端业务代码中的行数变化。统计目录：backend/zdmj/src/main

path="backend/zdmj/src/main"
root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "业务代码行数：无法定位仓库根目录" >&2
  exit 0
}
cd "$root" || exit 0

if git rev-parse --verify --quiet HEAD^ >/dev/null; then
  numstat=$(git diff --numstat HEAD^ HEAD -- "$path") || {
    echo "业务代码行数：统计失败" >&2
    exit 0
  }
else
  numstat=$(git show --numstat --format= HEAD -- "$path") || {
    echo "业务代码行数：统计失败" >&2
    exit 0
  }
fi

printf '%s\n' "$numstat" | awk '
  $1 == "-" || $2 == "-" { next }
  NF < 2 { next }
  {
    add += $1
    del += $2
    files++
  }
  END {
    if (files == 0) {
      print "业务代码行数（backend/zdmj/src/main）：无变化"
      exit
    }
    net = add - del
    if (net > 0) {
      label = "净增"
      amount = net
    } else if (net < 0) {
      label = "净减"
      amount = -net
    } else {
      label = "净变化"
      amount = 0
    }
    printf "业务代码行数（backend/zdmj/src/main）：新增 %d 行，删除 %d 行，%s %d 行，%d 个文件\n", add, del, label, amount, files
  }
'
