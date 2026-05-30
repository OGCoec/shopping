[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $JtlPath,

    [string] $OutputPath
)

$resolvedJtl = Resolve-Path -LiteralPath $JtlPath -ErrorAction Stop
if (-not $OutputPath) {
    $OutputPath = Join-Path (Split-Path -Parent $resolvedJtl) "summary.csv"
}

$samples = @(Import-Csv -LiteralPath $resolvedJtl)
if ($samples.Count -eq 0) {
    throw "No JMeter samples found in $resolvedJtl"
}
if (-not ($samples[0].PSObject.Properties.Name -contains "elapsed")) {
    throw "JTL must be CSV with field names enabled. Missing 'elapsed' column."
}

function Get-Percentile {
    param(
        [double[]] $Values,
        [double] $Percentile
    )
    if ($Values.Count -eq 0) {
        return 0
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return [Math]::Round($sorted[$index], 2)
}

$elapsedValues = @($samples | ForEach-Object { [double] $_.elapsed })
$successCount = @($samples | Where-Object { $_.success -eq "true" }).Count
$failureCount = $samples.Count - $successCount
$timestamps = @($samples | Where-Object { $_.timeStamp } | ForEach-Object { [double] $_.timeStamp })
$durationSeconds = 0
if ($timestamps.Count -gt 0) {
    $durationSeconds = (($timestamps | Measure-Object -Maximum).Maximum - ($timestamps | Measure-Object -Minimum).Minimum + ($elapsedValues | Measure-Object -Maximum).Maximum) / 1000.0
}
if ($durationSeconds -le 0) {
    $durationSeconds = 1
}

$rows = New-Object System.Collections.Generic.List[object]
function Add-SummaryRow {
    param([string] $Category, [string] $Name, [object] $Value)
    $rows.Add([pscustomobject]@{
        category = $Category
        name = $Name
        value = $Value
    })
}

Add-SummaryRow "overall" "total" $samples.Count
Add-SummaryRow "overall" "success" $successCount
Add-SummaryRow "overall" "failure" $failureCount
Add-SummaryRow "overall" "errorRatePercent" ([Math]::Round(($failureCount * 100.0 / $samples.Count), 2))
Add-SummaryRow "latencyMs" "avg" ([Math]::Round(($elapsedValues | Measure-Object -Average).Average, 2))
Add-SummaryRow "latencyMs" "p90" (Get-Percentile $elapsedValues 90)
Add-SummaryRow "latencyMs" "p95" (Get-Percentile $elapsedValues 95)
Add-SummaryRow "latencyMs" "p99" (Get-Percentile $elapsedValues 99)
Add-SummaryRow "latencyMs" "max" ([Math]::Round(($elapsedValues | Measure-Object -Maximum).Maximum, 2))
Add-SummaryRow "throughput" "qps" ([Math]::Round(($samples.Count / $durationSeconds), 2))

$samples |
    Group-Object responseCode |
    Sort-Object Name |
    ForEach-Object { Add-SummaryRow "httpStatus" $_.Name $_.Count }

$samples |
    ForEach-Object {
        $message = [string] $_.responseMessage
        if ($message -match "businessCode=([A-Za-z0-9_]+)") {
            $Matches[1]
        } else {
            "(none)"
        }
    } |
    Group-Object |
    Sort-Object Name |
    ForEach-Object { Add-SummaryRow "businessCode" $_.Name $_.Count }

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
$rows | Format-Table -AutoSize
Write-Host "Summary written to $OutputPath"
