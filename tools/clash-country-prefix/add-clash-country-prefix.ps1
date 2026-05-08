param(
    [string]$YamlPath = "",
    [string]$ExcludedTypes = "socks5,socks,http",
    [string]$NameMode = "Prefix",
    [bool]$AddFlag = $true,
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = $utf8NoBom
try {
    [Console]::InputEncoding = $utf8NoBom
    [Console]::OutputEncoding = $utf8NoBom
} catch {
}

if ($NameMode -ne "Prefix" -and $NameMode -ne "Suffix") {
    throw "NameMode must be Prefix or Suffix."
}

if ([string]::IsNullOrWhiteSpace($YamlPath)) {
    $YamlPath = Read-Host "Enter Clash YAML file path"
}

$YamlPath = $YamlPath.Trim().Trim([char[]]@([char]34, [char]39))
$YamlPath = [Environment]::ExpandEnvironmentVariables($YamlPath)
if ([string]::IsNullOrWhiteSpace($YamlPath)) {
    throw "YAML path is required."
}

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label not found: $Path"
    }
}

function New-CountryCodeAliasMap {
    return @{
        "UK" = "GB"
    }
}

function New-CountryNameMap {
    $map = @{}
    $cultures = [System.Globalization.CultureInfo]::GetCultures([System.Globalization.CultureTypes]::SpecificCultures)
    foreach ($culture in $cultures) {
        try {
            $region = New-Object System.Globalization.RegionInfo($culture.Name)
            $code = $region.TwoLetterISORegionName.ToUpperInvariant()
            if ($code -match "^[A-Z]{2}$" -and -not $map.ContainsKey($code)) {
                $map[$code] = $region.EnglishName
            }
        } catch {
        }
    }

    $fallback = @{
        "HK" = "Hong Kong"
        "MO" = "Macao"
        "TW" = "Taiwan"
    }
    foreach ($key in $fallback.Keys) {
        if (-not $map.ContainsKey($key)) {
            $map[$key] = $fallback[$key]
        }
    }

    return $map
}

function Normalize-CountryCode {
    param(
        [string]$CountryCode,
        [hashtable]$AliasMap
    )

    if ([string]::IsNullOrWhiteSpace($CountryCode)) {
        return ""
    }

    $code = $CountryCode.Trim().ToUpperInvariant()
    if ($code -notmatch "^[A-Z]{2}$") {
        return ""
    }

    if ($AliasMap.ContainsKey($code)) {
        return $AliasMap[$code]
    }

    return $code
}

function Get-CountryDisplayName {
    param(
        [string]$CountryCode,
        [hashtable]$CountryNameMap
    )

    if ([string]::IsNullOrWhiteSpace($CountryCode)) {
        return ""
    }

    $code = $CountryCode.Trim().ToUpperInvariant()
    if ($CountryNameMap.ContainsKey($code)) {
        return $CountryNameMap[$code]
    }

    return $code
}

function Get-CountryFlag {
    param([string]$CountryCode)

    if ([string]::IsNullOrWhiteSpace($CountryCode) -or $CountryCode.Length -ne 2) {
        return ""
    }

    $upper = $CountryCode.ToUpperInvariant()
    if ($upper -notmatch "^[A-Z]{2}$") {
        return ""
    }

    $first = [char]::ConvertFromUtf32(0x1F1E6 + ([int][char]$upper[0] - [int][char]'A'))
    $second = [char]::ConvertFromUtf32(0x1F1E6 + ([int][char]$upper[1] - [int][char]'A'))
    return "$first$second"
}

function Get-NodePrefixCountryCode {
    param(
        [string]$Name,
        [hashtable]$AliasMap
    )

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return ""
    }

    $trimmed = $Name.Trim()
    $trimmed = [regex]::Replace($trimmed, "^[\p{So}\uFE0F]+\s*", "").Trim()
    $match = [regex]::Match($trimmed, "^(?<code>[A-Za-z]{2})(?=\s|$)")
    if (-not $match.Success) {
        return ""
    }

    return Normalize-CountryCode -CountryCode $match.Groups["code"].Value -AliasMap $AliasMap
}

