param(
  [string]$CloudflaredPath,
  [Parameter(Mandatory = $true)]
  [string]$TunnelId,
  [int]$CheckIntervalSeconds = 5,
  [int]$BaselineWaitSeconds = 30
)

$ErrorActionPreference = "SilentlyContinue"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Resolve-CloudflaredPath {
  param(
    [string]$PreferredPath
  )

  $candidates = New-Object System.Collections.Generic.List[string]

  if (-not [string]::IsNullOrWhiteSpace($PreferredPath)) {
    $candidates.Add($PreferredPath)
  }

  try {
    $cmd = Get-Command cloudflared -ErrorAction Stop
    if (-not [string]::IsNullOrWhiteSpace($cmd.Source)) {
      $candidates.Add($cmd.Source)
    } elseif (-not [string]::IsNullOrWhiteSpace($cmd.Path)) {
      $candidates.Add($cmd.Path)
    }
  } catch {
    # Ignore and try fixed paths.
  }

  $candidates.Add("$env:ProgramFiles\cloudflared\cloudflared.exe")
  $candidates.Add("$env:ProgramFiles(x86)\cloudflared\cloudflared.exe")

  foreach ($item in $candidates) {
    if ([string]::IsNullOrWhiteSpace($item)) {
      continue
    }

    $normalized = $item.Trim().Trim('"').Trim("'")
    if (Test-Path -LiteralPath $normalized) {
      return (Resolve-Path -LiteralPath $normalized).Path
    }
  }

  return $null
}

function Start-CloudflaredProcess {
  param(
    [Parameter(Mandatory = $true)]
    [string]$ExePath,
    [Parameter(Mandatory = $true)]
    [string]$RunTunnelId,
    [Parameter(Mandatory = $true)]
    [System.Collections.Concurrent.ConcurrentQueue[string]]$OutputQueue
  )

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $ExePath
  $psi.Arguments = "tunnel run $RunTunnelId"
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $psi.CreateNoWindow = $true
  $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
  $psi.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)

  $proc = New-Object System.Diagnostics.Process
  $proc.StartInfo = $psi

  if (-not $proc.Start()) {
    return $null
  }

  $stdoutEvent = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action {
    if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
      [void]$Event.MessageData.Enqueue($EventArgs.Data)
    }
  } -MessageData $OutputQueue

  $stderrEvent = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -Action {
    if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
      [void]$Event.MessageData.Enqueue($EventArgs.Data)
    }
  } -MessageData $OutputQueue

  $proc.BeginOutputReadLine()
  $proc.BeginErrorReadLine()

  return [PSCustomObject]@{
    Process     = $proc
    StdoutEvent = $stdoutEvent
    StderrEvent = $stderrEvent
  }
}

function Flush-CloudflaredOutput {
  param(
    [Parameter(Mandatory = $true)]
    [System.Collections.Concurrent.ConcurrentQueue[string]]$OutputQueue
  )

  $line = $null
  while ($OutputQueue.TryDequeue([ref]$line)) {
    if (-not [string]::IsNullOrWhiteSpace($line)) {
      Write-CloudflaredLogLine -Line $line
    }
  }
}

function Write-CloudflaredSegment {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Text,
    [Parameter(Mandatory = $true)]
    [ConsoleColor]$Color,
    [switch]$NoNewline
  )

  if ($NoNewline) {
    Write-Host -NoNewline $Text -ForegroundColor $Color
  } else {
    Write-Host $Text -ForegroundColor $Color
  }
}

function Get-CloudflaredTokenColor {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Token
  )

  if ($Token -match '^\d{4}-\d{2}-\d{2}T') {
    return [ConsoleColor]::DarkGray
  }
  if ($Token -eq 'INF') {
    return [ConsoleColor]::Green
  }
  if ($Token -eq 'ERR') {
    return [ConsoleColor]::Red
  }
  if ($Token -eq 'WRN') {
    return [ConsoleColor]::Yellow
  }
  if ($Token -match '^error=') {
    return [ConsoleColor]::Red
  }
  if ($Token -match '^[A-Za-z0-9_.-]+=') {
    return [ConsoleColor]::Cyan
  }

  return [ConsoleColor]::Gray
}

function Write-CloudflaredLogLine {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Line
  )

  $tokens = [System.Text.RegularExpressions.Regex]::Split($Line, '(\s+)')
  for ($i = 0; $i -lt $tokens.Length; $i++) {
    $token = $tokens[$i]
    if ($token.Length -eq 0) {
      continue
    }

    if ($token -match '^\s+$') {
      Write-Host -NoNewline $token
      continue
    }

    $color = Get-CloudflaredTokenColor -Token $token
    Write-CloudflaredSegment -Text $token -Color $color -NoNewline
  }

  Write-Host ""
}

