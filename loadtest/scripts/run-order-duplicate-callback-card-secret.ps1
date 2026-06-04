[CmdletBinding()]
param(
    [int] $Threads = 10,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 8080,
    [string] $Protocol = "http",

    [int] $Orders = 2,
    [int] $QuantityPerOrder = 2,
    [int] $DuplicateCallbacksPerOrder = 5,
    [string] $InputCsv = "loadtest-output/order-duplicate-callback-card-secret-input.csv",
    [switch] $UseExistingInputCsv,
    [switch] $SeedIfShortage,

    [string] $RunId,
    [string] $OutputRoot = "loadtest-output/runs",
    [int] $DispatchSettleSeconds = 15,
    [switch] $Verify,
    [string] $PostgresUrl = "postgresql://postgres:123456@127.0.0.1:5432/shopping"
)

if (-not $RunId) {
    $RunId = "duplicate-callback-" + (Get-Date -Format "yyyyMMddHHmmss")
}

$plan = "loadtest/jmeter/order-duplicate-callback-card-secret-flow.jmx"
$verifySql = "loadtest/sql/verify-order-duplicate-callback-card-secret.sql"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-duplicate-callback-card-secret"

$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName
$jtlPath = Join-Path $runDir "order-duplicate-callback-card-secret.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$runIdPath = Join-Path $runDir "run-id.txt"
$callbackResultCsvPath = Join-Path $runDir "order-duplicate-callback-card-secret-results.csv"
$configPath = Join-Path $runDir "run-config.json"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"
Set-Content -LiteralPath $runIdPath -Value $RunId -Encoding UTF8

if ($UseExistingInputCsv) {
    if (-not (Test-Path -LiteralPath $InputCsv)) {
        throw "Input CSV does not exist: $InputCsv"
    }
} else {
    $seedIfShortageText = if ($SeedIfShortage) { "true" } else { "false" }
    $seedArgsText = "$Orders $QuantityPerOrder $DuplicateCallbacksPerOrder $InputCsv $RunId $seedIfShortageText"
    $seedArgs = @(
        "-q",
        "-pl", "shopping-web",
        "exec:java",
        "-Dexec.mainClass=com.example.ShoppingSystem.tools.loadtest.OrderDuplicateCallbackCardSecretSeedMain",
        "-Dexec.args=$seedArgsText"
    )
    Write-Host "Generating duplicate callback input CSV: $InputCsv"
    & mvn @seedArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Duplicate callback seed failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath $InputCsv)) {
    throw "Input CSV was not created: $InputCsv"
}
$InputCsv = (Resolve-Path -LiteralPath $InputCsv).Path

$config = [ordered]@{
    runId = $RunId
    plan = $plan
    inputCsv = $InputCsv
    resultCsv = $callbackResultCsvPath
    orders = $Orders
    quantityPerOrder = $QuantityPerOrder
    duplicateCallbacksPerOrder = $DuplicateCallbacksPerOrder
    seedIfShortage = [bool] $SeedIfShortage
    useExistingInputCsv = [bool] $UseExistingInputCsv
    threads = $Threads
    rampUp = $RampUp
    hostName = $HostName
    port = $Port
    protocol = $Protocol
    dispatchSettleSeconds = $DispatchSettleSeconds
}
$config | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $configPath -Encoding UTF8

$arguments = @(
    "-n",
    "-t", $plan,
    "-l", $jtlPath,
    "-j", $logPath,
    "-e",
    "-o", $reportDir,
    "-JRUN_ID=$RunId",
    "-JTHREADS=$Threads",
    "-JRAMP_UP=$RampUp",
    "-JHOST=$HostName",
    "-JPORT=$Port",
    "-JPROTOCOL=$Protocol",
    "-JINPUT_CSV=$InputCsv",
    "-JRESULT_CSV=$callbackResultCsvPath",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "RunId: $RunId"
Write-Host "Input CSV: $InputCsv"
Write-Host "Output directory: $runDir"
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath
$summaryRows = @(Import-Csv -LiteralPath $summaryPath)
$failureRow = $summaryRows | Where-Object { $_.category -eq "overall" -and $_.name -eq "failure" } | Select-Object -First 1
if ($failureRow -and [int] $failureRow.value -gt 0) {
    throw "JMeter completed but reported $($failureRow.value) failed samples. Check $summaryPath and $callbackResultCsvPath"
}

$inputCsvSqlPath = $InputCsv.Replace("\", "/")
$callbackResultSqlPath = $callbackResultCsvPath.Replace("\", "/")
$verifyCommand = @(
    "psql `"$PostgresUrl`"",
    "-v ON_ERROR_STOP=1",
    "-v run_id=$RunId",
    "-v input_csv=`"$inputCsvSqlPath`"",
    "-v result_csv=`"$callbackResultSqlPath`"",
    "-f $verifySql"
) -join " "
Set-Content -LiteralPath $verifyCommandPath -Value $verifyCommand -Encoding UTF8

if ($Verify) {
    if (-not (Test-Path -LiteralPath $callbackResultCsvPath)) {
        throw "Callback result CSV was not created, cannot run verification SQL: $callbackResultCsvPath"
    }
    if ($DispatchSettleSeconds -gt 0) {
        Write-Host "Waiting $DispatchSettleSeconds seconds for duplicate callback dispatch and card secret delivery."
        Start-Sleep -Seconds $DispatchSettleSeconds
    }
    $psqlArgs = @(
        $PostgresUrl,
        "-v", "ON_ERROR_STOP=1",
        "-v", "run_id=$RunId",
        "-v", "input_csv=$inputCsvSqlPath",
        "-v", "result_csv=$callbackResultSqlPath",
        "-f", $verifySql
    )
    & psql @psqlArgs | Tee-Object -FilePath $verifyOutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Verification SQL failed with exit code $LASTEXITCODE. Check $verifyOutputPath"
    }
}

Write-Host "RunId: $RunId"
Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
Write-Host "Input CSV: $InputCsv"
Write-Host "Callback result CSV: $callbackResultCsvPath"
Write-Host "Run config: $configPath"
Write-Host "Verify command: $verifyCommandPath"
