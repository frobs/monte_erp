-- RF-03: la categoría pasa a ser opcional en el formulario de productos.
-- Un producto sin categoría se guarda con category_id NULL; la relación
-- producto↔categoría sigue siendo la misma (FK ON DELETE RESTRICT).
ALTER TABLE PRODUCT
    MODIFY COLUMN category_id INT NULL;
