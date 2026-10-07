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
│   │   │       ├── app/                           # AppContext, AppShell, SessionState, SceneNavigator
│   │   │       ├── view/                          # LoginView, SetupView, MainView, UsersView (code-behind FXML)
│   │   │       ├── viewmodel/                     # LoginViewModel, SetupViewModel, MainViewModel, UsersViewModel
│   │   │       ├── domain/
│   │   │       │   ├── model/                     # User
│   │   │       │   └── repository/                # UserRepository (interfaz de puerto)
│   │   │       ├── infrastructure/                # MySqlUserRepository (implementación JDBC)
│   │   │       │   └── config/                    # DatabaseConnection, DatabaseConfig, DatabaseSetupService, MigrationRunner
│   │   │       └── security/                      # PasswordHasher
│   │   └── resources/
│   │       ├── application.properties             # URL, usuario y contraseña de la BD
│   │       ├── css/app.css                        # Estilos de la interfaz
│   │       ├── db/migration/V1__schema.sql        # Migración: esquema completo
│   │       └── com/entidaddesconocida/monte_erp/view/
│   │           ├── LoginView.fxml                 # vista de login (mismo paquete que LoginView)
│   │           ├── SetupView.fxml                 # vista de configuración de BD
│   │           ├── MainView.fxml                  # vista principal (mismo paquete que MainView)
│   │           └── UsersView.fxml                 # vista de gestión de usuarios (RF-02)
│   └── test/java/...                              # Pruebas JUnit 5 (unitarias + integración)
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

La conexión se configura en `src/main/resources/application.properties`:

| Propiedad       | Valor por defecto                                | Descripción                  |
| --------------- | ------------------------------------------------ | ---------------------------- |
| `db.url`        | `jdbc:mysql://localhost:3306/monte_erp?...`      | URL JDBC de la base de datos |
| `db.user`       | `erp_user`                                       | Usuario de la base de datos  |
| `db.password`   | `erp_pass`                                       | Contraseña del usuario       |

`DatabaseConnection` (paquete `infrastructure/config`) es el único punto que lee
ese fichero y reparte la conexión compartida al resto de la aplicación.

### Migraciones

Los scripts `.sql` versionados viven en `src/main/resources/db/migration` y
siguen la convención `V<n>__<descripcion>.sql`. Al arrancar, `MigrationRunner`
aplica en orden los que estén pendientes y registra cada uno en la tabla de
control `schema_migrations`, por lo que arrancar la aplicación varias veces no
repite trabajo. Para cambiar el esquema basta con añadir `V2__<descripcion>.sql`.

Esquema creado por `V1__schema.sql` (10 tablas):

| Tabla                 | Contenido                                                       |
| --------------------- | --------------------------------------------------------------- |
| `CATEGORY`            | Categorías de producto                                          |
| `SUPPLIER`            | Proveedores                                                     |
| `PRODUCT`             | Productos (el stock no es un campo: se calcula desde sus lotes) |
| `PURCHASE`            | Compras a proveedores                                           |
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


