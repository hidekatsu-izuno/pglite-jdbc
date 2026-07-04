#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
  echo "Usage: $(basename "$0") [postgres-pglite-dir]" >&2
  exit 2
fi

ROOT_DIR=$(cd "${1:-$(dirname "$0")/../postgres-pglite}" && pwd)
BUILD_DIR=${PGLITE_WASI_BUILD_DIR:-"$ROOT_DIR/build-wasi"}
PREFIX=${PGLITE_WASI_PREFIX:-"$ROOT_DIR/dist-wasi"}
REPO_ROOT=$(cd "$ROOT_DIR/.." && pwd)
BIN_DIR="$REPO_ROOT/.bin"
TMP_DIR="$BIN_DIR/tmp"
WASI_SDK_PATH=${WASI_SDK_PATH:-}
PGLITE_WASI_SJLJ_FLAGS=${PGLITE_WASI_SJLJ_FLAGS:-"-mllvm -wasm-enable-sjlj -mllvm -wasm-use-legacy-eh=false"}
PGLITE_WASI_LINK_FLAGS=${PGLITE_WASI_LINK_FLAGS:-"--no-wasm-opt"}
PGLITE_WASI_ICU_PREFIX=${PGLITE_WASI_ICU_PREFIX:-"$ROOT_DIR/../.bin/wasi-icu"}
PGLITE_WASI_LIBXML2_PREFIX=${PGLITE_WASI_LIBXML2_PREFIX:-"$ROOT_DIR/../.bin/wasi-libxml2"}
PGLITE_WASI_PROJ_PREFIX=${PGLITE_WASI_PROJ_PREFIX:-"$ROOT_DIR/../.bin/wasi-proj"}
PGLITE_WASI_SQLITE_PREFIX=${PGLITE_WASI_SQLITE_PREFIX:-"$ROOT_DIR/../.bin/wasi-sqlite"}
PGLITE_WASI_GEOS_PREFIX=${PGLITE_WASI_GEOS_PREFIX:-"$ROOT_DIR/../.bin/wasi-geos"}
PGLITE_WASI_JSON_C_PREFIX=${PGLITE_WASI_JSON_C_PREFIX:-"$ROOT_DIR/../.bin/wasi-json-c"}
PGLITE_WASI_GDAL_PREFIX=${PGLITE_WASI_GDAL_PREFIX:-"$ROOT_DIR/../.bin/wasi-gdal"}

for var in \
  PGLITE_WASI_ICU_PREFIX \
  PGLITE_WASI_LIBXML2_PREFIX \
  PGLITE_WASI_PROJ_PREFIX \
  PGLITE_WASI_SQLITE_PREFIX \
  PGLITE_WASI_GEOS_PREFIX \
  PGLITE_WASI_JSON_C_PREFIX \
  PGLITE_WASI_GDAL_PREFIX
do
  value=${!var}
  if [[ -d "$value" ]]; then
    printf -v "$var" '%s' "$(cd "$value" && pwd)"
  fi
done

if [[ -z "$WASI_SDK_PATH" ]]; then
  echo "WASI_SDK_PATH must point to a wasi-sdk installation" >&2
  exit 2
fi

CC=${CC:-"$WASI_SDK_PATH/bin/clang"}
CXX=${CXX:-"$WASI_SDK_PATH/bin/clang++"}
AR=${AR:-"$WASI_SDK_PATH/bin/llvm-ar"}
RANLIB=${RANLIB:-"$WASI_SDK_PATH/bin/llvm-ranlib"}

mkdir -p "$BUILD_DIR" "$PREFIX/bin"

download_file() {
  local url=$1
  local output=$2
  if [[ ! -f "$output" ]]; then
    curl -L "$url" -o "$output"
  fi
}

build_host_package() {
  local name=$1
  local version=$2
  local archive=$3
  local url=$4
  local src_dir="$TMP_DIR/$name-$version"
  local stamp="$BIN_DIR/autotools/.stamps/$name-$version"

  if [[ -f "$stamp" ]]; then
    return
  fi

  mkdir -p "$TMP_DIR" "$BIN_DIR/autotools/.stamps"
  download_file "$url" "$TMP_DIR/$archive"
  rm -rf "$src_dir"
  case "$archive" in
    *.tar.gz) tar -C "$TMP_DIR" -xzf "$TMP_DIR/$archive" ;;
    *.tar.xz) tar -C "$TMP_DIR" -xJf "$TMP_DIR/$archive" ;;
    *) echo "Unsupported archive: $archive" >&2; exit 2 ;;
  esac
  (
    cd "$src_dir"
    ./configure --prefix="$BIN_DIR/autotools"
    make -j "${PGLITE_WASI_JOBS:-2}"
    make install
  )
  touch "$stamp"
}

ensure_autotools() {
  if command -v autoconf >/dev/null 2>&1 &&
     command -v aclocal >/dev/null 2>&1 &&
     command -v libtoolize >/dev/null 2>&1; then
    return
  fi

  export PATH="$BIN_DIR/autotools/bin:$PATH"
  build_host_package m4 1.4.19 m4-1.4.19.tar.xz https://ftp.gnu.org/gnu/m4/m4-1.4.19.tar.xz
  build_host_package autoconf 2.72 autoconf-2.72.tar.xz https://ftp.gnu.org/gnu/autoconf/autoconf-2.72.tar.xz
  build_host_package automake 1.17 automake-1.17.tar.xz https://ftp.gnu.org/gnu/automake/automake-1.17.tar.xz
  build_host_package libtool 2.5.4 libtool-2.5.4.tar.xz https://ftp.gnu.org/gnu/libtool/libtool-2.5.4.tar.xz
}

ensure_autotools

export CC CXX AR RANLIB
export CFLAGS="${CFLAGS:-} -D__PGLITE__ -D_WASI_EMULATED_GETPID -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS -D_WASI_EMULATED_MMAN -Dpoll=pgl_poll $PGLITE_WASI_SJLJ_FLAGS"
export LDFLAGS="${LDFLAGS:-} $PGLITE_WASI_SJLJ_FLAGS $PGLITE_WASI_LINK_FLAGS"
export LIBS="${LIBS:-} -lsetjmp -lwasi-emulated-getpid -lwasi-emulated-signal -lwasi-emulated-process-clocks -lwasi-emulated-mman"
export PGLITE_CFLAGS="${PGLITE_CFLAGS:-} -D__PGLITE__ -D_WASI_EMULATED_GETPID -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS -D_WASI_EMULATED_MMAN $PGLITE_WASI_SJLJ_FLAGS"
export POSTGRES_PGLITE_WASI_FLAGS="${POSTGRES_PGLITE_WASI_FLAGS:-} \
$PGLITE_WASI_LINK_FLAGS \
-mexec-model=reactor \
-Wl,--export=malloc \
-Wl,--export=free \
-Wl,--export=pgwasm_init \
-Wl,--export=pgwasm_init_with_datadir \
-Wl,--export=pgwasm_initdb \
-Wl,--export=pgwasm_exec_json \
-Wl,--export=pgwasm_string_length \
-Wl,--export=pgwasm_recover_from_error \
-Wl,--export=pgwasm_last_error \
-Wl,--export=pgwasm_free \
-Wl,--export-memory \
-Wl,-z,stack-size=4194304 \
-Wl,--no-entry"

cd "$ROOT_DIR"

find src pglite/src/pglitec \( -name '*.o' -o -name '*.a' -o -name 'objfiles.txt' \) | xargs -r rm -f

configure_args=(
  --host=wasm32-wasi
  --with-template=wasi
  --prefix="$PREFIX"
  --disable-spinlocks
  --disable-atomics
  --without-zlib
  --without-llvm
  --without-readline
  --without-pam
  --with-system-tzdata=/usr/share/zoneinfo
  --with-openssl=no
)

if [[ -f "$PGLITE_WASI_ICU_PREFIX/lib/libicuuc.a" ]] &&
   [[ -f "$PGLITE_WASI_ICU_PREFIX/lib/libicui18n.a" ]]; then
  export ICU_CFLAGS="${ICU_CFLAGS:-"-I$PGLITE_WASI_ICU_PREFIX/include"}"
  export ICU_LIBS="${ICU_LIBS:-"-L$PGLITE_WASI_ICU_PREFIX/lib -licui18n -licuuc -licudata -lc++ -lc++abi -lwasi-emulated-signal"}"
  export CFLAGS="$CFLAGS $ICU_CFLAGS"
  export PGLITE_CFLAGS="$PGLITE_CFLAGS $ICU_CFLAGS"
  export LDFLAGS="$LDFLAGS -L$PGLITE_WASI_ICU_PREFIX/lib"
  export LIBS="$LIBS -licui18n -licuuc -licudata -lc++ -lc++abi"
  configure_args+=(--with-icu)
