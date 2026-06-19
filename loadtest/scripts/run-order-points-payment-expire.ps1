[CmdletBinding()]
param(
    [string] $HostName = "127.0.0.1",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $TokenCsv = "C:/Users/damn/Desktop/shopping/loadtest-output/xss-users-token.csv",
    [string] $SkuAHex = "019e771fc647010109a846daf0120d71",
    [string] $SkuBHex = "019e771d195901018a1bc1e4ee60017a",
    [string] $OutputRoot = "loadtest-output/runs",
    [int] $StockA = 100,
    [int] $StockB = 350,
    [int] $PointPrice = 20,
    [switch] $Verify,
    [switch] $NegativeNoCancel,
    [string] $PostgresUrl = "postgresql://postgres:123456@127.0.0.1:5432/shopping",
    [string] $RedisHost = "127.0.0.1",
    [int] $RedisPort = 6380,
    [string] $RedisPassword = "123456",
    [int] $RedisDatabase = 1,
    [string] $JMeterPath,
    [string] $PsqlPath,
    [string] $RedisCliPath,
    [string] $RedisContainerName = "redis-bloom",
    [string] $DockerPath,
    [int] $HttpTimeoutMs = 120000,
    [int] $LongHttpTimeoutMs = 430000,
    [int] $ClientTimeoutMs = 2000,
    [int] $ConcurrentThreads = 50,
    [int] $TimedEventThreads = 120,
    [int] $ConcurrentGroupPauseMs = 10000,
    [int] $SettleSeconds = 480,
    [string] $RunId
)

$ErrorActionPreference = "Stop"

function Resolve-ToolPath {
    param(
        [string] $RequestedPath,
        [string] $CommandName,
        [string[]] $Fallbacks = @()
    )

    if ($RequestedPath) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -LiteralPath $RequestedPath).Path
        }
        return $RequestedPath
    }

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    foreach ($fallback in $Fallbacks) {
        if (Test-Path -LiteralPath $fallback) {
            return (Resolve-Path -LiteralPath $fallback).Path
        }
    }

    throw "$CommandName was not found. Pass the matching path parameter or add it to PATH."
}

function Invoke-PsqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SqlPath,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    & $script:Psql $PostgresUrl "-v" "ON_ERROR_STOP=1" "-f" $SqlPath 2>&1 | Tee-Object -FilePath $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE. Check $OutputPath"
    }
}

function Invoke-PsqlRows {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql
    )

    $output = & $script:Psql $PostgresUrl "-v" "ON_ERROR_STOP=1" "-t" "-A" "-F" "," "-c" $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql query failed with exit code $LASTEXITCODE."
    }
    return @($output | Where-Object { $_ -and $_.Trim().Length -gt 0 })
}

function Resolve-RedisCliExecutor {
    if ($RedisCliPath) {
        $script:RedisCli = Resolve-ToolPath -RequestedPath $RedisCliPath -CommandName "redis-cli"
        $script:RedisCliMode = "local"
        return
    }

    $redisCommand = Get-Command "redis-cli" -ErrorAction SilentlyContinue
    if ($redisCommand) {
        $script:RedisCli = $redisCommand.Source
        $script:RedisCliMode = "local"
        return
    }

    $redisFallback = "E:\Redis-x64-5.0.14.1\redis-cli.exe"
    if (Test-Path -LiteralPath $redisFallback) {
        $script:RedisCli = (Resolve-Path -LiteralPath $redisFallback).Path
        $script:RedisCliMode = "local"
        return
    }

    if ($DockerPath) {
        if (Test-Path -LiteralPath $DockerPath) {
            $script:Docker = (Resolve-Path -LiteralPath $DockerPath).Path
        } else {
            $script:Docker = $DockerPath
        }
    } else {
        $dockerCommand = Get-Command "docker" -ErrorAction SilentlyContinue
        if ($dockerCommand) {
            $script:Docker = $dockerCommand.Source
        }
    }

    if ($script:Docker) {
        $containers = @(& $script:Docker ps --format "{{.Names}}")
        if ($LASTEXITCODE -eq 0 -and ($containers -contains $RedisContainerName)) {
            $script:RedisCliMode = "docker"
            return
        }
    }

    throw "redis-cli was not found locally and Redis Docker container '$RedisContainerName' was not found. Pass -RedisCliPath or -RedisContainerName."
}

function Invoke-RedisCli {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $RedisArgs,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    if ($script:RedisCliMode -eq "docker") {
        $baseArgs = @("exec", $RedisContainerName, "redis-cli", "-n", [string] $RedisDatabase)
        if ($RedisPassword) {
            $baseArgs += @("-a", $RedisPassword, "--no-auth-warning")
        }
        & $script:Docker @baseArgs @RedisArgs 2>&1 | Tee-Object -FilePath $OutputPath -Append
    } else {
        $baseArgs = @("-h", $RedisHost, "-p", [string] $RedisPort, "-n", [string] $RedisDatabase)
        if ($RedisPassword) {
            $baseArgs += @("-a", $RedisPassword, "--no-auth-warning")
        }
        & $script:RedisCli @baseArgs @RedisArgs 2>&1 | Tee-Object -FilePath $OutputPath -Append
    }

    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli failed with exit code $LASTEXITCODE. Check $OutputPath"
    }
}

function Invoke-RedisCliRows {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $RedisArgs
    )

    if ($script:RedisCliMode -eq "docker") {
        $baseArgs = @("exec", $RedisContainerName, "redis-cli", "-n", [string] $RedisDatabase)
        if ($RedisPassword) {
            $baseArgs += @("-a", $RedisPassword, "--no-auth-warning")
        }
        $output = @(& $script:Docker @baseArgs @RedisArgs 2>&1)
    } else {
        $baseArgs = @("-h", $RedisHost, "-p", [string] $RedisPort, "-n", [string] $RedisDatabase)
        if ($RedisPassword) {
            $baseArgs += @("-a", $RedisPassword, "--no-auth-warning")
        }
        $output = @(& $script:RedisCli @baseArgs @RedisArgs 2>&1)
    }

    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli failed with exit code $LASTEXITCODE."
    }
    return @($output | Where-Object { $_ -ne $null })
}

