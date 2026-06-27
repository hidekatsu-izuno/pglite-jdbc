#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
postgres_dir="$repo_root/postgres-pglite"
patch_root="$repo_root/patches"

if [[ ! -d "$postgres_dir/.git" && ! -f "$postgres_dir/.git" ]]; then
  echo "postgres-pglite submodule is not initialized: $postgres_dir" >&2
  exit 1
fi

if [[ ! -d "$patch_root" ]]; then
  echo "patch directory does not exist: $patch_root" >&2
  exit 1
fi

git -C "$postgres_dir" submodule update --init \
  pglite/other_extensions/age \
  pglite/other_extensions/pg_hashids \
  pglite/other_extensions/pg_ivm \
  pglite/other_extensions/pg_textsearch \
  pglite/other_extensions/pg_uuidv7 \
  pglite/other_extensions/postgis \
  pglite/other_extensions/vector

apply_patch_file() {
  local patch_file=$1
  local rel=${patch_file#"$patch_root"/}
  local target_dir=$postgres_dir

  case "$rel" in
    pglite/other_extensions/age/*)
      target_dir="$postgres_dir/pglite/other_extensions/age"
      ;;
    pglite/other_extensions/pg_hashids/*)
      target_dir="$postgres_dir/pglite/other_extensions/pg_hashids"
      ;;
    pglite/other_extensions/pg_ivm/*)
      target_dir="$postgres_dir/pglite/other_extensions/pg_ivm"
      ;;
    pglite/other_extensions/pg_textsearch/*)
      target_dir="$postgres_dir/pglite/other_extensions/pg_textsearch"
      ;;
    pglite/other_extensions/pg_uuidv7/*)
      target_dir="$postgres_dir/pglite/other_extensions/pg_uuidv7"
      ;;
    pglite/other_extensions/postgis/*)
      target_dir="$postgres_dir/pglite/other_extensions/postgis"
      ;;
    pglite/other_extensions/vector/*)
      target_dir="$postgres_dir/pglite/other_extensions/vector"
      ;;
  esac

  if git -C "$target_dir" apply --check "$patch_file" >/dev/null 2>&1; then
    echo "Applying ${rel}"
    git -C "$target_dir" apply --whitespace=nowarn "$patch_file"
  elif git -C "$target_dir" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    echo "Skipping already applied ${rel}"
  else
    echo "Patch does not apply cleanly: ${rel}" >&2
    exit 1
  fi
}

while IFS= read -r patch_file; do
  apply_patch_file "$patch_file"
done < <(find "$patch_root" -type f -name "*.patch" | LC_ALL=C sort)
