package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeProductRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakePurchaseRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSupplierRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PurchasesViewModel} (RF-14 / CU-10).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with the
 * in-memory doubles. The per-field error properties replace ValidatorFX, so the
 * rules are asserted without a toolkit.</p>
 */
class PurchasesViewModelTest {

    private static final Supplier ACME = new Supplier(1, "Acme", null, null, null, true);
    private static final Supplier GLOBEX = new Supplier(2, "Globex", null, null, null, true);
    private static final Supplier INACTIVE = new Supplier(3, "Old", null, null, null, false);

    private FakeSupplierRepository supplierRepository;
    private FakeProductRepository productRepository;
    private FakePurchaseRepository purchaseRepository;
    private PurchasesViewModel viewModel;

    @BeforeEach
    void setUp() {
        supplierRepository = new FakeSupplierRepository()
                .withSupplier(ACME).withSupplier(GLOBEX).withSupplier(INACTIVE);
        productRepository = new FakeProductRepository()
                .withProduct(product(1, "Teclado"))
                .withProduct(product(2, "Ratón"))
                .withProduct(product(3, "Antiguo", false));
        purchaseRepository = new FakePurchaseRepository();
        viewModel = new PurchasesViewModel(purchaseRepository, supplierRepository,
                productRepository, user());
        viewModel.load();
    }

    private static Product product(int id, String name) {
        return product(id, name, true);
    }

