package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCategoryRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeProductRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSupplierRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProductsViewModel} (RF-03 / RF-11).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with
 * in-memory repositories. The per-field validation properties replace
 * ValidatorFX here, so the rules are asserted without a toolkit.</p>
 *
 * <p>The supplier is not a product attribute (it belongs to the purchase), so
 * the supplier filter is resolved by the repository through the purchase
 * history: the fake records the product ↔ supplier pairs and the ViewModel sends
 * them in the {@code ProductFilter}.</p>
 */
class ProductsViewModelTest {

    private static final Category DRINKS = new Category(1, "Bebidas", "Refrescos");
    private static final Supplier ACME = new Supplier(1, "Acme", null, null, null, true);
    private static final Supplier GLOBEX = new Supplier(2, "Globex", null, null, null, true);

    private FakeProductRepository fakeRepository;
    private FakeCategoryRepository categoryRepository;
    private FakeSupplierRepository supplierRepository;
    private ProductsViewModel viewModel;

    @BeforeEach
    void setUp() {
        categoryRepository = new FakeCategoryRepository().withCategory(DRINKS);
        supplierRepository = new FakeSupplierRepository()
                .withSupplier(ACME)
                .withSupplier(GLOBEX);
        fakeRepository = new FakeProductRepository();
        viewModel = new ProductsViewModel(fakeRepository, categoryRepository,
                supplierRepository);
        viewModel.load();
    }

    /**
     * Registers that the product has a lot from the supplier, which is what the
     * repository query resolves through the purchase history. The available
     * quantity is irrelevant for the filter (decision D3).
     */
    private void boughtFrom(Supplier supplier, Product product, int availableQuantity) {
        fakeRepository.withSupplier(product.getId(), supplier.getId());
    }

    private static Product product(String id, String name, String sku, String price,
                                   int minStock, boolean active, Category category) {
        Product product = new Product(name, sku, "descripcion-" + sku, new BigDecimal(price),
                minStock, category, active);
        product.setId(Integer.parseInt(id));
        return product;
    }

    private void fillValidForm() {
        viewModel.nameProperty().set("Café molido");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");
        viewModel.categoryProperty().set(DRINKS);
    }

    @Test
    @DisplayName("load carga las opciones de categoría")
    void loadFillsTheComboOptions() {
        assertEquals(1, viewModel.categoryOptions().size());
        assertEquals("Bebidas", viewModel.categoryOptions().get(0).getName());
    }

    @Test
    @DisplayName("load carga los productos y actualiza el contador de mostrados")
    void loadFillsTheListAndCount() {
        fakeRepository.withProduct(product("1", "Agua", "AGU-001", "0.60", 10, true, DRINKS));
        viewModel.load();

        assertEquals(1, viewModel.products().size());
        assertEquals("Productos: 1", viewModel.productCountProperty().get());
    }

    @Test
    @DisplayName("Nombre vacío: error de campo y no se toca la base de datos")
    void emptyNameShowsFieldError() {
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");
        viewModel.categoryProperty().set(DRINKS);

        viewModel.save();

        assertEquals("Introduce el nombre del producto.", viewModel.nameErrorProperty().get());
        assertEquals(0, viewModel.products().size());
    }

    @Test
    @DisplayName("SKU vacío: error de campo")
    void emptySkuShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.priceProperty().set("4.50");
        viewModel.categoryProperty().set(DRINKS);

        viewModel.save();

