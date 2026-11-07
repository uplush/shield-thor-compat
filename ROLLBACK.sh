#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "${PYTHON:-python3}" "$ROOT/tools/mesa_patch.py" rollback "$@"