function Normalize-HexSku {
    param([Parameter(Mandatory = $true)][string] $SkuHex)

    $value = $SkuHex.Trim().ToLowerInvariant()
    if ($value -notmatch '^[0-9a-f]{32}$') {
        throw "SKU hex must be 32 lowercase/uppercase hex chars: $SkuHex"
    }
    return $value
}

function Convert-HexSkuToBase62 {
    param([Parameter(Mandatory = $true)][string] $SkuHex)

    $skuSql = (Normalize-HexSku -SkuHex $SkuHex).Replace("'", "''")
    $rows = @(Invoke-PsqlRows -Sql "SELECT to_base62(id) FROM product_sku WHERE encode(id, 'hex') = '$skuSql';")
    if ($rows.Count -ne 1) {
        throw "Expected exactly one product_sku row for hex $SkuHex, got $($rows.Count)."
    }
    return $rows[0].ToString().Trim()
}

function Write-DbPreconditionSql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SqlPath,

        [Parameter(Mandatory = $true)]
        [string] $SkuAHexValue,

        [Parameter(Mandatory = $true)]
        [string] $SkuBHexValue,

        [Parameter(Mandatory = $true)]
        [int] $StockAValue,

        [Parameter(Mandatory = $true)]
        [int] $StockBValue,

        [Parameter(Mandatory = $true)]
        [int] $PointPriceValue
    )

    $skuASql = (Normalize-HexSku -SkuHex $SkuAHexValue).Replace("'", "''")
    $skuBSql = (Normalize-HexSku -SkuHex $SkuBHexValue).Replace("'", "''")
    $sql = @"
\set ON_ERROR_STOP on

DO `$`$
DECLARE
    v_count integer;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM product_sku
    WHERE id IN (decode('$skuASql', 'hex'), decode('$skuBSql', 'hex'));

    IF v_count <> 2 THEN
        RAISE EXCEPTION 'expected both target SKU rows, got %', v_count;
    END IF;
END
`$`$;

WITH target(label, sku_id, stock_value) AS (
    VALUES
        ('SKU_A', decode('$skuASql', 'hex'), $StockAValue),
        ('SKU_B', decode('$skuBSql', 'hex'), $StockBValue)
),
updated_sku AS (
    UPDATE product_sku s
    SET point_exchange_enabled = TRUE,
        point_exchange_points = $PointPriceValue,
        stock_quantity = GREATEST(s.stock_quantity, t.stock_value),
        status = 'ACTIVE',
        updated_at = NOW()
    FROM target t
    WHERE s.id = t.sku_id
    RETURNING t.label, s.id, s.spu_id, t.stock_value
),
upsert_hot AS (
    INSERT INTO product_hot_sku (
        id,
        spu_id,
        sku_id,
        stock_quantity,
        remaining_quantity,
        status,
        start_at,
        end_at,
        version,
        created_at,
        updated_at
    )
    SELECT decode(md5('JMETER-POINTS-HOT:' || u.label || ':' || encode(u.id, 'hex')), 'hex'),
           u.spu_id,
           u.id,
           u.stock_value,
           u.stock_value,
           'ENABLED',
           NOW() - INTERVAL '1 hour',
           NOW() + INTERVAL '2 hours',
           1,
           NOW(),
           NOW()
    FROM updated_sku u
    ON CONFLICT (sku_id) DO UPDATE
    SET stock_quantity = EXCLUDED.stock_quantity,
        remaining_quantity = EXCLUDED.remaining_quantity,
        status = 'ENABLED',
        start_at = EXCLUDED.start_at,
        end_at = EXCLUDED.end_at,
        version = product_hot_sku.version + 1,
        updated_at = NOW()
    RETURNING sku_id,
              stock_quantity,
              remaining_quantity,
              status,
              start_at,
              end_at,
              version
)
SELECT encode(h.sku_id, 'hex') AS sku_hex,
       to_base62(h.sku_id) AS sku_base62,
       h.stock_quantity,
       h.remaining_quantity,
       h.status,
       FLOOR(EXTRACT(EPOCH FROM h.start_at) * 1000)::bigint AS start_at_epoch_ms,
       FLOOR(EXTRACT(EPOCH FROM h.end_at) * 1000)::bigint AS end_at_epoch_ms,
       h.version
FROM upsert_hot h
ORDER BY sku_hex;

WITH target_users AS (
    SELECT g.user_id::bigint AS user_id,
           CASE WHEN g.user_id IN (141, 142) OR g.user_id BETWEEN 161 AND 180 THEN 80 ELSE 100 END AS prepared_points
    FROM generate_series(1, 200) AS g(user_id)
),
upserted AS (
    INSERT INTO user_point_account (
        user_id,
        available_points,
        total_earned_points,
        total_used_points,
        created_at,
        updated_at,
        version
    )
    SELECT user_id,
           prepared_points,
           prepared_points,
           0,
           NOW(),
           NOW(),
           1
    FROM target_users
    ON CONFLICT (user_id) DO UPDATE
    SET available_points = EXCLUDED.available_points,
        total_earned_points = EXCLUDED.total_earned_points,
        total_used_points = 0,
        updated_at = NOW(),
        version = user_point_account.version + 1
    RETURNING user_id, available_points, total_earned_points, total_used_points
)
SELECT COUNT(*) AS prepared_user_count,
       MIN(available_points) AS min_available_points,
       MAX(available_points) AS max_available_points,
       SUM(total_used_points) AS total_used_points
FROM upserted;

SELECT encode(sku_id, 'hex') AS sku_hex,
       COUNT(*) AS unused_card_secret_count
FROM card_secret_inventory
WHERE sku_id IN (decode('$skuASql', 'hex'), decode('$skuBSql', 'hex'))
  AND status = 'UNUSED'
