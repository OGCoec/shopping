param(
  [string]$StaticRoot = "shopping-web/src/main/resources/static"
)

$ErrorActionPreference = "Stop"

$criticalPatterns = @(
  @{ Name = "eval"; Pattern = "eval\s*\(" },
  @{ Name = "new Function"; Pattern = "new\s+Function\b" },
  @{ Name = "document.write"; Pattern = "document\.write\s*\(" },
  @{ Name = "outerHTML"; Pattern = "outerHTML\s*=" },
  @{ Name = "insertAdjacentHTML"; Pattern = "insertAdjacentHTML\s*\(" },
  @{ Name = "javascript URL"; Pattern = "javascript:" },
  @{ Name = "HTML data URL"; Pattern = "data:text/html" }
)

$reviewPatterns = @(
  @{ Name = "innerHTML"; Pattern = "innerHTML" },
  @{ Name = "window.location.assign"; Pattern = "window\.location\.assign\s*\(" },
  @{ Name = "window.location.replace"; Pattern = "window\.location\.replace\s*\(" }
)

function Should-SkipCriticalMatch {
  param(
    [string]$Path,
    [string]$Name
  )
  $normalized = $Path -replace "\\", "/"
  return $Name -eq "javascript URL" -and $normalized.EndsWith("/js/security/security-urls.js")
}

$files = Get-ChildItem -Path $StaticRoot -Recurse -File -Include *.js,*.html |
  Where-Object {
    $normalized = $_.FullName -replace "\\", "/"
    $normalized -notmatch "/vendor/" -and
      $_.Name -notlike "*.bak" -and
      $_.Name -notlike "*.min.js"
  }

$criticalMatches = New-Object System.Collections.Generic.List[object]
$reviewMatches = New-Object System.Collections.Generic.List[object]

foreach ($file in $files) {
  $content = Get-Content -LiteralPath $file.FullName
  for ($lineIndex = 0; $lineIndex -lt $content.Count; $lineIndex += 1) {
    $line = $content[$lineIndex]
    foreach ($rule in $criticalPatterns) {
      if ($line -match $rule.Pattern -and -not (Should-SkipCriticalMatch -Path $file.FullName -Name $rule.Name)) {
        $criticalMatches.Add([pscustomobject]@{
          Rule = $rule.Name
          Path = $file.FullName
          Line = $lineIndex + 1
          Text = $line.Trim()
        })
      }
    }
    foreach ($rule in $reviewPatterns) {
      if ($line -match $rule.Pattern) {
        $reviewMatches.Add([pscustomobject]@{
          Rule = $rule.Name
          Path = $file.FullName
          Line = $lineIndex + 1
          Text = $line.Trim()
        })
      }
    }
  }
}

if ($criticalMatches.Count -gt 0) {
  Write-Host "XSS static scan failed. Critical matches:"
  $criticalMatches | Format-Table -AutoSize
  exit 1
}

Write-Host "XSS static scan passed: no critical matches."
if ($reviewMatches.Count -gt 0) {
  Write-Host "Review-only matches:"
  $reviewMatches | Format-Table -AutoSize
}
