# Monte ERP

Aplicación de escritorio ERP construida con **Java 25** y **JavaFX 21**, gestionada con **Maven**.

---

## Tabla de contenidos

1. [Requisitos previos](#requisitos-previos)
2. [Dependencias del sistema para JavaFX y los tests de interfaz](#dependencias-del-sistema-para-javafx-y-los-tests-de-interfaz)
3. [Estructura del proyecto](#estructura-del-proyecto)
4. [Compilar y empaquetar](#compilar-y-empaquetar)
5. [Ejecutar la aplicación](#ejecutar-la-aplicación)
6. [Base de datos MySQL con Docker](#base-de-datos-mysql-con-docker)
7. [Conexión de la aplicación a MySQL](#conexión-de-la-aplicación-a-mysql)
8. [Manual de usuario](#manual-de-usuario)

---

## Requisitos previos

| Herramienta     | Versión mínima   | Instalación                                                                                         |
| --------------- | ---------------- | --------------------------------------------------------------------------------------------------- |
| JDK             | 25               | [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) o [OpenJDK](https://openjdk.org/) |
| Maven           | 3.9+             | [Maven download](https://maven.apache.org/download.cgi) o usar el wrapper `./mvnw` incluido         |
| Docker          | 24+              | [Docker Desktop](https://www.docker.com/products/docker-desktop/)                                   |

> **Nota:** El proyecto incluye el wrapper de Maven (`mvnw` / `mvnw.cmd`), por lo que **no es necesario instalar Maven** globalmente. Basta con tener el JDK.

---

## Dependencias del sistema para JavaFX y los tests de interfaz

JavaFX y TestFX se descargan como dependencias de Maven: **no hace falta instalar
el SDK de JavaFX**. Lo que sí debe estar disponible en el sistema operativo son
las bibliotecas nativas que carga JavaFX 21 y, para los tests de interfaz, Docker
y un servidor gráfico.

Además:

- La aplicación necesita un **MySQL 8.0** accesible (ver
  [Base de datos MySQL con Docker](#base-de-datos-mysql-con-docker)) y un
  **keyring del sistema** para guardar la contraseña de la base de datos.
- Los tests de interfaz **levantan su propio MySQL efímero** con Docker; no usan
  el MySQL de la aplicación ni el keyring real (inyectan un
  `FakeCredentialStore`).
- Los tests usan el robot `glass` de JavaFX y renderizado software
  (`prism.order=sw`), así que no necesitan GPU.

### Resumen por sistema operativo

| Sistema | Arrancar la app (`javafx:run`) | Ejecutar los tests de interfaz |
| --- | --- | --- |
| Windows 10/11 x64 | JDK 25. JavaFX aporta sus DLL nativas; no hay paquetes extra. | Docker Desktop con contenedores Linux + sesión de escritorio interactiva. |
| macOS 11+ (Intel o Apple Silicon) | JDK 25. JavaFX aporta sus `.dylib`; no hay paquetes extra. | Docker Desktop + sesión de escritorio interactiva (y permiso de Accesibilidad si el robot no envía eventos). |
| Linux x86_64/aarch64 | JDK 25 + GTK 3.8+ + X11/XWayland + `libGL` (o renderizado software). | Docker + GTK 3 + Xvfb + `xauth` (+ `xdpyinfo` si no hay `xvfb-run`). |

> En Windows y macOS, para los tests bastan el JDK, el wrapper de Maven y Docker
> Desktop. En Linux, una instalación de escritorio completa ya trae GTK 3 y el
> keyring; las listas siguientes son para instalaciones mínimas, servidores o
> contenedores.

> **Fat JAR y plataforma:** `target/monte_erp-1.0-SNAPSHOT.jar` solo contiene las
> bibliotecas nativas de JavaFX de la plataforma donde se ejecutó `mvn package`.
> Para verlo en otro sistema operativo hay que empaquetarlo en ese sistema (o
> generar una imagen con `javafx:jlink` en el destino); `./mvnw javafx:run` siempre
> usa las nativas de la plataforma actual.

### Linux

JavaFX 21 en Linux exige **GTK 3.8 o superior** y se ejecuta sobre **X11**; en una
sesión Wayland lo hace a través de **XWayland** (JavaFX fuerza `GDK_BACKEND=x11`).
Maven descarga automáticamente el artefacto nativo `linux` o `linux-aarch64`
según la arquitectura del JDK, así que no hay que instalar JavaFX aparte.

Las bibliotecas que enlaza directamente JavaFX son, entre otras, `libgtk-3`,
`libX11`, `libxcb`, `libXxf86vm`, `libXtst`, `libGL`, FreeType y fontconfig.
`libgtk-3` arrastra la mayoría de dependencias (Pango, Cairo, GDK Pixbuf, ATK…),
pero `libXtst` y `libXxf86vm` suelen ser paquetes independientes.

#### Debian 12 / Ubuntu 22.04

```bash
sudo apt update
sudo apt install libgtk-3-0 libx11-6 libxcb1 libxxf86vm1 libxtst6 \
  libgl1 libfreetype6 libfontconfig1 fonts-dejavu-core

# Tests de interfaz en modo headless
sudo apt install xvfb xauth x11-utils
```

En **Debian 13 / Ubuntu 24.04 o posterior** los paquetes `t64` sustituyen a los
anteriores:

```bash
sudo apt install libgtk-3-0t64 libx11-6 libxcb1 libxxf86vm1 libxtst6 \
  libgl1 libfreetype6 libfontconfig1 fonts-dejavu-core
```

#### Fedora / RHEL 9+

```bash
sudo dnf install gtk3 libX11 libxcb libXxf86vm libXtst mesa-libGL \
  freetype fontconfig dejavu-sans-fonts

# Tests de interfaz en modo headless
sudo dnf install xorg-x11-server-Xvfb xorg-x11-xauth xdpyinfo
```

#### openSUSE Leap / Tumbleweed

```bash
sudo zypper install libgtk-3-0 libX11-6 libxcb1 libXxf86vm1 libXtst6 \
  libglvnd libfreetype6 libfontconfig1 dejavu-fonts

# Tests de interfaz en modo headless
sudo zypper install xorg-x11-server-Xvfb xvfb-run xauth xdpyinfo
```

#### Arch Linux / Manjaro

```bash
sudo pacman -S --needed gtk3 libx11 libxcb libxxf86vm libxtst libglvnd \
  freetype2 fontconfig ttf-dejavu

# Tests de interfaz en modo headless
sudo pacman -S --needed xorg-server-xvfb xorg-xauth xorg-xdpyinfo
```

En cualquier otra distribución con glibc, instala los paquetes equivalentes a
estas bibliotecas; los nombres cambian, pero JavaFX enlaza contra las mismas.

#### Keyring en Linux (solo para la app)

La aplicación guarda la contraseña de MySQL en el keyring del sistema. Necesita
una sesión **D-Bus** y un proveedor de **Secret Service**, normalmente
`gnome-keyring`. Sin él la app arranca, pero no puede guardar la conexión.

| Distribución | Instalación |
| --- | --- |
| Debian / Ubuntu | `sudo apt install dbus-user-session gnome-keyring` |
| Fedora / RHEL | `sudo dnf install dbus-daemon gnome-keyring` |
| openSUSE | `sudo zypper install dbus-1-daemon gnome-keyring` |
| Arch / Manjaro | `sudo pacman -S --needed dbus gnome-keyring` |

En un escritorio GNOME/KDE completo suele estar ya activo. `java-keyring` 1.0.4
usa **KWallet 5** en KDE; si el guardado falla en KDE 6, instala y arranca
`gnome-keyring`, que implementa Secret Service. Los tests de interfaz no
necesitan keyring.

#### Notas de Linux

- **Sin GPU / escritorio remoto / VM:** usa renderizado software con
  `JAVA_TOOL_OPTIONS=-Dprism.order=sw ./mvnw clean javafx:run` o, con el JAR,
  `java -Dprism.order=sw -jar target/monte_erp-1.0-SNAPSHOT.jar`.
- **Docker sin `sudo`:** el usuario que ejecuta los tests debe poder hablar con
  el daemon (`docker info` sin error). En Linux, añádelo al grupo `docker` o usa
  un daemon rootless.
- **Alpine y otras distribuciones con musl:** los binarios oficiales de JavaFX
  son para glibc; no funcionan en Alpine sin una capa de compatibilidad. Usa una
  distribución glibc o un contenedor con glibc.

### Windows

- **JDK 25 x64** (Temurin, Microsoft Build of OpenJDK u Oracle JDK) y el wrapper
  `mvnw.cmd` incluido.
- JavaFX incluye sus DLL nativas en los artefactos de Maven; no hay que instalar
  el SDK de JavaFX.
- La app se arranca desde una sesión de escritorio normal:

  ```bat
  mvnw.cmd clean javafx:run
  ```

- Para los tests de interfaz hacen falta **Docker Desktop** con contenedores
  Linux en ejecución y una **sesión de escritorio interactiva** (TestFX conduce
  la ventana real; no hay Xvfb nativo en Windows):

  ```bat
  mvnw.cmd test -Pui-tests
  ```

- Si necesitas ejecutar los tests sin escritorio (CI), usa **WSL2 con una
  distribución Linux** y sigue los pasos de Linux (`scripts/run-ui-tests.sh`
  dentro de WSL2, con integración de Docker Desktop).
- Las credenciales de MySQL se guardan en el **Administrador de credenciales de
  Windows**, incluido en el sistema.

### macOS

- **macOS 11 o superior** (mínimo de JavaFX 21) con un JDK 25 para Intel o Apple
  Silicon; Maven elige el artefacto `mac` o `mac-aarch64`. Comprueba además que
  tu versión de macOS esté soportada por el JDK 25.
- No hacen falta paquetes nativos adicionales: JavaFX incluye sus `.dylib`.
- La app se arranca desde una sesión gráfica:

  ```bash
  ./mvnw clean javafx:run
  ```

- Tests de interfaz con Docker Desktop (contenedores Linux) y sesión de
  escritorio iniciada:

  ```bash
  ./mvnw test -Pui-tests
  ```

- Si el robot no mueve el ratón ni escribe, concede permiso de **Accesibilidad**
  al terminal o al IDE en *Ajustes del Sistema → Privacidad y seguridad →
  Accesibilidad*.
- `scripts/run-ui-tests.sh` está pensado para Linux (usa Xvfb y `DISPLAY`); en
  macOS ejecuta directamente Maven.
- Las credenciales se guardan en el **Llavero** del usuario.

### Requisitos comunes de los tests de interfaz

Independientemente del sistema, la suite `-Pui-tests` necesita:

1. **Docker** con el daemon accesible (`docker info`). La primera ejecución
   descarga la imagen `mysql:8.0` y crea un contenedor efímero.
2. **Un display**: una sesión gráfica real o, en Linux, Xvfb. El perfil ya fija
   `prism.order=sw` y `testfx.robot=glass`, por lo que no hace falta GPU.
3. **GTK 3 y las bibliotecas de JavaFX** indicadas arriba. En Linux headless,
   además `xvfb`/`xvfb-run`, `xauth` y `xdpyinfo` (si no existe `xvfb-run`).
4. **Maven Wrapper** (`./mvnw` o `mvnw.cmd`) y JDK 25; no hace falta Maven global.

Con un display ya disponible se puede ejecutar directamente
`./mvnw test -Pui-tests` (o `mvnw.cmd test -Pui-tests` en Windows); en Linux sin
pantalla, `scripts/run-ui-tests.sh` arranca un Xvfb privado y limpia el
contenedor al terminar. En macOS y Windows no se usa Xvfb: ejecuta Maven desde
una sesión de escritorio.

---

## Estructura del proyecto

```
monte_erp/
├── pom.xml                  # Configuración Maven y dependencias
├── mvnw / mvnw.cmd          # Maven Wrapper (no requiere Maven global)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java
│   │   │   └── com/entidaddesconocida/monte_erp/
│   │   │       ├── Launcher.java                  # Punto de entrada (main)
│   │   │       ├── MonteErpApplication.java       # JavaFX Application
│   │   │       ├── app/                           # AppContext, AppShell, SessionState, SessionValidator, SceneNavigator
│   │   │       ├── view/                          # LoginView, SetupView, MainView, CrudView (base) y pantallas: UsersView, ProductsView, CategoriesView, SuppliersView, CustomersView, SalesOrdersView, PurchasesView, StockView, ReportsView + InputMasks/SearchableCombos
│   │   │       ├── viewmodel/                     # Un ViewModel por pantalla + CrudViewModel/FilterableCrudViewModel, SearchText y ReportCsv
│   │   │       ├── domain/
│   │   │       │   ├── model/                     # User, Category, Supplier, Product, ProductFilter, Purchase, PurchaseFilter, Stock, StockLevel, ProductSupplier, Customer, SalesOrder, SalesOrderFilter, SalesOrderLine, StockIssue, ReportPeriod y modelos de informe
│   │   │       │   ├── repository/                # UserRepository, CategoryRepository, SupplierRepository, ProductRepository, PurchaseRepository, StockRepository, CustomerRepository, SalesOrderRepository (puertos)
│   │   │       │   └── service/                   # SetupService, FifoAllocator, SalesReportCalculator, StockReportCalculator
│   │   │       ├── infrastructure/                # MySqlUserRepository, MySqlCategoryRepository, MySqlSupplierRepository, MySqlProductRepository, MySqlCustomerRepository, MySqlPurchaseRepository, MySqlStockRepository, MySqlSalesOrderRepository
│   │   │       │   └── config/                    # DatabaseConnection, DatabaseConfig, DatabaseSetupService, MigrationRunner, keyring
│   │   │       └── security/                      # PasswordHasher
│   │   └── resources/
│   │       ├── css/app.css                        # Estilos de la interfaz
│   │       ├── db/migration/V1..V10__*.sql        # Migraciones: esquema + bajas lógicas + índices de filtros
│   │       └── db/seed/demo_seed.sql               # Dataset de demostración (no se aplica al arrancar)
│   │       └── com/entidaddesconocida/monte_erp/view/
│   │           ├── LoginView.fxml                 # vista de login (mismo paquete que LoginView)
│   │           ├── SetupView.fxml                 # vista de configuración de BD
│   │           ├── MainView.fxml                  # vista principal (mismo paquete que MainView)
│   │           ├── UsersView.fxml                 # vista de gestión de usuarios (RF-02)
│   │           ├── ProductsView.fxml              # vista de gestión de productos (RF-03/RF-11)
│   │           ├── CategoriesView.fxml            # vista de gestión de categorías (RF-04)
│   │           ├── SuppliersView.fxml             # vista de proveedores (RF-05)
│   │           ├── CustomersView.fxml             # vista de clientes (RF-06)
│   │           ├── SalesOrdersView.fxml           # vista de pedidos (RF-07/RF-08)
│   │           ├── PurchasesView.fxml             # vista de compras y lotes (RF-14)
│   │           ├── StockView.fxml                 # vista de stock y alertas (RF-09/RF-10)
│   │           └── ReportsView.fxml               # vista de informes (RF-12)
│   └── test/java/...                              # Pruebas JUnit 5: unitarias y ui/ (TestFX)
├── docs/manual_de_usuario/                                   # Manual de usuario (Markdown + capturas reales)
├── scripts/run-ui-tests.sh                        # Suite de interfaz con Xvfb + MySQL efímero
└── target/                  # Artefactos generados (tras compilar)
```

---

## Manual de usuario

El manual de uso de la aplicación, con capturas de todas las pantallas, está en
[`docs/manual_de_usuario/manual.md`](docs/manual_de_usuario/manual.md).

Las imágenes no son mockups: se generan ejecutando la aplicación real con los
tests de TestFX.

```bash
scripts/run-ui-tests.sh --manual   # regenera docs/manual_de_usuario/img/*.png
```

---

## Compilar y empaquetar

### Empaquetar como JAR ejecutable (fat JAR)

El proyecto usa el **maven-shade-plugin** para generar un JAR con todas las dependencias incluidas:

```bash
./mvnw clean package
```

Esto genera dos archivos en `target/`:

| Archivo                                | Descripción                                            |
| -------------------------------------- | ------------------------------------------------------ |
| `monte_erp-1.0-SNAPSHOT.jar`           | **Fat JAR** — contiene todo lo necesario para ejecutar |
| `original-monte_erp-1.0-SNAPSHOT.jar`  | JAR original sin dependencias (no usar directamente)   |

### Ejecutar los tests

```bash
./mvnw test
```

## Ejecutar la aplicación

### Desde Maven (modo desarrollo)

```bash
./mvnw clean javafx:run
```

### Desde el JAR empaquetado

> ```bash
> java -jar target/monte_erp-1.0-SNAPSHOT.jar
> ```
---

## Base de datos MySQL con Docker

### Levantar un contenedor MySQL

```bash
docker run -d \
  --name monte_erp_mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root1234 \
  -e MYSQL_DATABASE=monte_erp \
  -e MYSQL_USER=erp_user \
  -e MYSQL_PASSWORD=erp_pass \
  -v monte_erp_data:/var/lib/mysql \
  mysql:8.0
``` 

### Conectarse al contenedor con el cliente MySQL

```bash
docker exec -it monte_erp_mysql mysql -u erp_user -p monte_erp
```

---

## Datos de demostración

El script `src/main/resources/db/seed/demo_seed.sql` carga un dataset completo
para una demo: usuarios, categorías, proveedores, clientes, productos, compras,
lotes de stock, pedidos y consumos FIFO. Vive **fuera de `db/migration`**, así
que `MigrationRunner` no lo ejecuta al arrancar la aplicación.

Requisitos y orden de uso:

1. Tener el contenedor MySQL creado (apartado anterior).
2. Arrancar la aplicación una vez para que las migraciones creen el esquema
   (o aplicar los scripts `V1`–`V10` manualmente).
3. Cargar el seed:

```bash
docker exec -i monte_erp_mysql mysql -u erp_user -perp_pass monte_erp \
  < src/main/resources/db/seed/demo_seed.sql
```

> **Aviso:** el script solo inserta datos y está pensado para una base de datos
> recién creada. No lo ejecutes sobre una base con datos reales. El script
> comprueba que la tabla `USER` esté vacía: si ya existe el administrador del
> onboarding (u otros usuarios), aborta con `Base de datos no vacia: ...` antes
> de insertar nada. Si lo vuelves a lanzar tras un seed correcto, el marcador
> `demo_seed` provoca un error de clave duplicada.

Para repetir la demo desde cero, se elimina el contenedor **y su volumen** y se
vuelve a crear con el comando del apartado anterior:

```bash
docker rm -f monte_erp_mysql
docker volume rm monte_erp_data
# Volver a ejecutar el docker run y cargar el seed otra vez
```

### Usuarios demo

| Usuario       | Contraseña | Rol           | Estado   |
| ------------- | ---------- | ------------- | -------- |
| `admin`       | `Admin1234` | Administrador | Activo   |
| `ana.garcia`  | `Demo1234`  | Empleado      | Activo   |
| `luis.moreno` | `Demo1234`  | Empleado      | Activo   |
| `marta.sanz`  | `Demo1234`  | Empleado      | Inactivo |

### Contenido del dataset

- **25 productos** (2 inactivos con ventas históricas y 2 sin categoría), con
  existencias repartidas en 44 lotes.
- **18 compras** en los últimos cinco meses: 13 `received`, 2 `pending`,
  2 `cancelled` y 1 `returned`. Una compra recibida queda totalmente consumida.
- **30 pedidos** en los últimos cuatro meses: 14 `delivered`, 7 `shipped`,
  5 `pending` y 4 `cancelled` (estos últimos sin consumir stock).
- **73 líneas de pedido** y **65 consumos FIFO**, con varias líneas repartidas
  entre dos lotes para poder enseñar la trazabilidad.
- **Alertas de stock:** 9 productos activos por debajo de su mínimo, dos de
  ellos a cero.
- Fechas relativas a `NOW()`: siempre hay actividad en el mes actual para los
  informes.

Al final del script se ejecutan comprobaciones de integridad (stock fuera de
rango, totales descuadrados, consumos incorrectos, compras no recibidas con
stock…). Todas deben devolver **0 filas**; las únicas filas esperadas son las dos
de aviso de productos activos sin existencias.

---

## Conexión de la aplicación a MySQL

La configuración vive en dos sitios:

- La **URL** (`db.url`) en un `application.properties` **junto al JAR** (en el
  directorio de trabajo), gestionado por `DatabaseConfig`.
- El **usuario y la contraseña** de la base de datos en el **keyring del sistema
  operativo** (java-keyring, vía `KeyringCredentialStore`), nunca en el repositorio.
- La URL JDBC no debe incluir credenciales ni parámetros sensibles. La pantalla de
  edición no revela la contraseña guardada: solo reutiliza la credencial del keyring
  para el mismo endpoint y usuario; al cambiar el endpoint hay que volver a introducirla.
  La URL que muestra la aplicación redacta valores sensibles de configuraciones antiguas.

Si falta esa configuración, la app abre la pantalla de **configuración**
(`SetupView`) en modo onboarding en lugar del login; desde el login se puede
cambiar la conexión con "Cambiar configuración de base de datos".
`DatabaseConnection` (paquete `infrastructure/config`) es el único punto que abre
conexiones JDBC.

### Migraciones

Los scripts `.sql` versionados viven en `src/main/resources/db/migration` y
siguen la convención `V<n>__<descripcion>.sql`. Al arrancar, `MigrationRunner`
aplica en orden los que estén pendientes y registra cada uno en la tabla de
control `schema_migrations`, por lo que arrancar la aplicación varias veces no
repite trabajo. Para cambiar el esquema basta con añadir el siguiente
`V<n>__<descripcion>.sql`. Migraciones actuales (`V1`–`V10`):
`V2`/`V4`/`V5`/`V6` añaden `PRODUCT.active`, `CATEGORY.active`, `SUPPLIER.active`
y `CUSTOMER.active` (bajas lógicas); `V3` hace `NULL`-able `PRODUCT.category_id`
porque la categoría es opcional; `V7` añade `SUPPLIER.address`; `V8` amplía el
`ENUM` de `PURCHASE.status` con `returned` para la devolución de compras; `V9`
añade el índice `(purchase_date, status)` que sirve al filtro del histórico de
compras (rango de fechas, proveedor y estado); y `V10` añade el índice
`(order_date, status)` que sirve al filtro del histórico de pedidos (rango de
fechas, cliente y estado).

Los filtros de las pantallas se aplican **en la consulta SQL**, no en memoria:
las búsquedas de productos, categorías, proveedores, clientes y stock viajan
como texto/ids al repositorio correspondiente (`ProductFilter` para la pantalla
de productos), y los históricos de compras y pedidos usan `PurchaseFilter` y
`SalesOrderFilter`. Todos los términos de texto se comparan con la colación
`utf8mb4_unicode_ci` (insensible a mayúsculas y acentos) y los `%`/`_` se
escapan para que se traten como texto literal.

Esquema creado por `V1__schema.sql` (10 tablas):

| Tabla                 | Contenido                                                       |
| --------------------- | --------------------------------------------------------------- |
| `CATEGORY`            | Categorías de producto                                          |
| `SUPPLIER`            | Proveedores                                                     |
| `PRODUCT`             | Productos (el stock no es un campo: se calcula desde sus lotes) |
| `PURCHASE`            | Compras a proveedores (`pending → received → returned/cancelled`) |
| `STOCK`               | Lotes de stock, uno por producto y compra                       |
| `CUSTOMER`            | Clientes                                                        |
| `SALES_ORDER`         | Pedidos de cliente (`ORDER` es palabra reservada en MySQL)      |
| `SALES_ORDER_LINE`    | Líneas de pedido                                                |
| `STOCK_ISSUE`         | Consumo de lotes por línea (reparto FIFO)                       |
| `USER`                | Usuarios del sistema                                            |

### Usuarios iniciales

En la primera ejecución la app abre la pantalla de
configuración y pide crear el **usuario administrador** (nombre, usuario y
contraseña). La contraseña se guarda hasheada con PBKDF2 (nunca en claro). El
resto de usuarios se dan de alta desde "Gestión de usuarios" (RF-02).

### Pruebas

Hay dos suites separadas por etiquetas JUnit (`@Tag`).

#### Tests unitarios (rápidos, sin Docker)

```bash
./mvnw test
```

Usan fakes en memoria y ficheros temporales: no necesitan MySQL, Docker ni el
toolkit de JavaFX. Los tests de interfaz quedan excluidos de esta ejecución.

#### Tests de interfaz (TestFX + MySQL efímero)

```bash
scripts/run-ui-tests.sh
# o, con un display ya disponible:
./mvnw test -Pui-tests
```

El script:

1. Comprueba que Docker está disponible.
2. Arranca un **Xvfb privado** (1920×1200) para poder ejecutar la suite en CI o
   en una máquina sin sesión gráfica.
3. Ejecuta `./mvnw test -Pui-tests`.
4. Elimina el contenedor MySQL y el Xvfb al terminar (también si la ejecución
   falla).

La suite conduce los propios controles de JavaFX (campos de texto, botones,
combos, tablas y diálogos) a través de sus manejadores y bindings, sin depender
del foco del sistema operativo: por eso también funciona directamente sobre un
escritorio normal con `./mvnw test -Pui-tests`. El Xvfb del script es solo para
que la ejecución sea reproducible en entornos sin pantalla.

Durante los tests, `ui.support.TestDatabase` crea un contenedor MySQL
`mysql:8.0` con las migraciones reales (`V1`–`V10`) y carga
`src/main/resources/db/seed/demo_seed.sql` con el cliente `mysql` del
contenedor. Después guarda un `mysqldump` del estado sembrado y **restaura ese
baseline antes de cada test**, de modo que todos los tests ven exactamente el
mismo dataset aunque alguno cree o modifique datos. El contenedor se elimina al
final de la ejecución (hook de apagado y limpieza del script).

Requisitos del entorno: los descritos en
[Dependencias del sistema para JavaFX y los tests de interfaz](#dependencias-del-sistema-para-javafx-y-los-tests-de-interfaz).
Para usar el script en un servidor sin pantalla hacen falta `xvfb` o
`xvfb-run` (si no está `xvfb-run`, el script arranca `Xvfb` directamente y usa
`xdpyinfo` para comprobar que está listo). Para ejecutar sobre el display actual:
`UI_USE_EXISTING_DISPLAY=1 scripts/run-ui-tests.sh`.

La suite cubre las 12 pantallas y los casos de uso de negocio completos:
login/onboarding, menú por rol, usuarios, categorías, proveedores, clientes,
productos, compras (pendiente → recibida → devuelta/cancelada), pedidos (con
consumo FIFO), stock/alertas e informes con exportación CSV/PDF.

### Convención de idiomas

Los identificadores, el Javadoc y los registros de log están en inglés; los
textos que ve el usuario (etiquetas, mensajes y títulos de ventana) están en
español.

## Regenerar las capturas del manual

Las imágenes de este documento las produce la aplicación real. El generador es el
test `src/test/java/com/entidaddesconocida/monte_erp/ui/ManualCaptureUiTest.java`,
que recorre la aplicación contra un MySQL efímero y guarda un PNG por pantalla en
`docs/manual_de_usuario/img`.

```bash
scripts/run-ui-tests.sh --manual
```

- Requiere **Docker** y un servidor gráfico (el script arranca su propio **Xvfb**).
- Guarda las capturas en `docs/manual_de_usuario/img` (se puede cambiar con
  `-Dmanual.screenshots.dir=…`).
- El test **no forma parte** de la suite normal: solo se ejecuta con
  `-Dmanual.capture=true`, así que `scripts/run-ui-tests.sh` (sin `--manual`) y
  `./mvnw test` no lo tocan.

Cada captura va precedida por las mismas aserciones que usan los tests de interfaz,
de modo que si un cambio en la aplicación altera una pantalla, el generador falla
en lugar de guardar una imagen equivocada. Si cambias textos o flujos de la
interfaz, actualiza también el apartado correspondiente de este manual.