GROUP BY sku_id
ORDER BY sku_hex;

WITH target(label, sku_id, required_unused) AS (
    VALUES
        ('SKU_A', decode('$skuASql', 'hex'), $StockAValue),
        ('SKU_B', decode('$skuBSql', 'hex'), $StockBValue)
),
releasable_cards AS (
    SELECT DISTINCT delivery.card_secret_id
    FROM order_card_secret_delivery delivery
    INNER JOIN card_secret_inventory inventory ON inventory.id = delivery.card_secret_id
    INNER JOIN target ON target.sku_id = delivery.sku_id
                AND target.sku_id = inventory.sku_id
    LEFT JOIN trade_order orders ON orders.order_no = delivery.order_no
    WHERE orders.order_no IS NULL
       OR orders.idempotency_key LIKE 'JMETER-POINTS-%'
       OR orders.idempotency_key LIKE '%:JMETER-POINTS-%'
    UNION
    SELECT inventory.id AS card_secret_id
    FROM card_secret_inventory inventory
    INNER JOIN target ON target.sku_id = inventory.sku_id
    LEFT JOIN trade_order orders ON orders.order_no = inventory.order_no
    WHERE inventory.status = 'SOLD'
      AND (
          inventory.order_no IS NULL
          OR orders.order_no IS NULL
          OR orders.idempotency_key LIKE 'JMETER-POINTS-%'
          OR orders.idempotency_key LIKE '%:JMETER-POINTS-%'
      )
),
deleted_delivery AS (
    DELETE FROM order_card_secret_delivery delivery
    USING releasable_cards cards
    WHERE delivery.card_secret_id = cards.card_secret_id
    RETURNING delivery.card_secret_id
),
reset_inventory AS (
    UPDATE card_secret_inventory inventory
    SET status = 'UNUSED',
        order_no = NULL,
        user_id = NULL,
        sold_at = NULL,
        updated_at = NOW(),
        version = inventory.version + 1
    FROM releasable_cards cards
    WHERE inventory.id = cards.card_secret_id
    RETURNING inventory.sku_id
)
SELECT target.label,
       COUNT(reset_inventory.sku_id) AS reset_card_secret_count
FROM target
LEFT JOIN reset_inventory ON reset_inventory.sku_id = target.sku_id
GROUP BY target.label
ORDER BY target.label;

DO `$`$
DECLARE
    v_bad text;
BEGIN
    WITH target(label, sku_id, required_unused) AS (
        VALUES
            ('SKU_A', decode('$skuASql', 'hex'), $StockAValue),
            ('SKU_B', decode('$skuBSql', 'hex'), $StockBValue)
    ),
    counts AS (
        SELECT target.label,
               target.required_unused,
               COUNT(inventory.id) FILTER (WHERE inventory.status = 'UNUSED') AS unused_count
        FROM target
        LEFT JOIN card_secret_inventory inventory ON inventory.sku_id = target.sku_id
        GROUP BY target.label, target.required_unused
    )
    SELECT string_agg(label || ': unused=' || unused_count || ', required=' || required_unused, '; ')
    INTO v_bad
    FROM counts
    WHERE unused_count < required_unused;

    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'target SKU card secrets are not enough after loadtest cleanup: %', v_bad;
    END IF;
END
`$`$;

SELECT encode(sku_id, 'hex') AS sku_hex,
       status,
       COUNT(*) AS card_secret_count
FROM card_secret_inventory
WHERE sku_id IN (decode('$skuASql', 'hex'), decode('$skuBSql', 'hex'))
GROUP BY sku_id, status
ORDER BY sku_hex, status;
"@
    Set-Content -LiteralPath $SqlPath -Value $sql -Encoding UTF8
}

function Write-PointExchangeToggleSql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SqlPath,

        [Parameter(Mandatory = $true)]
        [string] $SkuHexValue,

        [Parameter(Mandatory = $true)]
        [bool] $Enabled,

        [Parameter(Mandatory = $true)]
        [int] $PointPriceValue
    )

    $skuSql = (Normalize-HexSku -SkuHex $SkuHexValue).Replace("'", "''")
    $enabledSql = if ($Enabled) { "TRUE" } else { "FALSE" }
    $pointsSql = [string] $PointPriceValue
    $sql = @"
\set ON_ERROR_STOP on

UPDATE product_sku
SET point_exchange_enabled = $enabledSql,
    point_exchange_points = $pointsSql,
    updated_at = NOW()
WHERE id = decode('$skuSql', 'hex');

SELECT encode(id, 'hex') AS sku_hex,
       to_base62(id) AS sku_base62,
       point_exchange_enabled,
       point_exchange_points
FROM product_sku
WHERE id = decode('$skuSql', 'hex');
"@
    Set-Content -LiteralPath $SqlPath -Value $sql -Encoding UTF8
}

function Get-HotSkuRedisMeta {
    param([Parameter(Mandatory = $true)][string] $SkuBase62)

    $skuSql = $SkuBase62.Replace("'", "''")
    $sql = @"
SELECT h.spu_id,
       to_base62(h.sku_id),
       h.stock_quantity,
       h.remaining_quantity,
       h.status,
       FLOOR(EXTRACT(EPOCH FROM h.start_at) * 1000)::bigint,
       FLOOR(EXTRACT(EPOCH FROM h.end_at) * 1000)::bigint,
       h.version,
       s.point_exchange_enabled,
       s.point_exchange_points
FROM product_hot_sku h
INNER JOIN product_sku s ON s.id = h.sku_id
WHERE h.sku_id = from_base62('$skuSql')
"@
    $rows = @(Invoke-PsqlRows -Sql $sql)
    if ($rows.Count -ne 1) {
        throw "Could not read one hot SKU metadata row for $SkuBase62. Count=$($rows.Count)"
    }
    $parts = $rows[0] -split ","
    if ($parts.Count -lt 10) {
        throw "Unexpected hot SKU metadata output: $($rows[0])"
    }
    return [ordered]@{
        spuId = $parts[0]
        skuId = $parts[1]
        stockQuantity = $parts[2]
        remainingQuantity = $parts[3]
        status = $parts[4]
        startAtEpochMs = $parts[5]
        endAtEpochMs = $parts[6]
        version = $parts[7]
        pointExchangeEnabled = $parts[8]
        pointExchangePoints = $parts[9]
    }
}

