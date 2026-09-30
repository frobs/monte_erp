-- =====================================================================
-- V1__schema.sql · Esquema inicial de Monte ERP
-- Tablas del diagrama E-R (docs/03-diagrama-er.md).
-- Motor InnoDB (transaccional, necesario para las FK) y utf8mb4.
-- Claves foráneas con ON DELETE RESTRICT: no se borra una categoría con
-- productos, ni un producto ya comprado o vendido.
-- Las reglas de negocio no expresables en DDL (reparto FIFO, suma de
-- salidas igual a la cantidad de la línea, ...) se validan en la aplicación.
-- =====================================================================

CREATE TABLE IF NOT EXISTS CATEGORIA (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    CONSTRAINT uq_categoria_nombre UNIQUE (nombre)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS PROVEEDOR (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    nombre   VARCHAR(100) NOT NULL,
    telefono VARCHAR(20)  NULL,
    email    VARCHAR(100) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS USUARIO (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(100) NOT NULL,
    usuario       VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    rol           ENUM ('admin', 'empleado') NOT NULL DEFAULT 'empleado',
    activo        TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uq_usuario_usuario UNIQUE (usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS PRODUCTO (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(100)   NOT NULL,
    sku          VARCHAR(50)    NOT NULL,
    descripcion  VARCHAR(255)   NULL,
    precio       DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    stock_minimo INT            NOT NULL DEFAULT 0,
    id_categoria INT            NOT NULL,
    id_proveedor INT            NOT NULL,
    CONSTRAINT uq_producto_sku UNIQUE (sku),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES CATEGORIA (id) ON DELETE RESTRICT,
    CONSTRAINT fk_producto_proveedor FOREIGN KEY (id_proveedor) REFERENCES PROVEEDOR (id) ON DELETE RESTRICT,
    CONSTRAINT chk_producto_precio CHECK (precio >= 0),
    CONSTRAINT chk_producto_stock_minimo CHECK (stock_minimo >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS COMPRA (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    fecha        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado       ENUM ('pendiente', 'recibida', 'cancelada') NOT NULL DEFAULT 'pendiente',
    total        DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    id_proveedor INT            NOT NULL,
    id_usuario   INT            NOT NULL,
    CONSTRAINT fk_compra_proveedor FOREIGN KEY (id_proveedor) REFERENCES PROVEEDOR (id) ON DELETE RESTRICT,
    CONSTRAINT fk_compra_usuario FOREIGN KEY (id_usuario) REFERENCES USUARIO (id) ON DELETE RESTRICT,
    CONSTRAINT chk_compra_total CHECK (total >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS STOCK (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    cantidad_inicial     INT            NOT NULL,
    cantidad_disponible  INT            NOT NULL,
    precio_compra        DECIMAL(10, 2) NOT NULL,
    fecha_entrada        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_producto          INT            NOT NULL,
    id_compra            INT            NOT NULL,
    CONSTRAINT fk_stock_producto FOREIGN KEY (id_producto) REFERENCES PRODUCTO (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_compra FOREIGN KEY (id_compra) REFERENCES COMPRA (id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_cantidad_inicial CHECK (cantidad_inicial > 0),
    CONSTRAINT chk_stock_cantidad_disponible CHECK (cantidad_disponible >= 0),
    CONSTRAINT chk_stock_no_excede_inicial CHECK (cantidad_disponible <= cantidad_inicial),
    CONSTRAINT chk_stock_precio_compra CHECK (precio_compra >= 0),
    INDEX idx_stock_producto_fecha (id_producto, fecha_entrada)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS CLIENTE (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    telefono  VARCHAR(20)  NULL,
    email     VARCHAR(100) NULL,
    direccion VARCHAR(255) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS PEDIDO (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    fecha      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado     ENUM ('pendiente', 'enviado', 'entregado', 'cancelado') NOT NULL DEFAULT 'pendiente',
    total      DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    id_cliente INT            NOT NULL,
    id_usuario INT            NOT NULL,
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (id_cliente) REFERENCES CLIENTE (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedido_usuario FOREIGN KEY (id_usuario) REFERENCES USUARIO (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pedido_total CHECK (total >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS LINEA_PEDIDO (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    cantidad        INT            NOT NULL,
    precio_unitario DECIMAL(10, 2) NOT NULL,
    id_pedido       INT            NOT NULL,
    id_producto     INT            NOT NULL,
    CONSTRAINT fk_linea_pedido_pedido FOREIGN KEY (id_pedido) REFERENCES PEDIDO (id) ON DELETE RESTRICT,
    CONSTRAINT fk_linea_pedido_producto FOREIGN KEY (id_producto) REFERENCES PRODUCTO (id) ON DELETE RESTRICT,
    CONSTRAINT chk_linea_pedido_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_linea_pedido_precio CHECK (precio_unitario >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS SALIDA_STOCK (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    cantidad         INT NOT NULL,
    id_linea_pedido  INT NOT NULL,
    id_stock         INT NOT NULL,
    CONSTRAINT fk_salida_stock_linea FOREIGN KEY (id_linea_pedido) REFERENCES LINEA_PEDIDO (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salida_stock_stock FOREIGN KEY (id_stock) REFERENCES STOCK (id) ON DELETE RESTRICT,
    CONSTRAINT chk_salida_stock_cantidad CHECK (cantidad > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