else
  configure_args+=(--without-icu)
fi

if [[ -f "$PGLITE_WASI_LIBXML2_PREFIX/lib/libxml2.a" ]]; then
  export XML2_CFLAGS="${XML2_CFLAGS:-"-I$PGLITE_WASI_LIBXML2_PREFIX/include/libxml2"}"
  export XML2_LIBS="${XML2_LIBS:-"-L$PGLITE_WASI_LIBXML2_PREFIX/lib -lxml2"}"
  configure_args+=(--with-libxml)
fi

if [[ ! -f config.status ]] ||
   { [[ -f "$PGLITE_WASI_ICU_PREFIX/lib/libicuuc.a" ]] &&
     { ! grep -q 'S\["with_icu"\]="yes"' config.status ||
       ! grep -q -- '-lc++abi' config.status; }; } ||
   { [[ -f "$PGLITE_WASI_LIBXML2_PREFIX/lib/libxml2.a" ]] &&
     ! grep -q 'S\["with_libxml"\]="yes"' config.status; } ||
   ! grep -q 'pglite_postgis_lookup_function' src/backend/utils/fmgr/dfmgr.c; then
  ./configure "${configure_args[@]}"
fi

"$CC" $PGLITE_CFLAGS -static -fPIC -o pglite/src/pglitec/pglitec.o -c pglite/src/pglitec/pglitec.c
"$CC" $CFLAGS -DUSE_PRIVATE_ENCODING_FUNCS -Dmain=pglite_initdb_main -Dexit=pgl_exit -Dgeteuid=pgl_geteuid -Dgetuid=pgl_getuid -Dchmod=pgl_chmod -Dset_pglocale_pgservice=pgl_set_pglocale_pgservice -Dfind_other_exec=pgl_find_other_exec \
  -I"$ROOT_DIR/src/interfaces/libpq" \
  -I"$ROOT_DIR/src/timezone" \
  -I"$ROOT_DIR/src/include" \
  -DSYSTEMTZDIR='"/usr/share/zoneinfo"' \
  -o pglite/src/pglitec/initdb.o \
  -c src/bin/initdb/initdb.c
"$CC" $CFLAGS -DFRONTEND \
  -I"$ROOT_DIR/src/interfaces/libpq" \
  -I"$ROOT_DIR/src/include" \
  -o pglite/src/pglitec/pqexpbuffer.o \
  -c src/interfaces/libpq/pqexpbuffer.c
"$CC" $CFLAGS -DFRONTEND \
  -I"$ROOT_DIR/src/interfaces/libpq" \
  -I"$ROOT_DIR/src/include" \
  -o pglite/src/pglitec/option_utils.o \
  -c src/fe_utils/option_utils.c