function Write-HotSkuRedisBatch {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IEnumerable] $Metas,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    $metaList = @($Metas)
    if ($metaList.Count -eq 0) {
        return
    }

    $keys = @()
    $argv = @()
    $luaLines = New-Object System.Collections.Generic.List[string]
    for ($index = 0; $index -lt $metaList.Count; $index++) {
        $meta = $metaList[$index]
        $keyBase = ($index * 3) + 1
        $argBase = ($index * 10) + 1
        $skuIdValue = [string] $meta["skuId"]
        $pointEnabledValue = if (([string] $meta["pointExchangeEnabled"]).Equals("t", [StringComparison]::OrdinalIgnoreCase) -or
                ([string] $meta["pointExchangeEnabled"]).Equals("true", [StringComparison]::OrdinalIgnoreCase)) {
            "true"
        } else {
            "false"
        }

        $keys += "shopping:product:hot-sku:meta:$skuIdValue"
        $keys += "shopping:product:hot-sku:stock:$skuIdValue"
        $keys += "shopping:order:hot-sku:user:$skuIdValue"

        $argv += [string] $meta["spuId"]
        $argv += $skuIdValue
        $argv += [string] $meta["status"]
        $argv += [string] $meta["startAtEpochMs"]
        $argv += [string] $meta["endAtEpochMs"]
        $argv += [string] $meta["stockQuantity"]
        $argv += [string] $meta["version"]
        $argv += $pointEnabledValue
        $argv += [string] $meta["pointExchangePoints"]
        $argv += [string] $meta["remainingQuantity"]

        $luaLines.Add("redis.call('HSET', KEYS[$keyBase], 'spuId', ARGV[$argBase], 'skuId', ARGV[$($argBase + 1)], 'status', ARGV[$($argBase + 2)], 'startAtEpochMs', ARGV[$($argBase + 3)], 'endAtEpochMs', ARGV[$($argBase + 4)], 'stockQuantity', ARGV[$($argBase + 5)], 'version', ARGV[$($argBase + 6)], 'pointExchangeEnabled', ARGV[$($argBase + 7)], 'pointExchangePoints', ARGV[$($argBase + 8)])")
        $luaLines.Add("redis.call('SET', KEYS[$($keyBase + 1)], ARGV[$($argBase + 9)])")
        $luaLines.Add("redis.call('PERSIST', KEYS[$keyBase])")
        $luaLines.Add("redis.call('PERSIST', KEYS[$($keyBase + 1)])")
        $luaLines.Add("redis.call('DEL', KEYS[$($keyBase + 2)])")
    }
    $luaLines.Add("return tostring(#KEYS)")
    $lua = $luaLines -join "; "
    $redisArgs = @("EVAL", $lua, [string] $keys.Count)
    $redisArgs += $keys
    $redisArgs += $argv

    Add-Content -LiteralPath $OutputPath -Value "Writing Redis hot SKU metadata batch, count=$($metaList.Count)"
    Invoke-RedisCli -OutputPath $OutputPath -RedisArgs $redisArgs
}

function Read-HotSkuRedisBatch {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $SkuBase62Values,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    if ($SkuBase62Values.Count -eq 0) {
        return
    }

    $keys = @()
    foreach ($skuIdValue in $SkuBase62Values) {
        $keys += "shopping:product:hot-sku:meta:$skuIdValue"
        $keys += "shopping:product:hot-sku:stock:$skuIdValue"
    }

    $lua = @"
local result = {}
for i = 1, #KEYS, 2 do
    local meta = redis.call('HGETALL', KEYS[i])
    local stock = redis.call('GET', KEYS[i + 1])
    table.insert(result, KEYS[i])
    table.insert(result, cjson.encode(meta))
    table.insert(result, KEYS[i + 1])
    table.insert(result, stock or '')
end
return result
"@
    $redisArgs = @("EVAL", $lua, [string] $keys.Count)
    $redisArgs += $keys

    Add-Content -LiteralPath $OutputPath -Value "Reading Redis hot SKU metadata batch, count=$($SkuBase62Values.Count)"
    Invoke-RedisCli -OutputPath $OutputPath -RedisArgs $redisArgs
}

function Get-RunPaidInventoryExpectation {
    $runIdSql = $RunId.Replace("'", "''")
    $skuASql = $script:SkuABase62.Replace("'", "''")
    $skuBSql = $script:SkuBBase62.Replace("'", "''")
    $sql = @"
WITH target(sku_id) AS (
    VALUES (from_base62('$skuASql')), (from_base62('$skuBSql'))
),
run_orders AS (
    SELECT order_no, status, payment_type
    FROM trade_order
    WHERE idempotency_key LIKE ('JMETER-POINTS-$runIdSql-%')
       OR idempotency_key LIKE ('%:JMETER-POINTS-$runIdSql-%')
),
paid AS (
    SELECT i.sku_id,
           COUNT(DISTINCT o.order_no)::bigint AS paid_order_count,
           COALESCE(SUM(i.quantity), 0)::bigint AS paid_quantity
    FROM run_orders o
    INNER JOIN trade_order_item i ON i.order_no = o.order_no
    WHERE o.status = 'PAID'
      AND o.payment_type = 'POINTS'
    GROUP BY i.sku_id
)
SELECT to_base62(t.sku_id),
       COALESCE(p.paid_order_count, 0),
       COALESCE(p.paid_quantity, 0)
FROM target t
LEFT JOIN paid p ON p.sku_id = t.sku_id
ORDER BY 1;
"@
    $rows = @(Invoke-PsqlRows -Sql $sql)
    $result = @{}
    foreach ($row in $rows) {
        $parts = $row -split ","
        if ($parts.Count -lt 3) {
            continue
        }
        $result[$parts[0]] = [ordered]@{
            paidOrderCount = [long] $parts[1]
            paidQuantity = [long] $parts[2]
        }
    }
    return $result
}

