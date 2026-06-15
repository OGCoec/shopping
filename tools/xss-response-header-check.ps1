[CmdletBinding()]
param(
    [string] $BaseUrl = "https://localhost:6655",
    [string] $SpuId = "1",
    [string] $AdminCookie
)

$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSEdition -ne "Core") {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
}

function Invoke-CurlHeaderRequest {
    param([string] $Url)
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) {
        throw "Invoke-WebRequest failed and curl.exe is not available for fallback."
    }
    $arguments = @("--noproxy", "*", "-k", "-sS", "-D", "-", "-o", "NUL")
    if ($AdminCookie) {
        $arguments += @("-H", "Cookie: $AdminCookie")
    }
    $arguments += $Url

    $output = & $curl.Source @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl.exe header request failed with exit code $LASTEXITCODE for $Url"
    }

    $headers = @{}
    foreach ($line in $output) {
        if ($line -match "^HTTP/") {
            $headers = @{}
            continue
        }
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split ":", 2
        if ($parts.Count -ne 2) {
            continue
        }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($headers.ContainsKey($name)) {
            $headers[$name] = "$($headers[$name]) $value"
        } else {
            $headers[$name] = $value
        }
    }

    return [pscustomobject]@{
        Headers = $headers
    }
}

function Invoke-HeaderRequest {
    param([string] $Url)
    $headers = @{}
    if ($AdminCookie) {
        $headers["Cookie"] = $AdminCookie
    }
    $parameters = @{
        Uri = $Url
        Method = "GET"
        Headers = $headers
        MaximumRedirection = 0
        ErrorAction = "Stop"
    }
    if ((Get-Command Invoke-WebRequest).Parameters.ContainsKey("SkipCertificateCheck")) {
        $parameters["SkipCertificateCheck"] = $true
    }
    try {
        return Invoke-WebRequest @parameters
    } catch {
        if ($_.Exception.Response) {
            return $_.Exception.Response
        }
        return Invoke-CurlHeaderRequest -Url $Url
    }
}

function Get-HeaderValue {
    param(
        [object] $Response,
        [string] $Name
    )
    if ($Response.Headers -is [System.Net.WebHeaderCollection]) {
        return $Response.Headers[$Name]
    }
    $value = $Response.Headers[$Name]
    if ($value -is [array]) {
        return ($value -join " ")
    }
    return [string] $value
}

$root = $BaseUrl.TrimEnd("/")
$paths = @(
    "/shopping/user/products/$SpuId",
    "/shopping/admin/login",
    "/shopping/admin"
)
$requiredTokens = @(
    "default-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "frame-ancestors 'none'"
)

foreach ($path in $paths) {
    $url = "$root$path"
    $response = Invoke-HeaderRequest -Url $url
    $policy = Get-HeaderValue -Response $response -Name "Content-Security-Policy-Report-Only"
    if (-not $policy) {
        throw "Missing Content-Security-Policy-Report-Only header for $url"
    }
    foreach ($token in $requiredTokens) {
        if ($policy -notlike "*$token*") {
            throw "CSP Report-Only header for $url does not contain required token: $token"
        }
    }
    Write-Host "PASS CSP Report-Only: $url"
}
