[CmdletBinding()]
param(
    [Alias("ContinuousUserId")]
    [int] $RepeatUserId = 2,
    [Alias("ResetUserId")]
    [int] $OtherUserId = 3,
    [int] $ConcurrentUserId = 1,

    [Alias("ContinuousLoops")]
    [int] $RepeatLoops = 2,
    [ValidateSet(50)]
    [int] $ConcurrentThreads = 50,
    [int] $ConcurrentRampUp = 1,

    [string] $HostName = "127.0.0.1",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $TokenCsv = "loadtest-output/xss-users-token.csv",
    [string] $OutputRoot = "loadtest-output/runs",
    [string] $JMeterPath,
    [switch] $Clean,
    [switch] $Verify,
    [string] $PostgresUrl = "postgresql://postgres@127.0.0.1:5434/shopping_trade",
    [string] $PsqlPath
)

$ErrorActionPreference = "Stop"

function Resolve-JMeterPath {
    param([string] $RequestedPath)
    if ($RequestedPath) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -LiteralPath $RequestedPath).Path
        }
        return $RequestedPath
    }
    $command = Get-Command jmeter -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $bundled = "E:\apache-jmeter-5.6.3\bin\jmeter.bat"
    if (Test-Path -LiteralPath $bundled) {
        return $bundled
    }
    throw "JMeter was not found. Pass -JMeterPath or add jmeter to PATH."
}

function Resolve-PsqlPath {
    param([string] $RequestedPath)
    if ($RequestedPath) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -LiteralPath $RequestedPath).Path
        }
        return $RequestedPath
    }
    $command = Get-Command psql -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "psql was not found. Install PostgreSQL client tools or pass -PsqlPath."
}

function Write-TokenCsvForUser {
    param(
        [Parameter(Mandatory = $true)]
        [int] $UserId,

        [Parameter(Mandatory = $true)]
        [string] $SourceCsv,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    $rows = @(Import-Csv -LiteralPath $SourceCsv | Where-Object { [string] $_.userId -eq [string] $UserId })
    if ($rows.Count -lt 1) {
        throw "No access token row found for userId=$UserId in $SourceCsv"
    }
    @($rows[0]) | Select-Object userId, accessToken | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
}

function Invoke-PsqlCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    $psql = Resolve-PsqlPath -RequestedPath $PsqlPath
    & $psql $PostgresUrl "-v" "ON_ERROR_STOP=1" "-c" $Sql | Tee-Object -FilePath $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed with exit code $LASTEXITCODE. Check $OutputPath"
    }
}

$plan = "loadtest/jmeter/sign-in-points.jmx"
$verifySql = "loadtest/sql/verify-sign-in-points.sql"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-sign-in-points"
$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName

if (-not (Test-Path -LiteralPath $TokenCsv)) {
    throw "TokenCsv was not found: $TokenCsv"
}
$TokenCsv = (Resolve-Path -LiteralPath $TokenCsv).Path

$repeatTokenCsv = Join-Path $runDir "sign-in-user-$RepeatUserId-token.csv"
$otherTokenCsv = Join-Path $runDir "sign-in-user-$OtherUserId-token.csv"
$concurrentTokenCsv = Join-Path $runDir "sign-in-user-$ConcurrentUserId-token.csv"
Write-TokenCsvForUser -UserId $RepeatUserId -SourceCsv $TokenCsv -OutputPath $repeatTokenCsv
Write-TokenCsvForUser -UserId $OtherUserId -SourceCsv $TokenCsv -OutputPath $otherTokenCsv
Write-TokenCsvForUser -UserId $ConcurrentUserId -SourceCsv $TokenCsv -OutputPath $concurrentTokenCsv

$jtlPath = Join-Path $runDir "sign-in-points.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$configPath = Join-Path $runDir "run-config.json"
$cleanOutputPath = Join-Path $runDir "clean-output.txt"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"

$config = [ordered]@{
    plan = $plan
    tokenCsv = $TokenCsv
    repeatUserId = $RepeatUserId
    otherUserId = $OtherUserId
    concurrentUserId = $ConcurrentUserId
    repeatLoops = $RepeatLoops
    concurrentThreads = $ConcurrentThreads
    concurrentRampUp = $ConcurrentRampUp
    hostName = $HostName
    port = $Port
    protocol = $Protocol
}
$config | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $configPath -Encoding UTF8

if ($Clean) {
    $userIds = @($RepeatUserId, $OtherUserId, $ConcurrentUserId) | Sort-Object -Unique
    $userIdList = ($userIds -join ",")
    $cleanSql = "DELETE FROM user_sign_record WHERE user_id IN ($userIdList); DELETE FROM user_point_account WHERE user_id IN ($userIdList);"
    Write-Host "Cleaning sign-in test data for user ids: $userIdList"
    Invoke-PsqlCommand -Sql $cleanSql -OutputPath $cleanOutputPath
}

$jmeter = Resolve-JMeterPath -RequestedPath $JMeterPath
$arguments = @(
    "-n",
    "-t", $plan,
    "-l", $jtlPath,
    "-j", $logPath,
    "-e",
    "-o", $reportDir,
    "-JHOST=$HostName",
    "-JPORT=$Port",
    "-JPROTOCOL=$Protocol",
    "-JTOKEN_CSV_REPEAT=$repeatTokenCsv",
    "-JTOKEN_CSV_OTHER=$otherTokenCsv",
    "-JTOKEN_CSV_CONCURRENT=$concurrentTokenCsv",
    "-JREPEAT_LOOPS=$RepeatLoops",
    "-JCONCURRENT_THREADS=$ConcurrentThreads",
    "-JCONCURRENT_RAMP_UP=$ConcurrentRampUp",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "Output directory: $runDir"
& $jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath

$samples = @(Import-Csv -LiteralPath $jtlPath)
$failureCount = @($samples | Where-Object { $_.success -ne "true" }).Count
if ($failureCount -gt 0) {
    throw "Sign-in JMeter run has $failureCount failed samples. Check $jtlPath and $logPath"
}

$verifyCommand = @(
    "psql `"$PostgresUrl`"",
    "-v repeat_user_id=$RepeatUserId",
    "-v other_user_id=$OtherUserId",
    "-v concurrent_user_id=$ConcurrentUserId",
    "-f $verifySql"
) -join " "
Set-Content -LiteralPath $verifyCommandPath -Value $verifyCommand -Encoding UTF8

if ($Verify) {
    $psql = Resolve-PsqlPath -RequestedPath $PsqlPath
    $psqlArgs = @(
        $PostgresUrl,
        "-v", "ON_ERROR_STOP=1",
        "-v", "repeat_user_id=$RepeatUserId",
        "-v", "other_user_id=$OtherUserId",
        "-v", "concurrent_user_id=$ConcurrentUserId",
        "-f", $verifySql
    )
    & $psql @psqlArgs | Tee-Object -FilePath $verifyOutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Verification SQL failed with exit code $LASTEXITCODE. Check $verifyOutputPath"
    }
}

Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
Write-Host "Run config: $configPath"
Write-Host "Verify command: $verifyCommandPath"