function Get-HotSkuRedisInventorySnapshot {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $SkuBase62Values
    )

    $keys = @()
    foreach ($skuIdValue in $SkuBase62Values) {
        $keys += "shopping:product:hot-sku:stock:$skuIdValue"
        $keys += "shopping:order:hot-sku:user:$skuIdValue"
    }
    $lua = @"
local result = {}
for i = 1, #KEYS, 2 do
    local stock = redis.call('GET', KEYS[i])
    local reservations = redis.call('HLEN', KEYS[i + 1])
    table.insert(result, KEYS[i])
    table.insert(result, stock or '')
    table.insert(result, KEYS[i + 1])
    table.insert(result, tostring(reservations))
end
return result
"@
    $redisArgs = @("EVAL", $lua, [string] $keys.Count)
    $redisArgs += $keys
    $rows = @(Invoke-RedisCliRows -RedisArgs $redisArgs)
    $result = @{}
    for ($index = 0; $index + 3 -lt $rows.Count; $index += 4) {
        $stockKey = [string] $rows[$index]
        $stockValue = [string] $rows[$index + 1]
        $reservationValue = [string] $rows[$index + 3]
        $skuId = $stockKey -replace '^shopping:product:hot-sku:stock:', ''
        $result[$skuId] = [ordered]@{
            stock = if ($stockValue -match '^-?\d+$') { [long] $stockValue } else { $null }
            reservationCount = if ($reservationValue -match '^\d+$') { [long] $reservationValue } else { $null }
        }
    }
    return $result
}

function Wait-HotSkuRedisInventorySettle {
    param(
        [Parameter(Mandatory = $true)]
        [int] $TimeoutSeconds
    )

    if ($TimeoutSeconds -le 0) {
        return
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $skuStockBaseline = @{
        $script:SkuABase62 = [long] $StockA
        $script:SkuBBase62 = [long] $StockB
    }
    $lastMessage = ""
    while ((Get-Date) -lt $deadline) {
        $expectations = Get-RunPaidInventoryExpectation
        $actuals = Get-HotSkuRedisInventorySnapshot -SkuBase62Values @($script:SkuABase62, $script:SkuBBase62)
        $allSettled = $true
        $messages = New-Object System.Collections.Generic.List[string]
        foreach ($skuId in @($script:SkuABase62, $script:SkuBBase62)) {
            $expected = $expectations[$skuId]
            $actual = $actuals[$skuId]
            if ($expected -eq $null -or $actual -eq $null) {
                $allSettled = $false
                $messages.Add("$skuId missing expected/actual snapshot")
                continue
            }
            $expectedStock = [long] $skuStockBaseline[$skuId] - [long] $expected.paidQuantity
            $expectedReservations = [long] $expected.paidOrderCount
            if ($actual.stock -ne $expectedStock -or $actual.reservationCount -ne $expectedReservations) {
                $allSettled = $false
                $messages.Add("$skuId stock=$($actual.stock)/$expectedStock reservations=$($actual.reservationCount)/$expectedReservations")
            }
        }
        if ($allSettled) {
            Write-Host "Redis hot SKU inventory settled."
            return
        }
        $lastMessage = $messages -join "; "
        Write-Host "Waiting for Redis hot SKU inventory settle: $lastMessage"
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for Redis hot SKU inventory to settle after $TimeoutSeconds seconds. Last state: $lastMessage"
}

function Assert-JMeterJtlSuccessful {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JtlPath,

        [Parameter(Mandatory = $true)]
        [string] $ScenarioMode
    )

    if (-not (Test-Path -LiteralPath $JtlPath)) {
        throw "JMeter phase $ScenarioMode did not create JTL: $JtlPath"
    }

    $rows = @(Import-Csv -LiteralPath $JtlPath)
    if ($rows.Count -eq 0) {
        throw "JMeter phase $ScenarioMode created an empty JTL: $JtlPath"
    }

    $failedRows = @($rows | Where-Object { $_.success -and $_.success.ToString().ToLowerInvariant() -eq "false" })
    if ($failedRows.Count -gt 0) {
        $first = $failedRows | Select-Object -First 1
        throw "JMeter phase $ScenarioMode has failed sampler rows in $JtlPath. First failure: label=$($first.label), responseCode=$($first.responseCode), responseMessage=$($first.responseMessage)"
    }
}

function Invoke-JMeterPhase {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ScenarioMode,

        [Parameter(Mandatory = $true)]
        [bool] $AppendResults,

        [Parameter(Mandatory = $true)]
        [string] $JtlPath,

        [Parameter(Mandatory = $true)]
        [string] $LogPath,

        [Parameter(Mandatory = $true)]
        [string] $ReportDir
    )

    $appendText = if ($AppendResults) { "true" } else { "false" }
    $arguments = @(
        "-n",
        "-t", $script:PlanPath,
        "-l", $JtlPath,
        "-j", $LogPath,
        "-e",
        "-o", $ReportDir,
        "-JRUN_ID=$RunId",
        "-JSCENARIOS=$ScenarioMode",
        "-JAPPEND_RESULTS=$appendText",
        "-JHOST=$HostName",
        "-JPORT=$Port",
        "-JPROTOCOL=$Protocol",
        "-JSKU_A_ID=$script:SkuABase62",
        "-JSKU_B_ID=$script:SkuBBase62",
        "-JPOINTS_PER_ITEM=$PointPrice",
        "-JTOKEN_CSV=$TokenCsv",
        "-JORDER_POINTS_RESULT_CSV=$script:ResultCsvPath",
        "-JORDER_POINTS_SCRIPT=$script:DriverScriptPath",
        "-JHTTP_TIMEOUT_MS=$HttpTimeoutMs",
        "-JLONG_HTTP_TIMEOUT_MS=$LongHttpTimeoutMs",
        "-JCLIENT_TIMEOUT_MS=$ClientTimeoutMs",
        "-JCONCURRENT_THREADS=$ConcurrentThreads",
        "-JTIMED_EVENT_THREADS=$TimedEventThreads",
        "-JCONCURRENT_GROUP_PAUSE_MS=$ConcurrentGroupPauseMs",
        "-JFAIL_ON_HTTP_5XX=true",
        "-Jjmeter.save.saveservice.output_format=csv",
        "-Jjmeter.save.saveservice.print_field_names=true"
    )

    Write-Host "Running JMeter phase: $ScenarioMode"
    & $script:JMeter @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "JMeter phase $ScenarioMode failed with exit code $LASTEXITCODE. Check $LogPath"
    }
    Assert-JMeterJtlSuccessful -JtlPath $JtlPath -ScenarioMode $ScenarioMode
}

