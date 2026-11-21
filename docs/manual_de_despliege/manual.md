# Manual de despliegue — Monte ERP

**Aplicación:** Monte ERP `1.0-SNAPSHOT`
**Ámbito:** preparación del entorno, compilación, instalación, configuración,
actualización, operación y validación de la aplicación.

Monte ERP es una **aplicación de escritorio** JavaFX que se conecta directamente
a un servidor MySQL. No hay servidor de aplicaciones, API HTTP ni proceso
central: cada puesto de trabajo ejecuta el cliente y comparte la base de datos.
Este manual explica cómo dejar ese escenario funcionando de forma reproducible.

> Documentación relacionada:
> [Manual de usuario](../manual_de_usuario/manual.md) ·
> [README del proyecto](../../README.md)

## Índice

1. [Resumen y alcance](#1-resumen-y-alcance)
2. [Requisitos del entorno](#2-requisitos-del-entorno)
3. [Arquitectura y decisiones técnicas](#3-arquitectura-y-decisiones-técnicas)
4. [Preparación de la base de datos](#4-preparación-de-la-base-de-datos)
5. [Compilación y empaquetado](#5-compilación-y-empaquetado)
6. [Instalación y primera ejecución](#6-instalación-y-primera-ejecución)
7. [Operación](#7-operación)
8. [Actualización y vuelta atrás](#8-actualización-y-vuelta-atrás)
9. [Pruebas, TestFX y integración continua](#9-pruebas-testfx-e-integración-continua)
10. [Verificación posterior al despliegue](#10-verificación-posterior-al-despliegue)
11. [Limitaciones conocidas](#11-limitaciones-conocidas)
12. [Resolución de problemas](#12-resolución-de-problemas)
13. [Anexos](#13-anexos)

---

## 1. Resumen y alcance

### 1.1. Topología

```text
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ Puesto de trabajo            │        │ Servidor de base de datos    │
│                              │        │                              │
│  Monte ERP (JavaFX)          │  JDBC  │  MySQL 8.0                   │
│  Java 25 / JavaFX 21         │ ─────► │  esquema monte_erp           │
│                              │ 3306   │  utf8mb4_unicode_ci          │
│  application.properties      │        │  InnoDB                      │
│  (solo db.url)               │        │                              │
│                              │        │                              │
│  Keyring del sistema         │        │                              │
│  db.user / db.password       │        │                              │
└──────────────────────────────┘        └──────────────────────────────┘
```

- La **URL JDBC** se guarda en un `application.properties`. En desarrollo vive
  en el directorio de trabajo; en una app empaquetada con `jpackage`, en el
  directorio de configuración del usuario.
- El **usuario y la contraseña** de MySQL se guardan en el keyring del sistema
  operativo del usuario que ejecuta la aplicación. Nunca se escriben en el
  fichero de propiedades ni en el repositorio.
- Cada operación de base de datos abre y cierra su propia conexión JDBC.
- La aplicación aplica las migraciones pendientes al arrancar.

### 1.2. Qué se despliega y qué no

| Elemento | Se despliega | Notas |
| --- | --- | --- |
| Cliente JavaFX | Sí | Fat JAR, app-image de `jpackage` o instalador nativo. |
| Runtime de Java | Depende | El fat JAR necesita JDK 25 en el destino; la app-image de `jpackage` lo incluye. La imagen `jlink` de la aplicación no es generable con los módulos automáticos actuales. |
| Base de datos MySQL | Sí, una por instalación | Puede ser compartida por varios clientes. |
| Keyring del sistema | Sí, por usuario | Windows Credential Manager, macOS Keychain o Secret Service/KWallet. |
| Servidor de aplicaciones | No | No existe: el cliente habla directamente con MySQL. |
| Instalador nativo (`.msi`, `.dmg`, `.deb`, `.rpm`) | Opcional | Se genera con `jpackage` en la plataforma de destino; requiere sus herramientas nativas. |
| Actualización automática | No | La actualización es manual y controlada. |
| Servicio del sistema | No recomendado | Necesita sesión gráfica y keyring desbloqueado. |

---

## 2. Requisitos del entorno

### 2.1. Requisitos comunes

| Componente | Versión / condición | Uso |
| --- | --- | --- |
| JDK | **25** | Compilar, ejecutar el fat JAR y usar `jpackage`/`jlink`. |
| Maven | **3.9+** o el wrapper `./mvnw` / `mvnw.cmd` | Compilar y empaquetar. |
| MySQL | **8.0** | Persistencia. El driver incluido es `mysql-connector-j` 8.4.0. |
| JavaFX | **21.0.6** | Se descarga con Maven; no hay que instalar el SDK. |
| Keyring | Backend operativo y sesión desbloqueada | Guardar credenciales de MySQL. |
| Pantalla | Sesión gráfica X11/XWayland, Windows o macOS | La aplicación es de escritorio. |
| Red | Acceso TCP al puerto de MySQL | Normalmente `3306`. |

El proyecto incluye el **Maven Wrapper**, así que no hace falta instalar Maven
globalmente. Para compilar sí se necesita un JDK 25.

### 2.2. Requisitos por sistema operativo

| Sistema | Cliente | Keyring | Matices |
| --- | --- | --- | --- |
| Windows 10/11 x64 | JDK 25 | Administrador de credenciales | JavaFX incluye sus DLL; no hay paquetes extra. |
| macOS 11+ Intel/Apple Silicon | JDK 25 | Llavero del usuario | JavaFX incluye sus `.dylib`. |
| Linux x86_64/aarch64 (glibc) | JDK 25 + GTK 3 + X11/XWayland + `libGL` | Secret Service (`gnome-keyring`) o KWallet 5 | No usar Alpine/musl con los binarios oficiales de JavaFX. |

En Linux mínimo, las bibliotecas que enlaza JavaFX son, entre otras:
`libgtk-3`, `libX11`, `libxcb`, `libXxf86vm`, `libXtst`, `libGL`, FreeType y
fontconfig. En Debian/Ubuntu 12/22.04:

```bash
sudo apt install libgtk-3-0 libx11-6 libxcb1 libxxf86vm1 libxtst6 \
  libgl1 libfreetype6 libfontconfig1 fonts-dejavu-core
```

En Debian 13 / Ubuntu 24.04 o posterior, los paquetes `t64` sustituyen a los
anteriores (`libgtk-3-0t64`, etc.).

La aplicación necesita una **sesión D-Bus** y un proveedor de Secret Service.
Si el keyring no está disponible, la aplicación arranca pero no puede guardar la
conexión; el onboarding mostrará el error correspondiente.

### 2.3. Requisitos solo para pruebas

| Componente | Uso |
| --- | --- |
| Docker 24+ con daemon accesible | La suite de interfaz levanta un MySQL efímero. |
| `xvfb` / `xvfb-run`, `xauth`, `xdpyinfo` | Ejecutar TestFX sin pantalla en Linux. |
| Sesión gráfica o Xvfb | TestFX conduce una ventana JavaFX real. |
| Permiso de Accesibilidad (macOS) | Si el robot no recibe eventos. |

Estos requisitos **no** son necesarios para ejecutar la aplicación en producción.

---

## 3. Arquitectura y decisiones técnicas

### 3.1. Vista de capas

```text
┌──────────────────────────────────────────────────────────────┐
│ view/          FXML + code-behind (FxmlView)                 │
│                Bindings, eventos, diálogos, FileChooser      │
├──────────────────────────────────────────────────────────────┤
│ viewmodel/     Estado observable + casos de uso de pantalla  │
│                Sin referencias a la View                     │
├──────────────────────────────────────────────────────────────┤
│ app/           AppContext (composition root), AppShell,      │
│                SceneNavigator, SessionState, BackgroundTasks │
├──────────────────────────────────────────────────────────────┤
│ domain/        model/ + repository/ (puertos) + service/     │
│                Reglas de negocio, estados, FIFO, informes    │
├──────────────────────────────────────────────────────────────┤
│ infrastructure/ MySql*Repository, DatabaseConnection,        │
│                DatabaseConfig, MigrationRunner, keyring       │
└──────────────────────────────────────────────────────────────┘
```

La dirección de dependencia es **UI → dominio**. La infraestructura implementa
los puertos del dominio; el dominio no depende de JavaFX ni de MySQL. El punto
de composición es `MonteErpApplication`, que construye el grafo y lo entrega a
`AppContext`.

### 3.2. Registro de decisiones

| ID | Decisión | Motivo | Consecuencia |
| --- | --- | --- | --- |
| D1 | **Java 25 + JavaFX 21 + JPMS** (`module-info.java`) | Aprovechar el JDK moderno y declarar explícitamente las dependencias de módulo. | Hay que cuidar los *automatic modules* y sus nombres; el arranque falla pronto si falta un módulo. |
| D2 | **MVVM con mvvmFX** | Separar estado y presentación sin acoplar la View al controlador; probar ViewModels sin toolkit. | La View y el ViewModel deben registrarse en `AppContext`; los recursos FXML viven junto a la View. |
| D3 | **Inyección explícita con `AppContext`** | Grafo pequeño, sin escaneo de classpath ni framework DI pesado; errores de wiring visibles al arrancar. | Cada nueva View/ViewModel debe añadirse al contenedor; si falta, falla con un mensaje claro. |
| D4 | **Arquitectura por capas + puertos/adaptadores** | Aislar reglas de negocio y permitir tests con dobles. | Más interfaces y mapeo manual, a cambio de un dominio testeable. |
| D5 | **JDBC + SQL explícito, sin ORM** | Esquema pequeño, consultas concretas, control total de transacciones y del plan de ejecución. | No hay *lazy loading* ni caché de segundo nivel; el mapeo y el SQL se mantienen a mano. |
| D6 | **Conexión por operación, sin pool** | Evitar una conexión global mutable y transacciones que se pisan; el coste es aceptable contra MySQL local. | Más aperturas de conexión; si el servidor pasa a ser remoto y con mucha latencia, habría que evaluar un pool. |
| D7 | **Migraciones SQL propias** (`MigrationRunner`) | Sin dependencia de Flyway/Liquibase; funciona igual en classpath explotado y dentro del JAR. | No hay migraciones *down*, checksums ni bloqueo entre instancias. |
| D8 | **Transacciones y bloqueo pesimista para stock** | La venta y el consumo FIFO deben ser atómicos y no repartir las mismas unidades entre dos pedidos. | Las líneas se releen con `SELECT … FOR UPDATE`; una venta sin stock se rechaza completa. |
| D9 | **Bajas lógicas en maestros** | Conservar histórico y trazabilidad; las claves foráneas son `ON DELETE RESTRICT`. | Los listados filtran por `active`; no se borran físicamente categorías, productos, etc. |
| D10 | **Estado y transiciones en enums del dominio** | Un cambio imposible (`delivered → pending`, etc.) no se puede representar. | Las reglas viven en `OrderStatus` y `PurchaseStatus`, no en comparaciones de cadenas. |
| D11 | **Configuración dividida: URL en fichero, credenciales en keyring** | Evitar secretos en disco y en el repositorio. | El keyring debe estar disponible; cambiar de equipo exige volver a introducir la contraseña. |
| D12 | **Contraseñas con PBKDF2-HMAC-SHA256** | Hash lento con sal aleatoria y comparación en tiempo constante, sin dependencias externas. | 210 000 iteraciones, sal de 16 bytes y clave de 256 bits; el login tiene coste deliberado. |
| D13 | **Trabajo JDBC en hilos virtuales** | No congelar la interfaz con consultas lentas o bloqueos. | Las consultas salen del hilo JavaFX y los resultados se publican con `Platform.runLater`. |
| D14 | **Dos suites: unitaria y UI/E2E** | La unitaria es rápida y sin Docker; la de interfaz valida el cableado real y SQL real. | La suite UI es *opt-in*, necesita Docker y pantalla. |
| D15 | **Fat JAR + `jpackage` para distribución** | `jpackage` empaqueta el fat JAR en el classpath y genera un runtime; evita el problema de los módulos automáticos con `jlink`. | La app-image no necesita JDK en destino; los instaladores requieren herramientas nativas y se construyen por plataforma. `jlink` queda configurado pero no operativo. |
| D16 | **Informes CSV/PDF generados en el cliente** | Exportación local sin servicios externos. | CSV con BOM UTF-8 y `;` para Excel en español; PDF con OpenPDF. |

### 3.3. MVVM con mvvmFX

La interfaz sigue el patrón **Model-View-ViewModel** con
[mvvmFX](https://github.com/sialcasa/mvvmFX) 1.8.0:

- Cada pantalla tiene un **FXML** y una **View** que implementa
  `de.saxsys.mvvmfx.FxmlView<VM>`. La View es el *code-behind*: declara los
  controles con `@FXML`, recibe su ViewModel con `@InjectViewModel` y configura
  los bindings en `initialize()`.
- Cada pantalla tiene un **ViewModel** que implementa
  `de.saxsys.mvvmfx.ViewModel`. Expone propiedades JavaFX (`StringProperty`,
  `ObjectProperty`, `ObservableList`), ejecuta los casos de uso y **no conoce la
  View**.
- La navegación se delega en `AppShell`. Los mensajes puntuales se publican con
  `publish(...)` de mvvmFX (por ejemplo, `LOGIN_SUCCEEDED`), y la View o el
  `AppShell` se suscriben.
- `FluentViewLoader.fxmlView(XView.class).load()` carga la pareja View/FXML y
  devuelve un `ViewTuple`; `SceneNavigator` reemplaza la raíz de la escena.
- `MvvmFX.setCustomDependencyInjector(context::create)` conecta mvvmFX con
  `AppContext`. Este contenedor:
  - registra como **singleton** los recursos sin estado (repositorios,
    configuración, `SetupService`);
  - registra las **Views y ViewModels con factorías** para que cada carga
    obtenga una instancia nueva y no se compartan estados entre visitas;
  - inyecta `SessionState`/`SessionValidator` y `BackgroundTasks` donde
    corresponde.

Un esquema mínimo de una pantalla:

```java
public class LoginView implements FxmlView<LoginViewModel> {

    @FXML private TextField usernameField;
    @InjectViewModel private LoginViewModel viewModel;

    @FXML
    private void initialize() {
        usernameField.textProperty()
                .bindBidirectional(viewModel.usernameProperty());
        viewModel.subscribe(LoginViewModel.LOGIN_SUCCEEDED,
                (key, payload) -> appShell.showMain((User) payload[0]));
    }
}
```

**Por qué mvvmFX y no un controlador FXML clásico:** evita que la vista y la
lógica compartan un objeto mutable, permite probar los ViewModels con JUnit sin
arrancar el toolkit y deja la composición de dependencias en un único sitio.
También permite que las notificaciones y la navegación no acoplen las pantallas
entre sí.

**Restricciones de mvvmFX con JPMS:**

- `de.saxsys.mvvmfx` es un *automatic module*: el JAR declara
  `Automatic-Module-Name` pero no incluye `module-info.class`.
- Los artefactos `mvvmfx-validation` y `mvvmfx-easydi` **no se usan**: su nombre
  automático contiene un guion (`mvvmfx-validation`), que no es un identificador
  Java válido en el *module path*.
- `module-info.java` abre `view` a `de.saxsys.mvvmfx` y `javafx.fxml` (para
  `@FXML`), y `viewmodel` a `de.saxsys.mvvmfx` (para instanciar ViewModels e
  inyectar `@InjectViewModel`).
- Se fija `slf4j-api` 1.7.36 y se añade `slf4j-jdk14` para que los logs de
  mvvmFX salgan por el mismo `java.util.logging` del proyecto.

### 3.4. Persistencia y modelo de datos

La persistencia es **MySQL 8.0 + JDBC**, sin ORM:

- `DatabaseConnection` es el único punto que abre conexiones. Ofrece
  `withConnection(...)` y `withTransaction(...)`; cada operación usa su propia
  conexión y la cierra al terminar.
- Los repositorios (`MySqlUserRepository`, `MySqlProductRepository`, …)
  implementan los puertos de `domain/repository` y usan `PreparedStatement`.
- Las escrituras compuestas (pedido + líneas + consumos FIFO, recepción o
  devolución de compra) se ejecutan en **una transacción**.
- Los filtros de los listados se aplican **en SQL**, no en memoria. Los textos se
  comparan con la colación `utf8mb4_unicode_ci` (insensible a mayúsculas y
  acentos) y los caracteres `%`/`_` se escapan para tratarlos como literales.
- El stock **no es una columna de `PRODUCT`**: se calcula a partir de los lotes
  de `STOCK`. Cada lote procede de una compra y conserva su precio de compra.
- La asignación FIFO está en `domain/service/FifoAllocator`, una función pura
  compartida por la ViewModel (aviso previo) y el repositorio (autoridad dentro
  de la transacción). El repositorio relee los lotes con `FOR UPDATE` y aplica
  `Stock.consume(...)` solo si la transacción termina bien.
- Los estados de pedidos y compras son enums con transiciones declaradas:
  - pedido: `pending → shipped/cancelled` y `shipped → delivered/cancelled`;
  - compra: `pending → received/cancelled` y `received → returned`.
- Los maestros usan **baja lógica** (`active`), no borrado físico. Las claves
  foráneas usan `ON DELETE RESTRICT` para no romper el histórico.
- Los cambios de estado usan una **guardia optimista**: la sentencia solo
  actualiza si el estado actual coincide con el que vio el usuario.

**Por qué no hay ORM:** el esquema es pequeño, las consultas son concretas y el
comportamiento transaccional del stock es parte del negocio. Un ORM añadiría
una capa de mapeo y *lazy loading* que aquí no aporta, y dificultaría ver el SQL
real y los bloqueos. El coste es mantener el mapeo y las consultas a mano.

**Por qué no hay pool:** una conexión global mutable obligaba a cambiar
`autoCommit` sobre estado compartido y solo funcionaba si todo el trabajo ocurría
en el hilo de JavaFX. Abrir una conexión por operación elimina esa contaminación
cruzada y hace que una conexión caída sea un fallo de la operación, no un estado
global envenenado. A la escala de una tienda y con MySQL local, el coste es
irrelevante; si el servidor pasa a estar lejos, habría que reevaluarlo.

**Tablas del esquema** (creadas por `V1__schema.sql`):

`CATEGORY`, `SUPPLIER`, `PRODUCT`, `PURCHASE`, `STOCK`, `CUSTOMER`,
`SALES_ORDER`, `SALES_ORDER_LINE`, `STOCK_ISSUE` y `USER`. La tabla de control
`schema_migrations` la crea `MigrationRunner`.

### 3.5. Migraciones

Los scripts viven en `src/main/resources/db/migration` y siguen la convención
`V<n>__<descripcion>.sql`. Al arrancar, `MigrationRunner`:

1. crea la tabla `schema_migrations` si no existe;
2. lee las versiones ya aplicadas;
3. localiza los scripts en el *classpath* explotado o dentro del JAR;
4. aplica en orden numérico los pendientes y registra cada versión.

Es **idempotente** frente a reinicios: una versión ya registrada no se repite.
Para evolucionar el esquema se añade un script nuevo; **no se modifica uno ya
aplicado**.

Migraciones actuales:

| Versión | Contenido |
| --- | --- |
| `V1` | Esquema inicial: 10 tablas, InnoDB, `utf8mb4`, claves foráneas y checks. |
| `V2` | `PRODUCT.active` (baja lógica). |
| `V3` | `PRODUCT.category_id` pasa a `NULL` (categoría opcional). |
| `V4` | `CATEGORY.active`. |
| `V5` | `SUPPLIER.active`. |
| `V6` | `CUSTOMER.active`. |
| `V7` | `SUPPLIER.address`. |
| `V8` | `PURCHASE.status` admite `returned`. |
| `V9` | Índice `(purchase_date, status)` para el filtro de compras. |
| `V10` | Índice `(order_date, status)` para el filtro de pedidos. |

**Límites que hay que conocer:**

- No hay migraciones *down*: la vuelta atrás se hace restaurando una copia.
- No hay checksum: si alguien edita un script ya aplicado, el runner no lo
  detecta. Trata los scripts aplicados como inmutables.
- MySQL hace *commit* implícito en cada DDL. El runner envuelve el script en una
  transacción, pero la atomicidad real de un cambio de esquema depende de las
  sentencias del propio script.
- No hay bloqueo entre instancias. Si varios clientes arrancan a la vez contra
  una base con migraciones pendientes, pueden competir. En una actualización,
  arranca **un solo cliente** primero (o aplica los scripts de forma controlada)
  y después despliega el resto.

### 3.6. Configuración y seguridad

La configuración se reparte a propósito:

| Dato | Dónde vive | Clave / cuenta |
| --- | --- | --- |
| URL JDBC | `application.properties`: **directorio de trabajo** en desarrollo; **directorio de configuración del usuario** en la app empaquetada | `db.url` |
| Usuario de MySQL | Keyring del sistema | servicio `monte_erp`, cuenta `db.user` |
| Contraseña de MySQL | Keyring del sistema | servicio `monte_erp`, cuenta `db.password` |

`DatabaseConfig` resuelve el fichero así: un `monte.erp.config.dir` no vacío,
un `MONTE_ERP_CONFIG_DIR` no vacío, el directorio de usuario cuando alguno de
los dos está presente pero vacío (lo que hacen los lanzadores de `jpackage`) y,
si no hay override, el directorio de trabajo de siempre. Escribe el fichero de
propiedades de forma atómica (fichero temporal + `ATOMIC_MOVE`) y guarda primero
las credenciales, después la URL. Si falla una escritura, intenta restaurar los
valores anteriores del keyring. La
URL nunca debe contener credenciales: `DatabaseUrl.containsCredentials(...)` lo
impide y la interfaz redacta los parámetros sensibles de configuraciones
antiguas.

Medidas de seguridad incorporadas:

- **Contraseñas de usuario**: PBKDF2-HMAC-SHA256, 210 000 iteraciones, sal
  aleatoria de 16 bytes, clave de 256 bits, formato autocontenido
  `pbkdf2-sha256$<iteraciones>$<sal>$<clave>` y comparación con
  `MessageDigest.isEqual`.
- **Login genérico**: el mismo mensaje para usuario inexistente, contraseña
  incorrecta o cuenta dada de baja. Siempre se ejecuta una verificación PBKDF2,
  incluso para usuarios inexistentes, para no filtrar el estado de la cuenta.
- **Sesión**: `SessionValidator` relee la cuenta en cada navegación; si fue dada
  de baja, se cierra la sesión. El acceso a gestión de usuarios está protegido
  en la UI y también en `AppShell`.
- **Exportaciones CSV**: las celdas que empiezan por `=`, `+`, `-`, `@`, tabulador
  o retorno de carro se prefijan con un apóstrofo para evitar inyección de
  fórmulas al abrirlas en Excel/LibreOffice.

Recomendaciones de despliegue:

- Usa un **usuario MySQL dedicado** y un esquema dedicado. No uses `root` para la
  aplicación.
- Restringe el origen del usuario (`'erp_user'@'10.0.0.%'`) y limita el puerto a
  la red interna/VPN.
- Para conexiones fuera de la propia máquina, usa **TLS** y valida el
  certificado del servidor (`sslMode=VERIFY_IDENTITY` con la CA correspondiente)
  en lugar de desactivar la verificación.
- El keyring debe estar desbloqueado en la sesión del usuario. Un servicio del
  sistema que arranque antes del login no podrá acceder a él.

### 3.7. Concurrencia e interfaz

Las consultas JDBC y el cálculo PBKDF2 se ejecutan en **hilos virtuales** a
través de `BackgroundTasks`. El resultado o el error se entrega en el hilo de
JavaFX con `Platform.runLater`, que es el único que puede tocar las propiedades
observables y los controles. En tests se inyecta
`BackgroundTasks.synchronous()` para que todo ocurra en línea y las aserciones
no compitan con una tarea en segundo plano.

Consecuencias operativas:

- La ventana sigue respondiendo mientras se carga un informe o se espera un
  bloqueo de MySQL.
- Un fallo de base de datos se muestra como mensaje de error de la pantalla; el
  detalle técnico se registra en el log.
- No hay cancelación de consultas en curso: si MySQL tarda, la operación termina
  o falla por timeout del driver/servidor.

### 3.8. Empaquetado y JPMS

- `module-info.java` declara los módulos requeridos (`javafx.controls`,
  `javafx.fxml`, `javafx.web`, `java.sql`, `mysql.connector.j`, `java.keyring`,
  `de.saxsys.mvvmfx`, `com.github.librepdf.openpdf`, etc.) y abre `view` y
  `viewmodel` a mvvmFX.
- `javafx.swing` **no se referencia desde el código**, pero debe seguir en el
  POM: `eu.hansolo.tilesfx` arrastra `eu.hansolo.fx.countries`, que declara
  `requires javafx.swing`. Sin esa dependencia, el arranque con `javafx:run`
  falla con `Module javafx.swing not found`.
- El fat JAR se genera con **maven-shade-plugin**. El `mainClass` del manifiesto
  es `Launcher`, no la clase `Application`: así el JAR sombreado arranca en el
  *classpath* sin el error de "JavaFX runtime components are missing".
- El shade elimina las firmas `META-INF/*.SF`, `*.DSA`, `*.RSA` y `*.EC` de los
  JAR firmados que fusiona (por ejemplo, dependencias de `java-keyring`). Sin
  ese filtro la JVM aborta con `Invalid signature file digest`.
- Los tests corren en el *classpath* (`useModulePath=false`) para no tener que
  declarar una relación de lectura hacia JUnit en `module-info.java`.

---

## 4. Preparación de la base de datos

### 4.1. Crear esquema y usuario

En el servidor MySQL, con un usuario administrador:

```sql
CREATE DATABASE monte_erp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'erp_user'@'%' IDENTIFIED BY 'CAMBIA_ESTA_CONTRASENA';

GRANT ALL PRIVILEGES ON monte_erp.* TO 'erp_user'@'%';
FLUSH PRIVILEGES;
```

El usuario necesita permisos de **DDL y DML** sobre su esquema porque la
aplicación crea y actualiza tablas al arrancar (`CREATE`, `ALTER`, `INDEX`,
`REFERENCES`), además de `SELECT`, `INSERT`, `UPDATE` y `DELETE`. Si tu política
exige separar la
migración del runtime, aplica los scripts con un usuario de migración y revisa
antes el privilegio mínimo necesario para `schema_migrations`; la aplicación no
incluye un modo "sin DDL".

### 4.2. Contenedor MySQL para desarrollo o pruebas manuales

Para un entorno de desarrollo, no para producción:

```bash
docker run -d \
  --name monte_erp_mysql \
  --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD='root1234' \
  -e MYSQL_DATABASE=monte_erp \
  -e MYSQL_USER=erp_user \
  -e MYSQL_PASSWORD='erp_pass' \
  -e TZ=Europe/Madrid \
  -v monte_erp_data:/var/lib/mysql \
  mysql:8.0
```

Comprueba la conexión:

```bash
docker exec -it monte_erp_mysql \
  mysql -u erp_user -p monte_erp
```

No uses las contraseñas de ejemplo en producción.

### 4.3. Zona horaria y codificación

- El esquema usa `utf8mb4` / `utf8mb4_unicode_ci`. Mantén esa codificación en la
  base y en la conexión (`useUnicode=true`).
- Los históricos usan columnas `DATETIME` y los informes comparan por día. Para
  que cliente, servidor y datos coincidan, configura la misma zona horaria en el
  sistema operativo, la JVM (`-Duser.timezone=Europe/Madrid`) y MySQL
  (`TZ=Europe/Madrid` en el contenedor o `default-time-zone` en el servidor).
- Sincroniza los relojes de los clientes y del servidor (NTP). Una diferencia
  horaria desplaza pedidos, compras y cortes de informes.

### 4.4. Datos de demostración

`src/main/resources/db/seed/demo_seed.sql` contiene un dataset completo para
demos. **No se aplica al arrancar**. Para cargarlo:

1. Arranca la aplicación una vez para que las migraciones creen el esquema.
2. Ejecuta:

```bash
docker exec -i monte_erp_mysql \
  mysql -u erp_user -perp_pass monte_erp \
  < src/main/resources/db/seed/demo_seed.sql
```

El script aborta si la tabla `USER` ya tiene datos y está pensado para una base
recién creada. **No lo ejecutes sobre una base real.**

Usuarios demo: `admin` / `Admin1234` (administrador), `ana.garcia` y
`luis.moreno` / `Demo1234` (empleados), `marta.sanz` / `Demo1234` (inactivo).

---

## 5. Compilación y empaquetado

### 5.1. Compilación y pruebas unitarias

Desde la raíz del proyecto:

```bash
./mvnw clean verify
```

En Windows:

```bat
mvnw.cmd clean verify
```

`verify` compila, ejecuta la suite unitaria (TestFX queda excluido), genera el
informe de cobertura de JaCoCo y empaqueta. Al terminar:

| Archivo | Uso |
| --- | --- |
| `target/monte_erp-1.0-SNAPSHOT.jar` | **Fat JAR** con todas las dependencias. Es la entrada de `jpackage` y también se puede ejecutar directamente. |
| `target/original-monte_erp-1.0-SNAPSHOT.jar` | JAR sin dependencias; no usar. |
| `target/site/jacoco/index.html` | Informe de cobertura. |
| `target/surefire-reports/` | Resultados de los tests. |

Para empaquetar sin repetir pruebas:

```bash
./mvnw clean package -DskipTests
```

No hay un umbral de cobertura configurado que haga fallar el build.

### 5.2. Por qué no se usa `jlink` para la aplicación

El POM conserva configuración de `javafx:jlink` (`jlinkImageName=app`,
`jlinkZipName=app`), pero **la imagen no se puede generar con la versión actual
del proyecto**. `./mvnw javafx:jlink` falla con:

```text
Error: automatic module cannot be used with jlink: java.keyring
```

`jlink` solo acepta módulos explícitos, y varias dependencias necesarias son
módulos automáticos: `java.keyring`, `mysql-connector-j`, `de.saxsys.mvvmfx` y
`com.github.librepdf.openpdf`, entre otras. No hay una opción de `jlink` para
permitirlos. Para una imagen autocontenida se usa **`jpackage`**, que no enlaza
la aplicación (ver 5.3). Si en el futuro se necesita una imagen `jlink` real,
habría que convertir esas dependencias a módulos explícitos con `moditect` o
sustituirlas, y volver a probar el enlace; no es el camino soportado ahora.

### 5.3. Empaquetado con `jpackage` (recomendado)

`jpackage` **no enlaza la aplicación**: empaqueta el fat JAR en el *classpath* de
la app-image y crea un runtime con módulos del JDK. Por eso los módulos
automáticos dejan de ser un bloqueo.

El proyecto incluye dos lanzadores:

| Script | Plataforma |
| --- | --- |
| `scripts/package-app-image.sh` | Linux y macOS |
| `scripts/package-app-image.ps1` | Windows (PowerShell) |

Ambos ejecutan por defecto `./mvnw clean verify` (suite unitaria incluida),
preparan `target/jpackage-input` con el fat JAR y dejan el resultado en
`target/jpackage`. El tipo por defecto es **`app-image`**, que no necesita
herramientas nativas de instalación.

Linux/macOS:

```bash
scripts/package-app-image.sh                    # app-image
scripts/package-app-image.sh --skip-tests       # sin repetir tests
scripts/package-app-image.sh --type deb         # Debian/Ubuntu
scripts/package-app-image.sh --type rpm         # openSUSE/Fedora/RHEL
scripts/package-app-image.sh --type dmg         # macOS
```

Windows:

```powershell
.\scripts\package-app-image.ps1                  # app-image
.\scripts\package-app-image.ps1 -SkipTests      # sin repetir tests
.\scripts\package-app-image.ps1 -Type msi       # instalador MSI
.\scripts\package-app-image.ps1 -Type exe       # instalador EXE
```

Si PowerShell bloquea el script por la política de ejecución:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-app-image.ps1
```

Resultado habitual:

| Plataforma | Lanzador / artefacto |
| --- | --- |
| Linux | `target/jpackage/MonteERP/bin/MonteERP` |
| macOS | `target/jpackage/MonteERP.app/Contents/MacOS/MonteERP` |
| Windows | `target\jpackage\MonteERP\MonteERP.exe` |
| Instaladores | `target/jpackage/` (`.deb`, `.rpm`, `.msi`, `.exe`, `.dmg`...) |

La app-image incluye el runtime: **el destino no necesita JDK**. En la prueba de
este proyecto ocupa unos 200 MB (fat JAR + runtime).

Opciones útiles de los scripts:

- `APP_VERSION` / `-AppVersion` (por defecto `1.0.0`): `jpackage` no debe recibir
  la versión `SNAPSHOT`; fija una versión de release al distribuir.
- `APP_NAME`, `APP_VENDOR` / `-AppName`, `-Vendor`.
- `JPACKAGE_SW_RENDER=1`: añade `-Dprism.order=sw` para equipos sin GPU.
- `MONTE_ERP_CONFIG_DIR=/ruta`: embebe un directorio de configuración absoluto.
  Sin ella se embebe `-Dmonte.erp.config.dir=` y la app usa el directorio de
  configuración del usuario. También se puede definir `MONTE_ERP_CONFIG_DIR`
  en el entorno de ejecución.
- `JPACKAGE_EXTRA_ARGS` / `$env:JPACKAGE_EXTRA_ARGS`: argumentos adicionales de
  `jpackage` (icono, firma, etc.).

Los scripts pasan `--add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata`
para que el runtime incluya SQL, escritorio, logging, criptografía TLS y datos de
localización en español.

**Requisitos de los instaladores nativos:** se generan en la plataforma de
destino. `.deb` necesita `dpkg-deb` y `fakeroot`; `.rpm`, `rpmbuild`; `.msi`/`.exe`,
WiX Toolset; `.dmg`/`.pkg`, las herramientas de macOS. La app-image no necesita
ninguna de ellas.

La lista de tipos la decide el `jpackage` de cada JDK: comprueba
`jpackage --help`. En Linux suele ofrecer `app-image`, `deb` y `rpm`; en macOS,
`app-image`, `dmg` y `pkg`; en Windows, `app-image`, `msi` y `exe`. Si `rpm` no
aparece en la lista, ese JDK no puede generar RPM: usa `app-image`, `deb` o un
JDK 25 con soporte RPM. Si aparece pero `jpackage` responde
`Invalid or unsupported type: [rpm]`, normalmente falta `rpmbuild`:

```bash
# openSUSE
sudo zypper install rpm-build

# Fedora / RHEL
sudo dnf install rpm-build

# Debian / Ubuntu (para .deb)
sudo apt install dpkg-dev fakeroot
```

Los scripts validan el tipo y las herramientas antes de compilar.

**Aviso de JavaFX:** al cargar JavaFX desde el *classpath*, la aplicación muestra
`Unsupported JavaFX configuration: classes were loaded from unnamed module`. No
impide el arranque; eliminarlo exigiría mantener JavaFX en el *module path* o
convertir las dependencias a módulos explícitos (5.2).

### 5.4. Plataforma del artefacto

> **Importante:** el fat JAR solo contiene las bibliotecas nativas de JavaFX de
> la plataforma donde se ejecutó `mvn package`. No es portable entre Windows,
> macOS y Linux ni entre x86_64 y ARM.

Compila en el mismo sistema operativo y arquitectura de destino. Para distribuir
a varios sistemas, produce un artefacto por plataforma (JAR, app-image o
instalador) y asígnales un nombre inequívoco, por ejemplo:

```text
monte-erp-1.0.0-linux-x86_64.jar
monte-erp-1.0.0-windows-x64.jar
monte-erp-1.0.0-macos-aarch64.jar
```

Para app-image e instaladores, usa la misma convención (`MonteERP-1.0.0-linux-x86_64`,
`MonteERP-1.0.0-windows-x64`, etc.) y guarda también su `sha256`.

Guarda el `sha256sum` junto al artefacto para verificar la copia:

```bash
sha256sum target/monte_erp-1.0-SNAPSHOT.jar > monte-erp.sha256
```

---

## 6. Instalación y primera ejecución

### 6.1. Directorios recomendados

Separa los **binarios** (pueden ser de solo lectura) de los **datos de usuario**.
La ubicación de `application.properties` depende de cómo se arranque:

| Forma de arranque | Ubicación de `application.properties` |
| --- | --- |
| Fat JAR desde Maven o `java -jar` | Directorio de trabajo actual. |
| App-image o instalador `jpackage` | Directorio de configuración del usuario. |
| Override explícito | `monte.erp.config.dir` o `MONTE_ERP_CONFIG_DIR`. |

Directorios de configuración por plataforma para la app empaquetada:

| Plataforma | Directorio |
| --- | --- |
| Linux | `$XDG_CONFIG_HOME/monte-erp` (por defecto `~/.config/monte-erp`) |
| Windows | `%APPDATA%\Monte ERP` |
| macOS | `~/Library/Application Support/Monte ERP` |

Instalación manual del fat JAR (Linux):

```text
/opt/monte-erp/                              # binarios
  monte_erp-1.0-SNAPSHOT.jar
~/.local/share/monte-erp/                    # datos y configuración si se arranca desde aquí
  application.properties
```

Windows:

```text
C:\Program Files\MonteERP\                   # instalación (app-image o fat JAR)
  MonteERP.exe                              # app-image (o el JAR si es manual)
%APPDATA%\Monte ERP\                         # configuración de la app empaquetada
  application.properties
```

macOS:

```text
/Applications/MonteERP.app/                  # app-image o instalador
~/Library/Application Support/Monte ERP/     # configuración de la app empaquetada
  application.properties
```

La app-image de `jpackage` se puede distribuir como carpeta autocontenida (por
ejemplo en `~/.local/opt/MonteERP` o en `C:\Program Files\Monte ERP`) sin que el
usuario necesite un JDK.

### 6.2. Arranque

Fat JAR:

```bash
cd "$HOME/.local/share/monte-erp"
java -Duser.language=es -Duser.country=ES -Duser.timezone=Europe/Madrid \
  -jar /opt/monte-erp/monte_erp-1.0-SNAPSHOT.jar
```

App-image o instalación de `jpackage`:

```bash
# Linux: app-image recién generada
target/jpackage/MonteERP/bin/MonteERP

# Linux: instalada con .deb/.rpm (ruta habitual)
/opt/MonteERP/bin/MonteERP

# macOS
/Applications/MonteERP.app/Contents/MacOS/MonteERP
```

```bat
REM Windows: instalador MSI/EXE (ruta habitual)
"C:\Program Files\MonteERP\MonteERP.exe"
```

La app empaquetada ya usa el directorio de configuración del usuario; no hace
falta arrancarla desde una carpeta concreta. Para forzar otra carpeta:

```bash
MONTE_ERP_CONFIG_DIR="$HOME/.config/monte-erp" /opt/MonteERP/bin/MonteERP
```

En Windows, si arrancas el fat JAR manualmente, el acceso directo debe tener
configurado **"Iniciar en"** con el directorio de datos. En Linux, un lanzador
`.desktop` para el fat JAR puede usar `Path=`:

```ini
[Desktop Entry]
Type=Application
Name=Monte ERP
Exec=java -Duser.timezone=Europe/Madrid -jar /opt/monte-erp/monte_erp-1.0-SNAPSHOT.jar
Path=/home/usuario/.local/share/monte-erp
Terminal=false
Categories=Office;
```

Sin GPU, escritorio remoto o máquina virtual, añade renderizado software. Con el
fat JAR:

```bash
java -Dprism.order=sw -Duser.timezone=Europe/Madrid \
  -jar /opt/monte-erp/monte_erp-1.0-SNAPSHOT.jar
```

Con `jpackage`, genera la app-image con `JPACKAGE_SW_RENDER=1` para que el
lanzador incluya `-Dprism.order=sw`.

### 6.3. Primera ejecución y onboarding

En el primer arranque, si no hay configuración completa, la aplicación abre la
pantalla de **Configuración inicial**:

1. Introduce la URL JDBC, por ejemplo
   `jdbc:mysql://db.interna:3306/monte_erp?useUnicode=true&connectionTimeZone=Europe/Madrid`.
2. Pulsa **Probar conexión**.
3. Introduce el usuario y la contraseña de MySQL.
4. Rellena los datos del **usuario administrador** (nombre, usuario y
   contraseña).
5. Pulsa **Guardar y continuar**.

La operación:
- escribe `db.url` en el `application.properties` resuelto (directorio de trabajo
  con el fat JAR, o directorio de configuración del usuario con `jpackage`);
- guarda `db.user` y `db.password` en el keyring;
- aplica las migraciones pendientes;
- crea el administrador con la contraseña hasheada.

La pantalla de edición posterior **no revela la contraseña guardada**. Si cambias
el endpoint o el usuario, vuelve a introducir la contraseña.

### 6.4. Estados de arranque

| Situación | Pantalla |
| --- | --- |
| No hay fichero, URL o credenciales completas | Configuración inicial (*onboarding*). |
| Configuración completa, base accesible, migraciones correctas y administrador existente | Login. |
| Configuración completa, base accesible, pero no hay administrador | Configuración inicial. |
| Configuración completa, pero la base no responde o las credenciales fallan | Configuración de la base de datos (modo cambio) para corregirla. |
| Error leyendo la configuración | Configuración inicial. |

### 6.5. Keyring

| Sistema | Backend | Servicio/cuentas |
| --- | --- | --- |
| Windows | Credential Manager | `monte_erp` / `db.user`, `db.password` |
| macOS | Keychain | `monte_erp` / `db.user`, `db.password` |
| Linux | Secret Service (`gnome-keyring`) o KWallet 5 | `monte_erp` / `db.user`, `db.password` |

- El keyring pertenece al **usuario del sistema**. Copiar el JAR y el
  `application.properties` a otro equipo no copia las credenciales: hay que
  introducirlas de nuevo.
- Si el keyring está bloqueado o no hay backend, la lectura devuelve "sin
  credenciales" y la aplicación muestra la configuración. Las escrituras sí
  fallan de forma visible.
- `java-keyring` 1.0.4 usa KWallet 5 en KDE. Si falla en KDE 6, instala y
  arranca `gnome-keyring`, que implementa Secret Service.

### 6.6. Varios clientes contra la misma base

- Cada cliente tiene su propio `application.properties` y su propio keyring.
- Todos apuntan a la misma URL y usan el mismo usuario de base de datos (o
  usuarios equivalentes con los mismos permisos).
- La primera vez, arranca **un solo cliente** para crear el esquema y el
  administrador; después instala el resto.
- En cada actualización con migraciones nuevas, vuelve a arrancar un solo
  cliente antes de actualizar los demás (ver sección 8).

---

## 7. Operación

### 7.1. Logs

La aplicación usa `java.util.logging` (JUL) y el puente `slf4j-jdk14` para mvvmFX.
Por defecto escribe en la consola/`stderr`; no hay un `FileHandler` ni rotación
configurados. En explotación, redirige la salida a un fichero o al journal del
sistema:

```bash
java -jar /opt/monte-erp/monte_erp-1.0-SNAPSHOT.jar \
  >> /var/log/monte-erp/app.log 2>&1
```

Configura `logrotate` o el journal para evitar que el fichero crezca sin límite.
Revisa especialmente los registros `SEVERE`: un fallo de base de datos o de
keyring se registra con detalle técnico, mientras que la interfaz muestra un
mensaje genérico.

En una app-image/instalador lanzada desde el escritorio no hay consola: para ver
los logs, arráncala desde una terminal redirigiendo `stderr` o añade
`--win-console` al empaquetar en Windows. No hay un `FileHandler` propio.

### 7.2. Copias de seguridad

Copia la base con consistencia transaccional:

```bash
mysqldump --single-transaction --routines --triggers \
  -u root -p monte_erp > monte_erp_$(date +%F_%H%M).sql
```

El respaldo incluye:

- el esquema `monte_erp` (incluye `schema_migrations`);
- el `application.properties` de cada puesto, si quieres conservar la URL.

El **keyring no se incluye**: las credenciales deben custodiarse aparte en un
gestor de secretos.

Prueba periódicamente la restauración en un entorno aislado:

```bash
mysql -u root -p monte_erp < monte_erp_2026-01-01_0300.sql
```

### 7.3. Cambio de URL o de credenciales

- Desde la aplicación: login → **Cambiar configuración de base de datos**.
  La pantalla prueba la conexión y guarda URL y credenciales de forma
  coordinada.
- Editando `application.properties` a mano: localízalo en el directorio de
trabajo o en el directorio de configuración del usuario (ver 6.1), cambia solo
`db.url` y **reinicia** la aplicación; la configuración se lee al arrancar.
- Puedes forzar otra ubicación con `MONTE_ERP_CONFIG_DIR` o
  `-Dmonte.erp.config.dir`; es útil para pruebas o instalaciones compartidas.
- La contraseña no se puede recuperar desde el keyring a través de la
  aplicación. Si se pierde, vuelve a introducirla en la pantalla de
  configuración.

### 7.4. Gestión de usuarios y roles

- El **administrador** puede crear usuarios, cambiar contraseñas, darlos de baja
  y reactivarlos.
- El **empleado** no ve el módulo de usuarios; el acceso también se comprueba en
  `AppShell`.
- La baja es lógica: el usuario deja de poder entrar, pero su histórico de
  pedidos y compras se conserva.
- No borres filas de `USER` con SQL: romperías las claves foráneas del histórico.

### 7.5. Monitorización mínima

No hay endpoint de salud. Para comprobar una instalación:

1. El proceso Java sigue vivo y la ventana responde.
2. El login funciona con un usuario válido.
3. La cabecera muestra la URL de base de datos esperada.
4. Los listados cargan y los informes responden.
5. MySQL está accesible y el disco tiene espacio.
6. El log no contiene `SEVERE` inesperados.

---

## 8. Actualización y vuelta atrás

### 8.1. Procedimiento de actualización

1. **Anuncia la parada** y cierra las instancias de la aplicación.
2. **Haz copia de seguridad** de la base (`mysqldump`).
3. Compila o descarga el artefacto **para la plataforma de destino** y verifica
   su hash.
4. Sustituye el JAR, la app-image o el instalador en el directorio de binarios.
   No borres la configuración del usuario (el `application.properties` del
   directorio de trabajo o del directorio de configuración).
5. Arranca **un solo cliente** contra la base compartida. `MigrationRunner`
   aplicará los scripts pendientes.
6. Comprueba el log y la tabla `schema_migrations`.
7. Arranca el resto de clientes.
8. Ejecuta la verificación de la sección 10.

No ejecutes una actualización con clientes antiguos y nuevos arrancando a la vez
contra migraciones pendientes: no hay bloqueo de migración entre instancias.

### 8.2. Vuelta atrás

Las migraciones son **solo hacia delante**. Para volver a la versión anterior:

1. Detén todas las instancias.
2. Restaura la copia de la base previa a la actualización.
3. Restaura el JAR, la app-image o el instalador anterior.
4. Conserva el `application.properties` y las credenciales del keyring (si no
   cambió el endpoint).
5. Arranca y verifica.

No intentes "deshacer" una migración ejecutando SQL inverso a mano salvo que
hayas diseñado y probado ese procedimiento. Restaurar la copia es la vía
soportada.

### 8.3. Compatibilidad

Una versión antigua del cliente **no conoce** las migraciones nuevas. Aunque
`schema_migrations` contenga una versión que no está en su classpath, el runner
la ignora; el problema real es que el esquema puede haber cambiado de forma
incompatible. Por eso la vuelta atrás incluye restaurar la base, no solo el JAR.

---

## 9. Pruebas, TestFX e integración continua

### 9.1. Suite unitaria (rápida, sin Docker)

```bash
./mvnw test
```

Características:

- JUnit 5.12.1.
- No necesita MySQL, Docker ni el toolkit de JavaFX.
- Usa dobles en memoria (`FakeUserRepository`, `FakeProductRepository`, etc.) y
  `FakeJdbc`, un driver JDBC simulado con proxies dinámicos que registra el SQL,
  los parámetros y las llamadas de transacción.
- Cubre modelos, reglas de estado, FIFO, cálculos de informes, validaciones,
  ViewModels, configuración de base de datos, keyring con dobles y adaptadores
  MySQL sin servidor.
- Se ejecuta con Surefire en el *classpath* (`useModulePath=false`), con locale
  `es_ES` y zona `Europe/Madrid`.
- JaCoCo genera el informe en `target/site/jacoco/`.

### 9.2. Suite de interfaz/E2E con TestFX

La suite de interfaz está etiquetada con `@Tag("ui")` y **excluida** de
`./mvnw test`. Se ejecuta con el perfil `ui-tests`:

```bash
scripts/run-ui-tests.sh
```

o, con un display ya disponible:

```bash
./mvnw verify -Pui-tests
```

En Windows/macOS, desde una sesión de escritorio:

```bat
mvnw.cmd test -Pui-tests
```

```bash
./mvnw test -Pui-tests
```

Casos concretos:

```bash
scripts/run-ui-tests.sh -Dtest=LoginUiTest
./mvnw test -Pui-tests -Dtest=LoginUiTest
```

El script `scripts/run-ui-tests.sh`:

1. comprueba que Docker está instalado y el daemon responde;
2. elimina contenedores etiquetados de ejecuciones anteriores;
3. arranca un **Xvfb privado** de 1920×1200 si no hay display;
4. ejecuta `./mvnw test -Pui-tests`;
5. limpia el contenedor y el Xvfb al terminar, incluso si falla.

Para usar el display actual:

```bash
UI_USE_EXISTING_DISPLAY=1 scripts/run-ui-tests.sh
```

**Infraestructura de la suite:**

- `TestDatabase` crea un contenedor `mysql:8.0` con el **CLI de Docker** (no se
  usa la librería Testcontainers). El nombre es aleatorio y lleva la etiqueta
  `monte-erp-ui-test=true`.
- Aplica las migraciones reales disponibles (`V1`–`V10` en esta versión) y carga
  `src/main/resources/db/seed/demo_seed.sql` con el cliente `mysql` del
  contenedor, porque el seed usa `DELIMITER` y un procedimiento almacenado.
- Guarda un `mysqldump` como *baseline* y **restaura ese baseline antes de cada
  test**, de modo que todos ven el mismo dataset aunque uno cree o modifique
  datos.
- La imagen se puede cambiar con `-Dmysql.image=...`.
- `FxRunner` arranca el toolkit **una vez por JVM** y no lo cierra entre clases.
- `UiTestSupport` monta la aplicación real con el mismo grafo que
  `MonteErpApplication`, pero con `FakeCredentialStore` y sin keyring real. Solo
  sustituye `ReportsView` para inyectar un `FakeFileSaveDialog` y no abrir el
  diálogo nativo.
- `UiActions` conduce los controles por `id` y por texto visible (click, typing,
  selección de combo/tabla, confirmación de diálogo) y asserta etiquetas, filas y
  títulos. No inyecta ratón/teclado a nivel de sistema operativo.
- `LogRecorder` falla el test si aparece un `SEVERE` inesperado aunque la
  interfaz parezca correcta.
- El perfil fija `testfx.robot=glass`, `prism.order=sw`, `java.awt.headless=false`
  y un timeout de 20 s.

**Qué cubre:** login y onboarding, menú por rol, las 12 pantallas, usuarios,
categorías, proveedores, clientes, productos, compras
(`pending → received/cancelled`, `received → returned`), pedidos con consumo FIFO,
stock/alertas, informes y exportación CSV/PDF, navegación, cierre de sesión y
regresión del tamaño de la ventana al abrir diálogos.

**Qué no cubre:** instalación nativa, diálogos nativos del sistema, keyring real,
TLS/certificados, red real, ni el renderizado de una GPU concreta. Por eso la
verificación manual de la sección 10 sigue siendo necesaria en cada plataforma.

### 9.3. Regenerar las capturas del manual de usuario

Las capturas del manual las produce la aplicación real mediante
`ManualCaptureUiTest`:

```bash
scripts/run-ui-tests.sh --manual
```

- Solo se ejecuta con `-Dmanual.capture=true`; no forma parte de la suite normal.
- Escribe en `docs/manual_de_usuario/img` (se puede cambiar con
  `-Dmanual.screenshots.dir=...`).
- Genera una captura por pantalla/diálogo (45 en la versión actual).
- Antes de cada captura hace las mismas aserciones que la suite de interfaz: si
  un cambio rompe una pantalla, el generador falla en vez de guardar una imagen
  incorrecta.
- Las imágenes son *snapshots* de JavaFX, no capturas de la pantalla X: no
  incluyen decoraciones de ventana y no dependen de la resolución del escritorio.

### 9.4. Integración continua sugerida

Un pipeline mínimo:

1. **Build + tests unitarios**: `./mvnw -B verify`.
2. **Tests de interfaz** en un runner Linux con Docker y Xvfb:
   `scripts/run-ui-tests.sh`.
3. **Empaquetado** del artefacto para la plataforma de destino (o matriz de
   runners por sistema operativo). Después de los tests unitarios se puede usar
   `scripts/package-app-image.sh --skip-tests` (o `-SkipTests` en Windows) para
   generar la app-image o el instalador sin repetir la suite.
4. **Publicación** del JAR, la app-image/instalador y su `sha256`.

Conserva como artefactos de CI `target/surefire-reports/`,
`target/site/jacoco/` y las capturas si se regeneran. No hay un umbral de
cobertura que bloquee el pipeline.

---

## 10. Verificación posterior al despliegue

Marca esta lista en cada instalación o actualización:

- [ ] El proceso arranca sin errores en el log.
- [ ] La pantalla inicial es la esperada (login o configuración).
- [ ] El login funciona con un usuario administrador real.
- [ ] La cabecera muestra la URL de base de datos correcta y redactada.
- [ ] Se abre cada módulo: usuarios, categorías, proveedores, clientes,
      productos, compras, pedidos, stock e informes.
- [ ] Un listado con datos carga y los filtros responden.
- [ ] Se genera un informe y se exporta a CSV y PDF en una carpeta con permisos.
- [ ] No hay registros `SEVERE` inesperados.
- [ ] `schema_migrations` contiene todas las versiones esperadas:

  ```sql
  SELECT version, script, applied_at
    FROM schema_migrations
   ORDER BY applied_at, version;
  ```

- [ ] La copia de seguridad del día se ha generado y se ha probado una
      restauración reciente.
- [ ] El keyring del usuario conserva las credenciales tras reiniciar sesión.
- [ ] La zona horaria del cliente y del servidor coincide.
- [ ] Si se usa app-image/instalador, arranca desde otro directorio y crea
      `application.properties` en el directorio de configuración del usuario.

---

## 11. Limitaciones conocidas

| Limitación | Impacto | Mitigación |
| --- | --- | --- |
| Artefacto específico de plataforma | Un JAR de Linux no funciona en Windows/macOS. | Construir por plataforma y nombrar el artefacto con SO/arquitectura. |
| Empaquetado nativo fuera de Maven | `jpackage` se lanza con scripts, no con un plugin Maven; cada instalador necesita su plataforma. | Ejecutar `scripts/package-app-image.sh` o `.ps1` en el SO de destino e integrarlo en CI si hace falta. |
| Imagen `jlink` no generable | Varias dependencias son módulos automáticos y `jlink` los rechaza. | Usar `jpackage` (app-image o instalador) o el fat JAR; convertir las dependencias a módulos explícitos solo si se exige `jlink`. |
| JavaFX en el classpath de la app-image | Aparece el aviso `Unsupported JavaFX configuration...`, aunque la app funciona. | Aceptarlo o mantener JavaFX en el *module path* con una distribución modular; no afecta a la operación. |
| Sin actualización automática | Cada cliente se actualiza manualmente. | Procedimiento de la sección 8 y checklist. |
| Sin pool de conexiones | Más coste de apertura por operación. | Aceptable con MySQL local; revisar si el servidor es remoto. |
| Migraciones sin bloqueo entre instancias | Varios arranques simultáneos pueden competir. | Arrancar un cliente primero o aplicar migraciones de forma controlada. |
| Migraciones sin *down* ni checksum | La vuelta atrás exige restaurar copia; editar un script aplicado no se detecta. | Copias antes de actualizar y scripts inmutables. |
| Sin logs a fichero ni rotación propios | La salida se pierde si no se redirige. | Redirección a fichero/journal + `logrotate`. |
| Keyring obligatorio para guardar credenciales | Sin sesión desbloqueada no se puede configurar. | Ejecutar en la sesión del usuario, no como servicio previo al login. |
| Suite UI no cubre keyring/TLS/diálogos nativos | Quedan riesgos de integración con el SO. | Smoke test manual en cada plataforma. |
| Versión `1.0-SNAPSHOT` | No es una versión de release inmutable. | Fijar una versión semántica antes de distribuir. |
| Sin endpoint de salud | La monitorización es manual. | Comprobar proceso, login, listados y logs. |

---

## 12. Resolución de problemas

| Síntoma | Causa probable | Actuación |
| --- | --- | --- |
| `Module javafx.swing not found` | Se eliminó `javafx-swing` del POM. | Mantener la dependencia; es transitiva de `tilesfx`/`countries`. |
| `Invalid signature file digest for Manifest main attributes` | El shade fusionó un JAR firmado sin quitar sus firmas. | Mantener los filtros `META-INF/*.SF`, `*.DSA`, `*.RSA`, `*.EC`. |
| `No .sql script found in /db/migration` | Recursos no incluidos en el JAR o *classpath* incorrecto. | Verificar `src/main/resources/db/migration` y reconstruir el artefacto. |
| La app siempre abre configuración | Falta URL/credenciales, el keyring no responde o se busca `application.properties` en otra carpeta. | Comprobar `pwd`, la ruta de configuración (6.1), `MONTE_ERP_CONFIG_DIR`, el backend de keyring y las cuentas `db.user`/`db.password`. |
| "La configuración de la base de datos no está disponible" | URL, usuario o contraseña vacíos. | Repetir el onboarding o la pantalla de configuración. |
| `Access denied for user ...` | Usuario/host/contraseña incorrectos o permisos insuficientes. | Revisar `CREATE USER`, `GRANT` y el host de conexión. |
| `Public Key Retrieval is not allowed` | `caching_sha2_password` sin TLS ni recuperación de clave pública. | Preferir TLS; en local, valorar `allowPublicKeyRetrieval=true` solo de forma temporal. |
| `Communications link failure` | MySQL caído, puerto cerrado, firewall o URL incorrecta. | Probar `mysql` desde el cliente y revisar la URL. |
| El onboarding no guarda credenciales | Keyring bloqueado, sin D-Bus/Secret Service o KWallet incompatible. | Desbloquear el keyring, instalar `gnome-keyring` o revisar el backend. |
| La ventana no repinta o se congela | Se está ejecutando JDBC en el hilo de JavaFX (regresión). | Revisar que la ViewModel use `BackgroundTasks`; ejecutar la suite UI. |
| Error de fechas o informes desplazados | Zona horaria distinta entre cliente, JVM y MySQL. | Unificar `TZ`, `-Duser.timezone` y `default-time-zone`; reiniciar. |
| `docker is required` / daemon no accesible | Docker no instalado o el usuario no puede hablar con el daemon. | Instalar Docker, añadir el usuario al grupo `docker` o usar daemon rootless. |
| TestFX no mueve ratón/teclado en macOS | Falta permiso de Accesibilidad. | Conceder permiso al terminal/IDE en Privacidad y seguridad. |
| Tests UI inestables por foco | El gestor de ventanas roba el foco. | Usar el Xvfb privado de `scripts/run-ui-tests.sh`. |
| `Xvfb did not start` | Falta `xvfb` o `xdpyinfo`. | Instalar `xvfb`, `xauth` y `x11-utils`; revisar `/tmp/monte-erp-xvfb.log`. |
| El contenedor de pruebas tarda en arrancar | Primera descarga de `mysql:8.0` o arranque lento. | Predescargar la imagen y revisar Docker; el timeout es de 180 s. |
| La app no arranca en Wayland | Falta XWayland o GTK. | Usar una sesión X11/XWayland con GTK 3 instalado. |
| Pantalla en blanco o error de renderizado | Sin GPU/driver compatible. | Probar `-Dprism.order=sw`. |
| El fat JAR falla en otro SO | Nativas de JavaFX de otra plataforma. | Reconstruir en el SO/arquitectura de destino. |
| `automatic module cannot be used with jlink` | `java.keyring`, `mysql-connector-j`, mvvmFX u OpenPDF son módulos automáticos. | No usar `jlink` para enlazar la app; usar `jpackage` o el fat JAR. |
| `jpackage: command not found` | El JDK no está en el PATH o no incluye `jpackage`. | Instalar JDK 25 y comprobar `jpackage --version`. |
| `Invalid or unsupported type: [rpm]` | Falta `rpmbuild`; `jpackage` lo reporta de forma confusa como tipo no soportado. En macOS/Windows el tipo RPM tampoco existe. | En openSUSE: `sudo zypper install rpm-build` y comprobar `rpmbuild --version`. Si `rpm` no aparece en `jpackage --help`, usa otro JDK 25 o `--type app-image`. El script ya valida ambas cosas antes de compilar. |
| `jpackage` falla al crear `.deb` | Faltan `dpkg-deb` o `fakeroot`. | Instalarlos o generar primero una app-image. |
| `jpackage` falla al crear `.rpm` | Falta `rpmbuild`. | Instalar `rpm-build`/`rpmdevtools` o usar app-image. |
| `jpackage` falla al crear `.msi`/`.exe` | Falta WiX Toolset. | Instalar WiX y ejecutar el script en Windows. |
| `jpackage` no puede empaquetar para otro SO | `jpackage` no es cruzado. | Construir cada app-image/instalador en su plataforma y arquitectura. |
| `Invalid app version` en `jpackage` | Se pasó una versión no válida, por ejemplo `1.0-SNAPSHOT`. | Usar una versión de release (`1.0.0`) con `APP_VERSION`/`-AppVersion`. |
| La app empaquetada no encuentra la configuración antigua | La app-image usa el directorio de usuario, no el directorio desde el que se lanza. | Copiar la URL a la nueva ruta o definir `MONTE_ERP_CONFIG_DIR`/`-Dmonte.erp.config.dir`. |

---

## 13. Anexos

### A. Versiones de referencia

| Componente | Versión |
| --- | --- |
| Java (source/target) | 25 |
| JavaFX | 21.0.6 |
| mvvmFX | 1.8.0 |
| ControlsFX | 11.2.1 |
| FormsFX | 11.6.0 |
| ValidatorFX | 0.6.1 |
| Ikonli JavaFX | 12.3.1 |
| BootstrapFX | 0.4.0 |
| TilesFX | 21.0.9 |
| MySQL Connector/J | 8.4.0 |
| java-keyring | 1.0.4 |
| OpenPDF | 2.0.3 |
| JUnit Jupiter | 5.12.1 |
| TestFX | 4.0.18 |
| JaCoCo | 0.8.15 |
| maven-shade-plugin | 3.5.1 |
| maven-surefire-plugin | 3.5.2 |
| javafx-maven-plugin | 0.0.8 |
| jpackage | Incluido en JDK 25 |
| Imagen MySQL de pruebas | `mysql:8.0` |

### B. Ficheros y directorios relevantes

```text
monte_erp/
├── pom.xml
├── mvnw / mvnw.cmd
├── application.properties                 # generado en el directorio de trabajo
├── src/main/java/module-info.java
├── src/main/java/com/entidaddesconocida/monte_erp/
│   ├── Launcher.java                      # main del fat JAR
│   ├── MonteErpApplication.java           # composición y arranque JavaFX
│   ├── app/                               # AppContext, AppShell, SceneNavigator, sesión
│   ├── view/                              # Views + FXML
│   ├── viewmodel/                         # ViewModels y exportadores CSV/PDF
│   ├── domain/                            # modelos, puertos y servicios
│   ├── infrastructure/                    # adaptadores MySQL y configuración
│   └── security/                          # PasswordHasher
├── src/main/resources/
│   ├── css/app.css
│   ├── db/migration/V1..V10__*.sql
│   ├── db/seed/demo_seed.sql
│   └── com/entidaddesconocida/monte_erp/view/*.fxml
├── src/test/java/                         # JUnit + TestFX
├── scripts/run-ui-tests.sh                # Suite de interfaz
├── scripts/package-app-image.sh           # jpackage en Linux/macOS
├── scripts/package-app-image.ps1          # jpackage en Windows
└── docs/
    ├── manual_de_usuario/
    └── manual_de_despliege/                # este documento
```

### C. Configuración y claves

| Clave / cuenta | Ubicación | Ejemplo |
| --- | --- | --- |
| `db.url` | `application.properties` | `jdbc:mysql://db:3306/monte_erp?useUnicode=true&connectionTimeZone=Europe/Madrid` |
| `monte.erp.config.dir` | Propiedad del sistema (opcional) | `/home/usuario/.config/monte-erp` |
| `MONTE_ERP_CONFIG_DIR` | Variable de entorno (opcional) | `C:\Users\usuario\AppData\Roaming\Monte ERP` |
| `db.user` | Keyring, servicio `monte_erp` | `erp_user` |
| `db.password` | Keyring, servicio `monte_erp` | *(secreto)* |
| `schema_migrations` | Base de datos | Tabla de control de versiones |
| `mysql.image` | Propiedad de los tests UI | `mysql:8.0` |
| `manual.capture` | Propiedad del generador de capturas | `true` |
| `manual.screenshots.dir` | Propiedad del generador de capturas | `docs/manual_de_usuario/img` |

### D. Comandos de referencia

```bash
# Compilar y ejecutar tests unitarios
./mvnw clean verify

# Empaquetar sin tests
./mvnw clean package -DskipTests

# Empaquetar app-image con jpackage (Linux/macOS)
scripts/package-app-image.sh --skip-tests

# Empaquetar instalador .deb/.rpm/.dmg
scripts/package-app-image.sh --skip-tests --type deb

# Ejecutar en desarrollo
./mvnw clean javafx:run

# Ejecutar el fat JAR
java -Duser.timezone=Europe/Madrid -jar target/monte_erp-1.0-SNAPSHOT.jar

# Ejecutar sin GPU
java -Dprism.order=sw -jar target/monte_erp-1.0-SNAPSHOT.jar

# Tests de interfaz con Xvfb y MySQL efímero
scripts/run-ui-tests.sh

# Tests de interfaz sobre el display actual
UI_USE_EXISTING_DISPLAY=1 scripts/run-ui-tests.sh

# Un solo test de interfaz
scripts/run-ui-tests.sh -Dtest=LoginUiTest

# Regenerar las capturas del manual de usuario
scripts/run-ui-tests.sh --manual

# Copia de seguridad
mysqldump --single-transaction --routines --triggers \
  -u root -p monte_erp > monte_erp_backup.sql
```

Windows (PowerShell):

```powershell
# App-image
.\scripts\package-app-image.ps1 -SkipTests

# Instalador MSI
.\scripts\package-app-image.ps1 -SkipTests -Type msi

# Instalador EXE
.\scripts\package-app-image.ps1 -SkipTests -Type exe
```

### E. Checklist de release

- [ ] `./mvnw clean verify` en verde.
- [ ] `scripts/run-ui-tests.sh` en verde (o justificación si no aplica).
- [ ] Versión fijada (no `SNAPSHOT`) en `pom.xml`.
- [ ] App-image/instalador generado con `jpackage` para cada plataforma de destino.
- [ ] La app-image arranca desde un directorio distinto al de instalación y crea
      su `application.properties` en el directorio de configuración del usuario.
- [ ] `sha256` calculado y publicado.
- [ ] Copia de seguridad de la base antes de migrar.
- [ ] Migraciones nuevas probadas sobre una copia de datos reales.
- [ ] Un solo cliente arrancado primero para aplicar migraciones.
- [ ] Smoke test de la sección 10 completado.
- [ ] Manual de usuario actualizado si cambian textos o flujos (y capturas
      regeneradas con `--manual`).