    private static Product product(int id, String name, boolean active) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal("10.00"), 0, null, active);
        product.setId(id);
        return product;
    }

    private static User user() {
        User user = new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
        user.setId(9);
        return user;
    }

    private void selectSupplier() {
        viewModel.selectedSupplierProperty().set(ACME);
    }

    private void addLot(String productName, String quantity, String price) {
        Product product = viewModel.products().stream()
                .filter(candidate -> candidate.getName().equals(productName))
                .findFirst().orElseThrow();
        viewModel.selectedProductProperty().set(product);
        viewModel.quantityProperty().set(quantity);
        viewModel.purchasePriceProperty().set(price);
        viewModel.addLot();
    }

    @Test
    @DisplayName("load carga solo proveedores y productos activos, y el histórico")
    void loadFillsActiveCombosAndHistory() {
        assertEquals(List.of("Acme", "Globex"),
                viewModel.suppliers().stream().map(Supplier::getName).toList());
        assertEquals(List.of("Ratón", "Teclado"),
                viewModel.products().stream().map(Product::getName).toList());
        assertEquals(0, viewModel.purchases().size());
    }

    @Test
    @DisplayName("load con la base de datos caída muestra el error de conexión")
    void loadReportsDatabaseError() {
        PurchasesViewModel failing = new PurchasesViewModel(
                new FakePurchaseRepository().throwOnAccess(), supplierRepository,
                productRepository, user());

        failing.load();

        assertEquals(PurchasesViewModel.DATABASE_ERROR, failing.errorMessageProperty().get());
    }

    @Test
    @DisplayName("load limpia la selección del histórico y su detalle")
    void loadClearsSelectedPurchase() {
        Purchase purchase = new Purchase(5, LocalDateTime.now(), Purchase.STATUS_RECEIVED,
                new BigDecimal("20.00"), ACME, user(), List.of());
        purchaseRepository.withPurchase(purchase)
                .withLot(5, new Stock(product(1, "Teclado"), 2, new BigDecimal("10.00")));
        viewModel.selectedPurchaseProperty().set(purchase);
        viewModel.selectPurchase(purchase);
        assertEquals(1, viewModel.selectedPurchaseLots().size());

        viewModel.load();

        assertNull(viewModel.selectedPurchaseProperty().get());
        assertEquals(0, viewModel.selectedPurchaseLots().size());
    }

    @Test
    @DisplayName("addLot válido crea un lote disponible y calcula subtotal y total (D5/D6)")
    void addLotCreatesAvailableLotAndRecomputesTotal() {
        addLot("Teclado", "2", "10.00");

        assertEquals(1, viewModel.draftLots().size());
        Stock lot = viewModel.draftLots().get(0);
        assertEquals(2, lot.getInitialQuantity());
        assertEquals(2, lot.getAvailableQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(lot.getPurchasePrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(lot.subtotal()));
        assertEquals(0, new BigDecimal("20.00").compareTo(viewModel.purchaseTotalProperty().get()));
        assertNull(viewModel.selectedProductProperty().get());
        assertEquals("", viewModel.quantityProperty().get());
        assertEquals("", viewModel.purchasePriceProperty().get());
    }

    @Test
    @DisplayName("addLot sin producto muestra error de campo y no añade el lote")
    void addLotWithoutProductFails() {
        viewModel.quantityProperty().set("2");
        viewModel.purchasePriceProperty().set("10.00");

        viewModel.addLot();

        assertEquals(PurchasesViewModel.PRODUCT_REQUIRED, viewModel.productErrorProperty().get());
        assertEquals(0, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("addLot sin precio muestra error de campo")
    void addLotWithoutPriceFails() {
        Product product = viewModel.products().get(0);
        viewModel.selectedProductProperty().set(product);
        viewModel.quantityProperty().set("1");

        viewModel.addLot();

        assertEquals(PurchasesViewModel.PRICE_INVALID, viewModel.priceErrorProperty().get());
        assertEquals(0, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("addLot con precio no numérico o negativo muestra error de campo")
    void addLotWithInvalidPriceFails() {
        Product product = viewModel.products().get(0);
        viewModel.selectedProductProperty().set(product);
        viewModel.quantityProperty().set("1");

        viewModel.purchasePriceProperty().set("caro");
        viewModel.addLot();
        assertEquals(PurchasesViewModel.PRICE_INVALID, viewModel.priceErrorProperty().get());

        viewModel.selectedProductProperty().set(product);
        viewModel.purchasePriceProperty().set("-1.00");
        viewModel.addLot();
        assertEquals(PurchasesViewModel.PRICE_INVALID, viewModel.priceErrorProperty().get());
        assertEquals(0, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("addLot con cantidad vacía, no numérica, 0 o negativa muestra error de campo")
    void addLotWithInvalidQuantityFails() {
        Product product = viewModel.products().get(0);
        viewModel.purchasePriceProperty().set("10.00");

        for (String quantity : List.of("", "dos", "0", "-3")) {
            viewModel.selectedProductProperty().set(product);
            viewModel.quantityProperty().set(quantity);
            viewModel.addLot();
            assertEquals(PurchasesViewModel.QUANTITY_INVALID,
                    viewModel.quantityErrorProperty().get(), "cantidad: " + quantity);
        }
        assertEquals(0, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("addLot con producto repetido se rechaza (decisión D2)")
    void addLotRejectsDuplicatedProduct() {
        addLot("Teclado", "1", "10.00");

        addLot("Teclado", "1", "10.00");

        assertEquals(PurchasesViewModel.DUPLICATED_PRODUCT, viewModel.productErrorProperty().get());
        assertEquals(1, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("removeLot quita el lote y recalcula el total")
    void removeLotRecomputesTotal() {
        addLot("Teclado", "2", "10.00");
        addLot("Ratón", "3", "5.50");
        assertEquals(0, new BigDecimal("36.50").compareTo(viewModel.purchaseTotalProperty().get()));

        viewModel.removeLot(viewModel.draftLots().get(0));

        assertEquals(1, viewModel.draftLots().size());
        assertEquals(0, new BigDecimal("16.50").compareTo(viewModel.purchaseTotalProperty().get()));
    }

    @Test
    @DisplayName("newPurchase vacía el borrador y el total")
    void newPurchaseClearsTheDraft() {
        addLot("Teclado", "2", "10.00");

        viewModel.newPurchase();

        assertEquals(0, viewModel.draftLots().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(viewModel.purchaseTotalProperty().get()));
        assertNull(viewModel.selectedSupplierProperty().get());
    }

    @Test
    @DisplayName("save sin proveedor muestra error de campo y no toca el repositorio")
    void saveWithoutSupplierFails() {
        addLot("Teclado", "1", "10.00");
        purchaseRepository.failOn("insert");

        viewModel.save();

        assertEquals(PurchasesViewModel.SUPPLIER_REQUIRED, viewModel.supplierErrorProperty().get());
        assertEquals(0, viewModel.purchases().size());
    }

    @Test
    @DisplayName("save sin lotes muestra error de campo y no toca el repositorio")
    void saveWithoutLotsFails() {
        selectSupplier();
        purchaseRepository.failOn("insert");

        viewModel.save();

        assertEquals(PurchasesViewModel.LOTS_REQUIRED, viewModel.lotsErrorProperty().get());
        assertEquals(0, viewModel.purchases().size());
    }

    @Test
    @DisplayName("save válido inserta la compra received, con usuario y total, y limpia el formulario")
    void saveValidPurchase() {
        selectSupplier();
        addLot("Teclado", "2", "10.00");
        addLot("Ratón", "1", "5.50");

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.draftLots().size());
        assertNull(viewModel.selectedSupplierProperty().get());

        Purchase inserted = purchaseRepository.lastInserted();
        assertEquals(Purchase.STATUS_RECEIVED, inserted.getStatus());
        assertEquals("Acme", inserted.getSupplier().getName());
        assertEquals(9, inserted.getUser().getId());
        assertEquals(2, inserted.getLots().size());
        assertEquals(0, new BigDecimal("25.50").compareTo(inserted.getTotal()));

        assertEquals(1, viewModel.purchases().size());
        Purchase saved = viewModel.purchases().get(0);
        assertTrue(viewModel.statusMessageProperty().get().contains("Compra #" + saved.getId()));
        assertEquals(Purchase.STATUS_RECEIVED, saved.getStatus());
    }

    @Test
    @DisplayName("save con fallo de base de datos muestra el error de conexión")
    void saveReportsDatabaseError() {
        selectSupplier();
        addLot("Teclado", "1", "10.00");
        purchaseRepository.failOn("insert");

        viewModel.save();

        assertEquals(PurchasesViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("selectPurchase carga los lotes y null los vacía")
    void selectPurchaseLoadsAndClearsLots() {
        Purchase purchase = new Purchase(5, LocalDateTime.now(), Purchase.STATUS_RECEIVED,
                new BigDecimal("20.00"), ACME, user(), List.of());
        purchaseRepository.withPurchase(purchase)
                .withLot(5, new Stock(product(1, "Teclado"), 2, new BigDecimal("10.00")));

        viewModel.selectPurchase(purchase);
        assertEquals(1, viewModel.selectedPurchaseLots().size());
        assertEquals("Teclado", viewModel.selectedPurchaseLots().get(0).getProduct().getName());

        viewModel.selectPurchase(null);
        assertEquals(0, viewModel.selectedPurchaseLots().size());
    }

    @Test
    @DisplayName("selectPurchase con la base de datos caída vacía el detalle y avisa")
    void selectPurchaseReportsDatabaseError() {
        purchaseRepository.failOn("findLots");
        Purchase purchase = new Purchase(5, LocalDateTime.now(), Purchase.STATUS_RECEIVED,
                BigDecimal.ZERO, ACME, user(), List.of());

        viewModel.selectPurchase(purchase);

        assertEquals(0, viewModel.selectedPurchaseLots().size());
        assertEquals(PurchasesViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }
}