function Join-JtlFiles {
    param(
        [string[]] $InputPaths,
        [string] $OutputPath
    )

    $writtenHeader = $false
    if (Test-Path -LiteralPath $OutputPath) {
        Remove-Item -LiteralPath $OutputPath -Force
    }
    foreach ($path in $InputPaths) {
        if (-not (Test-Path -LiteralPath $path)) {
            continue
        }
        $lines = @(Get-Content -LiteralPath $path)
        if ($lines.Count -eq 0) {
            continue
        }
        if (-not $writtenHeader) {
            $lines | Add-Content -LiteralPath $OutputPath -Encoding UTF8
            $writtenHeader = $true
        } else {
            $lines | Select-Object -Skip 1 | Add-Content -LiteralPath $OutputPath -Encoding UTF8
        }
    }
}

if (-not $RunId) {
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
}

$SkuAHex = Normalize-HexSku -SkuHex $SkuAHex
$SkuBHex = Normalize-HexSku -SkuHex $SkuBHex

$script:JMeter = Resolve-ToolPath -RequestedPath $JMeterPath -CommandName "jmeter" -Fallbacks @("E:\apache-jmeter-5.6.3\bin\jmeter.bat")
$script:Psql = Resolve-ToolPath -RequestedPath $PsqlPath -CommandName "psql"
Resolve-RedisCliExecutor

if (-not (Test-Path -LiteralPath $TokenCsv)) {
    throw "TokenCsv was not found: $TokenCsv"
}
$TokenCsv = (Resolve-Path -LiteralPath $TokenCsv).Path

$tokenRows = @(Import-Csv -LiteralPath $TokenCsv)
$tokenUserIds = @(
    $tokenRows |
        Where-Object { $_.userId -match '^\d+$' -and $_.accessToken } |
        ForEach-Object { [long] $_.userId }
) | Sort-Object -Unique

$missingUserIds = @(1..200 | Where-Object { $tokenUserIds -notcontains [long] $_ })
if ($missingUserIds.Count -gt 0) {
    throw "TokenCsv must contain accessToken for userId=1..200. Missing: $($missingUserIds -join ', ')"
}

$script:SkuABase62 = Convert-HexSkuToBase62 -SkuHex $SkuAHex
$script:SkuBBase62 = Convert-HexSkuToBase62 -SkuHex $SkuBHex

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-points-payment-expire"
$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName
$effectiveSettleSeconds = if ($NegativeNoCancel) { [Math]::Max($SettleSeconds, 720) } else { $SettleSeconds }

$script:PlanPath = (Resolve-Path -LiteralPath "loadtest/jmeter/order-points-payment-expire-flow.jmx").Path
$script:DriverScriptPath = (Resolve-Path -LiteralPath "loadtest/scripts/order-points-payment-expire-flow.groovy").Path
if ($NegativeNoCancel) {
    $verifySql = "loadtest/sql/verify-order-points-payment-negative-no-cancel.sql"
} else {
    $verifySql = "loadtest/sql/verify-order-points-payment-expire.sql"
}

$script:ResultCsvPath = Join-Path $runDir "order-points-payment-results.csv"
$jtlPath = Join-Path $runDir "order-points-payment-expire.jtl"
$unsupportedJtlPath = Join-Path $runDir "order-points-payment-expire-unsupported.jtl"
$mainJtlPath = Join-Path $runDir "order-points-payment-expire-main.jtl"
$negativeInsufficientJtlPath = Join-Path $runDir "order-points-payment-expire-negative-insufficient.jtl"
$unsupportedLogPath = Join-Path $runDir "jmeter-unsupported.log"
$mainLogPath = Join-Path $runDir "jmeter-main.log"
$negativeInsufficientLogPath = Join-Path $runDir "jmeter-negative-insufficient.log"
$reportUnsupportedDir = Join-Path $runDir "html-report-unsupported"
$reportMainDir = Join-Path $runDir "html-report-main"
$reportNegativeInsufficientDir = Join-Path $runDir "html-report-negative-insufficient"
$summaryPath = Join-Path $runDir "summary.csv"
$configPath = Join-Path $runDir "run-config.json"
$dbPreconditionSqlPath = Join-Path $runDir "precondition-db.sql"
$dbPreconditionOutputPath = Join-Path $runDir "precondition-db-output.txt"
$redisPreconditionOutputPath = Join-Path $runDir "precondition-redis-output.txt"
$disableSqlPath = Join-Path $runDir "disable-sku-a-points-exchange.sql"
$disableOutputPath = Join-Path $runDir "disable-sku-a-points-exchange-output.txt"
$reenableSqlPath = Join-Path $runDir "reenable-sku-a-points-exchange.sql"
$reenableOutputPath = Join-Path $runDir "reenable-sku-a-points-exchange-output.txt"
$postRedisOutputPath = Join-Path $runDir "post-run-redis-output.txt"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"
$generatedVerifySqlPath = Join-Path $runDir "verify-order-points-payment-expire.generated.sql"

