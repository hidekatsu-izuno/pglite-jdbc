#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
bin_dir="$repo_root/.bin"
tmp_dir="$bin_dir/tmp"
jobs=${PGLITE_WASI_JOBS:-2}
PGLITE_WASI_SJLJ_FLAGS=${PGLITE_WASI_SJLJ_FLAGS:-"-mllvm -wasm-enable-sjlj -mllvm -wasm-use-legacy-eh=false"}
wasm_flags_marker=".pglite-wasi-exnref-sjlj"

exec 3>&1
exec 1>&2

cmake_generator_args=(-G "Unix Makefiles")

mkdir -p "$tmp_dir"

need_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required tool is missing: $1" >&2
    exit 2
  fi
}

download() {
  local url=$1
  local output=$2
  if [[ ! -f "$output" ]]; then
    curl -L "$url" -o "$output"
  fi
}

has_wasm_flags_marker() {
  [[ -f "$1/$wasm_flags_marker" ]]
}

write_wasm_flags_marker() {
  printf '%s\n' "$PGLITE_WASI_SJLJ_FLAGS" > "$1/$wasm_flags_marker"
}

ensure_wasi_sdk() {
  local version=${WASI_SDK_VERSION:-25}
  local install_dir=${WASI_SDK_PATH:-"$bin_dir/wasi-sdk"}
  local archive="wasi-sdk-${version}.0-x86_64-linux.tar.gz"
  local url="https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${version}/${archive}"
  local src_dir="$tmp_dir/wasi-sdk-${version}.0-x86_64-linux"

  if [[ -x "$install_dir/bin/clang" ]]; then
    export WASI_SDK_PATH="$install_dir"
    return
  fi

  download "$url" "$tmp_dir/$archive"
  rm -rf "$install_dir" "$src_dir"
  tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  mv "$src_dir" "$install_dir"
  export WASI_SDK_PATH="$install_dir"
}

