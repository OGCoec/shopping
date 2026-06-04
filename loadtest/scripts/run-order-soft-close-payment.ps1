[CmdletBinding()]
param(
    [int] $Threads = 500,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $TokenSkuCsv = "loadtest-output/order-create-single-hot-token-sku.csv",
    [string] $RunId,
    [string] $OutputRoot = "loadtest-output/runs",

    [int] $Stock = 50,
    [switch] $SeedHotSku,
    [string] $HotSkuCsv = "loadtest-output/order-hot-sku-ids.csv",

    [int] $PayNowCount = 4,
    [int] $ClosingCallbackCount = 4,
    [int] $ClosingUserPayNegativeCount = 4,
    [int] $ClosedCallbackNegativeCount = 4,

    [int] $PayNowMinDelaySeconds = 5,
    [int] $PayNowMaxDelaySeconds = 30,
    [int] $BoundaryRecordDelaySeconds = 299,
    [int] $ClosingCallbackDelaySeconds = 305,
    [int] $ClosedCallbackDelaySeconds = 610,

    [int] $CardSecretQuerySettleSeconds = 15,
    [int] $PersistSettleSeconds = 20,
    [switch] $Verify,
    [string] $PostgresUrl = "postgresql://postgres:123456@127.0.0.1:5432/shopping"
)

if (-not $RunId) {
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
}

$plan = "loadtest/jmeter/order-soft-close-payment-flow.jmx"
$verifySql = "loadtest/sql/verify-order-soft-close-payment.sql"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-soft-close-payment"

$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName
$jtlPath = Join-Path $runDir "order-soft-close-payment.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$runIdPath = Join-Path $runDir "run-id.txt"
$successCsvPath = Join-Path $runDir "order-soft-close-success-orders.csv"
$cardSecretResultCsvPath = Join-Path $runDir "order-card-secret-results.csv"
$configPath = Join-Path $runDir "run-config.json"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"
$generatedVerifySqlPath = Join-Path $runDir "verify-order-soft-close-payment.generated.sql"
Set-Content -LiteralPath $runIdPath -Value $RunId -Encoding UTF8

if ($SeedHotSku) {
    Write-Host "Seeding one hot SKU with stock $Stock"
    $seedArgs = @(
        "-q",
        "-pl", "shopping-web",
        "exec:java",
        "-Dexec.mainClass=com.example.ShoppingSystem.tools.loadtest.OrderLoadtestHotSkuSeedMain",
        "-Dexec.args=1 $Stock 990001 990001 $HotSkuCsv"
    )
    & mvn @seedArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Hot SKU seed failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath $TokenSkuCsv)) {
    throw "Token/SKU CSV does not exist: $TokenSkuCsv"
}
$TokenSkuCsv = (Resolve-Path -LiteralPath $TokenSkuCsv).Path