$config = [ordered]@{
    runId = $RunId
    plan = $script:PlanPath
    groovyDriver = $script:DriverScriptPath
    hostName = $HostName
    port = $Port
    protocol = $Protocol
    skuA = [ordered]@{
        hex = $SkuAHex
        base62 = $script:SkuABase62
        stock = $StockA
    }
    skuB = [ordered]@{
        hex = $SkuBHex
        base62 = $script:SkuBBase62
        stock = $StockB
    }
    pointPrice = $PointPrice
    tokenCsv = $TokenCsv
    postgresUrl = $PostgresUrl
    redisHost = $RedisHost
    redisPort = $RedisPort
    redisDatabase = $RedisDatabase
    redisCliMode = $script:RedisCliMode
    redisContainerName = $RedisContainerName
    httpTimeoutMs = $HttpTimeoutMs
    longHttpTimeoutMs = $LongHttpTimeoutMs
    clientTimeoutMs = $ClientTimeoutMs
    concurrentThreads = $ConcurrentThreads
    timedEventThreads = $TimedEventThreads
    settleSeconds = $effectiveSettleSeconds
    requestedSettleSeconds = $SettleSeconds
    negativeNoCancel = [bool] $NegativeNoCancel
    requiredAppEnv = [ordered]@{
        ORDER_LOADTEST_BYPASS_GUARDS = "true"
        ORDER_POINTS_PAYMENT_FAULT_ENABLED = "true"
        ORDER_EXPIRE_TTL_MILLIS = "300000"
        ORDER_EXPIRE_CLOSING_GRACE_MILLIS = "300000"
    }
}
$config | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8

Write-Host "Preparing DB hot SKUs, Redis metadata, and user point accounts."
Write-DbPreconditionSql -SqlPath $dbPreconditionSqlPath -SkuAHexValue $SkuAHex -SkuBHexValue $SkuBHex -StockAValue $StockA -StockBValue $StockB -PointPriceValue $PointPrice
Invoke-PsqlFile -SqlPath $dbPreconditionSqlPath -OutputPath $dbPreconditionOutputPath
$hotMetaA = Get-HotSkuRedisMeta -SkuBase62 $script:SkuABase62
$hotMetaB = Get-HotSkuRedisMeta -SkuBase62 $script:SkuBBase62
Write-HotSkuRedisBatch -Metas @($hotMetaA, $hotMetaB) -OutputPath $redisPreconditionOutputPath
Read-HotSkuRedisBatch -SkuBase62Values @($script:SkuABase62, $script:SkuBBase62) -OutputPath $redisPreconditionOutputPath

