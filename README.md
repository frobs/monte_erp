# Monte ERP

Aplicación de escritorio ERP construida con **Java 25** y **JavaFX 21**, gestionada con **Maven**.

---

## Tabla de contenidos

1. [Requisitos previos](#requisitos-previos)
2. [Estructura del proyecto](#estructura-del-proyecto)
3. [Compilar y empaquetar](#compilar-y-empaquetar)
4. [Ejecutar la aplicación](#ejecutar-la-aplicación)
5. [Base de datos MySQL con Docker](#base-de-datos-mysql-con-docker)
6. [Conexión de la aplicación a MySQL](#conexión-de-la-aplicación-a-mysql)

---

## Requisitos previos

| Herramienta     | Versión mínima   | Instalación                                                                                         |
| --------------- | ---------------- | --------------------------------------------------------------------------------------------------- |
| JDK             | 25               | [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) o [OpenJDK](https://openjdk.org/) |
| Maven           | 3.9+             | [Maven download](https://maven.apache.org/download.cgi) o usar el wrapper `./mvnw` incluido         |
| Docker          | 24+              | [Docker Desktop](https://www.docker.com/products/docker-desktop/)                                   |

> **Nota:** El proyecto incluye el wrapper de Maven (`mvnw` / `mvnw.cmd`), por lo que **no es necesario instalar Maven** globalmente. Basta con tener el JDK.

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
├── scripts/run-ui-tests.sh                        # Suite de interfaz con Xvfb + MySQL efímero
└── target/                  # Artefactos generados (tras compilar)
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

Requisitos del entorno: Docker y las librerías de GTK3 que necesita JavaFX en
Linux. Para usar el script en un servidor sin pantalla hacen falta `xvfb` o
`xvfb-run` (si no está `xvfb-run`, el script arranca `Xvfb` directamente). Para
ejecutar sobre el display actual: `UI_USE_EXISTING_DISPLAY=1 scripts/run-ui-tests.sh`.

La suite cubre las 12 pantallas y los casos de uso de negocio completos:
login/onboarding, menú por rol, usuarios, categorías, proveedores, clientes,
productos, compras (pendiente → recibida → devuelta/cancelada), pedidos (con
consumo FIFO), stock/alertas e informes con exportación CSV/PDF.

### Convención de idiomas

Los identificadores, el Javadoc y los registros de log están en inglés; los
textos que ve el usuario (etiquetas, mensajes y títulos de ventana) están en
español.


