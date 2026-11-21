#!/usr/bin/env bash
#
# Packages Monte ERP with jpackage.
#
#   scripts/package-app-image.sh                    # app-image (Linux/macOS)
#   scripts/package-app-image.sh --skip-tests       # faster, no unit tests
#   scripts/package-app-image.sh --type deb         # .deb on Debian/Ubuntu
#   scripts/package-app-image.sh --type rpm         # .rpm on openSUSE/Fedora/RHEL
#   scripts/package-app-image.sh --type dmg         # .dmg on macOS
#
# The default type is `app-image`, which does not need WiX, dpkg or rpmbuild.
# Installer types need the native packaging tools of the target platform; build
# each installer on the platform where it will be installed.
#
# Environment overrides:
#   APP_NAME=MonteERP
#   APP_VERSION=1.0.0          (jpackage does not accept a SNAPSHOT version)
#   APP_VENDOR="Entidad Desconocida"
#   JPACKAGE_ADD_MODULES=java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata
#   JPACKAGE_SW_RENDER=1       adds -Dprism.order=sw
#   MONTE_ERP_CONFIG_DIR=...   embeds an absolute configuration directory
#   JPACKAGE_EXTRA_ARGS="..."  extra jpackage arguments, space separated
#
# Requirements: JDK 25 (with jpackage), the Maven wrapper, and the native
# libraries needed by JavaFX. Docker and Xvfb are NOT needed.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APP_NAME="${APP_NAME:-MonteERP}"
APP_VERSION="${APP_VERSION:-1.0.0}"
APP_VENDOR="${APP_VENDOR:-Entidad Desconocida}"
PACKAGE_TYPE="${JPACKAGE_TYPE:-app-image}"
ADD_MODULES="${JPACKAGE_ADD_MODULES:-java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata}"
DEST_DIR="${JPACKAGE_DEST:-$ROOT_DIR/target/jpackage}"
INPUT_DIR="${JPACKAGE_INPUT:-$ROOT_DIR/target/jpackage-input}"
SKIP_TESTS=0

usage() {
    cat <<'EOF'
Uso: scripts/package-app-image.sh [opciones]

Opciones:
  --skip-tests          No ejecuta la suite unitaria (usa `mvn package -DskipTests`).
  --type <tipo>         app-image (por defecto), deb, rpm, dmg, pkg... (según plataforma)
  --version <version>   Versión de la aplicación (por defecto 1.0.0).
  --name <nombre>       Nombre del paquete y del lanzador (por defecto MonteERP).
  --vendor <nombre>     Vendor del paquete.
  -h, --help            Muestra esta ayuda.

Variables de entorno:
  APP_NAME, APP_VERSION, APP_VENDOR, JPACKAGE_TYPE, JPACKAGE_DEST,
  JPACKAGE_INPUT, JPACKAGE_ADD_MODULES, JPACKAGE_SW_RENDER,
  MONTE_ERP_CONFIG_DIR, JPACKAGE_EXTRA_ARGS

Ejemplos:
  scripts/package-app-image.sh
  scripts/package-app-image.sh --skip-tests
  scripts/package-app-image.sh --type deb
  scripts/package-app-image.sh --type rpm   # requiere rpmbuild (openSUSE: rpm-build)
  MONTE_ERP_CONFIG_DIR=/etc/monte-erp scripts/package-app-image.sh
EOF
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

# Types accepted by the jpackage of this JDK/platform, e.g. "app-image rpm deb".
supported_package_types() {
    jpackage --help 2>&1 \
        | sed -n 's/.*Valid values are: {\(.*\)}.*/\1/p' \
        | tr ',' ' ' \
        | tr -d '"' \
        | tr -s ' '
}

validate_package_type() {
    local supported
    supported="$(supported_package_types)"
    if [ -n "$supported" ]; then
        case " $supported " in
            *" $PACKAGE_TYPE "*) ;;
            *)
                fail "jpackage de este sistema no soporta --type $PACKAGE_TYPE. Tipos disponibles: $supported. Usa --type app-image (no requiere herramientas), --type deb en Debian/Ubuntu, o ejecuta el script en el SO de destino con un JDK 25 que soporte ese tipo."
                ;;
        esac
    fi

    case "$PACKAGE_TYPE" in
        rpm)
            command -v rpmbuild >/dev/null 2>&1 \
                || fail "El tipo rpm está soportado pero falta rpmbuild. En openSUSE: sudo zypper install rpm-build. En Fedora/RHEL: sudo dnf install rpm-build. También puedes usar --type app-image."
            ;;
        deb)
            for tool in dpkg-deb fakeroot; do
                command -v "$tool" >/dev/null 2>&1 \
                    || fail "El tipo deb requiere $tool: instálalo o usa --type app-image."
            done
            ;;
        msi|exe)
            fail "En Windows usa scripts/package-app-image.ps1 -Type $PACKAGE_TYPE"
            ;;
    esac
}