function Get-CloudflaredConnectionSnapshot {
  param(
    [Parameter(Mandatory = $true)]
    [int]$ProcessId
  )

  $connections = Get-NetTCPConnection -OwningProcess $ProcessId -State Established -ErrorAction SilentlyContinue |
    Where-Object {
      $_.RemoteAddress -and
      $_.RemoteAddress -ne "127.0.0.1" -and
      $_.RemoteAddress -ne "::1"
    }

  if (-not $connections) {
    return $null
  }

  $localAddresses = $connections |
    ForEach-Object { $_.LocalAddress } |
    Where-Object {
      -not [string]::IsNullOrWhiteSpace($_) -and
      $_ -ne "127.0.0.1" -and
      $_ -ne "::1"
    } |
    Sort-Object -Unique

  $remoteAddresses = $connections |
    ForEach-Object { $_.RemoteAddress } |
    Where-Object {
      -not [string]::IsNullOrWhiteSpace($_) -and
      $_ -ne "127.0.0.1" -and
      $_ -ne "::1"
    } |
    Sort-Object -Unique

  if (-not $localAddresses -and -not $remoteAddresses) {
    return $null
  }

  return [PSCustomObject]@{
    LocalSignature = ($localAddresses -join ",")
    LocalIps       = $localAddresses
    RemoteIps      = $remoteAddresses
  }
}

$resolvedCloudflaredPath = Resolve-CloudflaredPath -PreferredPath $CloudflaredPath
if ([string]::IsNullOrWhiteSpace($resolvedCloudflaredPath)) {
  Write-Host "ERROR: cloudflared executable not found in PATH or default install directories."
  exit 2
}

Write-Host ("cloudflared path: " + $resolvedCloudflaredPath)

$outputQueue = [System.Collections.Concurrent.ConcurrentQueue[string]]::new()
$cloudflaredRuntime = Start-CloudflaredProcess -ExePath $resolvedCloudflaredPath -RunTunnelId $TunnelId -OutputQueue $outputQueue
if (-not $cloudflaredRuntime) {
  Write-Host "ERROR: failed to start cloudflared process."
  exit 4
}

$cloudflared = $cloudflaredRuntime.Process

$exitCode = 0
$guardMessage = $null
$baselineSignature = $null
$baselineRemoteIps = @()
$baselineDeadline = (Get-Date).AddSeconds([Math]::Max(5, $BaselineWaitSeconds))
$monitorEnabled = $true

try {
  while ($true) {
    Flush-CloudflaredOutput -OutputQueue $outputQueue
    $cloudflared.Refresh()
    if ($cloudflared.HasExited) {
      $exitCode = [int]$cloudflared.ExitCode
      break
    }

    if ($monitorEnabled) {
      $snapshot = Get-CloudflaredConnectionSnapshot -ProcessId $cloudflared.Id
      if ($null -ne $snapshot) {
        $currentSignature = $snapshot.LocalSignature
      } else {
        $currentSignature = $null
      }

      if (-not [string]::IsNullOrWhiteSpace($currentSignature)) {
        if ([string]::IsNullOrWhiteSpace($baselineSignature)) {
          $baselineSignature = $currentSignature
          $baselineRemoteIps = @($snapshot.RemoteIps)
          Write-Host ("cloudflared local egress IP: " + $baselineSignature)
          if ($baselineRemoteIps.Count -gt 0) {
            Write-Host ("cloudflared connected edge IP: " + ($baselineRemoteIps -join ","))
          }
        } elseif ($currentSignature -ne $baselineSignature) {
          $guardMessage = "EGRESS_CHANGED:$baselineSignature->$currentSignature"
          $cloudflared.Kill()
          break
        }
      } elseif ([string]::IsNullOrWhiteSpace($baselineSignature) -and (Get-Date) -gt $baselineDeadline) {
        Write-Host "WARN: no baseline in time, continue without egress-change guard."
        $monitorEnabled = $false
      }
    }

    Start-Sleep -Seconds ([Math]::Max(1, $CheckIntervalSeconds))
  }
} finally {
  Flush-CloudflaredOutput -OutputQueue $outputQueue

  if ($cloudflared -and -not $cloudflared.HasExited) {
    try {
      $cloudflared.CancelOutputRead()
      $cloudflared.CancelErrorRead()
    } catch {
    }
    $cloudflared.Kill()
  }

  if ($cloudflaredRuntime) {
    if ($cloudflaredRuntime.StdoutEvent) {
      Unregister-Event -SourceIdentifier $cloudflaredRuntime.StdoutEvent.Name -ErrorAction SilentlyContinue
      Remove-Job -Id $cloudflaredRuntime.StdoutEvent.Id -Force -ErrorAction SilentlyContinue
    }
    if ($cloudflaredRuntime.StderrEvent) {
      Unregister-Event -SourceIdentifier $cloudflaredRuntime.StderrEvent.Name -ErrorAction SilentlyContinue
      Remove-Job -Id $cloudflaredRuntime.StderrEvent.Id -Force -ErrorAction SilentlyContinue
    }
  }
}

Flush-CloudflaredOutput -OutputQueue $outputQueue

if ($guardMessage -like "EGRESS_CHANGED:*") {
  $detail = $guardMessage.Substring("EGRESS_CHANGED:".Length)
  Write-Host ("Detected cloudflared egress change: " + $detail)
  exit 100
}

exit $exitCode