function Get-NodeBaseName {
    param(
        [string]$Name,
        [string]$CountryCode = ""
    )

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return ""
    }

    $baseName = $Name.Trim()
    $baseName = [regex]::Replace($baseName, "^[\p{So}\uFE0F]+\s*", "").Trim()

    if (-not [string]::IsNullOrWhiteSpace($CountryCode)) {
        $escapedCode = [regex]::Escape($CountryCode.Trim().ToUpperInvariant())
        $baseName = [regex]::Replace($baseName, "^(?i:$escapedCode)(?=\s|$)\s*", "").Trim()
    } else {
        $baseName = [regex]::Replace($baseName, "^[A-Za-z]{2}(?=\s|$)\s*", "").Trim()
    }

    return $baseName
}

function New-NodeName {
    param(
        [string]$BaseName,
        [string]$CountryCode
    )

    $code = $CountryCode.ToUpperInvariant()
    $label = $code
    if ($AddFlag) {
        $flag = Get-CountryFlag $code
        if (-not [string]::IsNullOrWhiteSpace($flag)) {
            $label = "$flag $code"
        }
    }

    if ([string]::IsNullOrWhiteSpace($BaseName)) {
        return $label
    }

    if ($NameMode -eq "Suffix") {
        return "$BaseName $label"
    }
    return "$label $BaseName"
}

function New-UniqueName {
    param(
        [string]$Name,
        [hashtable]$UsedNames
    )

    $baseName = $Name.Trim()
    $candidate = $baseName
    $index = 2
    while ($UsedNames.ContainsKey($candidate)) {
        $candidate = "$baseName $index"
        $index++
    }
    $UsedNames[$candidate] = $true
    return $candidate
}

function Get-InlineProxyType {
    param([string]$Line)

    $match = [regex]::Match($Line, "(?:^|,\s*)type:\s*(?<type>[^,\s}]+)")
    if ($match.Success) {
        return $match.Groups["type"].Value.Trim().ToLowerInvariant()
    }
    return ""
}

function New-ExcludedTypeSet {
    param([string]$RawTypes)

    $set = @{}
    if ([string]::IsNullOrWhiteSpace($RawTypes)) {
        return $set
    }

    foreach ($item in ($RawTypes -split ",")) {
        $type = $item.Trim().ToLowerInvariant()
        if (-not [string]::IsNullOrWhiteSpace($type)) {
            $set[$type] = $true
        }
    }
    return $set
}

function Get-CountrySortKey {
    param(
        [string]$CountryCode,
        [hashtable]$CountryNameMap
    )

    $countryName = Get-CountryDisplayName -CountryCode $CountryCode -CountryNameMap $CountryNameMap
    if (-not [string]::IsNullOrWhiteSpace($countryName)) {
        return $countryName.Trim().ToUpperInvariant()
    }
    if (-not [string]::IsNullOrWhiteSpace($CountryCode)) {
        return $CountryCode.Trim().ToUpperInvariant()
    }
    return "ZZZ"
}

function Sort-CfNodeRows {
    param([object[]]$Rows)

    return @($Rows | Sort-Object `
        @{ Expression = { $_.SortMissing }; Ascending = $true }, `
        @{ Expression = { $_.SortKey }; Ascending = $true }, `
        @{ Expression = { $_.CountryCode }; Ascending = $true }, `
        @{ Expression = { $_.OriginalOrder }; Ascending = $true })
}

