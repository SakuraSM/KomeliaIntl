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
$appLibrary = Join-Path $env:ProgramFiles 'Komelia\app'
& java "-Dcompose.application.resources.dir=$appLibrary\resources" -cp "$appLibrary\*" `
    (Join-Path $PSScriptRoot 'DesktopNativeSmoke.java') bundled *> (Join-Path $Evidence 'native-bundled.log')
if ($LASTEXITCODE -ne 0) { throw 'The installed native image decoder failed its smoke test.' }

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class KomeliaSmokeWindow {
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }
    public sealed class Info {
        public IntPtr Handle;
        public int ProcessId;
        public string Title;
        public bool Visible;
        public int Width, Height;
    }
    private delegate bool EnumCallback(IntPtr window, IntPtr state);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumCallback callback, IntPtr state);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr window, StringBuilder text, int length);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr window, out Rect bounds);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int width, int height, uint flags);
    public static Info[] Find(int[] processIds) {
        var allowed = new HashSet<int>(processIds);
        var result = new List<Info>();
        EnumWindows((window, state) => {
            GetWindowThreadProcessId(window, out uint processId);
            if (!allowed.Contains((int)processId)) return true;
            var title = new StringBuilder(512);
            GetWindowText(window, title, title.Capacity);
            GetWindowRect(window, out Rect bounds);
            result.Add(new Info { Handle = window, ProcessId = (int)processId,
                Title = title.ToString(), Visible = IsWindowVisible(window),
                Width = bounds.Right - bounds.Left, Height = bounds.Bottom - bounds.Top });
            return true;
        }, IntPtr.Zero);
        return result.ToArray();
    }
}
'@

function Save-Screenshot([System.Drawing.Rectangle]$Bounds, [string]$Name) {
    $bitmap = New-Object System.Drawing.Bitmap $Bounds.Width, $Bounds.Height
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($Bounds.Left, $Bounds.Top, 0, 0, $bitmap.Size)
        $bitmap.Save((Join-Path $Evidence $Name), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

# dev.dirs 26 passes the Windows PowerShell '-Version 2' option. In pwsh that
# option prints a version instead of running its directory lookup command.
$systemPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0'
if (-not (Test-Path (Join-Path $systemPowerShell 'powershell.exe'))) {
    throw 'The standard Windows PowerShell executable is required for this runtime fixture.'
}
$inheritedPath = $env:PATH
try {
    $env:PATH = "$systemPowerShell;$inheritedPath"
    $app = Start-Process $launcher -PassThru `
        -RedirectStandardOutput (Join-Path $Evidence 'stdout.log') `
        -RedirectStandardError (Join-Path $Evidence 'stderr.log')
} finally {
    $env:PATH = $inheritedPath
}
$ownedIds = [System.Collections.Generic.HashSet[int]]::new()
$ownedIds.Add($app.Id) | Out-Null
$initialized = $false
$window = $null
try {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $processes = @(Get-CimInstance Win32_Process)
        do {
            $added = $false
            foreach ($process in $processes) {
                if ($ownedIds.Contains([int]$process.ParentProcessId)) {
                    if ($ownedIds.Add([int]$process.ProcessId)) { $added = $true }
                }
            }
        } while ($added)
        $ownedProcesses = @($processes | Where-Object { $ownedIds.Contains([int]$_.ProcessId) })
        $ownedProcesses | Select-Object ProcessId, ParentProcessId, Name, ExecutablePath |
            ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 (Join-Path $Evidence 'processes.json')
        $windows = [KomeliaSmokeWindow]::Find([int[]]$ownedIds)
        @($windows | ForEach-Object {
            @{ handle = $_.Handle.ToInt64(); processId = $_.ProcessId; title = $_.Title;
               visible = $_.Visible; width = $_.Width; height = $_.Height }
        }) | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 (Join-Path $Evidence 'windows.json')
        $window = $windows | Where-Object { $_.Visible -and $_.Title -eq 'Komelia' -and $_.Width -ge 300 -and $_.Height -ge 200 } |
            Sort-Object -Property @{Expression = { $_.Width * $_.Height }; Descending = $true} | Select-Object -First 1
        $initialized = (Test-Path (Join-Path $Evidence 'stdout.log')) -and
            (Select-String -Path (Join-Path $Evidence 'stdout.log') -SimpleMatch 'Application dependencies initialized' -Quiet)
        if ($window -and $initialized) { break }
        if ($ownedProcesses.Count -eq 0) { throw 'The installed application process tree exited before opening a window.' }
        Start-Sleep -Seconds 1
    }
    if (-not $window) { throw 'No usable Komelia window belongs to the installed application process tree.' }
    if (-not $initialized) { throw 'The desktop window opened without completing application initialization.' }
    $database = [regex]::Match((Get-Content (Join-Path $Evidence 'stdout.log') -Raw),
        'Database: jdbc:sqlite:([^\r\n]*[/\\]komelia\.sqlite)')
    if (-not $database.Success -or -not [System.IO.Path]::IsPathFullyQualified($database.Groups[1].Value)) {
        throw 'The application did not resolve an absolute database path.'
    }
    $screen = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    [KomeliaSmokeWindow]::SetWindowPos($window.Handle, [IntPtr]::Zero, $screen.Left + 10, $screen.Top + 10,
        [Math]::Min(1280, $screen.Width - 20), [Math]::Min(800, $screen.Height - 20), 0x0040) | Out-Null
    [KomeliaSmokeWindow]::SetForegroundWindow($window.Handle) | Out-Null
    Start-Sleep -Seconds 5
    if (-not (Get-Process -Id $window.ProcessId -ErrorAction SilentlyContinue)) { throw 'The window process exited after initialization.' }
    $bounds = New-Object KomeliaSmokeWindow+Rect
    if (-not [KomeliaSmokeWindow]::GetWindowRect($window.Handle, [ref]$bounds)) { throw 'Could not read the application window bounds.' }
    $width = $bounds.Right - $bounds.Left
    $height = $bounds.Bottom - $bounds.Top
    if ($width -lt 300 -or $height -lt 200) { throw 'The application window is not visible at a usable size.' }
    Save-Screenshot ([System.Drawing.Rectangle]::new($bounds.Left, $bounds.Top, $width, $height)) 'desktop-startup.png'
    @{ processAlive = $true; windowPresent = $true; applicationInitialized = $true;
       dataDirectoryAbsolute = $true; rootProcessId = $app.Id; windowProcessId = $window.ProcessId;
       width = $width; height = $height } |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $Evidence 'result.json')
} catch {
    $failure = $_
    try {
        Save-Screenshot ([System.Windows.Forms.SystemInformation]::VirtualScreen) 'desktop-failure.png'
    } catch {
        Write-Warning "Could not capture the runner desktop: $_"
    }
    throw $failure
} finally {
    foreach ($processId in $ownedIds) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
}