        assertEquals("Introduce el SKU del producto.", viewModel.skuErrorProperty().get());
    }

    @Test
    @DisplayName("Precio vacío: error de campo")
    void emptyPriceShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.categoryProperty().set(DRINKS);

        viewModel.save();

        assertEquals("Introduce el precio del producto.", viewModel.priceErrorProperty().get());
    }

    @Test
    @DisplayName("Precio no numérico: error de campo")
    void nonNumericPriceShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("abc");
        viewModel.categoryProperty().set(DRINKS);

        viewModel.save();

        assertEquals("El precio debe ser un número válido.", viewModel.priceErrorProperty().get());
    }

    @Test
    @DisplayName("Precio negativo: error de campo")
    void negativePriceShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("-1");
        viewModel.categoryProperty().set(DRINKS);

        viewModel.save();

        assertEquals("El precio no puede ser negativo.", viewModel.priceErrorProperty().get());
    }

    @Test
    @DisplayName("Sin categoría: el producto se guarda con categoría nula")
    void missingCategorySavesWithNullCategory() throws Exception {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertNull(fakeRepository.findBySku("CAF-001").getCategory());
    }

    @Test
    @DisplayName("Varios errores de campo: el resumen une los mensajes")
    void severalFieldErrorsAreJoined() {
        viewModel.save();

        String message = viewModel.errorMessageProperty().get();
        assertTrue(message.contains("Introduce el nombre del producto."));
        assertTrue(message.contains("Introduce el SKU del producto."));
        assertTrue(message.contains("Introduce el precio del producto."));
    }

    @Test
    @DisplayName("Alta válida: inserta el producto con description NULL y min_stock 0")
    void createValidProduct() throws Exception {
        fillValidForm();

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("guardado"));
        Product created = fakeRepository.findBySku("CAF-001");
        assertEquals("Café molido", created.getName());
        assertEquals(new BigDecimal("4.50"), created.getPrice());
        assertNull(created.getDescription());
        assertEquals(0, created.getMinStock());
        assertTrue(created.isActive());
        assertEquals(DRINKS.getId(), created.getCategory().getId());
    }

    @Test
    @DisplayName("Alta válida: guarda la descripción escrita en el formulario")
    void createStoresDescription() throws Exception {
        fillValidForm();
        viewModel.descriptionProperty().set("  Molido natural  ");

        viewModel.save();

        assertEquals("Molido natural", fakeRepository.findBySku("CAF-001").getDescription());
    }

    @Test
    @DisplayName("Descripción vacía: se persiste como NULL")
    void blankDescriptionIsSavedAsNull() throws Exception {
        fillValidForm();
        viewModel.descriptionProperty().set("   ");

        viewModel.save();

        assertNull(fakeRepository.findBySku("CAF-001").getDescription());
    }

    @Test
    @DisplayName("Descripción de más de 255 caracteres: error de campo")
    void descriptionTooLongShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");
        viewModel.descriptionProperty().set("a".repeat(256));

        viewModel.save();

        assertEquals("La descripción no puede superar los 255 caracteres.",
                viewModel.descriptionErrorProperty().get());
    }

    @Test
    @DisplayName("El precio se normaliza a dos decimales")
    void priceIsScaledToTwoDecimals() throws Exception {
        fillValidForm();
        viewModel.priceProperty().set("3.456");

        viewModel.save();

        assertEquals(new BigDecimal("3.46"), fakeRepository.findBySku("CAF-001").getPrice());
    }

    @Test
    @DisplayName("SKU duplicado: error de campo y no se inserta")
    void duplicatedSkuShowsFieldError() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "4.50", 0, true, DRINKS));
        viewModel.load();
        fillValidForm();

        viewModel.save();

        assertEquals("El SKU 'CAF-001' ya existe.", viewModel.skuErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("SKU duplicado con otra capitalización: se rechaza (colación case-insensitive)")
    void duplicatedSkuIsCaseInsensitive() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "4.50", 0, true, DRINKS));
        viewModel.load();
        fillValidForm();
        viewModel.skuProperty().set("caf-001");

        viewModel.save();

        assertEquals("El SKU 'caf-001' ya existe.", viewModel.skuErrorProperty().get());
    }

    @Test
    @DisplayName("Editar carga la descripción, la guarda y actualiza el stock mínimo")
    void editUpdatesDescriptionAndMinStock() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 7, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        assertEquals("descripcion-CAF-001", viewModel.descriptionProperty().get());

        viewModel.descriptionProperty().set("Café de tueste natural");
        viewModel.priceProperty().set("9.99");
        viewModel.minStockProperty().set("3");
        viewModel.save();

        Product updated = fakeRepository.findBySku("CAF-001");
        assertEquals("Café de tueste natural", updated.getDescription());
        assertEquals(3, updated.getMinStock());
        assertEquals(new BigDecimal("9.99"), updated.getPrice());
    }

    @Test
    @DisplayName("Stock mínimo vacío: se guarda 0 (sin alerta)")
    void emptyMinStockIsZero() throws Exception {
        fillValidForm();
        viewModel.minStockProperty().set("");

        viewModel.save();

        assertEquals(0, fakeRepository.findBySku("CAF-001").getMinStock());
    }

    @Test
    @DisplayName("Stock mínimo válido: se persiste en el alta")
    void validMinStockIsPersisted() throws Exception {
        fillValidForm();
        viewModel.minStockProperty().set("12");

        viewModel.save();

        assertEquals("", viewModel.minStockErrorProperty().get());
        assertEquals(12, fakeRepository.findBySku("CAF-001").getMinStock());
    }

    @Test
    @DisplayName("Stock mínimo negativo: error de campo y no se inserta")
    void negativeMinStockShowsFieldError() throws Exception {
        fillValidForm();
        viewModel.minStockProperty().set("-1");

        viewModel.save();

        assertEquals("El stock mínimo no puede ser negativo.", viewModel.minStockErrorProperty().get());
        assertEquals(0, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Stock mínimo no numérico: error de campo")
    void nonNumericMinStockShowsFieldError() {
        fillValidForm();
        viewModel.minStockProperty().set("mucho");

        viewModel.save();

        assertEquals("El stock mínimo debe ser un número entero.",
                viewModel.minStockErrorProperty().get());
    }

    @Test
    @DisplayName("Editar a un SKU ya existente: error de duplicado")
    void editToExistingSkuFails() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Agua", "AGU-001", "0.60", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().stream()
                .filter(candidate -> candidate.getSku().equals("CAF-001"))
                .findFirst().orElseThrow());

        viewModel.skuProperty().set("AGU-001");
        viewModel.save();

        assertEquals("El SKU 'AGU-001' ya existe.", viewModel.skuErrorProperty().get());
        assertEquals("CAF-001", fakeRepository.findBySku("CAF-001").getSku());
    }

    @Test
    @DisplayName("Dar de baja: el producto queda inactivo (baja lógica)")
    void deactivateMakesProductInactive() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        viewModel.deactivate();

        assertFalse(fakeRepository.findBySku("CAF-001").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("baja"));
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Reactivar: un producto inactivo vuelve a estar activo")
    void activateMakesProductActiveAgain() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, false, DRINKS));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        viewModel.activate();

        assertTrue(fakeRepository.findBySku("CAF-001").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("reactivado"));
    }

    @Test
    @DisplayName("Dar de baja sin selección muestra error")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedProductProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona un producto de la tabla.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Dar de baja un producto ya inactivo avisa sin tocar la base de datos")
    void deactivatingAnInactiveProductFails() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, false, DRINKS));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        viewModel.deactivate();

        assertEquals("El producto ya está dado de baja.", viewModel.errorMessageProperty().get());
        assertFalse(fakeRepository.findBySku("CAF-001").isActive());
    }

    @Test
    @DisplayName("Reactivar un producto ya activo avisa sin tocar la base de datos")
    void activatingAnActiveProductFails() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        viewModel.activate();

        assertEquals("El producto ya está activo.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findBySku("CAF-001").isActive());
    }

    @Test
    @DisplayName("La búsqueda por nombre o SKU acota la tabla (RF-11)")
    void searchFiltersByNameOrSku() throws Exception {
        fakeRepository.withProduct(product("1", "Agua mineral", "AGU-001", "0.60", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Café molido", "CAF-001", "4.50", 0, true, DRINKS));
        viewModel.load();

        viewModel.searchTextProperty().set("agu");
        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("Agua mineral", viewModel.filteredProducts().get(0).getName());

        viewModel.searchTextProperty().set("caf-001");
        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("Café molido", viewModel.filteredProducts().get(0).getName());

        viewModel.searchTextProperty().set("");
        assertEquals(2, viewModel.filteredProducts().size());
    }

    @Test
    @DisplayName("El filtro por categoría acota la tabla (RF-11)")
    void filtersByCategory() throws Exception {
        Category snacks = new Category(2, "Snacks", "Aperitivos");
        categoryRepository.withCategory(snacks);
        fakeRepository.withProduct(product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Patatas", "PAT-001", "1.20", 0, true, snacks));
        viewModel.load();

        viewModel.categoryFilterProperty().set(snacks);
        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("Patatas", viewModel.filteredProducts().get(0).getName());

        viewModel.categoryFilterProperty().set(null);
        assertEquals(2, viewModel.filteredProducts().size());
    }

    @Test
    @DisplayName("Mostrar inactivos recarga la lista con los productos dados de baja")
    void showInactiveReloadsTheList() throws Exception {
        fakeRepository.withProduct(product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Viejo", "VIE-001", "1.00", 0, false, DRINKS));
        viewModel.load();
        assertEquals(1, viewModel.products().size());

        viewModel.showInactiveProperty().set(true);

        assertEquals(1, viewModel.products().size());
        assertEquals("Viejo", viewModel.products().get(0).getName());

        viewModel.showInactiveProperty().set(false);
        assertEquals("Agua", viewModel.products().get(0).getName());
    }

    @Test
    @DisplayName("Error de base de datos al cargar: muestra mensaje de conexión")
    void databaseErrorOnLoadShowsMessage() {
        fakeRepository.throwOnAccess();

        viewModel.load();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Error de base de datos al guardar: muestra mensaje de conexión")
    void databaseErrorOnSaveShowsMessage() {
        fillValidForm();
        fakeRepository.throwOnAccess();

        viewModel.save();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Si la recarga falla tras guardar, el alta sigue persistida y se avisa aparte")
    void failedReloadAfterSaveStillReportsTheWrite() throws Exception {
        fillValidForm();
        fakeRepository.failOn("findAll");

        viewModel.save();

        assertEquals("Café molido", fakeRepository.findBySku("CAF-001").getName());
        assertTrue(viewModel.errorMessageProperty().get().contains("se guardaron"));
    }

    @Test
    @DisplayName("La validación en vivo solo actúa tras el primer intento de guardado")
    void liveValidationStartsAfterFirstSaveAttempt() {
        viewModel.nameProperty().set("");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());

        viewModel.save();
        assertEquals("Introduce el nombre del producto.", viewModel.nameErrorProperty().get());

        viewModel.nameProperty().set("Café");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Nuevo producto limpia el formulario y los errores de campo")
    void newProductResetsTheForm() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        assertEquals("Editar producto", viewModel.formTitleProperty().get());

        viewModel.save();
        viewModel.newProduct();

        assertNull(viewModel.selectedProductProperty().get());
        assertEquals("Nuevo producto", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
        assertEquals("", viewModel.skuProperty().get());
        assertEquals("", viewModel.descriptionProperty().get());
        assertEquals("", viewModel.priceProperty().get());
        assertNull(viewModel.categoryProperty().get());
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Seleccionar un producto carga sus datos en el formulario")
    void selectingAProductFillsTheForm() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();

        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        assertEquals("Café", viewModel.nameProperty().get());
        assertEquals("CAF-001", viewModel.skuProperty().get());
        assertEquals("descripcion-CAF-001", viewModel.descriptionProperty().get());
        assertEquals("3.00", viewModel.priceProperty().get());
        assertEquals(DRINKS.getId(), viewModel.categoryProperty().get().getId());
        assertEquals("Editar producto", viewModel.formTitleProperty().get());
    }

    @Test
    @DisplayName("El estado del botón dar de baja/reactivar depende del producto seleccionado")
    void toggleStateFollowsSelection() throws Exception {
        Product active = product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS);
        Product inactive = product("2", "Viejo", "VIE-001", "1.00", 0, false, DRINKS);
        fakeRepository.withProduct(active).withProduct(inactive);
        viewModel.load();

        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals(ProductsViewModel.TOGGLE_DEACTIVATE_LABEL, viewModel.toggleLabelProperty().get());

        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals(ProductsViewModel.TOGGLE_DEACTIVATE_LABEL, viewModel.toggleLabelProperty().get());

        // Switching to the inactive list drops the (active) selection; selecting
        // the inactive product offers reactivation.
        viewModel.showInactiveProperty().set(true);
        assertTrue(viewModel.toggleDisabledProperty().get());
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        assertEquals(ProductsViewModel.TOGGLE_ACTIVATE_LABEL, viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }

    @Test
    @DisplayName("Un duplicado detectado por la clave única al insertar se reporta como duplicado")
    void integrityViolationOnInsertIsReportedAsDuplicate() {
        FakeProductRepository racing = new FakeProductRepository() {
            @Override
            public Product findBySku(String sku) {
                return null;
            }

            @Override
            public int insert(Product product) throws SQLException {
                throw new SQLIntegrityConstraintViolationException("Duplicate entry for key 'uq_product_sku'");
            }
        };
        ProductsViewModel racingViewModel = new ProductsViewModel(racing, categoryRepository,
                supplierRepository);
        racingViewModel.nameProperty().set("Café");
        racingViewModel.skuProperty().set("CAF-001");
        racingViewModel.priceProperty().set("4.50");
        racingViewModel.categoryProperty().set(DRINKS);

        racingViewModel.save();

        assertEquals("El SKU 'CAF-001' ya existe.", racingViewModel.skuErrorProperty().get());
    }

    @Test
    @DisplayName("Nombre de más de 100 caracteres: error de campo")
    void nameTooLongShowsFieldError() {
        viewModel.nameProperty().set("a".repeat(101));
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");

        viewModel.save();

        assertEquals("El nombre no puede superar los 100 caracteres.",
                viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("SKU de más de 50 caracteres: error de campo")
    void skuTooLongShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("a".repeat(51));
        viewModel.priceProperty().set("4.50");

        viewModel.save();

        assertEquals("El SKU no puede superar los 50 caracteres.",
                viewModel.skuErrorProperty().get());
    }

    @Test
    @DisplayName("Precio por encima de 99999999.99: error de campo")
    void priceTooHighShowsFieldError() {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("100000000.00");

        viewModel.save();

        assertEquals("El precio no puede superar 99999999.99.",
                viewModel.priceErrorProperty().get());
    }

    @Test
    @DisplayName("Precio con coma decimal: se interpreta y se normaliza a dos decimales")
    void commaDecimalPriceIsParsed() throws Exception {
        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4,50");

        viewModel.save();

        assertEquals(new BigDecimal("4.50"), fakeRepository.findBySku("CAF-001").getPrice());
    }

    @Test
    @DisplayName("Corregir los campos limpia el resumen tras un guardado fallido")
    void correctingFieldsClearsTheSummary() {
        viewModel.save();
        assertFalse(viewModel.errorMessageProperty().get().isBlank());

        viewModel.nameProperty().set("Café");
        viewModel.skuProperty().set("CAF-001");
        viewModel.priceProperty().set("4.50");

        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Reactivar sin selección muestra error")
    void activateWithoutSelectionFails() {
        viewModel.selectedProductProperty().set(null);

        viewModel.activate();

        assertEquals("Selecciona un producto de la tabla.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Error de base de datos al dar de baja: muestra mensaje de conexión")
    void databaseErrorOnDeactivateShowsMessage() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        fakeRepository.failOn("deactivate");

        viewModel.deactivate();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
        assertTrue(fakeRepository.findBySku("CAF-001").isActive());
    }

    @Test
    @DisplayName("Error de base de datos al reactivar: muestra mensaje de conexión")
    void databaseErrorOnActivateShowsMessage() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, false, DRINKS));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        fakeRepository.failOn("activate");

        viewModel.activate();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
        assertFalse(fakeRepository.findBySku("CAF-001").isActive());
    }

    @Test
    @DisplayName("Si falla la recarga al mostrar inactivos, la casilla vuelve a su estado")
    void showInactiveRollsBackOnDatabaseError() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        fakeRepository.failOn("findInactive");

        viewModel.showInactiveProperty().set(true);

        assertFalse(viewModel.showInactiveProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("El combo del formulario solo ofrece categorías activas (D4)")
    void formComboOnlyOffersActiveCategories() throws Exception {
        categoryRepository.withCategory(new Category(2, "Antigua", null, false));
        viewModel.load();

        assertEquals(1, viewModel.categoryOptions().size());
        assertTrue(viewModel.categoryOptions().get(0).isActive());
        assertEquals(2, viewModel.categoryFilterOptions().size());
    }

    @Test
    @DisplayName("Asignar una categoría dada de baja se rechaza y no se guarda (D4)")
    void assigningAnInactiveCategoryIsRejected() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        categoryRepository.withCategory(retired);
        viewModel.load();
        fillValidForm();
        viewModel.categoryProperty().set(retired);

        viewModel.save();

        assertEquals("La categoría seleccionada está dada de baja. Elige otra o déjala sin categoría.",
                viewModel.categoryErrorProperty().get());
        assertNull(fakeRepository.findBySku("CAF-001"));
    }

    @Test
    @DisplayName("Al editar, una categoría inactiva se muestra pero bloquea el guardado hasta cambiarla (D4)")
    void editingWithInactiveCategoryBlocksSave() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        categoryRepository.withCategory(retired);
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, retired));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        assertEquals(retired.getId(), viewModel.categoryProperty().get().getId());
        assertFalse(viewModel.categoryProperty().get().isActive());

        viewModel.priceProperty().set("9.99");
        viewModel.save();

        assertTrue(viewModel.categoryErrorProperty().get().contains("dada de baja"));
        assertEquals(new BigDecimal("3.00"), fakeRepository.findBySku("CAF-001").getPrice());

        viewModel.categoryProperty().set(DRINKS);
        viewModel.save();

        assertEquals(new BigDecimal("9.99"), fakeRepository.findBySku("CAF-001").getPrice());
    }

    @Test
    @DisplayName("Quitar la categoría al editar guarda el producto sin categoría")
    void clearingTheCategoryOnEditSavesItAsNull() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        assertEquals(DRINKS.getId(), viewModel.categoryProperty().get().getId());

        // The form combo offers "Sin categoría" as an option, which lands here
        // as a null value (the category is optional, RF-03).
        viewModel.categoryProperty().set(null);
        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.categoryErrorProperty().get().isEmpty());
        assertNull(fakeRepository.findBySku("CAF-001").getCategory());
    }

    @Test
    @DisplayName("El filtro por categoría incluye también las categorías dadas de baja (D5)")
    void filterOptionsIncludeInactiveCategories() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        categoryRepository.withCategory(retired);
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, retired));
        viewModel.load();

        assertTrue(viewModel.categoryFilterOptions().stream()
                .anyMatch(option -> option.getId() == retired.getId()));

        viewModel.categoryFilterProperty().set(retired);
        assertEquals(1, viewModel.filteredProducts().size());
    }

    @Test
    @DisplayName("Guardar con la categoría reactivada vuelve a funcionar (D4/D5)")
    void savingWorksAgainAfterCategoryIsReactivated() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        categoryRepository.withCategory(retired);
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, retired));
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        viewModel.save();
        assertTrue(viewModel.categoryErrorProperty().get().contains("dada de baja"));

        categoryRepository.activate(retired.getId());
        viewModel.load();
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        viewModel.priceProperty().set("4.50");
        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(new BigDecimal("4.50"), fakeRepository.findBySku("CAF-001").getPrice());
    }

    @Test
    @DisplayName("Un producto sin categoría se edita sin categoría y se guarda igual")
    void productWithoutCategoryIsEditedAndSavedWithoutCategory() throws Exception {
        fakeRepository.withProduct(product("1", "Agua", "AGU-001", "0.60", 0, true, null));
        viewModel.load();

        viewModel.selectedProductProperty().set(viewModel.products().get(0));

        assertNull(viewModel.categoryProperty().get());
        assertEquals("Editar producto", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.categoryErrorProperty().get());

        viewModel.priceProperty().set("0.75");
        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        Product saved = fakeRepository.findBySku("AGU-001");
        assertNull(saved.getCategory());
        assertEquals(new BigDecimal("0.75"), saved.getPrice());
    }

    @Test
    @DisplayName("El filtro por categoría excluye los productos sin categoría")
    void categoryFilterExcludesProductsWithoutCategory() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Agua", "AGU-001", "0.60", 0, true, null));
        viewModel.load();
        assertEquals(2, viewModel.filteredProducts().size());

        viewModel.categoryFilterProperty().set(DRINKS);

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("CAF-001", viewModel.filteredProducts().get(0).getSku());
    }

    @Test
    @DisplayName("El contador refleja las filas filtradas por la búsqueda")
    void countFollowsTheSearchFilter() throws Exception {
        fakeRepository.withProduct(product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        fakeRepository.withProduct(product("2", "Agua", "AGU-001", "0.60", 0, true, DRINKS));
        viewModel.load();
        assertEquals("Productos: 2", viewModel.productCountProperty().get());

        viewModel.searchTextProperty().set("caf");

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("Productos: 1", viewModel.productCountProperty().get());
    }

    @Test
    @DisplayName("La búsqueda es insensible a acentos: 'cafe' encuentra 'Café' (RF-11)")
    void searchIsAccentInsensitive() throws Exception {
        fakeRepository.withProduct(product("1", "Café molido", "CAF-001", "3.00", 0, true, DRINKS));
        viewModel.load();

        viewModel.searchTextProperty().set("cafe");

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("Café molido", viewModel.filteredProducts().get(0).getName());
    }

    @Test
    @DisplayName("load carga las opciones del filtro de proveedor (activos e inactivos)")
    void loadFillsTheSupplierFilterOptions() throws Exception {
        supplierRepository.withSupplier(new Supplier(3, "Bajado", null, null, null, false));
        viewModel.load();

        assertEquals(3, viewModel.supplierFilterOptions().size());
    }

    @Test
    @DisplayName("El filtro por proveedor muestra solo productos con lote de una compra suya (D3)")
    void filtersBySupplier() throws Exception {
        Product agua = product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS);
        Product cafe = product("2", "Café", "CAF-001", "3.00", 0, true, DRINKS);
        fakeRepository.withProduct(agua).withProduct(cafe);
        boughtFrom(ACME, agua, 5);
        boughtFrom(GLOBEX, cafe, 5);
        viewModel.load();
        assertEquals(2, viewModel.filteredProducts().size());

        viewModel.supplierFilterProperty().set(ACME);

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("AGU-001", viewModel.filteredProducts().get(0).getSku());
    }

    @Test
    @DisplayName("Un producto sin compras se excluye al filtrar por proveedor (D3)")
    void productWithoutPurchasesIsExcluded() throws Exception {
        Product agua = product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS);
        fakeRepository.withProduct(agua);
        fakeRepository.withProduct(product("2", "Café", "CAF-001", "3.00", 0, true, DRINKS));
        boughtFrom(ACME, agua, 5);
        viewModel.load();

        viewModel.supplierFilterProperty().set(ACME);

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("AGU-001", viewModel.filteredProducts().get(0).getSku());
    }

    @Test
    @DisplayName("El filtro usa el historial completo: un lote a 0 sigue contando (D3)")
    void zeroAvailableLotStillCounts() throws Exception {
        Product agua = product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS);
        fakeRepository.withProduct(agua);
        boughtFrom(ACME, agua, 0);
        viewModel.load();

        viewModel.supplierFilterProperty().set(ACME);

        assertEquals(1, viewModel.filteredProducts().size());
    }

    @Test
    @DisplayName("Un proveedor inactivo aparece en las opciones y filtra igual (D2)")
    void inactiveSupplierIsStillFilterable() throws Exception {
        Supplier retired = new Supplier(3, "Antiguo", null, null, null, false);
        supplierRepository.withSupplier(retired);
        Product agua = product("1", "Agua", "AGU-001", "0.60", 0, true, DRINKS);
        fakeRepository.withProduct(agua);
        boughtFrom(retired, agua, 5);
        viewModel.load();

        assertTrue(viewModel.supplierFilterOptions().stream()
                .anyMatch(option -> option.getId() == retired.getId()));
        viewModel.supplierFilterProperty().set(retired);
        assertEquals(1, viewModel.filteredProducts().size());
    }

    @Test
    @DisplayName("Los filtros de texto, categoría y proveedor componen con AND")
    void filtersComposeWithAnd() throws Exception {
        Category snacks = new Category(2, "Snacks", "Aperitivos");
        categoryRepository.withCategory(snacks);
        Product cafeBebida = product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS);
        Product cafeSnack = product("2", "Café snack", "SNK-001", "2.00", 0, true, snacks);
        Product aguaSnack = product("3", "Agua snack", "SNK-002", "1.00", 0, true, snacks);
        fakeRepository.withProduct(cafeBebida).withProduct(cafeSnack).withProduct(aguaSnack);
        boughtFrom(ACME, cafeBebida, 5);
        boughtFrom(ACME, cafeSnack, 5);
        boughtFrom(ACME, aguaSnack, 5);
        viewModel.load();

        viewModel.searchTextProperty().set("cafe");
        viewModel.categoryFilterProperty().set(snacks);
        viewModel.supplierFilterProperty().set(ACME);

        assertEquals(1, viewModel.filteredProducts().size());
        assertEquals("SNK-001", viewModel.filteredProducts().get(0).getSku());
    }

    @Test
    @DisplayName("clearFilters deja buscador, categoría, proveedor e inactivos por defecto")
    void clearFiltersResetsEverything() throws Exception {
        Product cafe = product("1", "Café", "CAF-001", "3.00", 0, true, DRINKS);
        Product viejo = product("2", "Viejo", "VIE-001", "1.00", 0, false, DRINKS);
        fakeRepository.withProduct(cafe).withProduct(viejo);
        boughtFrom(ACME, cafe, 5);
        viewModel.load();

        viewModel.searchTextProperty().set("caf");
        viewModel.categoryFilterProperty().set(DRINKS);
        viewModel.supplierFilterProperty().set(ACME);
        viewModel.showInactiveProperty().set(true);
        // The inactive list holds only "Viejo", which does not match "caf".
        assertEquals(0, viewModel.filteredProducts().size());

        viewModel.clearFilters();

        assertEquals("", viewModel.searchTextProperty().get());
        assertNull(viewModel.categoryFilterProperty().get());
        assertNull(viewModel.supplierFilterProperty().get());
        assertFalse(viewModel.showInactiveProperty().get());
        assertTrue(viewModel.filteredProducts().size() >= 1);
        assertEquals("Productos: 1", viewModel.productCountProperty().get());
    }
}
