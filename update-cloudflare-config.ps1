param(
  [Parameter(Mandatory = $true)]
  [string]$ConfigPath,
  [Parameter(Mandatory = $true)]
  [ValidateSet("http", "https")]
  [string]$Protocol,
  [Parameter(Mandatory = $true)]
  [int]$Port
)

$ErrorActionPreference = "Stop"

function Resolve-TextEncoding {
  param(
    [byte[]]$Bytes
  )

  if ($Bytes.Length -ge 4) {
    if ($Bytes[0] -eq 0xFF -and $Bytes[1] -eq 0xFE -and $Bytes[2] -eq 0x00 -and $Bytes[3] -eq 0x00) {
      return @{
        Encoding = [System.Text.Encoding]::UTF32
        HasBom   = $true
      }
    }
    if ($Bytes[0] -eq 0x00 -and $Bytes[1] -eq 0x00 -and $Bytes[2] -eq 0xFE -and $Bytes[3] -eq 0xFF) {
      return @{
        Encoding = [System.Text.Encoding]::GetEncoding("utf-32BE")
        HasBom   = $true
      }
    }
  }

  if ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and $Bytes[2] -eq 0xBF) {
    return @{
      Encoding = [System.Text.UTF8Encoding]::new($true)
      HasBom   = $true
    }
  }

  if ($Bytes.Length -ge 2) {
    if ($Bytes[0] -eq 0xFF -and $Bytes[1] -eq 0xFE) {
      return @{
        Encoding = [System.Text.Encoding]::Unicode
        HasBom   = $true
      }
    }
    if ($Bytes[0] -eq 0xFE -and $Bytes[1] -eq 0xFF) {
      return @{
        Encoding = [System.Text.Encoding]::BigEndianUnicode
        HasBom   = $true
      }
    }
  }

  $utf8NoBom = [System.Text.UTF8Encoding]::new($false, $true)
  try {
    $null = $utf8NoBom.GetString($Bytes)
    return @{
      Encoding = $utf8NoBom
      HasBom   = $false
    }
  } catch {
    return @{
      Encoding = [System.Text.Encoding]::Default
      HasBom   = $false
    }
  }
}

if (-not (Test-Path -LiteralPath $ConfigPath)) {
  Write-Host "ERROR: config file not found: $ConfigPath"
  exit 10
}

if ($Port -lt 1 -or $Port -gt 65535) {
  Write-Host "ERROR: invalid port range: $Port"
  exit 11
}

$serviceValue = "${Protocol}://localhost:$Port"
$rawBytes = [System.IO.File]::ReadAllBytes($ConfigPath)
$encodingInfo = Resolve-TextEncoding -Bytes $rawBytes
$text = $encodingInfo.Encoding.GetString($rawBytes)
if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) {
  $text = $text.Substring(1)
}

$newline = if ($text.Contains("`r`n")) {
  "`r`n"
} elseif ($text.Contains("`n")) {
  "`n"
} elseif ($text.Contains("`r")) {
  "`r"
} else {
  [Environment]::NewLine
}
$hasTrailingNewline = $text.EndsWith("`r`n") -or $text.EndsWith("`n") -or $text.EndsWith("`r")

$lines = [System.Text.RegularExpressions.Regex]::Split($text, "`r`n|`n|`r")
$updated = $false

for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '^\s*service:\s*' -and $lines[$i] -notmatch 'http_status:') {
    $indent = ([regex]::Match($lines[$i], '^\s*')).Value
    $lines[$i] = "$indent" + "service: $serviceValue"
    $updated = $true
    break
  }
}

if (-not $updated) {
  Write-Host "ERROR: no editable service line found in config.yml"
  exit 12
}

$newText = [string]::Join($newline, $lines)
if ($hasTrailingNewline) {
  $newText += $newline
}

$newBytes = $encodingInfo.Encoding.GetBytes($newText)
if ($encodingInfo.HasBom) {
  $preamble = $encodingInfo.Encoding.GetPreamble()
  if ($preamble.Length -gt 0) {
    $buffer = New-Object byte[] ($preamble.Length + $newBytes.Length)
    [Array]::Copy($preamble, 0, $buffer, 0, $preamble.Length)
    [Array]::Copy($newBytes, 0, $buffer, $preamble.Length, $newBytes.Length)
    $newBytes = $buffer
  }
}
[System.IO.File]::WriteAllBytes($ConfigPath, $newBytes)

Write-Host "config.yml updated: service -> $serviceValue"
exit 0