ensure_libxml2() {
  local version=2.12.10
  local archive="libxml2-${version}.tar.xz"
  local install_dir=${PGLITE_WASI_LIBXML2_PREFIX:-"$bin_dir/wasi-libxml2"}
  local src_dir="$tmp_dir/libxml2-${version}"

  if [[ -f "$install_dir/lib/libxml2.a" &&
        -f "$install_dir/include/libxml2/libxml/parser.h" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_LIBXML2_PREFIX="$install_dir"
    return
  fi

  download "https://download.gnome.org/sources/libxml2/2.12/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$install_dir"
  tar -C "$tmp_dir" -xJf "$tmp_dir/$archive"
  (
    cd "$src_dir"
    CC="$WASI_SDK_PATH/bin/clang" \
    AR="$WASI_SDK_PATH/bin/llvm-ar" \
    RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
    CFLAGS="-O2 $PGLITE_WASI_SJLJ_FLAGS" \
    ./configure \
      --host=wasm32-wasi \
      --prefix="$install_dir" \
      --disable-shared \
      --enable-static \
      --without-python \
      --without-zlib \
      --without-lzma \
      --without-iconv \
      --without-threads \
      --without-http \
      --without-ftp \
      --without-modules \
      --without-debug
    make -j "$jobs"
    make install
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_LIBXML2_PREFIX="$install_dir"
}

ensure_sqlite() {
  local version=3450300
  local archive="sqlite-autoconf-${version}.tar.gz"
  local install_dir=${PGLITE_WASI_SQLITE_PREFIX:-"$bin_dir/wasi-sqlite"}
  local src_dir="$tmp_dir/sqlite-autoconf-${version}"

  if [[ -f "$install_dir/lib/libsqlite3.a" &&
        -f "$install_dir/include/sqlite3.h" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_SQLITE_PREFIX="$install_dir"
    return
  fi

  download "https://www.sqlite.org/2024/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$install_dir"
  tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  (
    cd "$src_dir"
    CC="$WASI_SDK_PATH/bin/clang" \
    AR="$WASI_SDK_PATH/bin/llvm-ar" \
    RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
    CFLAGS="-O2 -DSQLITE_THREADSAFE=0 -DSQLITE_OMIT_LOAD_EXTENSION -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS -D_WASI_EMULATED_GETPID $PGLITE_WASI_SJLJ_FLAGS" \
    LIBS="-lwasi-emulated-signal -lwasi-emulated-process-clocks -lwasi-emulated-getpid" \
    ./configure \
      --host=wasm32-wasi \
      --prefix="$install_dir" \
      --disable-shared \
      --enable-static
    make -j "$jobs"
    mkdir -p "$install_dir/share/icu/76.1/icudt76l/brkitr"
    make install
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_SQLITE_PREFIX="$install_dir"
}

ensure_json_c() {
  local version=0.15
  local archive="json-c-${version}.tar.gz"
  local install_dir=${PGLITE_WASI_JSON_C_PREFIX:-"$bin_dir/wasi-json-c"}
  local src_dir="$tmp_dir/json-c-${version}"
  local build_dir="$tmp_dir/json-c-${version}-wasi-build"

  if [[ -f "$install_dir/lib/libjson-c.a" &&
        -f "$install_dir/include/json-c/json.h" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_JSON_C_PREFIX="$install_dir"
    return
  fi

  download "https://s3.amazonaws.com/json-c_releases/releases/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$build_dir" "$install_dir"
  tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  mkdir -p "$build_dir"
  (
    cd "$build_dir"
    cmake "${cmake_generator_args[@]}" "$src_dir" \
      -DCMAKE_SYSTEM_NAME=WASI \
      -DCMAKE_C_COMPILER="$WASI_SDK_PATH/bin/clang" \
      -DCMAKE_AR="$WASI_SDK_PATH/bin/llvm-ar" \
      -DCMAKE_RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
      -DCMAKE_INSTALL_PREFIX="$install_dir" \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_C_FLAGS="-O2 -DHAVE_CONFIG_H -DHAVE_SNPRINTF $PGLITE_WASI_SJLJ_FLAGS" \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_STATIC_LIBS=ON \
      -DDISABLE_THREAD_LOCAL_STORAGE=ON \
      -DDISABLE_WERROR=ON \
      -DBUILD_TESTING=OFF
    cmake --build . --parallel "$jobs"
    cmake --install .
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_JSON_C_PREFIX="$install_dir"
}

ensure_icu() {
  local version=76_1
  local archive="icu4c-${version}-src.tgz"
  local install_dir=${PGLITE_WASI_ICU_PREFIX:-"$bin_dir/wasi-icu"}
  local src_dir="$tmp_dir/icu/source"
  local host_dir="$tmp_dir/icu-host"
  local wasm_dir="$tmp_dir/icu-wasi"
  local thread_shim="$tmp_dir/icu-wasi-threads.h"

  if [[ -f "$install_dir/lib/libicuuc.a" &&
        -f "$install_dir/lib/libicui18n.a" &&
        -d "$install_dir/share/icu/76.1/icudt76l/brkitr" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_ICU_PREFIX="$install_dir"
    return
  fi

  download "https://github.com/unicode-org/icu/releases/download/release-76-1/${archive}" "$tmp_dir/$archive"
  if [[ ! -d "$src_dir" ]]; then
    rm -rf "$tmp_dir/icu"
    tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  fi
  cp "$src_dir/config/mh-linux" "$src_dir/config/mh-unknown"
  cat > "$thread_shim" <<'EOF'
#pragma once
static char pglite_wasi_icu_tz_utc[] = "UTC";
static char* tzname[2] = { pglite_wasi_icu_tz_utc, pglite_wasi_icu_tz_utc };
namespace std {
class mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
class condition_variable {
public:
  template <class Lock> void wait(Lock&) {}
  template <class Lock, class Predicate> void wait(Lock&, Predicate pred) { while (!pred()) {} }
  void notify_all() {}
  void notify_one() {}
};
template <class Mutex> class unique_lock {
public:
  explicit unique_lock(Mutex& mutex) : mutex_(&mutex), owns_(true) { mutex_->lock(); }
  ~unique_lock() { if (owns_) mutex_->unlock(); }
  unique_lock(const unique_lock&) = delete;
  unique_lock& operator=(const unique_lock&) = delete;
  void lock() { if (!owns_) { mutex_->lock(); owns_ = true; } }
  void unlock() { if (owns_) { mutex_->unlock(); owns_ = false; } }
  bool owns_lock() const { return owns_; }
private:
  Mutex* mutex_;
  bool owns_;
};
}
EOF

  if [[ ! -x "$host_dir/bin/pkgdata" ]]; then
    rm -rf "$host_dir"
    mkdir -p "$host_dir"
    (
      cd "$host_dir"
      "$src_dir/runConfigureICU" Linux --prefix="$host_dir/install"
      make -j "$jobs"
    )
  fi

  rm -rf "$wasm_dir" "$install_dir"
  mkdir -p "$wasm_dir"
  (
    cd "$wasm_dir"
    CC="$WASI_SDK_PATH/bin/clang" \
    CXX="$WASI_SDK_PATH/bin/clang++" \
    AR="$WASI_SDK_PATH/bin/llvm-ar" \
    RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
    CFLAGS="-O2 -D_WASI_EMULATED_SIGNAL $PGLITE_WASI_SJLJ_FLAGS" \
    CXXFLAGS="-O2 -D_WASI_EMULATED_SIGNAL $PGLITE_WASI_SJLJ_FLAGS -include $thread_shim" \
    "$src_dir/configure" \
      --host=wasm32-wasi \
      --prefix="$install_dir" \
      --with-cross-build="$host_dir" \
      --with-data-packaging=files \
      --disable-shared \
      --enable-static \
      --disable-threads \
      --disable-tools \
      --disable-tests \
      --disable-samples \
      --disable-extras \
      --disable-icuio \
      --disable-layoutex
    make -j "$jobs"
    if [[ -d "$wasm_dir/data/out/build/icudt76l" ]]; then
      (
        cd "$wasm_dir/data/out/build/icudt76l"
        find . -type d -exec mkdir -p "$install_dir/share/icu/76.1/icudt76l/{}" \;
      )
    fi
    make install
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_ICU_PREFIX="$install_dir"
}

ensure_proj() {
  local version=9.7.0
  local archive="proj-${version}.tar.gz"
  local install_dir=${PGLITE_WASI_PROJ_PREFIX:-"$bin_dir/wasi-proj"}
  local src_dir="$tmp_dir/proj-${version}"
  local build_dir="$tmp_dir/proj-${version}-wasi-build"
  local sqlite_src_dir="$tmp_dir/sqlite-autoconf-3450300"
  local sqlite_host_dir="$tmp_dir/sqlite-host"
  local thread_shim="$tmp_dir/proj-wasi-threads.h"

  if [[ -f "$install_dir/lib/libproj.a" &&
        -f "$install_dir/include/proj.h" &&
        -f "$install_dir/share/proj/proj.db" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_PROJ_PREFIX="$install_dir"
    return
  fi

  if [[ ! -x "$sqlite_host_dir/bin/sqlite3" ]]; then
    mkdir -p "$sqlite_host_dir/bin"
    cc -O2 "$sqlite_src_dir/sqlite3.c" "$sqlite_src_dir/shell.c" -ldl -lpthread -lm -o "$sqlite_host_dir/bin/sqlite3"
  fi
  cat > "$thread_shim" <<'EOF'
#pragma once
namespace std {
class mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
class recursive_mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
}
EOF

  download "https://download.osgeo.org/proj/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$build_dir" "$install_dir"
  tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  mkdir -p "$build_dir"
  (
    cd "$build_dir"
    cmake "${cmake_generator_args[@]}" "$src_dir" \
      -DCMAKE_SYSTEM_NAME=WASI \
      -DCMAKE_C_COMPILER="$WASI_SDK_PATH/bin/clang" \
      -DCMAKE_CXX_COMPILER="$WASI_SDK_PATH/bin/clang++" \
      -DCMAKE_AR="$WASI_SDK_PATH/bin/llvm-ar" \
      -DCMAKE_RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
      -DCMAKE_INSTALL_PREFIX="$install_dir" \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_C_FLAGS="-O2 -D_WASI_EMULATED_GETPID $PGLITE_WASI_SJLJ_FLAGS" \
      -DCMAKE_CXX_FLAGS="-O2 -D_WASI_EMULATED_GETPID $PGLITE_WASI_SJLJ_FLAGS -include $thread_shim" \
      -DCMAKE_EXE_LINKER_FLAGS="-lwasi-emulated-getpid" \
      -DCMAKE_SHARED_LINKER_FLAGS="-lwasi-emulated-getpid" \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_APPS=OFF \
      -DBUILD_TESTING=OFF \
      -DENABLE_TIFF=OFF \
      -DENABLE_CURL=OFF \
      -DSQLite3_INCLUDE_DIR="$PGLITE_WASI_SQLITE_PREFIX/include" \
      -DSQLite3_LIBRARY="$PGLITE_WASI_SQLITE_PREFIX/lib/libsqlite3.a" \
      -DEXE_SQLITE3="$sqlite_host_dir/bin/sqlite3"
    cmake --build . --parallel "$jobs"
    cmake --install .
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_PROJ_PREFIX="$install_dir"
}

ensure_geos() {
  local version=3.13.1
  local archive="geos-${version}.tar.bz2"
  local install_dir=${PGLITE_WASI_GEOS_PREFIX:-"$bin_dir/wasi-geos"}
  local src_dir="$tmp_dir/geos-${version}"
  local build_dir="$tmp_dir/geos-${version}-wasi-build"
  local thread_shim="$tmp_dir/geos-wasi-threads.h"

  if [[ -f "$install_dir/lib/libgeos.a" &&
        -f "$install_dir/lib/libgeos_c.a" &&
        -x "$install_dir/bin/geos-config" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_GEOS_PREFIX="$install_dir"
    return
  fi

  download "https://download.osgeo.org/geos/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$build_dir" "$install_dir"
  tar -C "$tmp_dir" -xjf "$tmp_dir/$archive"
  cat > "$thread_shim" <<'EOF'
#pragma once
namespace std {
class mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
}
EOF
  mkdir -p "$build_dir"
  (
    cd "$build_dir"
    cmake "${cmake_generator_args[@]}" "$src_dir" \
      -DCMAKE_SYSTEM_NAME=WASI \
      -DCMAKE_C_COMPILER="$WASI_SDK_PATH/bin/clang" \
      -DCMAKE_CXX_COMPILER="$WASI_SDK_PATH/bin/clang++" \
      -DCMAKE_AR="$WASI_SDK_PATH/bin/llvm-ar" \
      -DCMAKE_RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
      -DCMAKE_INSTALL_PREFIX="$install_dir" \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_C_FLAGS="-O2 $PGLITE_WASI_SJLJ_FLAGS" \
      -DCMAKE_CXX_FLAGS="-O2 $PGLITE_WASI_SJLJ_FLAGS -include $thread_shim" \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_TESTING=OFF \
      -DBUILD_GEOSOP=OFF
    cmake --build . --parallel "$jobs"
    cmake --install .
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_GEOS_PREFIX="$install_dir"
}

ensure_gdal() {
  local version=3.11.0
  local archive="gdal-${version}.tar.gz"
  local install_dir=${PGLITE_WASI_GDAL_PREFIX:-"$bin_dir/wasi-gdal"}
  local src_dir="$tmp_dir/gdal-${version}"
  local build_dir="$tmp_dir/gdal-${version}-wasi-build"
  local thread_shim="$tmp_dir/gdal-wasi-threads.h"

  if [[ -f "$install_dir/lib/libgdal.a" &&
        -f "$install_dir/include/gdal.h" &&
        -x "$install_dir/bin/gdal-config" ]] &&
     has_wasm_flags_marker "$install_dir"; then
    export PGLITE_WASI_GDAL_PREFIX="$install_dir"
    return
  fi

  download "https://download.osgeo.org/gdal/${version}/${archive}" "$tmp_dir/$archive"
  rm -rf "$src_dir" "$build_dir" "$install_dir"
  tar -C "$tmp_dir" -xzf "$tmp_dir/$archive"
  perl -0pi -e 's/\n\s*cpl_spawn\.cpp//' "$src_dir/port/CMakeLists.txt"
  cat > "$src_dir/port/cpl_spawn_wasi_stub.cpp" <<'EOF'
#include "cpl_spawn.h"
extern "C" {
int CPLSpawn(const char *const *, VSILFILE *, VSILFILE *, int) { return -1; }
CPLSpawnedProcess *CPLSpawnAsync(int (*)(CPL_FILE_HANDLE, CPL_FILE_HANDLE), const char *const *, int, int, int, char **) { return nullptr; }
CPL_PID CPLSpawnAsyncGetChildProcessId(CPLSpawnedProcess *) { return -1; }
int CPLSpawnAsyncFinish(CPLSpawnedProcess *, int, int) { return -1; }
CPL_FILE_HANDLE CPLSpawnAsyncGetInputFileHandle(CPLSpawnedProcess *) { return CPL_FILE_INVALID_HANDLE; }
CPL_FILE_HANDLE CPLSpawnAsyncGetOutputFileHandle(CPLSpawnedProcess *) { return CPL_FILE_INVALID_HANDLE; }
CPL_FILE_HANDLE CPLSpawnAsyncGetErrorFileHandle(CPLSpawnedProcess *) { return CPL_FILE_INVALID_HANDLE; }
void CPLSpawnAsyncCloseInputFileHandle(CPLSpawnedProcess *) {}
void CPLSpawnAsyncCloseOutputFileHandle(CPLSpawnedProcess *) {}
void CPLSpawnAsyncCloseErrorFileHandle(CPLSpawnedProcess *) {}
int CPLPipeRead(CPL_FILE_HANDLE, void *, int) { return -1; }
int CPLPipeWrite(CPL_FILE_HANDLE, const void *, int) { return -1; }
}
EOF
  perl -0pi -e 's/(\n\s*cpl_recode_stub\.cpp)/$1\n    cpl_spawn_wasi_stub.cpp/' "$src_dir/port/CMakeLists.txt"
  cat > "$thread_shim" <<'EOF'
#pragma once
#include <utility>
namespace std {
class mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
class recursive_mutex { public: void lock() {} void unlock() {} bool try_lock() { return true; } };
class condition_variable {
public:
  template <class Lock> void wait(Lock&) {}
  template <class Lock, class Predicate> void wait(Lock&, Predicate pred) { while (!pred()) {} }
  void notify_all() {}
  void notify_one() {}
};
template <class Mutex> class unique_lock {
public:
  explicit unique_lock(Mutex& mutex) : mutex_(&mutex), owns_(true) { mutex_->lock(); }
  ~unique_lock() { if (owns_) mutex_->unlock(); }
  unique_lock(const unique_lock&) = delete;
  unique_lock& operator=(const unique_lock&) = delete;
  void lock() { if (!owns_) { mutex_->lock(); owns_ = true; } }
  void unlock() { if (owns_) { mutex_->unlock(); owns_ = false; } }
  bool owns_lock() const { return owns_; }
private:
  Mutex* mutex_;
  bool owns_;
};
class thread {
public:
  thread() = default;
  template <class Function, class... Args> explicit thread(Function&& f, Args&&... args) { f(std::forward<Args>(args)...); }
  bool joinable() const { return false; }
  void join() {}
};
template <class T> class future;
template <> class future<void> { public: void get() {} };
enum class launch { async = 1, deferred = 2 };
template <class Function, class... Args> future<void> async(launch, Function&& f, Args&&... args) {
  f(std::forward<Args>(args)...);
  return future<void>();
}
}
EOF

  mkdir -p "$build_dir"
  (
    cd "$build_dir"
    cmake "${cmake_generator_args[@]}" "$src_dir" \
      -DCMAKE_SYSTEM_NAME=WASI \
      -DCMAKE_C_COMPILER="$WASI_SDK_PATH/bin/clang" \
      -DCMAKE_CXX_COMPILER="$WASI_SDK_PATH/bin/clang++" \
      -DCMAKE_AR="$WASI_SDK_PATH/bin/llvm-ar" \
      -DCMAKE_RANLIB="$WASI_SDK_PATH/bin/llvm-ranlib" \
      -DCMAKE_INSTALL_PREFIX="$install_dir" \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_C_FLAGS="-O2 -D_WASI_EMULATED_GETPID $PGLITE_WASI_SJLJ_FLAGS" \
      -DCMAKE_CXX_FLAGS="-O2 -D_WASI_EMULATED_GETPID -D_WASI_EMULATED_SIGNAL $PGLITE_WASI_SJLJ_FLAGS -include $thread_shim" \
      -DCMAKE_EXE_LINKER_FLAGS="-lwasi-emulated-getpid -lwasi-emulated-signal -lsetjmp" \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_APPS=OFF \
      -DBUILD_TESTING=OFF \
      -DGDAL_BUILD_OPTIONAL_DRIVERS=OFF \
      -DOGR_BUILD_OPTIONAL_DRIVERS=OFF \
      -DGDAL_ENABLE_DRIVER_MEM=ON \
      -DGDAL_ENABLE_DRIVER_PNG=ON \
      -DGDAL_ENABLE_DRIVER_GTIFF=OFF \
      -DGDAL_ENABLE_DRIVER_VRT=OFF \
      -DOGR_ENABLE_DRIVER_GEOJSON=OFF \
      -DOGR_ENABLE_DRIVER_SHAPE=OFF \
      -DGDAL_USE_LIBPNG_INTERNAL=ON \
      -DGDAL_USE_ZLIB_INTERNAL=ON \
      -DGDAL_USE_CURL=OFF \
      -DGDAL_USE_GEOS=OFF \
      -DGDAL_USE_SQLITE3=OFF \
      -DGDAL_USE_EXPAT=OFF \
      -DGDAL_USE_LIBXML2=OFF \
      -DGDAL_USE_ICONV=OFF \
      -DPROJ_INCLUDE_DIR="$PGLITE_WASI_PROJ_PREFIX/include" \
      -DPROJ_LIBRARY_RELEASE="$PGLITE_WASI_PROJ_PREFIX/lib/libproj.a"
    cmake --build . --parallel "$jobs"
    cmake --install .
  )
  write_wasm_flags_marker "$install_dir"
  export PGLITE_WASI_GDAL_PREFIX="$install_dir"
}

need_tool curl
need_tool tar
need_tool make
need_tool perl
need_tool cmake
need_tool bison
need_tool flex
need_tool cc

ensure_wasi_sdk
ensure_libxml2
ensure_sqlite
ensure_json_c
ensure_icu
ensure_proj
ensure_geos
ensure_gdal

cat >&3 <<EOF
export WASI_SDK_PATH=$(printf '%q' "$WASI_SDK_PATH")
export PGLITE_WASI_LIBXML2_PREFIX=$(printf '%q' "$PGLITE_WASI_LIBXML2_PREFIX")
export PGLITE_WASI_SQLITE_PREFIX=$(printf '%q' "$PGLITE_WASI_SQLITE_PREFIX")
export PGLITE_WASI_JSON_C_PREFIX=$(printf '%q' "$PGLITE_WASI_JSON_C_PREFIX")
export PGLITE_WASI_ICU_PREFIX=$(printf '%q' "$PGLITE_WASI_ICU_PREFIX")
export PGLITE_WASI_PROJ_PREFIX=$(printf '%q' "$PGLITE_WASI_PROJ_PREFIX")
export PGLITE_WASI_GEOS_PREFIX=$(printf '%q' "$PGLITE_WASI_GEOS_PREFIX")
export PGLITE_WASI_GDAL_PREFIX=$(printf '%q' "$PGLITE_WASI_GDAL_PREFIX")
EOF