$config = [ordered]@{
    runId = $RunId
    plan = $plan
    tokenSkuCsv = $TokenSkuCsv
    stock = $Stock
    seedHotSku = [bool] $SeedHotSku
    threads = $Threads
    rampUp = $RampUp
    payNowCount = $PayNowCount
    closingCallbackCount = $ClosingCallbackCount
    closingUserPayNegativeCount = $ClosingUserPayNegativeCount
    closedCallbackNegativeCount = $ClosedCallbackNegativeCount
    payNowMinDelaySeconds = $PayNowMinDelaySeconds
    payNowMaxDelaySeconds = $PayNowMaxDelaySeconds
    boundaryRecordDelaySeconds = $BoundaryRecordDelaySeconds
    closingCallbackDelaySeconds = $ClosingCallbackDelaySeconds
    closedCallbackDelaySeconds = $ClosedCallbackDelaySeconds
    cardSecretQuerySettleSeconds = $CardSecretQuerySettleSeconds
    cardSecretResultCsv = $cardSecretResultCsvPath
    persistSettleSeconds = $PersistSettleSeconds
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
    "-JTOKEN_SKU_CSV=$TokenSkuCsv",
    "-JORDER_SUCCESS_CSV=$successCsvPath",
    "-JORDER_CARD_SECRET_RESULT_CSV=$cardSecretResultCsvPath",
    "-JPAY_NOW_COUNT=$PayNowCount",
    "-JCLOSING_CALLBACK_COUNT=$ClosingCallbackCount",
    "-JCLOSING_USER_PAY_NEGATIVE_COUNT=$ClosingUserPayNegativeCount",
    "-JCLOSED_CALLBACK_NEGATIVE_COUNT=$ClosedCallbackNegativeCount",
    "-JPAY_NOW_MIN_DELAY_SECONDS=$PayNowMinDelaySeconds",
    "-JPAY_NOW_MAX_DELAY_SECONDS=$PayNowMaxDelaySeconds",
    "-JBOUNDARY_RECORD_DELAY_SECONDS=$BoundaryRecordDelaySeconds",
    "-JCLOSING_CALLBACK_DELAY_SECONDS=$ClosingCallbackDelaySeconds",
    "-JCLOSED_CALLBACK_DELAY_SECONDS=$ClosedCallbackDelaySeconds",
    "-JCARD_SECRET_QUERY_SETTLE_SECONDS=$CardSecretQuerySettleSeconds",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "RunId: $RunId"
Write-Host "Token/SKU CSV: $TokenSkuCsv"
Write-Host "Output directory: $runDir"
Write-Host "This scenario waits for real order TTLs and usually runs for more than 10 minutes."
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath

$successCsvSqlPath = $successCsvPath.Replace("\", "/")
$cardSecretResultCsvSqlPath = $cardSecretResultCsvPath.Replace("\", "/")
$generatedVerifySqlSqlPath = $generatedVerifySqlPath.Replace("\", "/")
$verifyCommand = @(
    "psql `"$PostgresUrl`"",
    "-v ON_ERROR_STOP=1",
    "-v run_id=$RunId",
    "-v success_csv=`"$successCsvSqlPath`"",
    "-v card_secret_result_csv=`"$cardSecretResultCsvSqlPath`"",
    "-v expected_stock=$Stock",
    "-v pay_now_count=$PayNowCount",
    "-v closing_callback_count=$ClosingCallbackCount",
    "-v closing_user_pay_negative_count=$ClosingUserPayNegativeCount",
    "-v closed_callback_negative_count=$ClosedCallbackNegativeCount",
    "-f `"$generatedVerifySqlSqlPath`""
) -join " "
Set-Content -LiteralPath $verifyCommandPath -Value $verifyCommand -Encoding UTF8

if ($Verify) {
    if (-not (Test-Path -LiteralPath $successCsvPath)) {
        throw "Success order CSV was not created, cannot run verification SQL: $successCsvPath"
    }
    if (-not (Test-Path -LiteralPath $cardSecretResultCsvPath)) {
        throw "Card secret result CSV was not created, cannot run verification SQL: $cardSecretResultCsvPath"
    }
    if ($PersistSettleSeconds -gt 0) {
        Write-Host "Waiting $PersistSettleSeconds seconds for Redis order persistence, callback dispatch, and refund dispatch before DB verification."
        Start-Sleep -Seconds $PersistSettleSeconds
    }
    $successCsvCopyPath = $successCsvSqlPath.Replace("'", "''")
    $cardSecretResultCsvCopyPath = $cardSecretResultCsvSqlPath.Replace("'", "''")
    $verifySqlText = Get-Content -LiteralPath $verifySql -Raw
    $verifySqlText = $verifySqlText.Replace("\copy soft_close_success_orders FROM :'success_csv' CSV HEADER", "\copy soft_close_success_orders FROM '$successCsvCopyPath' CSV HEADER")
    $verifySqlText = $verifySqlText.Replace("\copy order_card_secret_api_results FROM :'card_secret_result_csv' CSV HEADER", "\copy order_card_secret_api_results FROM '$cardSecretResultCsvCopyPath' CSV HEADER")
    Set-Content -LiteralPath $generatedVerifySqlPath -Value $verifySqlText -Encoding UTF8
    $psqlArgs = @(
        $PostgresUrl,
        "-v", "ON_ERROR_STOP=1",
        "-v", "run_id=$RunId",
        "-v", "success_csv=$successCsvSqlPath",
        "-v", "card_secret_result_csv=$cardSecretResultCsvSqlPath",
        "-v", "expected_stock=$Stock",
        "-v", "pay_now_count=$PayNowCount",
        "-v", "closing_callback_count=$ClosingCallbackCount",
        "-v", "closing_user_pay_negative_count=$ClosingUserPayNegativeCount",
        "-v", "closed_callback_negative_count=$ClosedCallbackNegativeCount",
        "-f", $generatedVerifySqlPath
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
Write-Host "Success order CSV: $successCsvPath"
Write-Host "Card secret result CSV: $cardSecretResultCsvPath"
Write-Host "Run config: $configPath"
Write-Host "Verify command: $verifyCommandPath"
