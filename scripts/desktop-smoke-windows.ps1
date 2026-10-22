param(
    [Parameter(Mandatory = $true)][string]$Installer,
    [Parameter(Mandatory = $true)][string]$Evidence
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Evidence | Out-Null
$Evidence = (Resolve-Path $Evidence).Path
$Installer = (Resolve-Path $Installer).Path
$install = Start-Process msiexec.exe -ArgumentList @(
    '/i', ('"' + $Installer + '"'), '/qn', '/norestart',
    '/l*v', ('"' + (Join-Path $Evidence 'install.log') + '"')
) -Wait -PassThru
if ($install.ExitCode -notin @(0, 3010)) { throw "MSI installation failed: $($install.ExitCode)" }

$launcher = Join-Path $env:ProgramFiles 'Komelia\Komelia.exe'
if (-not (Test-Path $launcher)) { throw 'Installed Komelia launcher is missing.' }
$app = Start-Process $launcher -PassThru `
    -RedirectStandardOutput (Join-Path $Evidence 'stdout.log') `
    -RedirectStandardError (Join-Path $Evidence 'stderr.log')
try {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $app.Refresh()
        if ($app.HasExited) { throw "Desktop application exited: $($app.ExitCode)" }
        if ($app.MainWindowHandle -ne 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($app.MainWindowHandle -eq 0) { throw 'The installed desktop application did not open a window.' }
    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class KomeliaSmokeWindow {
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr window, out Rect bounds);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
}
'@
    [KomeliaSmokeWindow]::SetForegroundWindow($app.MainWindowHandle) | Out-Null
    Start-Sleep -Seconds 5
    $app.Refresh()
    if ($app.HasExited) { throw "Desktop application exited after opening: $($app.ExitCode)" }
    $bounds = New-Object KomeliaSmokeWindow+Rect
    if (-not [KomeliaSmokeWindow]::GetWindowRect($app.MainWindowHandle, [ref]$bounds)) { throw 'Could not read the application window bounds.' }
    $width = $bounds.Right - $bounds.Left
    $height = $bounds.Bottom - $bounds.Top
    if ($width -lt 300 -or $height -lt 200) { throw 'The application window is not visible at a usable size.' }
    $bitmap = New-Object System.Drawing.Bitmap $width, $height
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bitmap.Size)
        $bitmap.Save((Join-Path $Evidence 'desktop-startup.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
    @{ processAlive = $true; windowPresent = $true; width = $width; height = $height } |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $Evidence 'result.json')
} finally {
    if (-not $app.HasExited) { Stop-Process -Id $app.Id }
}
