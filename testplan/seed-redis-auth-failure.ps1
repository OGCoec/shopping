# seed-redis-auth-failure.ps1
# Seeds Redis auth failure counters for a given userId to trigger AuthLockRiskWriter.
# Usage: .\seed-redis-auth-failure.ps1 -UserId 12345 [-RedisHost 127.0.0.1] [-RedisPort 6379]
param(
    [Parameter(Mandatory=$true)][long]$UserId,
    [string]$RedisHost = "127.0.0.1",
    [string]$RedisPort = "6379"
)

$cli = "redis-cli"
$conn = @("-h", $RedisHost, "-p", $RedisPort)

# Key patterns from UserAuthRiskRedisKeys
$totalKey  = "auth:fail:total:30m:$UserId"
$pwdKey    = "auth:fail:pwd:30m:$UserId"
$ttl       = 1800

Write-Host "Seeding Redis failure counters for userId=$UserId on $RedisHost`:$RedisPort"

& $cli @conn SET $totalKey 15 EX $ttl
& $cli @conn SET $pwdKey   8  EX $ttl

Write-Host ""
Write-Host "Done. Counters set:"
Write-Host "  $totalKey  = 15  (threshold: 15)"
Write-Host "  $pwdKey    = 8   (threshold: 8)"
Write-Host ""
Write-Host "Next: trigger one login attempt for userId=$UserId."
Write-Host "AuthLockRiskWriter will fire and write to RISK DB, then AccountStatusSyncConsumer"
Write-Host "will update CORE user_login_identity.status -> LOCKED via outbox/inbox."