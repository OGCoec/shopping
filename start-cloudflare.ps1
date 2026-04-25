param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function T {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Base64
  )

  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Base64))
}

function Show-Banner {
  Write-Host (T "PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09")
}

function Read-Protocol {
  while ($true) {
    $value = (Read-Host (T "6K+36L6T5YWl5Y2P6K6u77yIaHR0cC9odHRwc++8iQ==")).Trim().ToLowerInvariant()
    if ($value -in @("http", "https")) {
      return $value
    }
    Write-Host (T "5Y2P6K6u5peg5pWI77yM6K+36L6T5YWlIGh0dHAg5oiWIGh0dHBz44CC")
  }
}

function Read-Port {
  while ($true) {
    $raw = (Read-Host (T "6K+36L6T5YWl56uv5Y+j77yIMS02NTUzNe+8iQ==")).Trim()
    if ([string]::IsNullOrWhiteSpace($raw)) {
      Write-Host (T "56uv5Y+j5LiN6IO95Li656m677yM6K+36YeN5paw6L6T5YWl44CC")
      continue
    }

    $port = 0
    if (-not [int]::TryParse($raw, [ref]$port)) {
      Write-Host (T "56uv5Y+j5b+F6aG75piv5pWw5a2X77yM6K+36YeN5paw6L6T5YWl44CC")
      continue
    }

    if ($port -lt 1 -or $port -gt 65535) {
      Write-Host (T "56uv5Y+j6IyD5Zu05b+F6aG75ZyoIDEtNjU1MzXvvIzor7fph43mlrDovpPlhaXjgII=")
      continue
    }

    return $port
  }
}

$tunnelId = $env:CF_TUNNEL_ID
if ([string]::IsNullOrWhiteSpace($tunnelId)) {
  Show-Banner
  Write-Host (T "6ZSZ6K+v77ya5pyq6K6+572u546v5aKD5Y+Y6YePIENGX1RVTk5FTF9JROOAgg==")
  Write-Host (T "6K+35YWI6K6+572u6K+l546v5aKD5Y+Y6YeP5ZCO5YaN6L+Q6KGM5pys6ISa5pys44CC")
  Show-Banner
  exit 1
}

$configPath = Join-Path $env:USERPROFILE ".cloudflared\\config.yml"
if (-not (Test-Path -LiteralPath $configPath)) {
  Show-Banner
  Write-Host (T "6ZSZ6K+v77ya5pyq5om+5YiwIENsb3VkZmxhcmUg6YWN572u5paH5Lu277ya")
  Write-Host $configPath
  Show-Banner
  exit 1
}

$protocol = Read-Protocol
$port = Read-Port
$localService = "${protocol}://localhost:${port}"

Write-Host ""
Write-Host ((T "5q2j5Zyo5pu05pawIENsb3VkZmxhcmUg6YWN572u77ya") + $localService)
& "$PSScriptRoot\\update-cloudflare-config.ps1" -ConfigPath $configPath -Protocol $protocol -Port $port
$updateExitCode = if ($LASTEXITCODE -ne $null) { [int]$LASTEXITCODE } else { 0 }
if ($updateExitCode -ne 0) {
  Write-Host ((T "6YWN572u5pu05paw5aSx6LSl77yM6L+U5Zue56CB77ya") + $updateExitCode)
  exit $updateExitCode
}

Show-Banner
Write-Host (T "5q2j5Zyo5ZCv5YqoIENsb3VkZmxhcmUg6Zqn6YGTLi4u")
Write-Host ((T "6Zqn6YGTIElE77ya") + $tunnelId)
Write-Host (T "5p2l5rqQ77ya546v5aKD5Y+Y6YePIENGX1RVTk5FTF9JRA==")
Write-Host ((T "5Zue5rqQ5Zyw5Z2A77ya") + $localService)
Write-Host (T "6K6/6Zeu5Z+f5ZCN77yabmlrbzAwMG8uc2l0ZQ==")
Write-Host (T "6L+e5o6lIElQIOajgOa1i++8muWQr+eUqO+8iGNsb3VkZmxhcmVkIOWunumZheWHuuWPo++8iQ==")
Write-Host (T "5oyJ5LiL5Lit5pat6ZSu5Y2z5Y+v5YGc5q2i6Zqn6YGT44CC")
Show-Banner
Write-Host ""

& "$PSScriptRoot\\cloudflare-ip-guard.ps1" -TunnelId $tunnelId
$exitCode = if ($LASTEXITCODE -ne $null) { [int]$LASTEXITCODE } else { 0 }

Write-Host ""
if ($exitCode -eq 0) {
  Write-Host (T "6Zqn6YGT5bey5YGc5q2i44CC")
} elseif ($exitCode -eq 100) {
  Write-Host (T "5qOA5rWL5YiwIGNsb3VkZmxhcmVkIOWunumZheWHuuWPo+WPmOWMlu+8jOW3sue7iOatoumap+mBk+i/m+eoi+OAgg==")
} else {
  Write-Host ((T "6Zqn6YGT5byC5bi46YCA5Ye677yM6L+U5Zue56CB77ya") + $exitCode)
}

exit $exitCode
