# start_host_server.ps1
# Starts the Photo Editor Demo screenshot receiver.
# Usage: .\start_host_server.ps1   or   start_host_server.cmd
#
# NOTE: If you launch this .ps1 directly from PowerShell, Ctrl+C may stop
# PowerShell but leave the python child running. Use start_host_server.cmd
# instead, or run stop_host_server.cmd to force-stop the server.

$ServerScript = Join-Path $PSScriptRoot "screenshot_server.py"
$Port = 8888

if (-not (Test-Path $ServerScript)) {
    Write-Host "[!] screenshot_server.py not found next to this script: $ServerScript" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Locate a Python interpreter (py launcher first, then python)
$python = $null
try { $python = (Get-Command py -ErrorAction Stop).Source } catch { }
if (-not $python) {
    try { $python = (Get-Command python -ErrorAction Stop).Source } catch { }
}
if (-not $python) {
    Write-Host "[!] Python not found. Install from https://www.python.org/downloads/" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Photo Editor Demo - Screenshot Receiver" -ForegroundColor Cyan
Write-Host "  Listening on port $Port   |   Press Ctrl+C to stop" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

& $python $ServerScript $Port
