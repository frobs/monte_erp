# Packages Monte ERP with jpackage on Windows.
#
#   .\scripts\package-app-image.ps1
#   .\scripts\package-app-image.ps1 -SkipTests
#   .\scripts\package-app-image.ps1 -Type msi
#   .\scripts\package-app-image.ps1 -Type exe
#
# `app-image` does not need WiX. `msi` and `exe` need the WiX Toolset installed.
#
# Environment overrides:
#   APP_NAME, APP_VERSION, APP_VENDOR, JPACKAGE_ADD_MODULES,
#   JPACKAGE_SW_RENDER=1, MONTE_ERP_CONFIG_DIR, JPACKAGE_EXTRA_ARGS
#
# Requirements: JDK 25 (with jpackage), the Maven wrapper and the JavaFX
# libraries. Docker and Xvfb are NOT needed.

[CmdletBinding()]
param(
    [switch]$SkipTests,
    [ValidateSet("app-image", "msi", "exe")]
    [string]$Type = "app-image",
    [string]$AppVersion = $(if ($env:APP_VERSION) { $env:APP_VERSION } else { "1.0.0" }),
    [string]$AppName = $(if ($env:APP_NAME) { $env:APP_NAME } else { "MonteERP" }),
    [string]$Vendor = $(if ($env:APP_VENDOR) { $env:APP_VENDOR } else { "Entidad Desconocida" })
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

function Fail([string]$Message) {
    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail "java no está en el PATH (se necesita JDK 25)"
    }
    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        Fail "jpackage no está en el PATH: instala un JDK 25 o añade su bin al PATH"
    }
    if (-not (Test-Path ".\mvnw.cmd")) {
        Fail "no se encuentra el wrapper .\mvnw.cmd"
    }

    $helpText = (& jpackage --help 2>&1 | Out-String)
    if ($helpText -match "Valid values are: \{([^}]*)\}") {
        $supported = $Matches[1].Replace('"', '') -split ",\s*"
        if ($supported -notcontains $Type) {
            Fail "jpackage de este sistema no soporta -Type $Type. Tipos disponibles: $($supported -join ', ')."
        }
    }

    Write-Host "==> Compilando Monte ERP ($AppVersion)"
    $mavenArgs = if ($SkipTests) {
        @("-B", "clean", "package", "-DskipTests")
    } else {
        @("-B", "clean", "verify")
    }
    & .\mvnw.cmd @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "Maven terminó con código $LASTEXITCODE"
    }

    $jars = @(Get-ChildItem -Path "target" -Filter "*.jar" -File |
        Where-Object { $_.Name -notlike "original-*" })
    if ($jars.Count -ne 1) {
        Fail "se esperaba exactamente un fat JAR en target\ y hay $($jars.Count)"
    }
    $jar = $jars[0]

    $inputDir = Join-Path $root "target\jpackage-input"
    $destDir = Join-Path $root "target\jpackage"
    Remove-Item -Recurse -Force $inputDir, $destDir -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
    Copy-Item $jar.FullName $inputDir

    $addModules = if ($env:JPACKAGE_ADD_MODULES) {
        $env:JPACKAGE_ADD_MODULES
    } else {
        "java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata"
    }

    $configOption = if (-not [string]::IsNullOrWhiteSpace($env:MONTE_ERP_CONFIG_DIR)) {
        "-Dmonte.erp.config.dir=$($env:MONTE_ERP_CONFIG_DIR)"
    } else {
        # Empty value = platform per-user configuration directory.
        "-Dmonte.erp.config.dir="
    }

    $javaOptions = @(
        "-Duser.language=es",
        "-Duser.country=ES",
        "-Duser.timezone=Europe/Madrid",
        $configOption
    )
    if ($env:JPACKAGE_SW_RENDER -eq "1") {
        $javaOptions += "-Dprism.order=sw"
    }

    $jpackageArgs = @(
        "--type", $Type,
        "--name", $AppName,
        "--app-version", $AppVersion,
        "--vendor", $Vendor,
        "--input", $inputDir,
        "--main-jar", $jar.Name,
        "--main-class", "com.entidaddesconocida.monte_erp.Launcher",
        "--dest", $destDir,
        "--add-modules", $addModules
    )
    foreach ($option in $javaOptions) {
        $jpackageArgs += @("--java-options", $option)
    }
    if ($Type -in @("msi", "exe")) {
        $jpackageArgs += @("--win-shortcut", "--win-menu")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JPACKAGE_EXTRA_ARGS)) {
        $jpackageArgs += @($env:JPACKAGE_EXTRA_ARGS -split "\s+")
    }

    Write-Host "==> Ejecutando jpackage (tipo: $Type)"
    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "jpackage terminó con código $LASTEXITCODE"
    }

    Write-Host ""
    Write-Host "Paquete generado en: $destDir"
    if ($Type -eq "app-image") {
        Write-Host "Lanzador: $destDir\$AppName\${AppName}.exe"
    }
}
finally {
    Pop-Location
}
