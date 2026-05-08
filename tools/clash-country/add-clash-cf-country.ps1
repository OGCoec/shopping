param(
    [string]$YamlPath = "",
    [string]$BinPath = "",
    [string]$Ip2LocationJar = "$env:USERPROFILE\.m2\repository\com\ip2location\ip2location-java\8.13.0\ip2location-java-8.13.0.jar",
    [string]$NodePrefix = "",
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

if ([string]::IsNullOrWhiteSpace($BinPath)) {
    $BinPath = Read-Host "Enter IP2Location BIN file path"
}

$BinPath = $BinPath.Trim().Trim([char[]]@([char]34, [char]39))
$BinPath = [Environment]::ExpandEnvironmentVariables($BinPath)
if ([string]::IsNullOrWhiteSpace($BinPath)) {
    throw "IP2Location BIN path is required."
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

function Ensure-LookupClass {
    param(
        [string]$WorkDir,
        [string]$JarPath
    )

    New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

    $javaFile = Join-Path $WorkDir "Ip2LocationCountryLookup.java"

    $source = @'
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Ip2LocationCountryLookup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Ip2LocationCountryLookup <binPath>");
        }

        IP2Location ip2Location = new IP2Location();
        ip2Location.Open(args[0], true);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String ip;
            while ((ip = reader.readLine()) != null) {
                ip = ip.trim();
                if (ip.isEmpty()) {
                    continue;
                }

                try {
                    IPResult result = ip2Location.IPQuery(ip);
                    String status = result == null ? "" : safe(result.getStatus());
                    String countryShort = result == null ? "" : safe(result.getCountryShort());
                    String countryLong = result == null ? "" : safe(result.getCountryLong());
                    System.out.println(ip + "\t" + status + "\t" + countryShort + "\t" + countryLong);
                } catch (Exception e) {
                    System.out.println(ip + "\tERROR\t\t" + e.getClass().getSimpleName());
                }
            }
        } finally {
            ip2Location.Close();
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').trim();
    }
}
'@

    [System.IO.File]::WriteAllText($javaFile, $source, [System.Text.Encoding]::ASCII)
    & javac -encoding UTF-8 -cp $JarPath -d $WorkDir $javaFile
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed with exit code $LASTEXITCODE"
    }
}

Assert-FileExists -Path $YamlPath -Label "YAML"
Assert-FileExists -Path $BinPath -Label "IP2Location BIN"
Assert-FileExists -Path $Ip2LocationJar -Label "ip2location-java jar"

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

$servers = $nodes | Select-Object -ExpandProperty Server -Unique
$workDir = Join-Path $PSScriptRoot ".tmp-ip2location-country-lookup"
Ensure-LookupClass -WorkDir $workDir -JarPath $Ip2LocationJar

$lookupInput = $servers -join [Environment]::NewLine
$lookupOutput = $lookupInput | & java -cp "$workDir;$Ip2LocationJar" Ip2LocationCountryLookup $BinPath
if ($LASTEXITCODE -ne 0) {
    throw "java lookup failed with exit code $LASTEXITCODE"
}

$countryByServer = @{}
$countryLongByServer = @{}
foreach ($line in $lookupOutput) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    $parts = $line -split "`t", 4
    if ($parts.Count -lt 3) {
        continue
    }

    $ip = $parts[0].Trim()
    $status = $parts[1].Trim()
    $countryCode = $parts[2].Trim().ToUpperInvariant()
    $countryLong = if ($parts.Count -ge 4) { $parts[3].Trim() } else { "" }

    if ($status -eq "OK" -and $countryCode -match "^[A-Z]{2}$") {
        $countryByServer[$ip] = $countryCode
        $countryLongByServer[$ip] = $countryLong
    }
}

$nodeRows = New-Object System.Collections.Generic.List[object]
$nodeByReference = @{}
$usedNodeNames = @{}
foreach ($node in $nodes) {
    if (-not $countryByServer.ContainsKey($node.Server)) {
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

    $countryCode = $countryByServer[$node.Server]
    $countryName = $countryLongByServer[$node.Server]
    $newName = New-UniqueName -Name (New-NodeName -BaseName $node.BaseName -CountryCode $countryCode) -UsedNames $usedNodeNames
    $row = [pscustomobject]@{
        Success = $true
        Action = "UPDATE"
        OriginalOrder = $node.OriginalOrder
        LineIndex = $node.LineIndex
        OldName = $node.Name
        NewName = $newName
        BaseName = $node.BaseName
        Server = $node.Server
        Country = $countryCode
        CountryCode = $countryCode
        CountryName = $countryName
        SortKey = Get-CountrySortKey -CountryName $countryName -CountryCode $countryCode
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
Write-Host ("Resolved countries: {0}" -f $countryByServer.Count)
Write-Host ("Nodes to update: {0}" -f $updateCount)
Write-Host ("Preview rows: {0}" -f $previewRows.Count)
Write-Host "Source note: country comes from the YAML server IP in the local IP2Location BIN."
Write-Host "Cloudflare anycast or proxy exit IP checks may show a different live location."
Write-Host "Ordering: countries are grouped by measured country name A-Z; nodes inside the same country keep original order."
Write-Host ""

$index = 1
foreach ($previewRow in $previewRows) {
    Write-Host ("{0,3}. [{1}] {2} -> {3} | server={4} | country={5} | name={6}" -f
        $index,
        $previewRow.Action,
        $previewRow.OldName,
        $previewRow.NewName,
        $previewRow.Server,
        $previewRow.Country,
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
