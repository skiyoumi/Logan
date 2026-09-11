#!/usr/bin/env bash
# WSL entry point. Parameters are forwarded to publish.ps1 unchanged.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if command -v pwsh >/dev/null 2>&1; then
  exec pwsh -NoProfile -File "$script_dir/publish.ps1" "$@"
elif command -v powershell.exe >/dev/null 2>&1 && command -v wslpath >/dev/null 2>&1; then
  exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(wslpath -w "$script_dir/publish.ps1")" "$@"
else
  echo 'PowerShell is required. On WSL, enable Windows interop or run publish.ps1 in Windows PowerShell.' >&2
  exit 1
fi
