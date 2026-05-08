param(
    [string]$YamlPath = "",
    [string]$ApiUrl = "https://api.iping.cc/v1/query",
    [string]$Language = "en",
    [string]$NodePrefix = "",
    [string]$ExcludedTypes = "socks5,socks,http",
    [string]$NameMode = "Prefix",
    [bool]$AddFlag = $true,
    [int]$DelayMs = 1200,
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = $utf8NoBom
try {
    [Console]::InputEncoding = $utf8NoBom
    [Console]::OutputEncoding = $utf8NoBom
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
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

function Read-PropertyValue {
    param(
        [object]$Object,
        [string[]]$Names
    )

    if ($null -eq $Object -or $null -eq $Names) {
        return $null
    }

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) {
            return [string]$property.Value
        }
    }
    return $null
}

function New-CountryNameMap {
    $map = @{}
    $cultures = [System.Globalization.CultureInfo]::GetCultures([System.Globalization.CultureTypes]::SpecificCultures)
    foreach ($culture in $cultures) {
        try {
            $region = New-Object System.Globalization.RegionInfo($culture.Name)
            $code = $region.TwoLetterISORegionName.ToUpperInvariant()
            if ($code -match "^[A-Z]{2}$") {
                $map[$code.ToLowerInvariant()] = $code
                if (-not [string]::IsNullOrWhiteSpace($region.EnglishName)) {
                    $map[$region.EnglishName.Trim().ToLowerInvariant()] = $code
                }
                if (-not [string]::IsNullOrWhiteSpace($region.DisplayName)) {
                    $map[$region.DisplayName.Trim().ToLowerInvariant()] = $code
                }
            }
        } catch {
        }
    }

    $aliases = @{
        "united states of america" = "US"
        "united states" = "US"
        "usa" = "US"
        "u.s." = "US"
        "america" = "US"
        "mainland china" = "CN"
        "china" = "CN"
        "hong kong" = "HK"
        "hong kong sar" = "HK"
        "hong kong, china" = "HK"
        "macao" = "MO"
        "macau" = "MO"
        "taiwan" = "TW"
        "taiwan, province of china" = "TW"
        "south korea" = "KR"
        "korea, republic of" = "KR"
        "north korea" = "KP"
        "russia" = "RU"
        "vietnam" = "VN"
        "viet nam" = "VN"
        "laos" = "LA"
        "iran" = "IR"
        "syria" = "SY"
        "moldova" = "MD"
        "tanzania" = "TZ"
        "bolivia" = "BO"
        "venezuela" = "VE"
    }
    foreach ($key in $aliases.Keys) {
        $map[$key] = $aliases[$key]
    }
    return $map
}

function Normalize-CountryCode {
    param(
        [string]$CountryCode,
        [string]$CountryName,
        [hashtable]$CountryNameMap
    )

    foreach ($candidate in @($CountryCode, $CountryName)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        $trimmed = $candidate.Trim()
        if ($trimmed -match "^[A-Za-z]{2}$") {
            return $trimmed.ToUpperInvariant()
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($CountryName)) {
        $key = $CountryName.Trim().ToLowerInvariant()
        if ($CountryNameMap.ContainsKey($key)) {
            return $CountryNameMap[$key]
        }
    }

    return $null
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

function Get-NodeBaseName {
    param([string]$Name)

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return ""
    }

    $baseName = $Name.Trim()
    $baseName = [regex]::Replace($baseName, "^[\p{So}\uFE0F]+\s*[A-Za-z]{2}\s+", "").Trim()
    $baseName = [regex]::Replace($baseName, "^[A-Z]{2}\s+", "").Trim()
    return $baseName
}

function Test-TargetNodeName {
    param(
        [string]$Name,
        [string]$NodePrefix
    )

    if ([string]::IsNullOrWhiteSpace($NodePrefix)) {
        return $true
    }
    return -not [string]::IsNullOrWhiteSpace($Name) -and $Name.Contains($NodePrefix)
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
        [string]$CountryName,
        [string]$CountryCode
    )

    if (-not [string]::IsNullOrWhiteSpace($CountryName)) {
        return $CountryName.Trim().ToUpperInvariant()
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

    $name = if ($NodeRow.Success) { $NodeRow.NewName } else { $NodeRow.OldName }
    return "$($NodeRow.Before)$name$($NodeRow.Mid)$($NodeRow.Server)$($NodeRow.Rest)"
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
                $name = if ($source.Success) { $source.NewName } else { $source.OldName }
                $UpdatedLines[$target.LineIndex] = "$($target.Indent)$name"
            }
            continue
        }

        $i++
    }
}

