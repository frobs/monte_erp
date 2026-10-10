-- =====================================================================
-- V1__schema.sql · Initial schema for Monte ERP
-- Tables from the E-R diagram (docs/03-diagrama-er.md).
-- InnoDB engine (transactional, required for the foreign keys) and utf8mb4.
-- Foreign keys use ON DELETE RESTRICT: a category holding products cannot be
-- deleted, neither can a product that has already been purchased or sold.
-- Business rules that DDL cannot express (FIFO allocation, the sum of stock
-- issues matching the order line quantity, ...) are validated by the application.
-- =====================================================================

CREATE TABLE IF NOT EXISTS CATEGORY (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS SUPPLIER (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    phone VARCHAR(20)  NULL,
    email VARCHAR(100) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS USER (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM ('admin', 'employee') NOT NULL DEFAULT 'employee',
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uq_user_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS PRODUCT (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    sku         VARCHAR(50)    NOT NULL,
    description VARCHAR(255)   NULL,
    price       DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    min_stock   INT            NOT NULL DEFAULT 0,
    category_id INT            NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES CATEGORY (id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_price CHECK (price >= 0),
    CONSTRAINT chk_product_min_stock CHECK (min_stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS PURCHASE (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    purchase_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        ENUM ('pending', 'received', 'cancelled') NOT NULL DEFAULT 'pending',
    total         DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    supplier_id   INT            NOT NULL,
    user_id       INT            NOT NULL,
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES SUPPLIER (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE RESTRICT,
    CONSTRAINT chk_purchase_total CHECK (total >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- STOCK holds the individual lots: a product has one or more lots, each one
-- coming from a purchase with its own purchase price.
CREATE TABLE IF NOT EXISTS STOCK (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    initial_quantity   INT            NOT NULL,
    available_quantity INT            NOT NULL,
    purchase_price     DECIMAL(10, 2) NOT NULL,
    entry_date         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    product_id         INT            NOT NULL,
    purchase_id        INT            NOT NULL,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES PRODUCT (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_purchase FOREIGN KEY (purchase_id) REFERENCES PURCHASE (id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_initial_quantity CHECK (initial_quantity > 0),
    CONSTRAINT chk_stock_available_quantity CHECK (available_quantity >= 0),
    CONSTRAINT chk_stock_not_exceed_initial CHECK (available_quantity <= initial_quantity),
    CONSTRAINT chk_stock_purchase_price CHECK (purchase_price >= 0),
    INDEX idx_stock_product_date (product_id, entry_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS CUSTOMER (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    phone   VARCHAR(20)  NULL,
    email   VARCHAR(100) NULL,
    address VARCHAR(255) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- NOTE: the customer order is called SALES_ORDER because ORDER is a reserved
-- word in MySQL and would have to be quoted in every single query.
CREATE TABLE IF NOT EXISTS SALES_ORDER (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    order_date  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status      ENUM ('pending', 'shipped', 'delivered', 'cancelled') NOT NULL DEFAULT 'pending',
    total       DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    customer_id INT            NOT NULL,
    user_id     INT            NOT NULL,
    CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_order_user FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sales_order_total CHECK (total >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS SALES_ORDER_LINE (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    quantity       INT            NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    sales_order_id INT            NOT NULL,
    product_id     INT            NOT NULL,
    CONSTRAINT fk_sales_order_line_order FOREIGN KEY (sales_order_id) REFERENCES SALES_ORDER (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_order_line_product FOREIGN KEY (product_id) REFERENCES PRODUCT (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sales_order_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sales_order_line_unit_price CHECK (unit_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One order line may be served from several lots (FIFO), so each row records
-- how many units were taken from one specific lot.
CREATE TABLE IF NOT EXISTS STOCK_ISSUE (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    quantity            INT NOT NULL,
    sales_order_line_id INT NOT NULL,
    stock_id            INT NOT NULL,
    CONSTRAINT fk_stock_issue_line FOREIGN KEY (sales_order_line_id) REFERENCES SALES_ORDER_LINE (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_issue_stock FOREIGN KEY (stock_id) REFERENCES STOCK (id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_issue_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
