param(
    [string]$Adb = 'C:/Users/rog/AppData/Local/Android/Sdk/platform-tools/adb.exe',
    [string]$Serial = '127.0.0.1:16896',
    [ValidateRange(1, 30)][int]$Seconds = 10
)
$ErrorActionPreference = 'Stop'
$package = 'com.beibei.calculator.debug'
$activity = "$package/com.codex.fx991smooth.MainActivity"
$appPid = (& $Adb -s $Serial shell pidof $package).Trim()
if ($appPid -notmatch '^\d+$') { throw '需要先打开独立测试包，且只允许单一进程。' }
$ticksPerSecond = [long](& $Adb -s $Serial shell getconf CLK_TCK)

function Read-CpuTicks {
    $raw = (& $Adb -s $Serial shell run-as $package cat "/proc/$appPid/stat") -join ''
    if ($LASTEXITCODE -ne 0) { throw '进程统计读取失败' }
    # proc stat: after the final ')' field 3 begins; utime/stime are fields 14/15.
    $fields = $raw.Substring($raw.LastIndexOf(')') + 2).Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
    [long]$fields[11] + [long]$fields[12]
}

function Measure-Window([string]$Label) {
    $before = Read-CpuTicks
    $timer = [Diagnostics.Stopwatch]::StartNew()
    Start-Sleep -Seconds $Seconds
    $after = Read-CpuTicks
    $timer.Stop()
    $delta = $after - $before
    [pscustomobject]@{
        state = $Label
        pid = $appPid
        elapsedSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
        ticksPerSecond = $ticksPerSecond
        ticksBefore = $before
        ticksAfter = $after
        cpuMilliseconds = 1000.0 * $delta / $ticksPerSecond
        oneCorePercent = [math]::Round(100.0 * $delta / $ticksPerSecond / $timer.Elapsed.TotalSeconds, 4)
    } | ConvertTo-Json -Compress
}

Measure-Window 'foreground-table-result-idle'
try {
    & $Adb -s $Serial shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 2
    Measure-Window 'background-idle'
} finally {
    & $Adb -s $Serial shell am start -n $activity | Out-Null
}
Write-Output '仅模拟器短窗 CPU 时间；不代表电流、手机温度、帧率或长期耗电。'
