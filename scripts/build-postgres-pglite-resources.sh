#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)

wasi_sdk_version=${WASI_SDK_VERSION:-33}
wasi_sdk_dir="$repo_root/.bin/wasi-sdk"
wasi_sdk_tmp_dir="$repo_root/.bin/tmp"

skip_build=0
sync_jdbc=1
sync_pglite_custom=1
sync_pglite_custom_dist=1

default_postgres_dir() {
  if [[ -d "$repo_root/postgres-pglite" ]]; then
    printf '%s\n' "$repo_root/postgres-pglite"
  elif [[ -d "$repo_root/../pglite-custom/postgres-pglite" ]]; then
    printf '%s\n' "$(cd "$repo_root/../pglite-custom/postgres-pglite" && pwd)"
  else
    printf '%s\n' "$repo_root/postgres-pglite"
  fi
}

postgres_dir=${POSTGRES_PGLITE_DIR:-$(default_postgres_dir)}
pglite_custom_dir=${PGLITE_CUSTOM_DIR:-$(cd "$postgres_dir/.." 2>/dev/null && pwd)}
jdbc_release_dir=${PGLITE_JDBC_RELEASE_DIR:-"$repo_root/src/main/resources/io/github/hidekatsu_izuno/pglite_jdbc/pglite/release"}
pglite_custom_release_dir=${PGLITE_CUSTOM_RELEASE_DIR:-"$pglite_custom_dir/packages/pglite/release"}
pglite_custom_dist_dir=${PGLITE_CUSTOM_DIST_DIR:-"$pglite_custom_dir/packages/pglite/dist"}
install_root=${PGLITE_WASI_INSTALL_ROOT:-"$postgres_dir/pglite/out/wasi-install/pglite"}

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Build postgres-pglite for WASI, then replace resource artifacts in both
pglite-jdbc and pglite-custom from the generated install tree.

Options:
  --skip-build             Copy existing WASI artifacts only.
  --jdbc-only              Update only pglite-jdbc resources.
  --pglite-custom-only     Update only pglite-custom resources.
  --no-pglite-custom-dist  Do not update packages/pglite/dist resources.
  --postgres-dir DIR       postgres-pglite checkout to build/copy from.
  -h, --help               Show this help.

Environment:
  POSTGRES_PGLITE_DIR       postgres-pglite checkout. Defaults to ./postgres-pglite
                            or ../pglite-custom/postgres-pglite when present.
  PGLITE_WASI_INSTALL_ROOT  Generated install root. Defaults to
                            <postgres-dir>/pglite/out/wasi-install/pglite.
  PGLITE_CUSTOM_DIR         pglite-custom checkout. Defaults to <postgres-dir>/...
  PGLITE_JDBC_RELEASE_DIR   pglite-jdbc release resource directory.
  PGLITE_CUSTOM_RELEASE_DIR pglite-custom packages/pglite/release directory.
  PGLITE_CUSTOM_DIST_DIR    pglite-custom packages/pglite/dist directory.
  WASI_SDK_PATH             Use an existing wasi-sdk installation.
  WASI_SDK_VERSION          wasi-sdk version to download when WASI_SDK_PATH is unset.
                            Defaults to 33.
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

copy_tree_contents() {
  local source_dir=$1
  local target_dir=$2

  if [[ ! -d "$source_dir" ]]; then
    return
  fi

  mkdir -p "$target_dir"
  cp -R "$source_dir"/. "$target_dir"/
}

copy_release_artifacts() {
  local target_dir=$1

  mkdir -p "$target_dir"

  for artifact in "$install_root/bin/pglite.wasm" "$install_root/bin/initdb.wasm"; do
    if [[ ! -f "$artifact" ]]; then
      echo "Missing build artifact: $artifact" >&2
      exit 1
    fi
  done

  cp "$install_root/bin/pglite.wasm" "$target_dir/pglite.wasm"
  cp "$install_root/bin/initdb.wasm" "$target_dir/initdb.wasm"

  rm -f "$target_dir"/*.tar.gz
  if [[ -d "$install_root/extensions" ]]; then
    find "$install_root/extensions" -maxdepth 1 -type f -name "*.tar.gz" -print0 |
      while IFS= read -r -d '' artifact; do
        cp "$artifact" "$target_dir/"
      done
  fi

  rm -rf "$target_dir/lib" "$target_dir/share"
  copy_tree_contents "$install_root/lib" "$target_dir/lib"
  copy_tree_contents "$install_root/share" "$target_dir/share"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      skip_build=1
      ;;
    --jdbc-only)
      sync_jdbc=1
      sync_pglite_custom=0
      sync_pglite_custom_dist=0
      ;;
    --pglite-custom-only)
      sync_jdbc=0
      sync_pglite_custom=1
      ;;
    --no-pglite-custom-dist)
      sync_pglite_custom_dist=0
      ;;
    --postgres-dir)
      if [[ $# -lt 2 ]]; then
        echo "--postgres-dir requires a directory" >&2
        exit 2
      fi
      postgres_dir=$(cd "$2" && pwd)
      pglite_custom_dir=${PGLITE_CUSTOM_DIR:-$(cd "$postgres_dir/.." && pwd)}
      pglite_custom_release_dir=${PGLITE_CUSTOM_RELEASE_DIR:-"$pglite_custom_dir/packages/pglite/release"}
      pglite_custom_dist_dir=${PGLITE_CUSTOM_DIST_DIR:-"$pglite_custom_dir/packages/pglite/dist"}
      install_root=${PGLITE_WASI_INSTALL_ROOT:-"$postgres_dir/pglite/out/wasi-install/pglite"}
      shift
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
  echo "postgres-pglite checkout is not initialized: $postgres_dir" >&2
  exit 1
fi

if [[ $skip_build -eq 0 ]]; then
  if [[ -x "$postgres_dir/build-pglite-wasi.sh" ]]; then
    ensure_wasi_sdk
    (cd "$postgres_dir" && WASI_SDK_PATH="$WASI_SDK_PATH" PGLITE_WASI_JOBS="${PGLITE_WASI_JOBS:-2}" ./build-pglite-wasi.sh)
  else
    if [[ ! -x "$script_dir/build-pglite-wasi.sh" ]]; then
      echo "Missing build script: $script_dir/build-pglite-wasi.sh" >&2
      exit 1
    fi

    ensure_wasi_sdk
    dependency_exports=$("$script_dir/setup-wasi-dependencies.sh")
    eval "$dependency_exports"
    "$script_dir/build-pglite-wasi.sh" "$postgres_dir"
  fi
fi

if [[ $sync_jdbc -eq 1 ]]; then
  copy_release_artifacts "$jdbc_release_dir"
  echo "Copied WASI release artifacts to $jdbc_release_dir"
fi

if [[ $sync_pglite_custom -eq 1 ]]; then
  copy_release_artifacts "$pglite_custom_release_dir"
  echo "Copied WASI release artifacts to $pglite_custom_release_dir"

  if [[ $sync_pglite_custom_dist -eq 1 && -d "$pglite_custom_dist_dir" ]]; then
    copy_release_artifacts "$pglite_custom_dist_dir"
    echo "Copied WASI release artifacts to $pglite_custom_dist_dir"
  fi
fi