function Resolve-CfNodeRow {
    param(
        [string]$EntryName,
        [hashtable]$NodeByReference
    )

    if ([string]::IsNullOrWhiteSpace($EntryName) -or $EntryName.Trim().StartsWith("{")) {
        return $null
    }

    $trimmed = $EntryName.Trim()
    if ($NodeByReference.ContainsKey($trimmed)) {
        return $NodeByReference[$trimmed]
    }

    $baseName = Get-NodeBaseName $trimmed
    if ($NodeByReference.ContainsKey($baseName)) {
        return $NodeByReference[$baseName]
    }

    return $null
}

function New-CfProxyLine {
    param([object]$NodeRow)

    return "$($NodeRow.Before)$($NodeRow.NewName)$($NodeRow.After)"
}

function Set-CfProxyNodeOrder {
    param(
        [string[]]$UpdatedLines,
        [object[]]$Nodes,
        [object[]]$NodeRows
    )

    $targetNodes = @($Nodes | Sort-Object @{ Expression = { $_.LineIndex }; Ascending = $true })
    $sourceRows = Sort-CfNodeRows $NodeRows
    for ($i = 0; $i -lt $targetNodes.Count -and $i -lt $sourceRows.Count; $i++) {
        $UpdatedLines[$targetNodes[$i].LineIndex] = New-CfProxyLine $sourceRows[$i]
    }
}

function Set-CfGroupReferenceOrder {
    param(
        [string[]]$UpdatedLines,
        [hashtable]$NodeByReference
    )

    $groupEntryPattern = "^(?<indent>\s*-\s*)(?<name>.+?)\s*$"
    $i = 0
    while ($i -lt $UpdatedLines.Count) {
        $run = New-Object System.Collections.Generic.List[object]
        while ($i -lt $UpdatedLines.Count) {
            $match = [regex]::Match($UpdatedLines[$i], $groupEntryPattern)
            if (-not $match.Success) {
                break
            }

            $entryName = $match.Groups["name"].Value.Trim()
            $nodeRow = Resolve-CfNodeRow -EntryName $entryName -NodeByReference $NodeByReference
            if ($null -eq $nodeRow) {
                break
            }

            $run.Add([pscustomobject]@{
                LineIndex = $i
                Indent = $match.Groups["indent"].Value
                NodeRow = $nodeRow
            })
            $i++
        }

        if ($run.Count -gt 0) {
            $sortedRun = @($run | Sort-Object `
                @{ Expression = { $_.NodeRow.SortMissing }; Ascending = $true }, `
                @{ Expression = { $_.NodeRow.SortKey }; Ascending = $true }, `
                @{ Expression = { $_.NodeRow.CountryCode }; Ascending = $true }, `
                @{ Expression = { $_.NodeRow.OriginalOrder }; Ascending = $true })

            for ($j = 0; $j -lt $run.Count; $j++) {
                $target = $run[$j]
                $source = $sortedRun[$j].NodeRow
                $UpdatedLines[$target.LineIndex] = "$($target.Indent)$($source.NewName)"
            }
            continue
        }

        $i++
    }
}

Assert-FileExists -Path $YamlPath -Label "YAML"

$lines = Get-Content -LiteralPath $YamlPath -Encoding UTF8
$proxyPattern = "^(?<before>\s*-\s*\{\s*name\s*:\s*)(?<name>[^,{}]+)(?<after>.*)$"
$excludedTypeSet = New-ExcludedTypeSet $ExcludedTypes
$countryCodeAliasMap = New-CountryCodeAliasMap
$countryNameMap = New-CountryNameMap

