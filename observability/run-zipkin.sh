#!/usr/bin/env bash
set -euo pipefail

module_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
zipkin_version=3.6.1
cache_dir="$module_dir/.cache"
jar_file="$cache_dir/zipkin-server-$zipkin_version-exec.jar"

mkdir -p "$cache_dir"
if [[ ! -s "$jar_file" ]]; then
  download_file="$jar_file.download"
  trap 'rm -f "$download_file"' EXIT
  curl --fail --location --retry 2 --connect-timeout 15 \
    "https://repo.maven.apache.org/maven2/io/zipkin/zipkin-server/$zipkin_version/zipkin-server-$zipkin_version-exec.jar" \
    --output "$download_file"
  mv "$download_file" "$jar_file"
fi

exec java -jar "$jar_file" "$@"