mkdir -p pglite/src/pglitec/snowball
POSTGRES_PGLITE_WASI_EXTRA_OBJS=""
for source in src/backend/snowball/dict_snowball.c src/backend/snowball/libstemmer/*.c; do
  obj="pglite/src/pglitec/snowball/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "src/backend/snowball/dict_snowball.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_dict_snowball_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/src/include/snowball" \
    -I"$ROOT_DIR/src/include/snowball/libstemmer" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

make PORTNAME=wasi -C src/pl/plpgsql/src pl_gram.c pl_gram.h plerrcodes.h pl_reserved_kwlist_d.h pl_unreserved_kwlist_d.h
mkdir -p pglite/src/pglitec/plpgsql
for source in src/pl/plpgsql/src/pl_comp.c src/pl/plpgsql/src/pl_exec.c src/pl/plpgsql/src/pl_funcs.c src/pl/plpgsql/src/pl_gram.c src/pl/plpgsql/src/pl_handler.c src/pl/plpgsql/src/pl_scanner.c; do
  obj="pglite/src/pglitec/plpgsql/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "src/pl/plpgsql/src/pl_handler.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_plpgsql_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/src/pl/plpgsql/src" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_trgm
for source in contrib/pg_trgm/trgm_gin.c contrib/pg_trgm/trgm_gist.c contrib/pg_trgm/trgm_op.c contrib/pg_trgm/trgm_regexp.c; do
  obj="pglite/src/pglitec/pg_trgm/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/pg_trgm/trgm_op.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_pg_trgm_Pg_magic_func -D_PG_init=pglite_pg_trgm_PG_init)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/pg_trgm" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/unaccent
for source in contrib/unaccent/unaccent.c; do
  obj="pglite/src/pglitec/unaccent/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_unaccent_Pg_magic_func \
    -I"$ROOT_DIR/contrib/unaccent" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/citext
for source in contrib/citext/citext.c; do
  obj="pglite/src/pglitec/citext/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_citext_Pg_magic_func \
    -I"$ROOT_DIR/contrib/citext" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/cube
for source in contrib/cube/cube.c contrib/cube/cubeparse.c contrib/cube/cubescan.c; do
  obj="pglite/src/pglitec/cube/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/cube/cube.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_cube_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/cube" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/hstore
for source in \
  contrib/hstore/hstore_compat.c \
  contrib/hstore/hstore_gin.c \
  contrib/hstore/hstore_gist.c \
  contrib/hstore/hstore_io.c \
  contrib/hstore/hstore_op.c \
  contrib/hstore/hstore_subs.c; do
  obj="pglite/src/pglitec/hstore/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/hstore/hstore_io.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_hstore_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/hstore" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/fuzzystrmatch
for source in contrib/fuzzystrmatch/daitch_mokotoff.c contrib/fuzzystrmatch/dmetaphone.c contrib/fuzzystrmatch/fuzzystrmatch.c; do
  obj="pglite/src/pglitec/fuzzystrmatch/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/fuzzystrmatch/fuzzystrmatch.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_fuzzystrmatch_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/fuzzystrmatch" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/btree_gin
for source in contrib/btree_gin/btree_gin.c; do
  obj="pglite/src/pglitec/btree_gin/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_btree_gin_Pg_magic_func \
    -I"$ROOT_DIR/contrib/btree_gin" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/btree_gist
for source in \
  contrib/btree_gist/btree_bit.c \
  contrib/btree_gist/btree_bool.c \
  contrib/btree_gist/btree_bytea.c \
  contrib/btree_gist/btree_cash.c \
  contrib/btree_gist/btree_date.c \
  contrib/btree_gist/btree_enum.c \
  contrib/btree_gist/btree_float4.c \
  contrib/btree_gist/btree_float8.c \
  contrib/btree_gist/btree_gist.c \
  contrib/btree_gist/btree_inet.c \
  contrib/btree_gist/btree_int2.c \
  contrib/btree_gist/btree_int4.c \
  contrib/btree_gist/btree_int8.c \
  contrib/btree_gist/btree_interval.c \
  contrib/btree_gist/btree_macaddr.c \
  contrib/btree_gist/btree_macaddr8.c \
  contrib/btree_gist/btree_numeric.c \
  contrib/btree_gist/btree_oid.c \
  contrib/btree_gist/btree_text.c \
  contrib/btree_gist/btree_time.c \
  contrib/btree_gist/btree_ts.c \
  contrib/btree_gist/btree_utils_num.c \
  contrib/btree_gist/btree_utils_var.c \
  contrib/btree_gist/btree_uuid.c; do
  obj="pglite/src/pglitec/btree_gist/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/btree_gist/btree_gist.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_btree_gist_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/btree_gist" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/amcheck
for source in \
  contrib/amcheck/verify_common.c \
  contrib/amcheck/verify_gin.c \
  contrib/amcheck/verify_heapam.c \
  contrib/amcheck/verify_nbtree.c; do
  obj="pglite/src/pglitec/amcheck/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/amcheck/verify_nbtree.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_amcheck_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/amcheck" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/bloom
for source in \
  contrib/bloom/blcost.c \
  contrib/bloom/blinsert.c \
  contrib/bloom/blscan.c \
  contrib/bloom/blutils.c \
  contrib/bloom/blvacuum.c \
  contrib/bloom/blvalidate.c; do
  obj="pglite/src/pglitec/bloom/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/bloom/blinsert.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_bloom_Pg_magic_func)
  fi
  if [[ "$source" == "contrib/bloom/blutils.c" ]]; then
    magic_flags=(-D_PG_init=pglite_bloom_PG_init)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/bloom" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/dict_int
for source in contrib/dict_int/dict_int.c; do
  obj="pglite/src/pglitec/dict_int/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_dict_int_Pg_magic_func \
    -I"$ROOT_DIR/contrib/dict_int" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/dict_xsyn
for source in contrib/dict_xsyn/dict_xsyn.c; do
  obj="pglite/src/pglitec/dict_xsyn/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_dict_xsyn_Pg_magic_func \
    -I"$ROOT_DIR/contrib/dict_xsyn" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/earthdistance
for source in contrib/earthdistance/earthdistance.c; do
  obj="pglite/src/pglitec/earthdistance/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_earthdistance_Pg_magic_func \
    -I"$ROOT_DIR/contrib/earthdistance" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/file_fdw
for source in contrib/file_fdw/file_fdw.c; do
  obj="pglite/src/pglitec/file_fdw/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_file_fdw_Pg_magic_func \
    -I"$ROOT_DIR/contrib/file_fdw" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/isn
for source in contrib/isn/isn.c; do
  obj="pglite/src/pglitec/isn/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_isn_Pg_magic_func \
    -D_PG_init=pglite_isn_PG_init \
    -I"$ROOT_DIR/contrib/isn" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/lo
for source in contrib/lo/lo.c; do
  obj="pglite/src/pglitec/lo/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_lo_Pg_magic_func \
    -I"$ROOT_DIR/contrib/lo" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/ltree
for source in \
  contrib/ltree/_ltree_gist.c \
  contrib/ltree/_ltree_op.c \
  contrib/ltree/crc32.c \
  contrib/ltree/lquery_op.c \
  contrib/ltree/ltree_gist.c \
  contrib/ltree/ltree_io.c \
  contrib/ltree/ltree_op.c \
  contrib/ltree/ltxtquery_io.c \
  contrib/ltree/ltxtquery_op.c; do
  obj="pglite/src/pglitec/ltree/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/ltree/ltree_op.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_ltree_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/ltree" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pageinspect
for source in \
  contrib/pageinspect/brinfuncs.c \
  contrib/pageinspect/btreefuncs.c \
  contrib/pageinspect/fsmfuncs.c \
  contrib/pageinspect/ginfuncs.c \
  contrib/pageinspect/gistfuncs.c \
  contrib/pageinspect/hashfuncs.c \
  contrib/pageinspect/heapfuncs.c \
  contrib/pageinspect/rawpage.c; do
  obj="pglite/src/pglitec/pageinspect/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/pageinspect/rawpage.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_pageinspect_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/pageinspect" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_buffercache
for source in contrib/pg_buffercache/pg_buffercache_pages.c; do
  obj="pglite/src/pglitec/pg_buffercache/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_buffercache_Pg_magic_func \
    -I"$ROOT_DIR/contrib/pg_buffercache" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_freespacemap
for source in contrib/pg_freespacemap/pg_freespacemap.c; do
  obj="pglite/src/pglitec/pg_freespacemap/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_freespacemap_Pg_magic_func \
    -I"$ROOT_DIR/contrib/pg_freespacemap" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_visibility
for source in contrib/pg_visibility/pg_visibility.c; do
  obj="pglite/src/pglitec/pg_visibility/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_visibility_Pg_magic_func \
    -I"$ROOT_DIR/contrib/pg_visibility" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_surgery
for source in contrib/pg_surgery/heap_surgery.c; do
  obj="pglite/src/pglitec/pg_surgery/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_surgery_Pg_magic_func \
    -I"$ROOT_DIR/contrib/pg_surgery" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/tcn
for source in contrib/tcn/tcn.c; do
  obj="pglite/src/pglitec/tcn/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_tcn_Pg_magic_func \
    -I"$ROOT_DIR/contrib/tcn" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_stat_statements
for source in contrib/pg_stat_statements/pg_stat_statements.c; do
  obj="pglite/src/pglitec/pg_stat_statements/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_stat_statements_Pg_magic_func \
    -D_PG_init=pglite_pg_stat_statements_PG_init \
    -I"$ROOT_DIR/contrib/pg_stat_statements" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/auto_explain
for source in contrib/auto_explain/auto_explain.c; do
  obj="pglite/src/pglitec/auto_explain/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_auto_explain_Pg_magic_func \
    -D_PG_init=pglite_auto_explain_PG_init \
    -I"$ROOT_DIR/contrib/auto_explain" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_walinspect
for source in contrib/pg_walinspect/pg_walinspect.c; do
  obj="pglite/src/pglitec/pg_walinspect/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pg_walinspect_Pg_magic_func \
    -I"$ROOT_DIR/contrib/pg_walinspect" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pgcrypto
for source in \
  contrib/pgcrypto/crypt-blowfish.c \
  contrib/pgcrypto/crypt-des.c \
  contrib/pgcrypto/crypt-gensalt.c \
  contrib/pgcrypto/crypt-md5.c \
  contrib/pgcrypto/crypt-sha.c \
  contrib/pgcrypto/mbuf.c \
  contrib/pgcrypto/openssl.c \
  contrib/pgcrypto/pgcrypto.c \
  contrib/pgcrypto/pgp-armor.c \
  contrib/pgcrypto/pgp-cfb.c \
  contrib/pgcrypto/pgp-compress.c \
  contrib/pgcrypto/pgp-decrypt.c \
  contrib/pgcrypto/pgp-encrypt.c \
  contrib/pgcrypto/pgp-info.c \
  contrib/pgcrypto/pgp-mpi.c \
  contrib/pgcrypto/pgp-mpi-openssl.c \
  contrib/pgcrypto/pgp-pgsql.c \
  contrib/pgcrypto/pgp-pubdec.c \
  contrib/pgcrypto/pgp-pubenc.c \
  contrib/pgcrypto/pgp-pubkey.c \
  contrib/pgcrypto/pgp-s2k.c \
  contrib/pgcrypto/pgp.c \
  contrib/pgcrypto/px-crypt.c \
  contrib/pgcrypto/px-hmac.c \
  contrib/pgcrypto/px.c; do
  obj="pglite/src/pglitec/pgcrypto/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_pgcrypto_Pg_magic_func \
    -D_PG_init=pglite_pgcrypto_PG_init \
    -I"$ROOT_DIR/contrib/pgcrypto" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/tablefunc
for source in contrib/tablefunc/tablefunc.c; do
  obj="pglite/src/pglitec/tablefunc/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_tablefunc_Pg_magic_func \
    -I"$ROOT_DIR/contrib/tablefunc" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/seg
for source in contrib/seg/seg.c contrib/seg/segparse.c contrib/seg/segscan.c; do
  obj="pglite/src/pglitec/seg/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/seg/seg.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_seg_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/seg" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/tsm_system_rows
for source in contrib/tsm_system_rows/tsm_system_rows.c; do
  obj="pglite/src/pglitec/tsm_system_rows/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_tsm_system_rows_Pg_magic_func \
    -I"$ROOT_DIR/contrib/tsm_system_rows" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/tsm_system_time
for source in contrib/tsm_system_time/tsm_system_time.c; do
  obj="pglite/src/pglitec/tsm_system_time/${source//\//_}.o"
  "$CC" $CFLAGS \
    -DPg_magic_func=pglite_tsm_system_time_Pg_magic_func \
    -I"$ROOT_DIR/contrib/tsm_system_time" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/intarray
for source in \
  contrib/intarray/_int_bool.c \
  contrib/intarray/_int_gin.c \
  contrib/intarray/_int_gist.c \
  contrib/intarray/_int_op.c \
  contrib/intarray/_int_selfuncs.c \
  contrib/intarray/_int_tool.c \
  contrib/intarray/_intbig_gist.c; do
  obj="pglite/src/pglitec/intarray/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "contrib/intarray/_int_op.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_intarray_Pg_magic_func)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/contrib/intarray" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_hashids
for source in pglite/other_extensions/pg_hashids/hashids.c pglite/other_extensions/pg_hashids/pg_hashids.c; do
  obj="pglite/src/pglitec/pg_hashids/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "pglite/other_extensions/pg_hashids/pg_hashids.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_pg_hashids_Pg_magic_func)
  else
    magic_flags=(-include postgres.h)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/pglite/other_extensions/pg_hashids" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/vector
for source in \
  pglite/other_extensions/vector/src/bitutils.c \
  pglite/other_extensions/vector/src/bitvec.c \
  pglite/other_extensions/vector/src/halfutils.c \
  pglite/other_extensions/vector/src/halfvec.c \
  pglite/other_extensions/vector/src/hnsw.c \
  pglite/other_extensions/vector/src/hnswbuild.c \
  pglite/other_extensions/vector/src/hnswinsert.c \
  pglite/other_extensions/vector/src/hnswscan.c \
  pglite/other_extensions/vector/src/hnswutils.c \
  pglite/other_extensions/vector/src/hnswvacuum.c \
  pglite/other_extensions/vector/src/ivfbuild.c \
  pglite/other_extensions/vector/src/ivfflat.c \
  pglite/other_extensions/vector/src/ivfinsert.c \
  pglite/other_extensions/vector/src/ivfkmeans.c \
  pglite/other_extensions/vector/src/ivfscan.c \
  pglite/other_extensions/vector/src/ivfutils.c \
  pglite/other_extensions/vector/src/ivfvacuum.c \
  pglite/other_extensions/vector/src/sparsevec.c \
  pglite/other_extensions/vector/src/vector.c; do
  obj="pglite/src/pglitec/vector/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "pglite/other_extensions/vector/src/vector.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_vector_Pg_magic_func -D_PG_init=pglite_vector_PG_init)
  fi
  parser_flags=()
  if [[ "$source" == "pglite/other_extensions/vector/src/vector.c" || \
        "$source" == "pglite/other_extensions/vector/src/halfvec.c" || \
        "$source" == "pglite/other_extensions/vector/src/sparsevec.c" ]]; then
    parser_flags=(-Dstrtof=pglite_vector_strtof)
  fi
  "$CC" $CFLAGS \
    -ftree-vectorize -fassociative-math -fno-signed-zeros -fno-trapping-math \
    "${magic_flags[@]}" \
    "${parser_flags[@]}" \
    -I"$ROOT_DIR/pglite/other_extensions/vector/src" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_ivm
perl -0pi -e 's{/\*\n\t \* record the subxid that updated the view incrementally\n\t \*\n\t \* Note:\n\t \* PG16 or later has list_member_xid and lappend_xid\. It would be better\n\t \* to use them, but we use integer for supporting older PGs since there\n\t \* is no problem or now\.\n\t \*/\n\tsubxid = GetCurrentSubTransactionId\(\);\n\tif \(!list_member_int\(entry->subxids, subxid\)\)\n\t\{\n\t\toldcxt = MemoryContextSwitchTo\(TopTransactionContext\);\n\t\tentry->subxids = lappend_int\(entry->subxids, subxid\);\n\t\tMemoryContextSwitchTo\(oldcxt\);\n\t\}}{/* record the subxid that updated the view incrementally */\n\tsubxid = GetCurrentSubTransactionId();\n#if defined(PG_VERSION_NUM) \&\& (PG_VERSION_NUM >= 160000)\n\t/* Keep subxids empty for PG16+ to avoid mixing integer and xid List cells. */\n#else\n\tif (!list_member_int(entry->subxids, subxid))\n\t{\n\t\toldcxt = MemoryContextSwitchTo(TopTransactionContext);\n\t\tentry->subxids = lappend_int(entry->subxids, subxid);\n\t\tMemoryContextSwitchTo(oldcxt);\n\t}\n#endif}' pglite/other_extensions/pg_ivm/matview.c
perl -0pi -e 's{/\* Note:\n\t\t\t\t \* PG16 or later has lfirst_xid, but we use lfirst_int for\n\t\t\t\t \* supporting older PGs since there is no problem or now\.\n\t\t\t\t \*/\n\t\t\t\tif \(lfirst_int\(lc\) == subxid\)}{#if defined(PG_VERSION_NUM) \&\& (PG_VERSION_NUM >= 160000)\n\t\t\t\tif (false)\n#else\n\t\t\t\tif (lfirst_int(lc) == subxid)\n#endif}' pglite/other_extensions/pg_ivm/matview.c
for source in \
  pglite/other_extensions/pg_ivm/createas.c \
  pglite/other_extensions/pg_ivm/matview.c \
  pglite/other_extensions/pg_ivm/pg_ivm.c \
  pglite/other_extensions/pg_ivm/ruleutils.c \
  pglite/other_extensions/pg_ivm/subselect.c; do
  obj="pglite/src/pglitec/pg_ivm/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "pglite/other_extensions/pg_ivm/pg_ivm.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_pg_ivm_Pg_magic_func -D_PG_init=pglite_pg_ivm_PG_init)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/pglite/other_extensions/pg_ivm" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/pg_textsearch
