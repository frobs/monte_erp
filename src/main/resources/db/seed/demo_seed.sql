-- =====================================================================
-- demo_seed.sql · Datos de demostración para Monte ERP
-- =====================================================================
-- SOLO PARA DEMOS. Este script inserta un dataset completo y coherente
-- (usuarios, catálogo, compras, lotes, pedidos y consumos FIFO) sobre una
-- base de datos recién creada por las migraciones de la aplicación.
--
-- No borra ni modifica datos existentes: está pensado para cargarse en un
-- contenedor MySQL nuevo. Para repetir la demo, se elimina el contenedor y
-- se vuelve a crear (ver README, sección "Datos de demostración").
--
-- El primer INSERT deja un marcador en la tabla `demo_seed`: si el script se
-- ejecuta dos veces sobre la misma base de datos, MySQL aborta en ese punto
-- con un error de clave duplicada y no se inserta nada más.
--
-- Orden recomendado:
--   1. Arrancar la aplicación una vez para que MigrationRunner cree el esquema.
--   2. Cargar este script con el cliente mysql o con docker exec.
--   3. Entrar en la aplicación con el usuario `admin` (contraseña Admin1234).
--
-- Las fechas son relativas a NOW(), de modo que la demo siempre muestra
-- actividad reciente en las pantallas de compras, pedidos, stock e informes.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- Preflight: el seed solo puede cargarse sobre un esquema recién creado.
-- Si ya existe algún usuario (por ejemplo, el admin del onboarding), aborta
-- con un mensaje explícito antes de tocar ninguna tabla.
-- ---------------------------------------------------------------------
DELIMITER //
DROP PROCEDURE IF EXISTS demo_seed_preflight//
CREATE PROCEDURE demo_seed_preflight()
BEGIN
    IF (SELECT COUNT(*) FROM USER) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Base de datos no vacia: elimina el contenedor y su volumen, recrea el esquema y vuelve a cargar el seed.';
    END IF;
END//
DELIMITER ;
CALL demo_seed_preflight();
DROP PROCEDURE demo_seed_preflight;

