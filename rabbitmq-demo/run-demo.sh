#!/usr/bin/env bash
set -euo pipefail

module_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
env_file="$module_dir/.rabbitmq.env.local"

if [[ ! -f "$env_file" ]]; then
  echo "请先将 rabbitmq-demo 中的 .rabbitmq.env.example 复制为 .rabbitmq.env.local 并填写连接信息。" >&2
  exit 1
fi

set -a
source "$env_file"
set +a
cd "$module_dir"
exec bash ./mvnw spring-boot:run "$@"
