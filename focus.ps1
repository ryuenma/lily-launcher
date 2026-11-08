$p = Get-Process emulator | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if ($p) {
    Add-Type -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);' -Name Win -Namespace User
    [User.Win]::SetForegroundWindow($p.MainWindowHandle)
    Write-Host "Focused emulator window, pid=$($p.Id)"
} else {
    Write-Host "No emulator window with handle found"
}