$nodes = New-Object System.Collections.Generic.List[object]
$reservedNames = @{}
$skippedExcludedTypeCount = 0
$skippedNoCountryCodeCount = 0

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $match = [regex]::Match($line, $proxyPattern)
    if (-not $match.Success) {
        continue
    }

    $name = $match.Groups["name"].Value.Trim()
    $proxyType = Get-InlineProxyType $line
    if (-not [string]::IsNullOrWhiteSpace($proxyType) -and $excludedTypeSet.ContainsKey($proxyType)) {
        $reservedNames[$name] = $true
        $skippedExcludedTypeCount++
        continue
    }

    $countryCode = Get-NodePrefixCountryCode -Name $name -AliasMap $countryCodeAliasMap
    if ([string]::IsNullOrWhiteSpace($countryCode)) {
        $reservedNames[$name] = $true
        $skippedNoCountryCodeCount++
        continue
    }

    $nodes.Add([pscustomobject]@{
        LineIndex = $i
        OriginalOrder = $nodes.Count
        Name = $name
        BaseName = Get-NodeBaseName -Name $name -CountryCode $countryCode
        CountryCode = $countryCode
        CountryName = Get-CountryDisplayName -CountryCode $countryCode -CountryNameMap $countryNameMap
        Before = $match.Groups["before"].Value
        After = $match.Groups["after"].Value
    })
}

if ($nodes.Count -eq 0) {
    Write-Host "No proxy nodes with a leading country code matched."
    exit 0
}

$nodeRows = New-Object System.Collections.Generic.List[object]
$nodeByReference = @{}
$usedNodeNames = @{}
foreach ($reservedName in $reservedNames.Keys) {
    $usedNodeNames[$reservedName] = $true
}

foreach ($node in $nodes) {
    $newName = New-UniqueName -Name (New-NodeName -BaseName $node.BaseName -CountryCode $node.CountryCode) -UsedNames $usedNodeNames
    $action = if ($node.Name -ceq $newName) { "KEEP" } else { "UPDATE" }
    $row = [pscustomobject]@{
        Success = $true
        Action = $action
        OriginalOrder = $node.OriginalOrder
        LineIndex = $node.LineIndex
        OldName = $node.Name
        NewName = $newName
        BaseName = $node.BaseName
        CountryCode = $node.CountryCode
        CountryName = $node.CountryName
        SortKey = Get-CountrySortKey -CountryCode $node.CountryCode -CountryNameMap $countryNameMap
        SortMissing = 0
        Before = $node.Before
        After = $node.After
    }
    $nodeRows.Add($row)
    $nodeByReference[$node.Name] = $row
    $nodeByReference[$node.BaseName] = $row
    $nodeByReference[$newName] = $row
}

$previewRows = Sort-CfNodeRows $nodeRows
$renamedCount = @($nodeRows | Where-Object { $_.OldName -cne $_.NewName }).Count

Write-Host ("Matched proxy nodes: {0}" -f $nodes.Count)
Write-Host ("Skipped excluded proxy types: {0}" -f $skippedExcludedTypeCount)
Write-Host ("Skipped without leading country code: {0}" -f $skippedNoCountryCodeCount)
Write-Host ("Renamed nodes: {0}" -f $renamedCount)
Write-Host ("Preview rows: {0}" -f $previewRows.Count)
Write-Host "Source note: country comes only from the node name prefix in this YAML."
Write-Host "Ordering: countries are grouped by country name A-Z; nodes inside the same country keep original order."
Write-Host ""

$index = 1
foreach ($previewRow in $previewRows) {
    Write-Host ("{0,3}. [{1}] {2} -> {3} | country={4} | name={5}" -f
        $index,
        $previewRow.Action,
        $previewRow.OldName,
        $previewRow.NewName,
        $previewRow.CountryCode,
        $previewRow.CountryName)
    $index++
}

if (-not $Apply) {
    Write-Host "Dry run only. Re-run with -Apply to write the YAML."
    exit 0
}

$updatedLines = [string[]]$lines.Clone()
Set-CfProxyNodeOrder -UpdatedLines $updatedLines -Nodes $nodes -NodeRows $nodeRows
Set-CfGroupReferenceOrder -UpdatedLines $updatedLines -NodeByReference $nodeByReference

$backupPath = "$YamlPath.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $YamlPath -Destination $backupPath
[System.IO.File]::WriteAllLines($YamlPath, $updatedLines, $utf8NoBom)
Write-Host "Updated YAML: $YamlPath"
Write-Host "Backup created: $backupPath"
