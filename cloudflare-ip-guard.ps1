param(
  [Parameter(Mandatory = $true)]
  [string]$CloudflaredPath,
  [Parameter(Mandatory = $true)]
  [string]$TunnelId,
  [int]$CheckIntervalSeconds = 5
)

$ErrorActionPreference = "SilentlyContinue"

function Get-PublicIp {
  $candidates = @(
    "https://api64.ipify.org?format=text",
    "https://ifconfig.me/ip"
  )
  foreach ($url in $candidates) {
    try {
      $value = (Invoke-RestMethod -Uri $url -TimeoutSec 8).ToString().Trim()
      if (-not [string]::IsNullOrWhiteSpace($value)) {
        return $value
      }
    } catch {
      # Try next provider.
    }
  }
  return $null
}

if (-not (Test-Path -LiteralPath $CloudflaredPath)) {
  Write-Host "ERROR: cloudflared binary not found: $CloudflaredPath"
  exit 2
}

$baselineIp = Get-PublicIp
if ([string]::IsNullOrWhiteSpace($baselineIp)) {
  Write-Host "ERROR: failed to fetch current public IP."
  exit 3
}

Write-Host "公网 IP（基线）: $baselineIp"

$monitor = Start-Job -ArgumentList $baselineIp, $CheckIntervalSeconds -ScriptBlock {
  param($baseIp, $interval)

  function Get-PublicIp {
    $candidates = @(
      "https://api64.ipify.org?format=text",
      "https://ifconfig.me/ip"
    )
    foreach ($url in $candidates) {
      try {
        $value = (Invoke-RestMethod -Uri $url -TimeoutSec 8).ToString().Trim()
        if (-not [string]::IsNullOrWhiteSpace($value)) {
          return $value
        }
      } catch {
        # Try next provider.
      }
    }
    return $null
  }

  while ($true) {
    Start-Sleep -Seconds $interval
    $currentIp = Get-PublicIp
    if ([string]::IsNullOrWhiteSpace($currentIp)) {
      continue
    }
    if ($currentIp -ne $baseIp) {
      Write-Output "IP_CHANGED:$baseIp->$currentIp"
      Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
      break
    }
  }
}

$exitCode = 0
$guardMessage = $null

try {
  & $CloudflaredPath tunnel run $TunnelId
  if ($LASTEXITCODE -ne $null) {
    $exitCode = [int]$LASTEXITCODE
  }
} finally {
  $jobOutput = Receive-Job -Job $monitor -Keep -ErrorAction SilentlyContinue
  if ($jobOutput) {
    $guardMessage = ($jobOutput | Select-Object -Last 1).ToString()
  }
  Stop-Job -Job $monitor -ErrorAction SilentlyContinue | Out-Null
  Remove-Job -Job $monitor -Force -ErrorAction SilentlyContinue | Out-Null
}

if ($guardMessage -like "IP_CHANGED:*") {
  $detail = $guardMessage.Substring("IP_CHANGED:".Length)
  Write-Host "检测到公网 IP 变化: $detail"
  exit 100
}

exit $exitCode