function Build-IpingUri {
    param([string]$Ip)

    $separator = if ($ApiUrl.Contains("?")) { "&" } else { "?" }
    return $ApiUrl + $separator + "ip=" + [Uri]::EscapeDataString($Ip) + "&language=" + [Uri]::EscapeDataString($Language)
}

function Invoke-IpingLookup {
    param(
        [string]$Ip,
        [hashtable]$CountryNameMap
    )

    if ([string]::IsNullOrWhiteSpace($Ip)) {
        return [pscustomobject]@{ Success = $false; Reason = "invalid_ip"; Country = ""; CountryName = ""; Region = ""; City = "" }
    }
    if ($Ip.Contains(":")) {
        return [pscustomobject]@{ Success = $false; Reason = "ipv6_not_supported"; Country = ""; CountryName = ""; Region = ""; City = "" }
    }

    try {
        $uri = Build-IpingUri $Ip.Trim()
        $response = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 15
        $codeText = Read-PropertyValue $response @("code")
        if (-not [string]::IsNullOrWhiteSpace($codeText) -and [int]$codeText -ne 200) {
            return [pscustomobject]@{ Success = $false; Reason = "business_code_$codeText"; Country = ""; CountryName = ""; Region = ""; City = "" }
        }

        $payload = $response
        $dataProperty = $response.PSObject.Properties["data"]
        if ($null -ne $dataProperty -and $null -ne $dataProperty.Value) {
            $payload = $dataProperty.Value
        }

        $countryName = Read-PropertyValue $payload @("country", "country_name", "countryName")
        $countryCode = Read-PropertyValue $payload @("country_code", "countryCode")
        $resolvedCountryCode = Normalize-CountryCode -CountryCode $countryCode -CountryName $countryName -CountryNameMap $CountryNameMap
        if ([string]::IsNullOrWhiteSpace($resolvedCountryCode)) {
            return [pscustomobject]@{ Success = $false; Reason = "country_not_found"; Country = ""; CountryName = $countryName; Region = ""; City = "" }
        }

        return [pscustomobject]@{
            Success = $true
            Reason = "ok"
            Country = $resolvedCountryCode
            CountryName = $countryName
            Region = Read-PropertyValue $payload @("region", "province", "state", "region_name", "regionName")
            City = Read-PropertyValue $payload @("city", "city_name", "cityName")
        }
    } catch {
        return [pscustomobject]@{ Success = $false; Reason = "http_error: " + $_.Exception.Message; Country = ""; CountryName = ""; Region = ""; City = "" }
    }
}

Assert-FileExists -Path $YamlPath -Label "YAML"

$lines = Get-Content -LiteralPath $YamlPath -Encoding UTF8
$proxyPattern = "^(?<before>\s*-\s*\{\s*name:\s*)(?<name>[^,{}]+)(?<mid>\s*,\s*server:\s*)(?<server>[^,\s}]+)(?<rest>.*)$"
$excludedTypeSet = New-ExcludedTypeSet $ExcludedTypes

$nodes = New-Object System.Collections.Generic.List[object]
$skippedExcludedTypeCount = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $proxyType = Get-InlineProxyType $line
    if (-not [string]::IsNullOrWhiteSpace($proxyType) -and $excludedTypeSet.ContainsKey($proxyType)) {
        $skippedExcludedTypeCount++
        continue
    }

    $match = [regex]::Match($line, $proxyPattern)
    if (-not $match.Success) {
        continue
    }

    $name = $match.Groups["name"].Value.Trim()
    if (-not (Test-TargetNodeName -Name $name -NodePrefix $NodePrefix)) {
        continue
    }

    $nodes.Add([pscustomobject]@{
        LineIndex = $i
        OriginalOrder = $nodes.Count
        Name = $name
        BaseName = Get-NodeBaseName $name
        Server = $match.Groups["server"].Value.Trim()
        Before = $match.Groups["before"].Value
        Mid = $match.Groups["mid"].Value
        Rest = $match.Groups["rest"].Value
    })
}

if ($nodes.Count -eq 0) {
    Write-Host "No proxy nodes matched."
    exit 0
}

