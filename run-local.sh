#!/usr/bin/env bash
# 导入本机 .env 后启动；只 source 自己维护的文件，不输出变量值。
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"

if [[ ! -f .env ]]; then
  echo '缺少 .env：请复制 .env.example 为 .env，并填写 OPENAI_API_KEY。' >&2
  exit 1
fi

set -a
source ./.env
set +a

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo '请先在 .env 中填写 OPENAI_API_KEY，然后重新启动。' >&2
  exit 1
fi

exec ./mvnw spring-boot:run "$@"
