param([int]$Port = 8765)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw 'Run py -3 -m venv .venv and .\.venv\Scripts\python.exe -m pip install -r requirements.txt first.'
}
& $python -m uvicorn discovery.app:app --host 127.0.0.1 --port $Port --no-access-log
