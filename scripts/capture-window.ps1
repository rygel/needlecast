param(
    [int]$ProcessId = 43772,
    [string]$OutputPath = "$PSScriptRoot\..\needlecast-desktop\target\codex-screenshots\needlecast-current.png"
)

Add-Type -AssemblyName System.Drawing

$src = @"
using System;
using System.Runtime.InteropServices;
public class W {
    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")]
    public static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll", SetLastError=true)]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
Add-Type -TypeDefinition $src -ErrorAction SilentlyContinue

$target = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
if (-not $target) { Write-Error "No process with PID $ProcessId"; exit 1 }

$hwnd = [IntPtr]::Zero
$cb = [W+EnumWindowsProc]{
    param($h, $l)
    $pid_out = 0
    [void][W]::GetWindowThreadProcessId($h, [ref]$pid_out)
    if ($pid_out -eq $ProcessId) {
        $len = [W]::GetWindowTextLength($h)
        if ($len -gt 0) {
            $sb = New-Object System.Text.StringBuilder ($len + 1)
            [void][W]::GetWindowText($h, $sb, $sb.Capacity)
            $t = $sb.ToString()
            if ($t -like "Needlecast*") {
                $script:hwnd = $h
                return $false
            }
        }
    }
    return $true
}
[void][W]::EnumWindows($cb, [IntPtr]::Zero)

if ($hwnd -eq [IntPtr]::Zero) { Write-Error "No Needlecast window found"; exit 2 }

Write-Host "Window: $hwnd, Visible=$([W]::IsWindowVisible($hwnd)), Iconic=$([W]::IsIconic($hwnd))"

if ([W]::IsIconic($hwnd)) {
    [void][W]::ShowWindow($hwnd, 9)
    Start-Sleep -Milliseconds 500
}

$cw = New-Object W+RECT
[void][W]::GetClientRect($hwnd, [ref]$cw)
$w = $cw.Right - $cw.Left
$h = $cw.Bottom - $cw.Top
Write-Host "Client: ${w}x${h}"

if ($w -le 0 -or $h -le 0) { Write-Error "Empty client area"; exit 3 }

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()

$ok = [W]::PrintWindow($hwnd, $hdc, 2)
Write-Host "PrintWindow(PW_RENDERFULLCONTENT=2): $ok"

$g.ReleaseHdc($hdc)
$g.Dispose()

if (-not $ok) {
    $bmp.Dispose()
    Write-Host "Falling back to CopyFromScreen"
    $wr = New-Object W+RECT
    [void][W]::GetWindowRect($hwnd, [ref]$wr)
    $bmp2 = New-Object System.Drawing.Bitmap $w, $h
    $g2 = [System.Drawing.Graphics]::FromImage($bmp2)
    $g2.CopyFromScreen($wr.Left, $wr.Top, 0, 0, (New-Object System.Drawing.Size $w, $h))
    $g2.Dispose()
    $bmp2.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp2.Dispose()
} else {
    $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

Write-Host "Saved: $OutputPath"