$countryNameMap = New-CountryNameMap
$lookupByServer = @{}
$servers = $nodes | Select-Object -ExpandProperty Server -Unique
$serverIndex = 1
foreach ($server in $servers) {
    Write-Host ("Querying {0}/{1}: {2}" -f $serverIndex, $servers.Count, $server)
    $lookup = Invoke-IpingLookup -Ip $server -CountryNameMap $countryNameMap
    $lookupByServer[$server] = $lookup
    if ($lookup.Success) {
        Write-Host ("Result {0}/{1}: server={2} | country={3} | name={4} | region={5} | city={6} | reason={7}" -f
            $serverIndex,
            $servers.Count,
            $server,
            $lookup.Country,
            $lookup.CountryName,
            $lookup.Region,
            $lookup.City,
            $lookup.Reason)
    } else {
        Write-Host ("Result {0}/{1}: server={2} | failed | reason={3}" -f
            $serverIndex,
            $servers.Count,
            $server,
            $lookup.Reason)
    }
    if ($DelayMs -gt 0 -and $serverIndex -lt $servers.Count) {
        Start-Sleep -Milliseconds $DelayMs
    }
    $serverIndex++
}

$nodeRows = New-Object System.Collections.Generic.List[object]
$nodeByReference = @{}
$usedNodeNames = @{}
foreach ($node in $nodes) {
    $lookup = $lookupByServer[$node.Server]
    if ($null -eq $lookup -or -not $lookup.Success) {
        $row = [pscustomobject]@{
            Success = $false
            Action = "MISS"
            OriginalOrder = $node.OriginalOrder
            LineIndex = $node.LineIndex
            OldName = $node.Name
            NewName = $node.Name
            BaseName = $node.BaseName
            Server = $node.Server
            Country = ""
            CountryCode = ""
            CountryName = ""
            Region = ""
            City = ""
            Reason = if ($null -eq $lookup) { "missing_lookup" } else { $lookup.Reason }
            SortKey = "ZZZ"
            SortMissing = 1
            Before = $node.Before
            Mid = $node.Mid
            Rest = $node.Rest
        }
        $nodeRows.Add($row)
        $usedNodeNames[$node.Name] = $true
        $nodeByReference[$node.Name] = $row
        $nodeByReference[$node.BaseName] = $row
        continue
    }

    $newName = New-UniqueName -Name (New-NodeName -BaseName $node.BaseName -CountryCode $lookup.Country) -UsedNames $usedNodeNames
    $row = [pscustomobject]@{
        Success = $true
        Action = "UPDATE"
        OriginalOrder = $node.OriginalOrder
        LineIndex = $node.LineIndex
        OldName = $node.Name
        NewName = $newName
        BaseName = $node.BaseName
        Server = $node.Server
        Country = $lookup.Country
        CountryCode = $lookup.Country
        CountryName = $lookup.CountryName
        Region = $lookup.Region
        City = $lookup.City
        Reason = $lookup.Reason
        SortKey = Get-CountrySortKey -CountryName $lookup.CountryName -CountryCode $lookup.Country
        SortMissing = 0
        Before = $node.Before
        Mid = $node.Mid
        Rest = $node.Rest
    }
    $nodeRows.Add($row)
    $nodeByReference[$node.Name] = $row
    $nodeByReference[$node.BaseName] = $row
    $nodeByReference[$newName] = $row
}

$previewRows = Sort-CfNodeRows $nodeRows
$updateCount = @($nodeRows | Where-Object { $_.Success }).Count

Write-Host ("Matched proxy nodes: {0}" -f $nodes.Count)
Write-Host ("Skipped excluded proxy types: {0}" -f $skippedExcludedTypeCount)
Write-Host ("Queried unique servers: {0}" -f $servers.Count)
Write-Host ("Nodes to update: {0}" -f $updateCount)
Write-Host ("Preview rows: {0}" -f $previewRows.Count)
Write-Host "Source note: country comes from iPing API for the YAML server IP."
Write-Host "This is more current than the local BIN, but it is still not a per-node live proxy exit test."
Write-Host "Ordering: countries are grouped by measured country name A-Z; nodes inside the same country keep original order."
Write-Host ""

$index = 1
foreach ($previewRow in $previewRows) {
    Write-Host ("{0,3}. [{1}] {2} -> {3} | server={4} | country={5} | name={6} | region={7} | city={8} | reason={9}" -f
        $index,
        $previewRow.Action,
        $previewRow.OldName,
        $previewRow.NewName,
        $previewRow.Server,
        $previewRow.Country,
        $previewRow.CountryName,
        $previewRow.Region,
        $previewRow.City,
        $previewRow.Reason)
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
