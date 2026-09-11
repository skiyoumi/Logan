<#
.SYNOPSIS
Build and push versioned Logan frontend/backend images.
.EXAMPLE
.\publish.ps1 1.5.0
.EXAMPLE
.\publish.ps1 1.5.0 -Service frontend -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^[A-Za-z0-9_][A-Za-z0-9_.-]{0,118}$')]
    [string]$Version,

    [ValidateSet('all', 'frontend', 'backend')]
    [string]$Service = 'all',

    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9.-]*(?::[0-9]+)?$')]
    [string]$Registry = 'crpi-1a0zvjxwrmr36txn.cn-hongkong.personal.cr.aliyuncs.com',

    [ValidatePattern('^[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*$')]
    [string]$Repository = 'skiyoumi/logan',

    [switch]$NoCache,
    [switch]$SkipBuild,
    [switch]$SkipPush,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Handle Docker's exit code explicitly on both Windows PowerShell 5.1 and PowerShell 7.
$PSNativeCommandUseErrorActionPreference = $false

function Invoke-Docker {
    param([string[]]$Arguments)
    $display = ($Arguments | ForEach-Object {
        if ($_ -match '\s') { '"' + $_ + '"' } else { $_ }
    }) -join ' '
    Write-Host "> docker $display"
    if ($DryRun) { return }
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker failed (exit $LASTEXITCODE): docker $display"
    }
}

try {
    if ($NoCache -and $SkipBuild) {
        throw '-NoCache cannot be combined with -SkipBuild.'
    }
    $composeFile = Join-Path $PSScriptRoot 'docker-compose.yaml'
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "Compose file not found: $composeFile"
    }
    if (-not $DryRun) {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw 'Docker is not installed or is not on PATH.'
        }
        Invoke-Docker -Arguments @('version', '--format', '{{.Server.Version}}')
        if (-not $SkipBuild) {
            Invoke-Docker -Arguments @('compose', 'version')
        }
    }

    $Service = $Service.ToLowerInvariant()
    $services = if ($Service -eq 'all') { @('backend', 'frontend') } else { @($Service) }
    $images = foreach ($name in $services) {
        [pscustomobject]@{
            Source = "logan-${name}:latest"
            Target = "${Registry}/${Repository}:${name}-${Version}"
        }
    }
    Write-Host "Logan $Version | services: $($services -join ', ')"

    if (-not $SkipBuild) {
        # A fixed project name keeps local image names independent of the calling directory.
        $buildArgs = @('compose', '--project-name', 'logan', '--project-directory', $PSScriptRoot,
            '--file', $composeFile, 'build')
        if ($NoCache) { $buildArgs += '--no-cache' }
        Invoke-Docker -Arguments ($buildArgs + $services)
    }

    # Verify all source images before tagging or publishing any of them.
    foreach ($image in $images) {
        Invoke-Docker -Arguments @('image', 'inspect', '--format', '{{.Id}}', $image.Source)
    }
    foreach ($image in $images) {
        Invoke-Docker -Arguments @('tag', $image.Source, $image.Target)
    }
    if (-not $SkipPush) {
        foreach ($image in $images) {
            Invoke-Docker -Arguments @('push', $image.Target)
        }
    }

    if ($DryRun) { Write-Host 'Preview complete. No Docker commands were executed.' }
    elseif ($SkipPush) { Write-Host 'Images are ready locally; push was skipped.' }
    else { Write-Host 'Published successfully:' }
    foreach ($image in $images) { Write-Host "  $($image.Target)" }
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