if ($NegativeNoCancel) {
    Write-Host "Running negative no-cancel unsupported phase with SKU_A point exchange disabled."
    Write-PointExchangeToggleSql -SqlPath $disableSqlPath -SkuHexValue $SkuAHex -Enabled $false -PointPriceValue $PointPrice
    Invoke-PsqlFile -SqlPath $disableSqlPath -OutputPath $disableOutputPath
    $disabledMetaA = Get-HotSkuRedisMeta -SkuBase62 $script:SkuABase62
    $currentMetaB = Get-HotSkuRedisMeta -SkuBase62 $script:SkuBBase62
    Write-HotSkuRedisBatch -Metas @($disabledMetaA, $currentMetaB) -OutputPath $redisPreconditionOutputPath
    Invoke-JMeterPhase -ScenarioMode "NEGATIVE_UNSUPPORTED" -AppendResults $false -JtlPath $unsupportedJtlPath -LogPath $unsupportedLogPath -ReportDir $reportUnsupportedDir

    if ($effectiveSettleSeconds -gt 0) {
        Write-Host "Waiting up to $effectiveSettleSeconds seconds for unsupported negative orders to close naturally and restore Redis stock."
        Wait-HotSkuRedisInventorySettle -TimeoutSeconds $effectiveSettleSeconds
    }

    Write-Host "Re-enabling SKU_A point exchange for negative insufficient phase."
    Write-PointExchangeToggleSql -SqlPath $reenableSqlPath -SkuHexValue $SkuAHex -Enabled $true -PointPriceValue $PointPrice
    Invoke-PsqlFile -SqlPath $reenableSqlPath -OutputPath $reenableOutputPath
    $hotMetaA = Get-HotSkuRedisMeta -SkuBase62 $script:SkuABase62
    $hotMetaB = Get-HotSkuRedisMeta -SkuBase62 $script:SkuBBase62
    Write-HotSkuRedisBatch -Metas @($hotMetaA, $hotMetaB) -OutputPath $redisPreconditionOutputPath

    Write-Host "Running negative no-cancel insufficient-points phase."
    Invoke-JMeterPhase -ScenarioMode "NEGATIVE_INSUFFICIENT" -AppendResults $true -JtlPath $negativeInsufficientJtlPath -LogPath $negativeInsufficientLogPath -ReportDir $reportNegativeInsufficientDir
    Join-JtlFiles -InputPaths @($unsupportedJtlPath, $negativeInsufficientJtlPath) -OutputPath $jtlPath
} else {
    Write-Host "Running unsupported-points phase with SKU_A point exchange disabled."
    Write-PointExchangeToggleSql -SqlPath $disableSqlPath -SkuHexValue $SkuAHex -Enabled $false -PointPriceValue $PointPrice
    Invoke-PsqlFile -SqlPath $disableSqlPath -OutputPath $disableOutputPath
    $disabledMetaA = Get-HotSkuRedisMeta -SkuBase62 $script:SkuABase62
    $currentMetaB = Get-HotSkuRedisMeta -SkuBase62 $script:SkuBBase62
    Write-HotSkuRedisBatch -Metas @($disabledMetaA, $currentMetaB) -OutputPath $redisPreconditionOutputPath
    Invoke-JMeterPhase -ScenarioMode "UNSUPPORTED" -AppendResults $false -JtlPath $unsupportedJtlPath -LogPath $unsupportedLogPath -ReportDir $reportUnsupportedDir

    Write-Host "Re-enabling SKU_A point exchange for the main phase."
    Write-PointExchangeToggleSql -SqlPath $reenableSqlPath -SkuHexValue $SkuAHex -Enabled $true -PointPriceValue $PointPrice
    Invoke-PsqlFile -SqlPath $reenableSqlPath -OutputPath $reenableOutputPath
    $hotMetaA = Get-HotSkuRedisMeta -SkuBase62 $script:SkuABase62
    $hotMetaB = Get-HotSkuRedisMeta -SkuBase62 $script:SkuBBase62
    Write-HotSkuRedisBatch -Metas @($hotMetaA, $hotMetaB) -OutputPath $redisPreconditionOutputPath

    Write-Host "Running main points-payment expiry flow as sequential correctness phases. Boundary and blocking phases still wait for real expireAt values."
    $mainPhaseJtlPaths = New-Object System.Collections.Generic.List[string]
    $mainPhaseJtlPaths.Add($unsupportedJtlPath)
    foreach ($phaseName in @("NORMAL", "CONCURRENT", "BOUNDARY", "BLOCK", "INSUFFICIENT", "CLIENT_TIMEOUT")) {
        $phaseSlug = $phaseName.ToLowerInvariant().Replace("_", "-")
        $phaseJtlPath = Join-Path $runDir "order-points-payment-expire-$phaseSlug.jtl"
        $phaseLogPath = Join-Path $runDir "jmeter-$phaseSlug.log"
        $phaseReportDir = Join-Path $runDir "html-report-$phaseSlug"
        Invoke-JMeterPhase -ScenarioMode $phaseName -AppendResults $true -JtlPath $phaseJtlPath -LogPath $phaseLogPath -ReportDir $phaseReportDir
        $mainPhaseJtlPaths.Add($phaseJtlPath)
    }
    Join-JtlFiles -InputPaths $mainPhaseJtlPaths.ToArray() -OutputPath $jtlPath
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath

if ($effectiveSettleSeconds -gt 0) {
    Write-Host "Waiting up to $effectiveSettleSeconds seconds for async order close, Redis stock release, and card delivery before optional verification."
    Wait-HotSkuRedisInventorySettle -TimeoutSeconds $effectiveSettleSeconds
}

Write-Host "Capturing final Redis hot SKU snapshots."
Read-HotSkuRedisBatch -SkuBase62Values @($script:SkuABase62, $script:SkuBBase62) -OutputPath $postRedisOutputPath

$resultCsvSqlPath = $script:ResultCsvPath.Replace("\", "/")
$generatedVerifySqlSqlPath = $generatedVerifySqlPath.Replace("\", "/")
$verifyCommand = @(
    "psql `"$PostgresUrl`"",
    "-v ON_ERROR_STOP=1",
    "-v run_id=$RunId",
    "-v sku_a_id=$script:SkuABase62",
    "-v sku_b_id=$script:SkuBBase62",
    "-v result_csv=`"$resultCsvSqlPath`"",
    "-v expected_stock_a=$StockA",
    "-v expected_stock_b=$StockB",
    "-v points_per_item=$PointPrice",
    "-f `"$generatedVerifySqlSqlPath`""
) -join " "
Set-Content -LiteralPath $verifyCommandPath -Value $verifyCommand -Encoding UTF8

$resultCsvCopyPath = $resultCsvSqlPath.Replace("'", "''")
$verifySqlText = Get-Content -LiteralPath $verifySql -Raw
$verifySqlText = $verifySqlText.Replace("\copy order_points_payment_results FROM :'result_csv' CSV HEADER", "\copy order_points_payment_results FROM '$resultCsvCopyPath' CSV HEADER")
Set-Content -LiteralPath $generatedVerifySqlPath -Value $verifySqlText -Encoding UTF8

if ($Verify) {
    if (-not (Test-Path -LiteralPath $script:ResultCsvPath)) {
        throw "Result CSV was not created, cannot run verification SQL: $script:ResultCsvPath"
    }
    & $script:Psql $PostgresUrl `
        "-v" "ON_ERROR_STOP=1" `
        "-v" "run_id=$RunId" `
        "-v" "sku_a_id=$script:SkuABase62" `
        "-v" "sku_b_id=$script:SkuBBase62" `
        "-v" "result_csv=$resultCsvSqlPath" `
        "-v" "expected_stock_a=$StockA" `
        "-v" "expected_stock_b=$StockB" `
        "-v" "points_per_item=$PointPrice" `
        "-f" $generatedVerifySqlPath 2>&1 | Tee-Object -FilePath $verifyOutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Verification SQL failed with exit code $LASTEXITCODE. Check $verifyOutputPath"
    }
}

Write-Host "RunId: $RunId"
Write-Host "Output directory: $runDir"
Write-Host "SKU_A: $SkuAHex => $script:SkuABase62"
Write-Host "SKU_B: $SkuBHex => $script:SkuBBase62"
Write-Host "Result CSV: $script:ResultCsvPath"
Write-Host "Combined JTL: $jtlPath"
Write-Host "Unsupported JMeter log: $unsupportedLogPath"
if ($NegativeNoCancel) {
    Write-Host "Negative insufficient JMeter log: $negativeInsufficientLogPath"
} else {
    Write-Host "Main JMeter logs: $(Join-Path $runDir 'jmeter-*.log')"
}
Write-Host "Summary: $summaryPath"
Write-Host "Run config: $configPath"
Write-Host "DB precondition output: $dbPreconditionOutputPath"
Write-Host "Redis precondition output: $redisPreconditionOutputPath"
Write-Host "Final Redis output: $postRedisOutputPath"
Write-Host "Verify command: $verifyCommandPath"
Write-Host "Redis CLI mode: $script:RedisCliMode"


