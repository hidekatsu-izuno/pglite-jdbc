#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
postgres_dir="$repo_root/postgres-pglite"
resource_dir="$repo_root/src/main/resources/pglite"
wasi_sdk_version=${WASI_SDK_VERSION:-25}
wasi_sdk_dir="$repo_root/.bin/wasi-sdk"
wasi_sdk_tmp_dir="$repo_root/.bin/tmp"

skip_build=0

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--skip-build]

Build postgres-pglite with the WASI build script and copy the generated
runtime modules into src/main/resources/pglite.

Options:
  --skip-build     Copy existing postgres-pglite/dist-wasi artifacts only.

Environment:
  WASI_SDK_PATH     Use an existing wasi-sdk installation.
  WASI_SDK_VERSION  wasi-sdk version to download when WASI_SDK_PATH is unset.
                    Defaults to 25.
USAGE
}

ensure_wasi_sdk() {
  if [[ -n "${WASI_SDK_PATH:-}" ]]; then
    if [[ ! -x "$WASI_SDK_PATH/bin/clang" ]]; then
      echo "WASI_SDK_PATH does not contain bin/clang: $WASI_SDK_PATH" >&2
      exit 2
    fi
    return
  fi

  if [[ -x "$wasi_sdk_dir/bin/clang" ]]; then
    export WASI_SDK_PATH="$wasi_sdk_dir"
    return
  fi

  local archive="wasi-sdk-${wasi_sdk_version}.0-x86_64-linux.tar.gz"
  local extracted_dir="$wasi_sdk_tmp_dir/wasi-sdk-${wasi_sdk_version}.0-x86_64-linux"
  local url="https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${wasi_sdk_version}/${archive}"

  mkdir -p "$wasi_sdk_tmp_dir"
  echo "Downloading wasi-sdk ${wasi_sdk_version} to $wasi_sdk_dir"
  curl -L "$url" -o "$wasi_sdk_tmp_dir/$archive"
  rm -rf "$wasi_sdk_dir" "$extracted_dir"
  tar -C "$wasi_sdk_tmp_dir" -xzf "$wasi_sdk_tmp_dir/$archive"
  mv "$extracted_dir" "$wasi_sdk_dir"

  export WASI_SDK_PATH="$wasi_sdk_dir"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      skip_build=1
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
  shift
done

if [[ ! -d "$postgres_dir/.git" && ! -f "$postgres_dir/.git" ]]; then
  echo "postgres-pglite submodule is not initialized: $postgres_dir" >&2
  exit 1
fi

if [[ $skip_build -eq 0 ]]; then
  if [[ ! -x "$script_dir/build-pglite-wasi.sh" ]]; then
    echo "Missing build script: $script_dir/build-pglite-wasi.sh" >&2
    exit 1
  fi

  ensure_wasi_sdk
  dependency_exports=$("$script_dir/setup-wasi-dependencies.sh")
  eval "$dependency_exports"
  "$script_dir/build-pglite-wasi.sh" "$postgres_dir"
fi

dist_dir="$postgres_dir/dist-wasi"
bin_dir="$dist_dir/bin"
extensions_dir="$dist_dir/extensions"

for artifact in \
  "$bin_dir/pglite.wasi.wasm" \
  "$bin_dir/pglite.wasi.share.tar.gz"
do
  if [[ ! -f "$artifact" ]]; then
    echo "Missing build artifact: $artifact" >&2
    exit 1
  fi
done

mkdir -p "$resource_dir/bin" "$resource_dir/extensions"

cp "$bin_dir/pglite.wasi.wasm" "$resource_dir/bin/"
cp "$bin_dir/pglite.wasi.share.tar.gz" "$resource_dir/bin/"

if [[ -f "$bin_dir/pglite.wasi.exnref.wasm" ]]; then
  cp "$bin_dir/pglite.wasi.exnref.wasm" "$resource_dir/bin/"
fi

if [[ -d "$extensions_dir" ]]; then
  find "$extensions_dir" -type f -name "*.tar.gz" | while IFS= read -r artifact; do
    rel=${artifact#"$extensions_dir"/}
    mkdir -p "$resource_dir/extensions/$(dirname "$rel")"
    cp "$artifact" "$resource_dir/extensions/$rel"
  done
fi

echo "Copied postgres-pglite WASI artifacts to $resource_dir"
