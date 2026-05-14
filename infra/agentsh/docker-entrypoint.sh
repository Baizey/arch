#!/usr/bin/env bash
set -euo pipefail

mkdir -p /arch/backing /arch/mount /arch/policy /arch/workspace /var/log/arch-sandbox /var/lib/arch-sandbox

exec "$@"
