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

| Herramienta   | Versión mínima | Instalación                                                |
|---------------|----------------|--------------------------------------------------------------|
| JDK           | 25             | [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) o [OpenJDK](https://openjdk.org/) |
| Maven         | 3.9+           | [Maven download](https://maven.apache.org/download.cgi) o usar el wrapper `./mvnw` incluido |
| Docker        | 24+            | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |

> **Nota:** El proyecto incluye el wrapper de Maven (`mvnw` / `mvnw.cmd`), por lo que **no es necesario instalar Maven** globalmente. Basta con tener el JDK.

---

## Estructura del proyecto

```
monte_erp/
├── pom.xml                  # Configuración Maven y dependencias
├── mvnw / mvnw.cmd          # Maven Wrapper (no requiere Maven global)
├── src/
│   └── main/
│       ├── java/
│       │   ├── module-info.java
│       │   └── com/entidaddesconocida/monte_erp/
│       │       ├── Launcher.java            # Punto de entrada (main)
│       │       ├── HelloApplication.java    # JavaFX Application
│       │       └── HelloController.java     # Controlador FXML
│       └── resources/
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

| Archivo                              | Descripción                          |
|--------------------------------------|--------------------------------------|
| `monte_erp-1.0-SNAPSHOT.jar`         | **Fat JAR** — contiene todo lo necesario para ejecutar |
| `original-monte_erp-1.0-SNAPSHOT.jar`| JAR original sin dependencias (no usar directamente) |

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

# Conectarse al contenedor con el cliente MySQL
`docker exec -it monte_erp_mysql mysql -u erp_user -p monte_erp`

---