for source in \
  pglite/other_extensions/pg_textsearch/src/mod.c \
  pglite/other_extensions/pg_textsearch/src/access/handler.c \
  pglite/other_extensions/pg_textsearch/src/access/build.c \
  pglite/other_extensions/pg_textsearch/src/access/build_context.c \
  pglite/other_extensions/pg_textsearch/src/access/build_parallel.c \
  pglite/other_extensions/pg_textsearch/src/access/scan.c \
  pglite/other_extensions/pg_textsearch/src/access/vacuum.c \
  pglite/other_extensions/pg_textsearch/src/memtable/arena.c \
  pglite/other_extensions/pg_textsearch/src/memtable/expull.c \
  pglite/other_extensions/pg_textsearch/src/memtable/memtable.c \
  pglite/other_extensions/pg_textsearch/src/memtable/posting.c \
  pglite/other_extensions/pg_textsearch/src/memtable/stringtable.c \
  pglite/other_extensions/pg_textsearch/src/memtable/scan.c \
  pglite/other_extensions/pg_textsearch/src/memtable/source.c \
  pglite/other_extensions/pg_textsearch/src/segment/segment.c \
  pglite/other_extensions/pg_textsearch/src/segment/dictionary.c \
  pglite/other_extensions/pg_textsearch/src/segment/scan.c \
  pglite/other_extensions/pg_textsearch/src/segment/merge.c \
  pglite/other_extensions/pg_textsearch/src/segment/docmap.c \
  pglite/other_extensions/pg_textsearch/src/segment/alive_bitset.c \
  pglite/other_extensions/pg_textsearch/src/segment/compression.c \
  pglite/other_extensions/pg_textsearch/src/segment/fieldnorm.c \
  pglite/other_extensions/pg_textsearch/src/scoring/bmw.c \
  pglite/other_extensions/pg_textsearch/src/scoring/bm25.c \
  pglite/other_extensions/pg_textsearch/src/types/array.c \
  pglite/other_extensions/pg_textsearch/src/types/vector.c \
  pglite/other_extensions/pg_textsearch/src/types/query.c \
  pglite/other_extensions/pg_textsearch/src/index/state.c \
  pglite/other_extensions/pg_textsearch/src/index/registry.c \
  pglite/other_extensions/pg_textsearch/src/index/metapage.c \
  pglite/other_extensions/pg_textsearch/src/index/limit.c \
  pglite/other_extensions/pg_textsearch/src/index/memory.c \
  pglite/other_extensions/pg_textsearch/src/index/resolve.c \
  pglite/other_extensions/pg_textsearch/src/index/source.c \
  pglite/other_extensions/pg_textsearch/src/replication/rmgr.c \
  pglite/other_extensions/pg_textsearch/src/planner/hooks.c \
  pglite/other_extensions/pg_textsearch/src/planner/cost.c \
  pglite/other_extensions/pg_textsearch/src/debug/dump.c; do
  obj="pglite/src/pglitec/pg_textsearch/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "pglite/other_extensions/pg_textsearch/src/mod.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_pg_textsearch_Pg_magic_func -D_PG_init=pglite_pg_textsearch_PG_init)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -DPG_TEXTSEARCH_VERSION='"1.2.0"' \
    -I"$ROOT_DIR/pglite/other_extensions/pg_textsearch/src" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

