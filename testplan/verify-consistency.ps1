# verify-consistency.ps1
# Runs SQL assertions against all five databases to verify eventual consistency.
# Usage: .\verify-consistency.ps1 -OrderNo ORD-xxx -UserId 12345
# Requires: psql on PATH, five PostgreSQL instances running (see README.md for ports)
param(
    [string]$OrderNo   = "",
    [long]  $UserId    = 0,
    [string]$PgUser    = "postgres",
    [string]$PgPass    = "123456",
    [string]$CoreHost  = "127.0.0.1",
    [int]   $CorePort  = 5433,
    [string]$TradeHost = "127.0.0.1",
    [int]   $TradePort = 5434,
    [string]$ProductHost = "127.0.0.1",
    [int]   $ProductPort = 5435,
    [string]$RiskHost  = "127.0.0.1",
    [int]   $RiskPort  = 5437
)

$env:PGPASSWORD = $PgPass
$pass = 0
$fail = 0

function Run-Assert {
    param([string]$Label, [string]$Host, [int]$Port, [string]$Db, [string]$Sql, [string]$Expected)
    $result = psql -h $Host -p $Port -U $PgUser -d $Db -t -A -c $Sql 2>&1
    $result = ($result | Out-String).Trim()
    if ($result -eq $Expected) {
        Write-Host "[PASS] $Label (got: $result)"
        $script:pass++
    } else {
        Write-Host "[FAIL] $Label  expected=[$Expected]  got=[$result]"
        $script:fail++
    }
}

Write-Host "=== Outbox/Inbox Eventual Consistency Assertions ==="
Write-Host ""

if ($OrderNo) {
    Write-Host "--- Order Saga: $OrderNo ---"

    # TRADE: order must exist (source not rolled back)
    Run-Assert -Label "TRADE: order exists" `
        -Host $TradeHost -Port $TradePort -Db "shopping_trade" `
        -Sql "SELECT count(*) FROM trade_order WHERE order_no='$OrderNo'" `
        -Expected "1"

    # TRADE: order must NOT be stuck in STOCK_CONFIRMING
    Run-Assert -Label "TRADE: order not stuck in STOCK_CONFIRMING" `
        -Host $TradeHost -Port $TradePort -Db "shopping_trade" `
        -Sql "SELECT count(*) FROM trade_order WHERE order_no='$OrderNo' AND status='STOCK_CONFIRMING'" `
        -Expected "0"

    # TRADE: outbox event published
    $stockEventId = "order-stock-deduct-requested:$OrderNo"
    Run-Assert -Label "TRADE: outbox event PUBLISHED" `
        -Host $TradeHost -Port $TradePort -Db "shopping_trade" `
        -Sql "SELECT count(*) FROM outbox_event WHERE event_id='$stockEventId' AND status='PUBLISHED'" `
        -Expected "1"

    # PRODUCT: inbox event processed exactly once
    Run-Assert -Label "PRODUCT: inbox idempotent (count=1)" `
        -Host $ProductHost -Port $ProductPort -Db "shopping_product" `
        -Sql "SELECT count(*) FROM inbox_event WHERE event_id='$stockEventId' AND consumer_name='order-stock-deduct-product' AND status='PROCESSED'" `
        -Expected "1"

    # Cancel path: check inventory release outbox
    $releaseEventId = "order-inventory-release:$OrderNo`:CANCEL"
    $releaseCount = psql -h $TradeHost -p $TradePort -U $PgUser -d "shopping_trade" -t -A `
        -c "SELECT count(*) FROM outbox_event WHERE event_id='$releaseEventId'" 2>&1
    if (($releaseCount | Out-String).Trim() -eq "1") {
        Write-Host "[INFO] Cancel release outbox event found for $OrderNo"
        Run-Assert -Label "PRODUCT: inventory release inbox idempotent" `
            -Host $ProductHost -Port $ProductPort -Db "shopping_product" `
            -Sql "SELECT count(*) FROM inbox_event WHERE event_id='$releaseEventId' AND consumer_name='order-inventory-release-product'" `
            -Expected "1"
    }

    Write-Host ""
}

if ($UserId -gt 0) {
    Write-Host "--- Auth Risk Lock: userId=$UserId ---"

    # RISK: user_risk_profile lock_count > 0
    Run-Assert -Label "RISK: lock_count > 0" `
        -Host $RiskHost -Port $RiskPort -Db "shopping_risk" `
        -Sql "SELECT count(*) FROM user_risk_profile WHERE user_id=$UserId AND lock_count > 0" `
        -Expected "1"

    # RISK: outbox event for account status sync published
    Run-Assert -Label "RISK: account-status-sync outbox published" `
        -Host $RiskHost -Port $RiskPort -Db "shopping_risk" `
        -Sql "SELECT count(*) FROM outbox_event WHERE event_id LIKE 'acct-status-$UserId-%' AND status='PUBLISHED'" `
        -Expected "1"

    # CORE: user_login_identity status = LOCKED
    Run-Assert -Label "CORE: user status=LOCKED" `
        -Host $CoreHost -Port $CorePort -Db "shopping_core" `
        -Sql "SELECT count(*) FROM user_login_identity WHERE user_id=$UserId AND status='LOCKED'" `
        -Expected "1"

    # CORE: inbox_event processed exactly once
    Run-Assert -Label "CORE: account-status-sync inbox idempotent (count=1)" `
        -Host $CoreHost -Port $CorePort -Db "shopping_core" `
        -Sql "SELECT count(*) FROM inbox_event WHERE consumer_name='account-status-sync-core' AND event_id LIKE 'acct-status-$UserId-%' AND status='PROCESSED'" `
        -Expected "1"

    Write-Host ""
}

Write-Host "=== Results: $pass passed, $fail failed ==="
if ($fail -gt 0) { exit 1 } else { exit 0 }