while [ $# -gt 0 ]; do
    case "$1" in
        --skip-tests)
            SKIP_TESTS=1
            shift
            ;;
        --type)
            [ $# -ge 2 ] || fail "--type necesita un valor"
            PACKAGE_TYPE="$2"
            shift 2
            ;;
        --version)
            [ $# -ge 2 ] || fail "--version necesita un valor"
            APP_VERSION="$2"
            shift 2
            ;;
        --name)
            [ $# -ge 2 ] || fail "--name necesita un valor"
            APP_NAME="$2"
            shift 2
            ;;
        --vendor)
            [ $# -ge 2 ] || fail "--vendor necesita un valor"
            APP_VENDOR="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "opción desconocida: $1 (usa --help)"
            ;;
    esac
done

command -v java >/dev/null 2>&1 || fail "java no está en el PATH (se necesita JDK 25)"
command -v jpackage >/dev/null 2>&1 \
    || fail "jpackage no está en el PATH: instala un JDK 25 o añade su bin al PATH"
[ -x "$ROOT_DIR/mvnw" ] || fail "no se encuentra el wrapper ./mvnw"
validate_package_type

echo "==> Compilando Monte ERP ($APP_VERSION)"
if [ "$SKIP_TESTS" = "1" ]; then
    ./mvnw -B clean package -DskipTests
else
    ./mvnw -B clean verify
fi

JAR_COUNT="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' -print | wc -l | tr -d ' ')"
[ "$JAR_COUNT" = "1" ] \
    || fail "se esperaba exactamente un fat JAR en target/ y hay $JAR_COUNT"
JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' -print | head -n 1)"
JAR_NAME="$(basename "$JAR")"

rm -rf "$INPUT_DIR" "$DEST_DIR"
mkdir -p "$INPUT_DIR" "$DEST_DIR"
cp "$JAR" "$INPUT_DIR/"

JAVA_OPTIONS=(
    "-Duser.language=es"
    "-Duser.country=ES"
    "-Duser.timezone=Europe/Madrid"
)
if [ "${JPACKAGE_SW_RENDER:-0}" = "1" ]; then
    JAVA_OPTIONS+=("-Dprism.order=sw")
fi
# An empty value means "use the platform per-user configuration directory".
# This keeps the installation directory read-only and gives every OS user their
# own application.properties.
if [ -n "${MONTE_ERP_CONFIG_DIR:-}" ]; then
    JAVA_OPTIONS+=("-Dmonte.erp.config.dir=$MONTE_ERP_CONFIG_DIR")
else
    JAVA_OPTIONS+=("-Dmonte.erp.config.dir=")
fi

EXTRA_ARGS=()
if [ -n "${JPACKAGE_EXTRA_ARGS:-}" ]; then
    # Deliberate word splitting: extra arguments are a space-separated list.
    # shellcheck disable=SC2206
    EXTRA_ARGS=(${JPACKAGE_EXTRA_ARGS})
fi

JPACKAGE_ARGS=(
    --type "$PACKAGE_TYPE"
    --name "$APP_NAME"
    --app-version "$APP_VERSION"
    --vendor "$APP_VENDOR"
    --input "$INPUT_DIR"
    --main-jar "$JAR_NAME"
    --main-class com.entidaddesconocida.monte_erp.Launcher
    --dest "$DEST_DIR"
    --add-modules "$ADD_MODULES"
)

for option in "${JAVA_OPTIONS[@]}"; do
    JPACKAGE_ARGS+=(--java-options "$option")
done

case "$PACKAGE_TYPE" in
    deb|rpm)
        JPACKAGE_ARGS+=(--linux-shortcut)
        ;;
esac
if [ "${#EXTRA_ARGS[@]}" -gt 0 ]; then
    JPACKAGE_ARGS+=("${EXTRA_ARGS[@]}")
fi

echo "==> Ejecutando jpackage (tipo: $PACKAGE_TYPE)"
jpackage "${JPACKAGE_ARGS[@]}"

echo
echo "Paquete generado en: $DEST_DIR"
if [ "$PACKAGE_TYPE" = "app-image" ]; then
    echo "App image: $DEST_DIR/$APP_NAME"
fi
