#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ "${1:-}" = subtitle ]; then
    shift
    exec "${PYTHON:-python3}" "$ROOT/tools/subtitle_patch.py" rollback "$@"
fi
exec "${PYTHON:-python3}" "$ROOT/tools/mesa_patch.py" rollback "$@"
