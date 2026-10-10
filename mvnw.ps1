param([Parameter(ValueFromRemainingArguments = $true)][string[]] $MavenArgs)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenVersion = '3.9.11'
$mavenHome = Join-Path $projectRoot ".mvn\wrapper\dists\apache-maven-$mavenVersion"
$mavenExecutable = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path $mavenExecutable)) {
    $wrapperDirectory = Join-Path $projectRoot '.mvn\wrapper\dists'
    New-Item -ItemType Directory -Force -Path $wrapperDirectory | Out-Null
    $archive = Join-Path $wrapperDirectory "apache-maven-$mavenVersion-bin.zip"
    $downloadUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Write-Host "Downloading Apache Maven $mavenVersion..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $wrapperDirectory -Force
    Remove-Item -LiteralPath $archive
}

& $mavenExecutable @MavenArgs
exit $LASTEXITCODE