-- ---------------------------------------------------------------------
-- Guard de ejecución única
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demo_seed (
    id        TINYINT  NOT NULL PRIMARY KEY,
    seeded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO demo_seed (id) VALUES (1);

-- ---------------------------------------------------------------------
-- Usuarios (contraseñas hasheadas con PBKDF2-HMAC-SHA256)
--   admin      / Admin1234
--   ana.garcia / Demo1234
--   luis.moreno/ Demo1234
--   marta.sanz / Demo1234  (cuenta inactiva)
-- ---------------------------------------------------------------------
INSERT INTO USER (id, name, username, password_hash, role, active) VALUES
(1, 'Administrador', 'admin', 'pbkdf2-sha256$210000$ulCnPgUE1+SK64lBzx4cBw==$Ew9l7FeMJkRoJoWYQRj8t0UtayRlh7Ld10X+XWdAsuE=', 'admin', 1),
(2, 'Ana García', 'ana.garcia', 'pbkdf2-sha256$210000$t/UdLXj7DxG1UWmqVqonMg==$4TdP0qqObnHRHCjzcKfuPvyLDL6XpDyy74xVP/mMECM=', 'employee', 1),
(3, 'Luis Moreno', 'luis.moreno', 'pbkdf2-sha256$210000$JY87pC8dEZdR8JEovWiB7g==$OpI5KXeedLI/aKyuY+pPBtrrHXc1VI8ia47yQXS7AKA=', 'employee', 1),
(4, 'Marta Sanz', 'marta.sanz', 'pbkdf2-sha256$210000$qyEQaM1gufK1EEV5NJC5cQ==$zRlqUErNUKNhtH3du6EfiCPSNiRuzZa80+aREeuu/xc=', 'employee', 0);

-- ---------------------------------------------------------------------
-- Categorías (dos inactivas, con productos históricos asociados)
-- ---------------------------------------------------------------------
INSERT INTO CATEGORY (id, name, description, active) VALUES
(1, 'Portátiles', 'Equipos portátiles para oficina y movilidad', 1),
(2, 'Monitores', 'Pantallas y monitores de escritorio', 1),
(3, 'Periféricos', 'Teclados, ratones, webcams y auriculares', 1),
(4, 'Almacenamiento', 'Discos SSD, HDD y soluciones NAS', 1),
(5, 'Redes', 'Switches, routers, puntos de acceso y cableado', 1),
(6, 'Componentes', 'Fuentes, placas base y procesadores', 1),
(7, 'Impresión', 'Tóners y consumibles de impresión', 0),
(8, 'Sobremesa', 'Equipos de sobremesa y estaciones de trabajo', 0);

-- ---------------------------------------------------------------------
-- Proveedores (uno inactivo)
-- ---------------------------------------------------------------------
INSERT INTO SUPPLIER (id, name, phone, email, address, active) VALUES
(1, 'TechData Ibérica S.L.', '+34 934 567 120', 'pedidos@techdata-iberica.es', 'Carrer de la Ciència, 12, 08940 Cornellà de Llobregat, Barcelona', 1),
(2, 'Almacén Informático Levante S.A.', '+34 963 210 445', 'ventas@ailevante.es', 'Avenida del Puerto, 88, 46021 Valencia', 1),
(3, 'Global Peripherals Europe S.L.', '+34 914 880 730', 'compras@globalperipherals.eu', 'Calle Albasanz, 55, 28037 Madrid', 1),
(4, 'Redes y Conectividad Sur S.L.', '+34 954 332 118', 'comercial@redessur.es', 'Polígono Pineda, Calle A, 14, 41012 Sevilla', 1),
(5, 'Distribuciones Norte S.A.', '+34 944 120 875', 'pedidos@distnorte.es', 'Barrio Atxuri, 22, 48015 Bilbao', 1),
(6, 'Papelería y Consumibles Monte S.L.', '+34 958 271 336', 'clientes@consumiblesmonte.es', 'Camino de Ronda, 101, 18003 Granada', 1),
(7, 'Compusistemas Canarias S.L.', '+34 928 455 902', 'info@compusistemas-canarias.es', 'Calle León y Castillo, 240, 35005 Las Palmas de Gran Canaria', 0);

-- ---------------------------------------------------------------------
-- Clientes (dos inactivos con pedidos históricos)
-- ---------------------------------------------------------------------
INSERT INTO CUSTOMER (id, name, phone, email, address, active) VALUES
(1, 'Ayuntamiento de Getafe', '+34 912 080 000', 'compras@ayto-getafe.es', 'Plaza de la Constitución, 1, 28901 Getafe, Madrid', 1),
(2, 'Clínica Dental Sonrisa', '+34 913 456 789', 'administracion@clinicasonrisa.es', 'Calle Serrano, 210, 28002 Madrid', 1),
(3, 'Talleres Ruiz e Hijos S.L.', '+34 976 554 321', 'taller@ruizhijos.es', 'Polígono Cogullada, Nave 12, 50014 Zaragoza', 1),
(4, 'Hotel Costa Azul', '+34 971 702 344', 'recepcion@hotelcostaazul.es', 'Paseo Marítimo, 44, 07014 Palma de Mallorca', 1),
(5, 'Colegio San Miguel', '+34 963 887 200', 'secretaria@colegiosanmiguel.es', 'Calle Sagunto, 18, 46009 Valencia', 1),
(6, 'Gestoría Méndez y Asociados', '+34 918 765 432', 'despacho@gestoriemendez.es', 'Calle Alcalá, 145, 28009 Madrid', 1),
(7, 'Estudio de Arquitectura Línea', '+34 933 210 776', 'info@estudiolinea.cat', 'Carrer de Girona, 88, 08009 Barcelona', 1),
(8, 'Farmacia Central', '+34 952 301 449', 'farmaciacentral@farmacias.es', 'Calle Larios, 24, 29015 Málaga', 1),
(9, 'Biblioteca Municipal de Alcalá', '+34 918 890 155', 'biblioteca@ayto-alcala.es', 'Plaza de Cervantes, 5, 28801 Alcalá de Henares, Madrid', 1),
(10, 'Coffee & Code S.L.', '+34 915 543 210', 'hola@coffeecode.es', 'Calle Fuencarral, 72, 28004 Madrid', 0),
(11, 'Asesoría Delta', '+34 961 234 567', 'contacto@asesoriadelta.es', 'Avenida de Francia, 45, 46023 Valencia', 1),
(12, 'Universidad Rural de Valladolid', '+34 983 445 566', 'secretaria@universidadrural.es', 'Calle Duque de la Victoria, 8, 47001 Valladolid', 0);

-- ---------------------------------------------------------------------
-- Productos (25): dos inactivos con ventas históricas y dos sin categoría.
-- min_stock se ha elegido para que la pantalla de stock muestre varias
-- alertas y también productos con existencias saneadas.
-- ---------------------------------------------------------------------
INSERT INTO PRODUCT (id, name, sku, description, price, min_stock, category_id, active) VALUES
(1, 'Portátil Lenovo ThinkPad E14 Gen 5', 'PORT-LEN-T14', 'Portátil 14 pulgadas con Intel Core i5-1335U, 16 GB RAM y 512 GB SSD', 949.00, 8, 1, 1),
(2, 'Portátil HP EliteBook 840 G10', 'PORT-HP-840', 'Portátil 14 pulgadas con Intel Core i7-1365U, 16 GB RAM y 512 GB SSD', 1249.00, 10, 1, 1),
(3, 'Portátil Apple MacBook Air M3 13', 'PORT-APP-MBA', 'Portátil 13,6 pulgadas con Apple M3, 8 GB RAM y 256 GB SSD', 1349.00, 3, 1, 1),
(4, 'Portátil ASUS ExpertBook B1', 'PORT-ASU-B1', 'Portátil 15,6 pulgadas con Intel Core i5-1235U, 8 GB RAM y 256 GB SSD', 679.00, 6, 1, 0),
(5, 'Monitor Dell P2422H 24', 'MON-DEL-P2422', 'Monitor 23,8 pulgadas Full HD IPS con base regulable en altura', 179.90, 8, 2, 1),
(6, 'Monitor LG 27UP850 27 4K', 'MON-LG-27UP', 'Monitor 27 pulgadas UHD 4K IPS con USB-C de 96 W', 449.00, 4, 2, 1),
(7, 'Monitor Samsung Odyssey G5 27', 'MON-SAM-G5', 'Monitor gaming 27 pulgadas QHD de 165 Hz con pantalla curva', 279.00, 5, 2, 0),
(8, 'Teclado Logitech MX Keys', 'PER-LOG-MXK', 'Teclado inalámbrico retroiluminado para profesionales', 109.00, 22, 3, 1),
(9, 'Ratón Logitech MX Master 3S', 'PER-LOG-MXM3', 'Ratón inalámbrico ergonómico de 8.000 DPI', 99.90, 10, 3, 1),
(10, 'Webcam Logitech Brio 4K', 'PER-LOG-BRIO', 'Cámara web 4K UHD con HDR y micrófonos duales', 129.00, 6, 3, 1),
(11, 'Auriculares Jabra Evolve2 40', 'PER-JAB-EV2', 'Auriculares con cable USB-A para llamadas profesionales', 89.00, 8, 3, 1),
(12, 'SSD Samsung 990 PRO 1 TB', 'ALM-SAM-990P1', 'Unidad SSD NVMe M.2 PCIe 4.0 de 1 TB', 129.99, 32, 4, 1),
(13, 'SSD Crucial P3 Plus 2 TB', 'ALM-CRU-P3P2', 'Unidad SSD NVMe M.2 PCIe 4.0 de 2 TB', 139.90, 10, 4, 1),
(14, 'Disco Seagate IronWolf 4 TB', 'ALM-SEA-IW4', 'Disco duro NAS de 4 TB, 5.900 rpm y 64 MB de caché', 109.50, 6, 4, 1),
(15, 'Switch TP-Link TL-SG1024 24 puertos', 'RED-TPL-SG24', 'Switch Gigabit de 24 puertos para rack de 19 pulgadas', 89.00, 5, 5, 1),
(16, 'Router MikroTik hEX S', 'RED-MIK-HEXS', 'Router Gigabit de 5 puertos con SFP y PoE', 69.90, 12, 5, 1),
(17, 'Punto de acceso Ubiquiti U6-Lite', 'RED-UBI-U6L', 'Punto de acceso Wi-Fi 6 de doble banda para techo', 129.00, 22, 5, 1),
(18, 'Cable UTP Cat6 305 m', 'RED-CAT6-305', 'Bobina de cable de red Cat6 de 305 metros', 119.00, 4, 5, 1),
(19, 'Tóner HP 26A CF226A', 'IMP-HP-26A', 'Cartucho de tóner negro original para LaserJet Pro', 79.50, 10, 7, 1),
(20, 'Tóner Brother TN-2420', 'IMP-BRO-TN2420', 'Cartucho de tóner negro original para impresoras Brother', 59.90, 8, 7, 1),
(21, 'Fuente de alimentación Corsair RM750e', 'COM-COR-RM750', 'Fuente ATX de 750 W 80 PLUS Gold totalmente modular', 99.90, 5, 6, 1),
(22, 'Placa base MSI B650 Tomahawk', 'COM-MSI-B650', 'Placa base ATX AM5 con DDR5 y PCIe 4.0', 209.00, 4, 6, 1),
(23, 'Procesador AMD Ryzen 7 7700X', 'COM-AMD-7700X', 'Procesador AM5 de 8 núcleos y 16 hilos, 5,4 GHz', 329.00, 3, 6, 1),
(24, 'Licencia Microsoft 365 Empresas', 'SOFT-MS365-1A', 'Suscripción anual de 12 meses para 1 usuario', 99.00, 0, NULL, 1),
(25, 'Servicio de instalación y configuración', 'SERV-INST-01', 'Servicio técnico a domicilio de 2 horas', 150.00, 0, NULL, 1);

-- ---------------------------------------------------------------------
-- Compras: 18 en los últimos ~5 meses.
--   received : 13 (una de ellas, la P6, queda totalmente consumida)
--   pending  : 2  (sus lotes no están disponibles)
--   cancelled: 2  (sin stock)
--   returned : 1  (devuelta al proveedor, sin consumos)
-- Los totales se recalculan al final desde sus lotes.
-- ---------------------------------------------------------------------
INSERT INTO PURCHASE (id, purchase_date, status, total, supplier_id, user_id) VALUES
(1,  DATE_SUB(NOW(), INTERVAL 150 DAY), 'received',  0.00, 1, 1),
(2,  DATE_SUB(NOW(), INTERVAL 140 DAY), 'received',  0.00, 2, 2),
(3,  DATE_SUB(NOW(), INTERVAL 130 DAY), 'received',  0.00, 3, 2),
(4,  DATE_SUB(NOW(), INTERVAL 120 DAY), 'received',  0.00, 4, 3),
(5,  DATE_SUB(NOW(), INTERVAL 110 DAY), 'received',  0.00, 5, 2),
(6,  DATE_SUB(NOW(), INTERVAL 100 DAY), 'received',  0.00, 6, 3),
(7,  DATE_SUB(NOW(), INTERVAL  90 DAY), 'received',  0.00, 1, 1),
(8,  DATE_SUB(NOW(), INTERVAL  80 DAY), 'received',  0.00, 2, 2),
(9,  DATE_SUB(NOW(), INTERVAL  70 DAY), 'received',  0.00, 3, 2),
(10, DATE_SUB(NOW(), INTERVAL  60 DAY), 'received',  0.00, 7, 1),
(11, DATE_SUB(NOW(), INTERVAL  50 DAY), 'received',  0.00, 4, 3),
(12, DATE_SUB(NOW(), INTERVAL  40 DAY), 'cancelled', 0.00, 5, 2),
(13, DATE_SUB(NOW(), INTERVAL  30 DAY), 'received',  0.00, 6, 3),
(14, DATE_SUB(NOW(), INTERVAL   3 DAY), 'received',  0.00, 1, 1),
(15, DATE_SUB(NOW(), INTERVAL  10 DAY), 'pending',   0.00, 2, 2),
(16, DATE_SUB(NOW(), INTERVAL   6 DAY), 'pending',   0.00, 3, 3),
(17, DATE_SUB(NOW(), INTERVAL  18 DAY), 'cancelled', 0.00, 4, 1),
(18, DATE_SUB(NOW(), INTERVAL  25 DAY), 'returned',  0.00, 5, 2);

-- ---------------------------------------------------------------------
-- Lotes de stock: 44 lotes ligados a las compras anteriores.
-- La cantidad disponible inicial se recalcula al final del script. Los
-- lotes de compras no recibidas se quedan a 0.
-- ---------------------------------------------------------------------
INSERT INTO STOCK (id, initial_quantity, available_quantity, purchase_price, entry_date, product_id, purchase_id) VALUES
(1,  10, 10, 810.00,  DATE_SUB(NOW(), INTERVAL 150 DAY), 1,  1),
(2,   6,  6, 1050.00, DATE_SUB(NOW(), INTERVAL 150 DAY), 2,  1),
(3,   4,  4, 1180.00, DATE_SUB(NOW(), INTERVAL 150 DAY), 3,  1),
(4,  20, 20, 140.00,  DATE_SUB(NOW(), INTERVAL 140 DAY), 5,  2),
(5,   8,  8, 360.00,  DATE_SUB(NOW(), INTERVAL 140 DAY), 6,  2),
(6,  30, 30, 78.00,   DATE_SUB(NOW(), INTERVAL 130 DAY), 8,  3),
(7,  30, 30, 70.00,   DATE_SUB(NOW(), INTERVAL 130 DAY), 9,  3),
(8,  12, 12, 95.00,   DATE_SUB(NOW(), INTERVAL 130 DAY), 10, 3),
(9,  20, 20, 62.00,   DATE_SUB(NOW(), INTERVAL 130 DAY), 11, 3),
(10, 10, 10, 66.00,   DATE_SUB(NOW(), INTERVAL 120 DAY), 15, 4),
(11, 12, 12, 50.00,   DATE_SUB(NOW(), INTERVAL 120 DAY), 16, 4),
(12, 15, 15, 96.00,   DATE_SUB(NOW(), INTERVAL 120 DAY), 17, 4),
(13,  8,  8, 88.00,   DATE_SUB(NOW(), INTERVAL 120 DAY), 18, 4),
(14, 20, 20, 95.00,   DATE_SUB(NOW(), INTERVAL 110 DAY), 12, 5),
(15, 15, 15, 105.00,  DATE_SUB(NOW(), INTERVAL 110 DAY), 13, 5),
(16, 10, 10, 82.00,   DATE_SUB(NOW(), INTERVAL 110 DAY), 14, 5),
(17, 12, 12, 58.00,   DATE_SUB(NOW(), INTERVAL 100 DAY), 19, 6),
(18,  8,  8, 72.00,   DATE_SUB(NOW(), INTERVAL  90 DAY), 21, 7),
(19,  6,  6, 155.00,  DATE_SUB(NOW(), INTERVAL  90 DAY), 22, 7),
(20,  5,  5, 250.00,  DATE_SUB(NOW(), INTERVAL  90 DAY), 23, 7),
(21,  6,  6, 825.00,  DATE_SUB(NOW(), INTERVAL  80 DAY), 1,  8),
(22, 15, 15, 145.00,  DATE_SUB(NOW(), INTERVAL  80 DAY), 5,  8),
(23, 25, 25, 80.00,   DATE_SUB(NOW(), INTERVAL  70 DAY), 8,  9),
(24, 20, 20, 72.00,   DATE_SUB(NOW(), INTERVAL  70 DAY), 9,  9),
(25, 25, 25, 98.00,   DATE_SUB(NOW(), INTERVAL  70 DAY), 12, 9),
(26,  8,  8, 560.00,  DATE_SUB(NOW(), INTERVAL  60 DAY), 4, 10),
(27,  6,  6, 235.00,  DATE_SUB(NOW(), INTERVAL  60 DAY), 7, 10),
(28,  8,  8, 68.00,   DATE_SUB(NOW(), INTERVAL  50 DAY), 15, 11),
(29, 10, 10, 98.00,   DATE_SUB(NOW(), INTERVAL  50 DAY), 17, 11),
(30,  6,  6, 52.00,   DATE_SUB(NOW(), INTERVAL  50 DAY), 16, 11),
(31, 20,  0, 108.00,  DATE_SUB(NOW(), INTERVAL  40 DAY), 13, 12),
(32, 12,  0, 84.00,   DATE_SUB(NOW(), INTERVAL  40 DAY), 14, 12),
(33, 20, 20, 59.00,   DATE_SUB(NOW(), INTERVAL  30 DAY), 19, 13),
(34, 15, 15, 44.00,   DATE_SUB(NOW(), INTERVAL  30 DAY), 20, 13),
(35, 15, 15, 70.00,   DATE_SUB(NOW(), INTERVAL  30 DAY), 24, 13),
(36,  5,  5, 1060.00, DATE_SUB(NOW(), INTERVAL   3 DAY), 2, 14),
(37,  3,  3, 1195.00, DATE_SUB(NOW(), INTERVAL   3 DAY), 3, 14),
(38, 30, 30, 68.00,   DATE_SUB(NOW(), INTERVAL   3 DAY), 25, 14),
(39,  8,  0, 830.00,  DATE_SUB(NOW(), INTERVAL  10 DAY), 1, 15),
(40, 12,  0, 148.00,  DATE_SUB(NOW(), INTERVAL  10 DAY), 5, 15),
(41, 10,  0, 97.00,   DATE_SUB(NOW(), INTERVAL   6 DAY), 10, 16),
(42, 15,  0, 64.00,   DATE_SUB(NOW(), INTERVAL   6 DAY), 11, 16),
(43,  5,  0, 90.00,   DATE_SUB(NOW(), INTERVAL  18 DAY), 18, 17),
(44, 10,  0, 100.00,  DATE_SUB(NOW(), INTERVAL  25 DAY), 12, 18);

-- ---------------------------------------------------------------------
-- Pedidos de cliente: 30 pedidos en los últimos ~4 meses.
--   delivered: 14   shipped: 7   pending: 5   cancelled: 4
-- Los tres últimos son de hoy para que los informes del mes actual
-- siempre tengan actividad. Los totales se recalculan desde sus líneas.
-- ---------------------------------------------------------------------
INSERT INTO SALES_ORDER (id, order_date, status, total, customer_id, user_id) VALUES
(1,  DATE_SUB(NOW(), INTERVAL 110 DAY), 'delivered', 0.00, 1,  2),
(2,  DATE_SUB(NOW(), INTERVAL 105 DAY), 'delivered', 0.00, 2,  3),
(3,  DATE_SUB(NOW(), INTERVAL 100 DAY), 'delivered', 0.00, 3,  2),
(4,  DATE_SUB(NOW(), INTERVAL  95 DAY), 'cancelled', 0.00, 4,  3),
(5,  DATE_SUB(NOW(), INTERVAL  90 DAY), 'shipped',   0.00, 5,  2),
(6,  DATE_SUB(NOW(), INTERVAL  85 DAY), 'delivered', 0.00, 6,  3),
(7,  DATE_SUB(NOW(), INTERVAL  80 DAY), 'cancelled', 0.00, 7,  2),
(8,  DATE_SUB(NOW(), INTERVAL  75 DAY), 'delivered', 0.00, 8,  3),
(9,  DATE_SUB(NOW(), INTERVAL  70 DAY), 'delivered', 0.00, 9,  2),
(10, DATE_SUB(NOW(), INTERVAL  65 DAY), 'pending',   0.00, 10, 3),
(11, DATE_SUB(NOW(), INTERVAL  60 DAY), 'delivered', 0.00, 11, 2),
(12, DATE_SUB(NOW(), INTERVAL  55 DAY), 'shipped',   0.00, 12, 3),
(13, DATE_SUB(NOW(), INTERVAL  50 DAY), 'delivered', 0.00, 1,  2),
(14, DATE_SUB(NOW(), INTERVAL  45 DAY), 'delivered', 0.00, 2,  3),
(15, DATE_SUB(NOW(), INTERVAL  40 DAY), 'cancelled', 0.00, 3,  2),
(16, DATE_SUB(NOW(), INTERVAL  35 DAY), 'shipped',   0.00, 4,  3),
(17, DATE_SUB(NOW(), INTERVAL  30 DAY), 'delivered', 0.00, 5,  2),
(18, DATE_SUB(NOW(), INTERVAL  28 DAY), 'delivered', 0.00, 6,  3),
(19, DATE_SUB(NOW(), INTERVAL  25 DAY), 'pending',   0.00, 7,  2),
(20, DATE_SUB(NOW(), INTERVAL  22 DAY), 'delivered', 0.00, 8,  3),
(21, DATE_SUB(NOW(), INTERVAL  20 DAY), 'shipped',   0.00, 9,  2),
(22, DATE_SUB(NOW(), INTERVAL  18 DAY), 'delivered', 0.00, 10, 3),
(23, DATE_SUB(NOW(), INTERVAL  15 DAY), 'shipped',   0.00, 11, 2),
(24, DATE_SUB(NOW(), INTERVAL  12 DAY), 'pending',   0.00, 12, 3),
(25, DATE_SUB(NOW(), INTERVAL  10 DAY), 'shipped',   0.00, 1,  2),
(26, DATE_SUB(NOW(), INTERVAL   8 DAY), 'delivered', 0.00, 2,  3),
(27, DATE_SUB(NOW(), INTERVAL   5 DAY), 'cancelled', 0.00, 3,  2),
(28, DATE_SUB(NOW(), INTERVAL   6 HOUR), 'pending',  0.00, 4,  3),
(29, DATE_SUB(NOW(), INTERVAL   4 HOUR), 'shipped',  0.00, 5,  2),
(30, DATE_SUB(NOW(), INTERVAL   1 HOUR), 'pending',  0.00, 6,  3);

-- ---------------------------------------------------------------------
-- Líneas de pedido (73). Los pedidos cancelados O4, O7, O15 y O27 tienen
-- líneas (el histórico conserva el importe) pero no consumen stock.
-- ---------------------------------------------------------------------
INSERT INTO SALES_ORDER_LINE (id, quantity, unit_price, sales_order_id, product_id) VALUES
(1,  12, 109.00, 1,  8),
(2,  10, 99.90,  1,  9),
(3,   2, 949.00, 2,  1),
(4,   2, 179.90, 2,  5),
(5,   2, 109.00, 2,  8),
(6,   4, 79.50,  3,  19),
(7,   2, 99.90,  3,  9),
(8,   3, 129.99, 4,  12),
(9,   2, 139.90, 4,  13),
(10,  1, 109.50, 4,  14),
(11,  3, 89.00,  5,  15),
(12,  2, 129.00, 5,  17),
(13,  1, 949.00, 6,  1),
(14,  1, 1249.00,6,  2),
(15,  1, 1349.00,6,  3),
(16,  1, 109.00, 6,  8),
(17,  1, 179.90, 7,  5),
(18,  1, 99.90,  7,  9),
(19,  3, 79.50,  8,  19),
(20,  2, 109.00, 8,  8),
(21,  4, 69.90,  9,  16),
(22,  2, 119.00, 9,  18),
(23,  2, 949.00, 10, 1),
(24,  1, 179.90, 10, 5),
(25,  2, 99.90,  11, 21),
(26,  1, 209.00, 11, 22),
(27,  1, 329.00, 11, 23),
(28,  5, 109.00, 12, 8),
(29,  3, 99.90,  12, 9),
(30,  2, 129.00, 12, 10),
(31,  2, 679.00, 13, 4),
(32,  2, 279.00, 13, 7),
(33,  2, 89.00,  14, 15),
(34,  2, 69.90,  14, 16),
(35,  2, 129.00, 14, 17),
(36,  1, 949.00, 15, 1),
(37,  2, 79.50,  15, 19),
(38,  6, 109.00, 16, 8),
(39,  4, 99.90,  16, 9),
(40,  1, 129.00, 16, 10),
(41,  4, 79.50,  17, 19),
(42,  2, 179.90, 17, 5),
(43,  5, 129.99, 18, 12),
(44,  3, 139.90, 18, 13),
(45,  2, 109.00, 19, 8),
(46,  1, 99.90,  19, 9),
(47,  1, 679.00, 20, 4),
(48,  1, 279.00, 20, 7),
(49,  1, 949.00, 20, 1),
(50,  4, 129.99, 21, 12),
(51,  2, 139.90, 21, 13),
(52,  1, 109.50, 21, 14),
(53,  1, 79.50,  22, 19),
(54,  3, 109.00, 22, 8),
(55,  1, 99.90,  23, 21),
(56,  1, 329.00, 23, 23),
(57,  1, 209.00, 23, 22),
(58,  2, 89.00,  24, 15),
(59,  2, 69.90,  24, 16),
(60,  2, 949.00, 25, 1),
(61,  3, 179.90, 25, 5),
(62,  4, 129.99, 26, 12),
(63,  2, 139.90, 26, 13),
(64,  1, 109.50, 26, 14),
(65,  2, 99.90,  27, 9),
(66,  1, 949.00, 28, 1),
(67,  1, 1249.00,28, 2),
(68,  2, 109.00, 29, 8),
(69,  2, 99.90,  29, 9),
(70,  2, 129.99, 30, 12),
(71,  1, 139.90, 30, 13),
(72,  4, 209.00, 30, 22),
(73,  3, 329.00, 30, 23);

-- ---------------------------------------------------------------------
-- Consumos FIFO de lotes (STOCK_ISSUE). Cada fila indica cuántas unidades
-- de una línea salieron de un lote concreto. Se ha respetado el orden de
-- entrada (lote más antiguo primero) y ninguna línea supera su cantidad.
-- ---------------------------------------------------------------------
INSERT INTO STOCK_ISSUE (quantity, sales_order_line_id, stock_id) VALUES
-- O1
(12, 1, 6), (10, 2, 7),
-- O2
(2, 3, 1), (2, 4, 4), (2, 5, 6),
-- O3
(4, 6, 17), (2, 7, 7),
-- O5
(3, 11, 10), (2, 12, 12),
-- O6
(1, 13, 1), (1, 14, 2), (1, 15, 3), (1, 16, 6),
-- O8
(3, 19, 17), (2, 20, 6),
-- O9
(4, 21, 11), (2, 22, 13),
-- O10
(2, 23, 1), (1, 24, 4),
-- O11
(2, 25, 18), (1, 26, 19), (1, 27, 20),
-- O12
(5, 28, 6), (3, 29, 7), (2, 30, 8),
-- O13
(2, 31, 26), (2, 32, 27),
-- O14
(2, 33, 10), (2, 34, 11), (2, 35, 12),
-- O16
(6, 38, 6), (4, 39, 7), (1, 40, 8),
-- O17
(4, 41, 17), (2, 42, 4),
-- O18
(5, 43, 14), (3, 44, 15),
-- O19
(2, 45, 6), (1, 46, 7),
-- O20
(1, 47, 26), (1, 48, 27), (1, 49, 1),
-- O21
(4, 50, 14), (2, 51, 15), (1, 52, 16),
-- O22
(1, 53, 17), (3, 54, 23),
-- O23
(1, 55, 18), (1, 56, 20), (1, 57, 19),
-- O24
(2, 58, 10), (2, 59, 11),
-- O25
(2, 60, 1), (3, 61, 4),
-- O26
(4, 62, 14), (2, 63, 15), (1, 64, 16),
-- O28
(1, 66, 1), (1, 67, 2),
-- O29
(2, 68, 23), (2, 69, 7),
-- O30 (cierra las existencias de chapa base y procesador)
(2, 70, 14), (1, 71, 15), (4, 72, 19), (3, 73, 20);

-- ---------------------------------------------------------------------
-- Recálculo de columnas derivadas
-- ---------------------------------------------------------------------

-- Importe de cada pedido = suma de sus líneas.
UPDATE SALES_ORDER o
  JOIN (SELECT sales_order_id, SUM(quantity * unit_price) AS total
          FROM SALES_ORDER_LINE
         GROUP BY sales_order_id) l
    ON l.sales_order_id = o.id
   SET o.total = l.total;

-- Importe de cada compra = suma de sus lotes (cantidad inicial x precio).
UPDATE PURCHASE p
  JOIN (SELECT purchase_id, SUM(initial_quantity * purchase_price) AS total
          FROM STOCK
         GROUP BY purchase_id) s
    ON s.purchase_id = p.id
   SET p.total = s.total;

-- Stock disponible de cada lote = cantidad inicial - consumos FIFO.
-- Los lotes de compras no recibidas (pending/cancelled/returned) quedan a 0.
UPDATE STOCK s
  JOIN PURCHASE p ON p.id = s.purchase_id
  LEFT JOIN (SELECT stock_id, SUM(quantity) AS consumed
               FROM STOCK_ISSUE
              GROUP BY stock_id) i
    ON i.stock_id = s.id
   SET s.available_quantity = CASE
       WHEN p.status = 'received'
           THEN s.initial_quantity - COALESCE(i.consumed, 0)
       ELSE 0
   END;

-- ---------------------------------------------------------------------
-- Verificación: todas estas consultas deben devolver 0 filas.
-- ---------------------------------------------------------------------

-- Lotes con disponible fuera del rango válido.
SELECT 'stock fuera de rango' AS problema, s.id, s.initial_quantity, s.available_quantity
  FROM STOCK s
 WHERE s.available_quantity < 0 OR s.available_quantity > s.initial_quantity;

-- Compras no recibidas que aún tuvieran stock disponible.
SELECT 'compra no recibida con stock' AS problema, s.id, p.id AS purchase_id, p.status
  FROM STOCK s
  JOIN PURCHASE p ON p.id = s.purchase_id
 WHERE p.status <> 'received' AND s.available_quantity <> 0;

-- Totales de pedido descuadrados.
SELECT 'total de pedido descuadrado' AS problema, o.id, o.total, COALESCE(l.total, 0) AS calculado
  FROM SALES_ORDER o
  LEFT JOIN (SELECT sales_order_id, SUM(quantity * unit_price) AS total
               FROM SALES_ORDER_LINE GROUP BY sales_order_id) l
    ON l.sales_order_id = o.id
 WHERE COALESCE(l.total, 0) <> o.total;

-- Totales de compra descuadrados.
SELECT 'total de compra descuadrado' AS problema, p.id, p.total, COALESCE(s.total, 0) AS calculado
  FROM PURCHASE p
  LEFT JOIN (SELECT purchase_id, SUM(initial_quantity * purchase_price) AS total
               FROM STOCK GROUP BY purchase_id) s
    ON s.purchase_id = p.id
 WHERE COALESCE(s.total, 0) <> p.total;

-- Líneas cuyo consumo no coincide con su cantidad (o canceladas con consumo).
SELECT 'consumo de línea incorrecto' AS problema, l.id, o.status, l.quantity,
       COALESCE(si.consumed, 0) AS consumido
  FROM SALES_ORDER_LINE l
  JOIN SALES_ORDER o ON o.id = l.sales_order_id
  LEFT JOIN (SELECT sales_order_line_id, SUM(quantity) AS consumed
               FROM STOCK_ISSUE GROUP BY sales_order_line_id) si
    ON si.sales_order_line_id = l.id
 WHERE (o.status <> 'cancelled' AND COALESCE(si.consumed, 0) <> l.quantity)
    OR (o.status = 'cancelled' AND COALESCE(si.consumed, 0) <> 0);

-- Consumos que apuntan a lotes de compras no recibidas.
SELECT 'consumo sobre lote no disponible' AS problema, si.id, si.stock_id, p.status
  FROM STOCK_ISSUE si
  JOIN STOCK s ON s.id = si.stock_id
  JOIN PURCHASE p ON p.id = s.purchase_id
 WHERE p.status <> 'received';

-- Productos activos sin existencias (informativo: deben ser las 2 alertas
-- de stock a cero del dataset, placa base y procesador).
SELECT 'producto activo sin stock' AS aviso, p.id, p.name, p.min_stock
  FROM PRODUCT p
  LEFT JOIN STOCK s ON s.product_id = p.id
 WHERE p.active = 1
 GROUP BY p.id, p.name, p.min_stock
HAVING COALESCE(SUM(s.available_quantity), 0) = 0;

-- Resumen de filas cargadas.
SELECT 'users' AS tabla, COUNT(*) AS filas FROM USER
UNION ALL SELECT 'categories', COUNT(*) FROM CATEGORY
UNION ALL SELECT 'suppliers', COUNT(*) FROM SUPPLIER
UNION ALL SELECT 'customers', COUNT(*) FROM CUSTOMER
UNION ALL SELECT 'products', COUNT(*) FROM PRODUCT
UNION ALL SELECT 'purchases', COUNT(*) FROM PURCHASE
UNION ALL SELECT 'stock_lots', COUNT(*) FROM STOCK
UNION ALL SELECT 'sales_orders', COUNT(*) FROM SALES_ORDER
UNION ALL SELECT 'sales_order_lines', COUNT(*) FROM SALES_ORDER_LINE
UNION ALL SELECT 'stock_issues', COUNT(*) FROM STOCK_ISSUE;