mkdir -p pglite/src/pglitec/age
for source in \
  pglite/other_extensions/age/src/backend/age.c \
  pglite/other_extensions/age/src/backend/catalog/ag_catalog.c \
  pglite/other_extensions/age/src/backend/catalog/ag_graph.c \
  pglite/other_extensions/age/src/backend/catalog/ag_label.c \
  pglite/other_extensions/age/src/backend/catalog/ag_namespace.c \
  pglite/other_extensions/age/src/backend/commands/graph_commands.c \
  pglite/other_extensions/age/src/backend/commands/label_commands.c \
  pglite/other_extensions/age/src/backend/executor/cypher_create.c \
  pglite/other_extensions/age/src/backend/executor/cypher_delete.c \
  pglite/other_extensions/age/src/backend/executor/cypher_merge.c \
  pglite/other_extensions/age/src/backend/executor/cypher_set.c \
  pglite/other_extensions/age/src/backend/executor/cypher_utils.c \
  pglite/other_extensions/age/src/backend/nodes/ag_nodes.c \
  pglite/other_extensions/age/src/backend/nodes/cypher_copyfuncs.c \
  pglite/other_extensions/age/src/backend/nodes/cypher_outfuncs.c \
  pglite/other_extensions/age/src/backend/nodes/cypher_readfuncs.c \
  pglite/other_extensions/age/src/backend/optimizer/cypher_createplan.c \
  pglite/other_extensions/age/src/backend/optimizer/cypher_pathnode.c \
  pglite/other_extensions/age/src/backend/optimizer/cypher_paths.c \
  pglite/other_extensions/age/src/backend/parser/ag_scanner.c \
  pglite/other_extensions/age/src/backend/parser/cypher_analyze.c \
  pglite/other_extensions/age/src/backend/parser/cypher_clause.c \
  pglite/other_extensions/age/src/backend/parser/cypher_expr.c \
  pglite/other_extensions/age/src/backend/parser/cypher_gram.c \
  pglite/other_extensions/age/src/backend/parser/cypher_item.c \
  pglite/other_extensions/age/src/backend/parser/cypher_keywords.c \
  pglite/other_extensions/age/src/backend/parser/cypher_parse_agg.c \
  pglite/other_extensions/age/src/backend/parser/cypher_parse_node.c \
  pglite/other_extensions/age/src/backend/parser/cypher_parser.c \
  pglite/other_extensions/age/src/backend/parser/cypher_transform_entity.c \
  pglite/other_extensions/age/src/backend/utils/adt/ag_float8_supp.c \
  pglite/other_extensions/age/src/backend/utils/adt/age_global_graph.c \
  pglite/other_extensions/age/src/backend/utils/adt/age_graphid_ds.c \
  pglite/other_extensions/age/src/backend/utils/adt/age_session_info.c \
  pglite/other_extensions/age/src/backend/utils/adt/age_vle.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_ext.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_gin.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_ops.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_parser.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_raw.c \
  pglite/other_extensions/age/src/backend/utils/adt/agtype_util.c \
  pglite/other_extensions/age/src/backend/utils/adt/cypher_funcs.c \
  pglite/other_extensions/age/src/backend/utils/adt/graphid.c \
  pglite/other_extensions/age/src/backend/utils/ag_func.c \
  pglite/other_extensions/age/src/backend/utils/ag_guc.c \
  pglite/other_extensions/age/src/backend/utils/cache/ag_cache.c \
  pglite/other_extensions/age/src/backend/utils/graph_generation.c \
  pglite/other_extensions/age/src/backend/utils/load/ag_load_edges.c \
  pglite/other_extensions/age/src/backend/utils/load/ag_load_labels.c \
  pglite/other_extensions/age/src/backend/utils/load/age_load.c \
  pglite/other_extensions/age/src/backend/utils/name_validation.c; do
  obj="pglite/src/pglitec/age/${source//\//_}.o"
  magic_flags=()
  if [[ "$source" == "pglite/other_extensions/age/src/backend/age.c" ]]; then
    magic_flags=(-DPg_magic_func=pglite_age_Pg_magic_func -D_PG_init=pglite_age_PG_init)
  fi
  "$CC" $CFLAGS \
    "${magic_flags[@]}" \
    -I"$ROOT_DIR/pglite/other_extensions/age/src/include" \
    -I"$ROOT_DIR/pglite/other_extensions/age/src/include/parser" \
    -I"$ROOT_DIR/src/include" \
    -o "$obj" \
    -c "$source"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $ROOT_DIR/$obj"
done

build_postgis_core() {
  local postgis_src="$ROOT_DIR/pglite/other_extensions/postgis"
  local postgis_build="$BUILD_DIR/postgis-core"
  local pg_config="$BUILD_DIR/pglite-wasi-pg_config"
  local lookup_c="$BUILD_DIR/pglite-postgis-lookup.c"
  local lookup_o="$BUILD_DIR/pglite-postgis-lookup.o"
  local raster_lookup_c="$BUILD_DIR/pglite-postgis-raster-lookup.c"
  local raster_lookup_o="$BUILD_DIR/pglite-postgis-raster-lookup.o"
  local topology_lookup_c="$BUILD_DIR/pglite-postgis-topology-lookup.c"
  local topology_lookup_o="$BUILD_DIR/pglite-postgis-topology-lookup.o"
  local cxx_exception_stubs_c="$BUILD_DIR/pglite-cxx-exception-stubs.cpp"
  local cxx_exception_stubs_o="$BUILD_DIR/pglite-cxx-exception-stubs.o"
  local json_header="$PGLITE_WASI_JSON_C_PREFIX/include/json-c/json.h"
  local json_header_cache
  local build_raster=0

  if [[ ! -d "$postgis_src" ||
        ! -f "$PGLITE_WASI_PROJ_PREFIX/lib/libproj.a" ||
        ! -f "$PGLITE_WASI_GEOS_PREFIX/lib/libgeos_c.a" ||
        ! -f "$PGLITE_WASI_JSON_C_PREFIX/lib/libjson-c.a" ]]; then
    echo "Skipping PostGIS core build; WASI dependencies are incomplete" >&2
    return
  fi
  if [[ -x "$PGLITE_WASI_GDAL_PREFIX/bin/gdal-config" &&
        -f "$PGLITE_WASI_GDAL_PREFIX/lib/libgdal.a" ]]; then
    build_raster=1
  fi

  rm -rf "$postgis_build"
  mkdir -p "$postgis_build"
  cp -a "$postgis_src/." "$postgis_build/"
  find "$postgis_build" \( -name '*.o' -o -name '*.lo' -o -name '*.la' -o -name '*.a' -o -name '*.so' \) -delete
  find "$postgis_build" -name Makefile -delete
  rm -f "$postgis_build/config.status" "$postgis_build/GNUmakefile"
  if [[ ! -x "$postgis_build/configure" ]]; then
    (
      cd "$postgis_build"
      ./autogen.sh
    )
  fi

  cat > "$pg_config" <<EOF
#!/usr/bin/env sh
case "\$1" in
  --version) echo 'PostgreSQL 18.3' ;;
  --pgxs) echo '$ROOT_DIR/src/makefiles/pgxs.mk' ;;
  --pkglibdir) echo '$PREFIX/lib/postgresql' ;;
  --libdir) echo '$ROOT_DIR/dist/lib' ;;
  --sharedir) echo '$PREFIX/share/postgresql' ;;
  --includedir) echo '$ROOT_DIR/src/interfaces/libpq' ;;
  --includedir-server) echo '$ROOT_DIR/src/include' ;;
  --docdir) echo '$PREFIX/share/doc/postgresql' ;;
  --mandir) echo '$PREFIX/share/man' ;;
  --localedir) echo '$PREFIX/share/locale' ;;
  --bindir) echo '$PREFIX/bin' ;;
  --cc) echo '$CC' ;;
  --cflags) echo '$CFLAGS -I$ROOT_DIR/src/include -I$ROOT_DIR/src/interfaces/libpq -I$ROOT_DIR/dist/include' ;;
  --configure) echo '--host=wasm32-wasi --with-template=wasi' ;;
  *) echo "unsupported pg_config option: \$1" >&2; exit 1 ;;
