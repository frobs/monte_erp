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
│   │   │       │   ├── model/                     # User, Category, Supplier, Product, Purchase, Stock, StockLevel, ProductSupplier, Customer, SalesOrder, SalesOrderLine, StockIssue, ReportPeriod y modelos de informe
│   │   │       │   ├── repository/                # UserRepository, CategoryRepository, SupplierRepository, ProductRepository, PurchaseRepository, StockRepository, CustomerRepository, SalesOrderRepository (puertos)
│   │   │       │   └── service/                   # SetupService, FifoAllocator, SalesReportCalculator, StockReportCalculator
│   │   │       ├── infrastructure/                # MySqlUserRepository, MySqlCategoryRepository, MySqlSupplierRepository, MySqlProductRepository, MySqlCustomerRepository, MySqlPurchaseRepository, MySqlStockRepository, MySqlSalesOrderRepository
│   │   │       │   └── config/                    # DatabaseConnection, DatabaseConfig, DatabaseSetupService, MigrationRunner, keyring
│   │   │       └── security/                      # PasswordHasher
│   │   └── resources/
│   │       ├── css/app.css                        # Estilos de la interfaz
│   │       ├── db/migration/V1..V8__*.sql         # Migraciones: esquema + bajas lógicas (PRODUCT, CATEGORY, SUPPLIER, CUSTOMER) + dirección de proveedor + estados de compra
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
│   └── test/java/...                              # Pruebas JUnit 5 unitarias (fakes en memoria y ficheros temporales)
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

```bash
./mvnw test
```

Todos los tests son unitarios (fakes en memoria y ficheros temporales): no
necesitan MySQL ni el toolkit de JavaFX.

### Convención de idiomas

Los identificadores, el Javadoc y los registros de log están en inglés; los
textos que ve el usuario (etiquetas, mensajes y títulos de ventana) están en
español.