esac
EOF
  chmod +x "$pg_config"

  local raster_configure_args=(--without-raster)
  if [[ "$build_raster" == 1 ]]; then
    raster_configure_args=(--with-raster --with-gdalconfig="$PGLITE_WASI_GDAL_PREFIX/bin/gdal-config")
  fi
  json_header_cache="ac_cv_file_$(printf '%s' "$json_header" | sed 's/[^A-Za-z0-9_]/_/g')"

  (
    cd "$postgis_build"
    export "$json_header_cache=yes"
    PATH="$PGLITE_WASI_GDAL_PREFIX/bin:$PGLITE_WASI_GEOS_PREFIX/bin:$PGLITE_WASI_LIBXML2_PREFIX/bin:$PATH" \
    CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" PG_CONFIG="$pg_config" \
    PROJ_VERSION=9.7.0 \
    ac_cv_lib_pq_PQserverVersion=yes \
    CFLAGS="$CFLAGS -DPg_magic_func=pglite_postgis_Pg_magic_func -D_PG_init=pglite_postgis_PG_init -D_PG_fini=pglite_postgis_PG_fini -Ddifference=pglite_postgis_legacy_difference -Dpg_finfo_difference=pglite_postgis_legacy_pg_finfo_difference -I$ROOT_DIR/src/include -I$ROOT_DIR/src/interfaces/libpq -I$ROOT_DIR/dist/include -I$PGLITE_WASI_JSON_C_PREFIX/include -I$PGLITE_WASI_LIBXML2_PREFIX/include/libxml2 -I$PGLITE_WASI_GEOS_PREFIX/include -I$PGLITE_WASI_PROJ_PREFIX/include -fno-math-errno -fno-signed-zeros -O2" \
    CXXFLAGS="-D_WASI_EMULATED_GETPID -include $ROOT_DIR/../.bin/tmp/proj-wasi-threads.h" \
    LDFLAGS="$LDFLAGS -L$ROOT_DIR/dist/lib -L$PGLITE_WASI_JSON_C_PREFIX/lib -L$PGLITE_WASI_LIBXML2_PREFIX/lib -L$PGLITE_WASI_GEOS_PREFIX/lib -L$PGLITE_WASI_PROJ_PREFIX/lib -L$PGLITE_WASI_SQLITE_PREFIX/lib -L$PGLITE_WASI_GDAL_PREFIX/lib -lm" \
    LIBS="-lgdal -lproj -lsqlite3 -lc++ -lc++abi -lsetjmp -lwasi-emulated-getpid -lwasi-emulated-signal -lwasi-emulated-mman" \
    ./configure \
      --host=wasm32-wasi \
      --without-protobuf \
      "${raster_configure_args[@]}" \
      --without-address-standardizer \
      --without-tiger \
      --with-pgconfig="$pg_config" \
      --with-geosconfig="$PGLITE_WASI_GEOS_PREFIX/bin/geos-config" \
      --with-jsondir="$PGLITE_WASI_JSON_C_PREFIX" \
      --with-xml2config="$PGLITE_WASI_LIBXML2_PREFIX/bin/xml2-config" \
      --with-projdir="$PGLITE_WASI_PROJ_PREFIX"
    make -j "${PGLITE_WASI_JOBS:-2}" -C liblwgeom
    make -j "${PGLITE_WASI_JOBS:-2}" -C postgis
    perl -0pi -e 's/pglite_postgis_Pg_magic_func/pglite_postgis_topology_Pg_magic_func/g; s/pglite_postgis_PG_init/pglite_postgis_topology_PG_init/g; s/pglite_postgis_PG_fini/pglite_postgis_topology_PG_fini/g' topology/Makefile
    make -j "${PGLITE_WASI_JOBS:-2}" -C topology
    if [[ "$build_raster" == 1 ]]; then
      perl -0pi -e 's/pglite_postgis_Pg_magic_func/pglite_postgis_raster_Pg_magic_func/g; s/pglite_postgis_PG_init/pglite_postgis_raster_PG_init/g; s/pglite_postgis_PG_fini/pglite_postgis_raster_PG_fini/g' raster/rt_pg/Makefile
      make -j "${PGLITE_WASI_JOBS:-2}" -C raster/rt_core
      make -j "${PGLITE_WASI_JOBS:-2}" -C raster/rt_pg
    fi
    make -j "${PGLITE_WASI_JOBS:-2}" -C extensions
  )

  {
    echo '#include <string.h>'
    echo '#include "postgres.h"'
    echo 'extern void pglite_postgis_legacy_difference(void);'
    echo 'extern void pg_finfo_pglite_postgis_legacy_difference(void);'
    "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/postgis/*.o |
      awk '/ [T] / {
        name=$3
        if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
            name != "pglite_postgis_Pg_magic_func" &&
            name != "pglite_postgis_PG_init")
          print name
      }' |
      sort -u |
      awk '{ print "extern void " $1 "(void);" }'
    echo 'void *pglite_postgis_lookup_function(const char *funcname) {'
    echo '  if (strcmp(funcname, "difference") == 0) return pglite_postgis_legacy_difference;'
    echo '  if (strcmp(funcname, "pg_finfo_difference") == 0) return pg_finfo_pglite_postgis_legacy_difference;'
    "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/postgis/*.o |
      awk '/ [T] / {
        name=$3
        if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
            name != "pglite_postgis_Pg_magic_func" &&
            name != "pglite_postgis_PG_init")
          print name
      }' |
      sort -u |
      awk '{ print "  if (strcmp(funcname, \"" $1 "\") == 0) return " $1 ";" }'
    echo '  return NULL;'
    echo '}'
  } > "$lookup_c"

  "$CC" $CFLAGS -I"$ROOT_DIR/src/include" -o "$lookup_o" -c "$lookup_c"

  {
    echo '#include <string.h>'
    echo '#include "postgres.h"'
    "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/topology/*.o |
      awk '/ [T] / {
        name=$3
        if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
            name != "pglite_postgis_topology_Pg_magic_func" &&
            name != "pglite_postgis_topology_PG_init")
          print name
      }' |
      sort -u |
      awk '{ print "extern void " $1 "(void);" }'
    echo 'void *pglite_postgis_topology_lookup_function(const char *funcname) {'
    "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/topology/*.o |
      awk '/ [T] / {
        name=$3
        if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
            name != "pglite_postgis_topology_Pg_magic_func" &&
            name != "pglite_postgis_topology_PG_init")
          print name
      }' |
      sort -u |
      awk '{ print "  if (strcmp(funcname, \"" $1 "\") == 0) return " $1 ";" }'
    echo '  return NULL;'
    echo '}'
  } > "$topology_lookup_c"
  "$CC" $CFLAGS -I"$ROOT_DIR/src/include" -o "$topology_lookup_o" -c "$topology_lookup_c"

  if [[ "$build_raster" == 1 ]]; then
    {
      echo '#include <string.h>'
      echo '#include "postgres.h"'
      "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/raster/rt_pg/*.o |
        awk '/ [T] / {
          name=$3
          if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
              name != "pglite_postgis_raster_Pg_magic_func" &&
              name != "pglite_postgis_raster_PG_init")
            print name
        }' |
        sort -u |
        awk '{ print "extern void " $1 "(void);" }'
      echo 'void *pglite_postgis_raster_lookup_function(const char *funcname) {'
      "$WASI_SDK_PATH/bin/llvm-nm" -g "$postgis_build"/raster/rt_pg/*.o |
        awk '/ [T] / {
          name=$3
          if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/ &&
              name != "pglite_postgis_raster_Pg_magic_func" &&
              name != "pglite_postgis_raster_PG_init")
            print name
        }' |
        sort -u |
        awk '{ print "  if (strcmp(funcname, \"" $1 "\") == 0) return " $1 ";" }'
      echo '  return NULL;'
      echo '}'
    } > "$raster_lookup_c"
    "$CC" $CFLAGS -I"$ROOT_DIR/src/include" -o "$raster_lookup_o" -c "$raster_lookup_c"
  fi

  cat > "$cxx_exception_stubs_c" <<'EOF'
#include <exception>
#include <typeinfo>

extern "C" {
#include "postgres.h"
#include "utils/elog.h"
}

extern "C"
void *
__cxa_allocate_exception(size_t thrown_size)
{
	return malloc(thrown_size == 0 ? 1 : thrown_size);
}

extern "C"
void
__cxa_throw(void *thrown_exception, std::type_info *tinfo, void (*dest)(void *))
{
	(void) tinfo;
	(void) dest;
	const std::exception *ex = static_cast<const std::exception *>(thrown_exception);
	ereport(ERROR, (errmsg("%s", ex ? ex->what() : "C++ exception")));
}

extern "C"
int
__cxa_thread_atexit(void (*dtor)(void *), void *obj, void *dso_symbol)
{
	(void) dtor;
	(void) obj;
	(void) dso_symbol;
	return 0;
}
EOF
  "$CXX" -D__PGLITE__ -D_WASI_EMULATED_GETPID -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS -D_WASI_EMULATED_MMAN $PGLITE_WASI_SJLJ_FLAGS -I"$ROOT_DIR/src/include" -I"$ROOT_DIR/dist/include" -I"$PGLITE_WASI_ICU_PREFIX/include" -fno-exceptions -o "$cxx_exception_stubs_o" -c "$cxx_exception_stubs_c"

  for obj in "$postgis_build"/postgis/*.o "$postgis_build"/deps/flatgeobuf/*.o; do
    [[ -e "$obj" ]] || continue
    POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $obj"
  done
  for obj in "$postgis_build"/topology/*.o; do
    [[ -e "$obj" ]] || continue
    POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $obj"
  done
  if [[ "$build_raster" == 1 ]]; then
    for obj in "$postgis_build"/raster/rt_pg/*.o; do
      [[ -e "$obj" ]] || continue
      POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $obj"
    done
    POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $postgis_build/raster/rt_core/librtcore.a $raster_lookup_o $PGLITE_WASI_GDAL_PREFIX/lib/libgdal.a"
  fi
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $postgis_build/libpgcommon/libpgcommon.a $postgis_build/liblwgeom/.libs/liblwgeom.a $postgis_build/deps/ryu/.libs/libryu.a $lookup_o $topology_lookup_o $cxx_exception_stubs_o"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $PGLITE_WASI_GEOS_PREFIX/lib/libgeos_c.a $PGLITE_WASI_GEOS_PREFIX/lib/libgeos.a $PGLITE_WASI_PROJ_PREFIX/lib/libproj.a $PGLITE_WASI_SQLITE_PREFIX/lib/libsqlite3.a $PGLITE_WASI_JSON_C_PREFIX/lib/libjson-c.a $PGLITE_WASI_LIBXML2_PREFIX/lib/libxml2.a"
  POSTGRES_PGLITE_WASI_EXTRA_OBJS="$POSTGRES_PGLITE_WASI_EXTRA_OBJS $WASI_SDK_PATH/share/wasi-sysroot/lib/wasm32-wasi/libc++.a $WASI_SDK_PATH/share/wasi-sysroot/lib/wasm32-wasi/libc++abi.a"
}

build_postgis_core

export POSTGRES_PGLITE_WASI_EXTRA_OBJS

make PORTNAME=wasi -C src/backend generated-headers
make PORTNAME=wasi -C src/backend pglite-wasi
make PORTNAME=wasi -C src/backend install-pglite-wasi
mkdir -p "$PREFIX/share"
make PORTNAME=wasi -C src/include/catalog install
make PORTNAME=wasi -C src/backend/catalog install-data
make PORTNAME=wasi -C src/backend/tsearch install-data
make PORTNAME=wasi -C src/backend/utils install-data
make PORTNAME=wasi -C src/timezone/tznames install
make PORTNAME=wasi -C src/pl/plpgsql/src install-data
make PORTNAME=wasi -C src/backend/snowball snowball_create.sql
mkdir -p "$PREFIX/share/extension" "$PREFIX/share/tsearch_data"
install -m 644 src/backend/libpq/pg_hba.conf.sample "$PREFIX/share/pg_hba.conf.sample"
install -m 644 src/backend/libpq/pg_ident.conf.sample "$PREFIX/share/pg_ident.conf.sample"
install -m 644 src/backend/snowball/snowball_create.sql "$PREFIX/share/snowball_create.sql"
install -m 644 src/backend/snowball/stopwords/*.stop "$PREFIX/share/tsearch_data/"
install -m 644 src/backend/utils/misc/postgresql.conf.sample "$PREFIX/share/postgresql.conf.sample"

install_pglite_extension_sqls() {
  local extension_name=$1
  local extension_dir=$2
  local sql_dir=$3
  local control="$extension_dir/$extension_name.control"

  install -m 644 "$control" "$PREFIX/share/extension/"
  for sql in "$sql_dir"/"$extension_name"--*.sql; do
    [[ -e "$sql" ]] || continue
    install -m 644 "$sql" "$PREFIX/share/extension/"
  done

  local default_version
  default_version=$(sed -n "s/^default_version[[:space:]]*=[[:space:]]*'\\([^']*\\)'.*/\\1/p" "$control" | head -1)
  if [[ -z "$default_version" || -e "$sql_dir/$extension_name--$default_version.sql" ]]; then
    return
  fi

  local base_sql
  base_sql=$(find "$sql_dir" -maxdepth 1 -name "$extension_name--*.sql" ! -name "$extension_name--*--*.sql" | sort -V | head -1)
  if [[ -z "$base_sql" ]]; then
    return
  fi

  local base_file current_version
  base_file=$(basename "$base_sql")
  current_version=${base_file#"$extension_name--"}
  current_version=${current_version%.sql}
  local temp_sql
  temp_sql=$(mktemp)
  {
    cat "$base_sql"
    while [[ "$current_version" != "$default_version" ]]; do
      local upgrade_sql upgrade_file
      upgrade_sql=$(find "$sql_dir" -maxdepth 1 -name "$extension_name--$current_version--*.sql" | sort -V | head -1)
      if [[ -z "$upgrade_sql" ]]; then
        break
      fi
      sed '/^\\echo /d' "$upgrade_sql"
      upgrade_file=$(basename "$upgrade_sql")
      current_version=${upgrade_file#"$extension_name--$current_version--"}
      current_version=${current_version%.sql}
    done
  } > "$temp_sql"
  if [[ "$current_version" != "$default_version" ]]; then
    echo "Could not build $extension_name install script for version $default_version" >&2
    rm -f "$temp_sql"
    exit 1
  fi
  mv "$temp_sql" "$PREFIX/share/extension/$extension_name--$default_version.sql"
}

for control in contrib/*/*.control; do
  extension_dir=$(dirname "$control")
  extension_name=$(basename "$control" .control)
  install -m 644 "$control" "$PREFIX/share/extension/"
  for sql in "$extension_dir"/*.sql; do
    [[ -e "$sql" ]] || continue
    sql_file=$(basename "$sql")
    if [[ "$extension_name" == "pg_freespacemap" && "$sql_file" == "pg_freespacemap--1.2--1.3.sql" ]]; then
      cat > "$PREFIX/share/extension/$sql_file" <<'SQL'
/* contrib/pg_freespacemap/pg_freespacemap--1.2--1.3.sql */

CREATE OR REPLACE FUNCTION
  pg_freespace(rel regclass, blkno OUT bigint, avail OUT int2)
RETURNS SETOF RECORD
AS 'MODULE_PATHNAME', 'pg_freespace_rel'
LANGUAGE C STRICT PARALLEL SAFE;
SQL
    elif [[ "$extension_name" == "pg_freespacemap" ]]; then
      sed '/^\\echo /d' "$sql" > "$PREFIX/share/extension/$sql_file"
    else
      install -m 644 "$sql" "$PREFIX/share/extension/"
    fi
  done
  default_version=$(sed -n "s/^default_version[[:space:]]*=[[:space:]]*'\\([^']*\\)'.*/\\1/p" "$control" | head -1)
  if [[ "$extension_name" == "pg_freespacemap" && "$default_version" == "1.3" ]]; then
    {
      sed '/^\\echo /d' "$extension_dir/pg_freespacemap--1.1.sql"
      sed '/^\\echo /d' "$extension_dir/pg_freespacemap--1.1--1.2.sql"
      cat <<'SQL'
/* contrib/pg_freespacemap/pg_freespacemap--1.2--1.3.sql */

CREATE OR REPLACE FUNCTION
  pg_freespace(rel regclass, blkno OUT bigint, avail OUT int2)
RETURNS SETOF RECORD
AS 'MODULE_PATHNAME', 'pg_freespace_rel'
LANGUAGE C STRICT PARALLEL SAFE;
SQL
    } > "$PREFIX/share/extension/$extension_name--$default_version.sql"
    continue
  fi
  if [[ -n "$default_version" ]]; then
    base_sql=$(find "$extension_dir" -maxdepth 1 -name "$extension_name--*.sql" ! -name "$extension_name--*--*.sql" | sort -V | head -1)
    if [[ -n "$base_sql" ]]; then
      base_file=$(basename "$base_sql")
      current_version=${base_file#"$extension_name--"}
      current_version=${current_version%.sql}
      {
        cat "$base_sql"
        while [[ "$current_version" != "$default_version" ]]; do
          upgrade_sql=$(find "$extension_dir" -maxdepth 1 -name "$extension_name--$current_version--*.sql" | sort -V | head -1)
          if [[ -z "$upgrade_sql" ]]; then
            break
          fi
          sed '/^\\echo /d' "$upgrade_sql"
          upgrade_file=$(basename "$upgrade_sql")
          current_version=${upgrade_file#"$extension_name--$current_version--"}
          current_version=${current_version%.sql}
        done
      } > "$PREFIX/share/extension/$extension_name--$default_version.sql"
    fi
  fi
done
install -m 644 contrib/pg_trgm/pg_trgm.control "$PREFIX/share/extension/pg_trgm.control"
install -m 644 contrib/pg_trgm/pg_trgm--*.sql "$PREFIX/share/extension/"
{
  cat contrib/pg_trgm/pg_trgm--1.3.sql
  for upgrade in \
    contrib/pg_trgm/pg_trgm--1.3--1.4.sql \
    contrib/pg_trgm/pg_trgm--1.4--1.5.sql \
    contrib/pg_trgm/pg_trgm--1.5--1.6.sql; do
    sed '/^\\echo /d' "$upgrade"
  done
} > "$PREFIX/share/extension/pg_trgm--1.6.sql"
install -m 644 contrib/unaccent/unaccent.control "$PREFIX/share/extension/unaccent.control"
install -m 644 contrib/unaccent/unaccent--*.sql "$PREFIX/share/extension/"
install -m 644 contrib/unaccent/unaccent.rules "$PREFIX/share/tsearch_data/unaccent.rules"
install -m 644 contrib/dict_xsyn/xsyn_sample.rules "$PREFIX/share/tsearch_data/xsyn_sample.rules"
cat > "$PREFIX/share/extension/pg_uuidv7.control" <<'EOF'
comment = 'UUID version 7'
default_version = '1.7'
module_pathname = '$libdir/pg_uuidv7'
relocatable = true
EOF
cat > "$PREFIX/share/extension/pg_uuidv7--1.7.sql" <<'EOF'
CREATE FUNCTION uuid_generate_v7()
RETURNS uuid
AS 'MODULE_PATHNAME', 'uuid_generate_v7'
VOLATILE STRICT LANGUAGE C PARALLEL SAFE;

CREATE FUNCTION uuid_v7_to_timestamptz(uuid)
RETURNS timestamptz
AS 'MODULE_PATHNAME', 'uuid_v7_to_timestamptz'
STABLE STRICT LANGUAGE C PARALLEL SAFE;

CREATE FUNCTION uuid_timestamptz_to_v7(timestamptz, zero bool = false)
RETURNS uuid
AS 'MODULE_PATHNAME', 'uuid_timestamptz_to_v7'
STABLE STRICT LANGUAGE C PARALLEL SAFE;

CREATE FUNCTION uuid_v7_to_timestamp(uuid)
RETURNS timestamp
AS 'MODULE_PATHNAME', 'uuid_v7_to_timestamp'
STABLE STRICT LANGUAGE C PARALLEL SAFE;

CREATE FUNCTION uuid_timestamp_to_v7(timestamp, zero bool = false)
RETURNS uuid
AS 'MODULE_PATHNAME', 'uuid_timestamp_to_v7'
STABLE STRICT LANGUAGE C PARALLEL SAFE;
EOF
install_pglite_extension_sqls pg_hashids pglite/other_extensions/pg_hashids pglite/other_extensions/pg_hashids
install_pglite_extension_sqls vector pglite/other_extensions/vector pglite/other_extensions/vector/sql
install_pglite_extension_sqls pg_ivm pglite/other_extensions/pg_ivm pglite/other_extensions/pg_ivm
install_pglite_extension_sqls pg_textsearch pglite/other_extensions/pg_textsearch pglite/other_extensions/pg_textsearch/sql
install_pglite_extension_sqls age pglite/other_extensions/age pglite/other_extensions/age
install_pglite_extension_sqls pgtap pglite/other_extensions/pgtap pglite/other_extensions/pgtap/sql
if [[ -d /usr/share/zoneinfo ]]; then
  rm -rf "$PREFIX/share/zoneinfo"
  mkdir -p "$PREFIX/share/zoneinfo"
  cp -LR /usr/share/zoneinfo/. "$PREFIX/share/zoneinfo/"
fi
if [[ -d "$PGLITE_WASI_ICU_PREFIX/share/icu/76.1/icudt76l" ]]; then
  icu_src="$PGLITE_WASI_ICU_PREFIX/share/icu/76.1/icudt76l"
  icu_dst="$PREFIX/share/icu/76.1/icudt76l"
  rm -rf "$PREFIX/share/icu"
  mkdir -p "$icu_dst"
  for pattern in \
    root.res en.res en_*.res de.res de_*.res fr.res fr_*.res it.res it_*.res \
    ja.res ja_*.res ko.res ko_*.res sv.res sv_*.res tr.res tr_*.res es.res es_*.res \
    cnvalias.icu currencyNumericCodes.res likelySubtags.res numberingSystems.res \
    pluralRanges.res plurals.res supplementalData.res res_index.res pool.res metadata.res
  do
    for file in "$icu_src"/$pattern; do
      [[ -e "$file" ]] || continue
      cp "$file" "$icu_dst/"
    done
  done
  for dir in coll curr lang region unit zone; do
    if [[ -d "$icu_src/$dir" ]]; then
      mkdir -p "$icu_dst/$dir"
      if [[ "$dir" == "coll" && -f "$icu_src/$dir/ucadata.icu" ]]; then
        cp "$icu_src/$dir/ucadata.icu" "$icu_dst/$dir/"
      fi
      for file in "$icu_src/$dir"/res_index.res "$icu_src/$dir"/pool.res "$icu_src/$dir"/supplementalData.res; do
        [[ -e "$file" ]] || continue
        cp "$file" "$icu_dst/$dir/"
      done
      for pattern in root.res en.res en_*.res de.res de_*.res fr.res fr_*.res it.res it_*.res ja.res ja_*.res ko.res ko_*.res sv.res sv_*.res tr.res tr_*.res es.res es_*.res; do
        for file in "$icu_src/$dir"/$pattern; do
          [[ -e "$file" ]] || continue
          cp "$file" "$icu_dst/$dir/"
        done
      done
    fi
  done
fi
if [[ -d "$PGLITE_WASI_PROJ_PREFIX/share/proj" ]]; then
  rm -rf "$PREFIX/share/proj"
  mkdir -p "$PREFIX/share/proj"
  cp -R "$PGLITE_WASI_PROJ_PREFIX/share/proj/." "$PREFIX/share/proj/"
fi
tar -C "$PREFIX/share" -czf "$PREFIX/bin/pglite.wasi.share.tar.gz" .

package_pglite_extension() {
  local extension_name=$1
  local tarball_name=$2
  local temp_dir
  temp_dir=$(mktemp -d)
  mkdir -p "$temp_dir/share/postgresql/extension"
  install -m 644 "$PREFIX/share/extension/$extension_name.control" "$temp_dir/share/postgresql/extension/"
  for sql in "$PREFIX/share/extension/$extension_name"--*.sql; do
    [[ -e "$sql" ]] || continue
    install -m 644 "$sql" "$temp_dir/share/postgresql/extension/"
  done
  mkdir -p "$PREFIX/extensions/other"
  tar -C "$temp_dir" -czf "$PREFIX/extensions/other/$tarball_name.tar.gz" .
  rm -rf "$temp_dir"
}

package_pglite_extension age age
package_pglite_extension pg_hashids pg_hashids
package_pglite_extension pg_ivm pg_ivm
package_pglite_extension pg_textsearch pg_textsearch
package_pglite_extension pg_uuidv7 pg_uuidv7
package_pglite_extension vector vector
package_pglite_extension pgtap pgtap

package_postgis_extension() {
  local postgis_src_dir="$ROOT_DIR/pglite/other_extensions/postgis"
  local postgis_build_dir="$BUILD_DIR/postgis-core"
  local temp_dir
  temp_dir=$(mktemp -d)
  mkdir -p "$temp_dir/share/postgresql/extension"

  for extension_name in postgis postgis_raster postgis_topology postgis_tiger_geocoder; do
    local extension_dir="$postgis_build_dir/extensions/$extension_name"
    if [[ ! -d "$extension_dir" ]]; then
      extension_dir="$postgis_src_dir/extensions/$extension_name"
    fi
    [[ -d "$extension_dir" ]] || continue
    [[ -f "$extension_dir/$extension_name.control" ]] || continue
    install -m 644 "$extension_dir/$extension_name.control" "$temp_dir/share/postgresql/extension/"
    for sql in "$extension_dir/sql/$extension_name"--*.sql; do
      [[ -e "$sql" ]] || continue
      install -m 644 "$sql" "$temp_dir/share/postgresql/extension/"
    done
  done

  if [[ -f "$postgis_build_dir/extensions/postgis/sql/spatial_ref_sys.sql" ]]; then
    install -m 644 "$postgis_build_dir/extensions/postgis/sql/spatial_ref_sys.sql" "$temp_dir/share/postgresql/extension/"
  elif [[ -f "$postgis_src_dir/extensions/postgis/sql/spatial_ref_sys.sql" ]]; then
    install -m 644 "$postgis_src_dir/extensions/postgis/sql/spatial_ref_sys.sql" "$temp_dir/share/postgresql/extension/"
  fi

  if [[ -d "$PREFIX/share/proj" ]]; then
    mkdir -p "$temp_dir/share/proj"
    cp -R "$PREFIX/share/proj/." "$temp_dir/share/proj/"
  fi

  mkdir -p "$PREFIX/extensions/other"
  tar -C "$temp_dir" -czf "$PREFIX/extensions/other/postgis.tar.gz" .
  rm -rf "$temp_dir"
}

package_postgis_extension
