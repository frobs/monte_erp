package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
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
import java.time.LocalDate;
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
        User user = new User("Bea", "bea", "hash", Role.EMPLOYEE, true);
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
        Purchase purchase = new Purchase(5, LocalDateTime.now(), PurchaseStatus.RECEIVED,
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
    @DisplayName("save válido inserta la compra pending, con usuario y total, y limpia el formulario")
    void saveValidPurchase() {
        selectSupplier();
        addLot("Teclado", "2", "10.00");
        addLot("Ratón", "1", "5.50");

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.draftLots().size());
        assertNull(viewModel.selectedSupplierProperty().get());

        Purchase inserted = purchaseRepository.lastInserted();
        assertEquals(PurchaseStatus.PENDING, inserted.getStatus());
        assertEquals("Acme", inserted.getSupplier().getName());
        assertEquals(9, inserted.getUser().getId());
        assertEquals(2, inserted.getLots().size());
        assertEquals(0, new BigDecimal("25.50").compareTo(inserted.getTotal()));

        assertEquals(1, viewModel.purchases().size());
        Purchase saved = viewModel.purchases().get(0);
        assertTrue(viewModel.statusMessageProperty().get().contains("Compra #" + saved.getId()));
        assertEquals(PurchaseStatus.PENDING, saved.getStatus());
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
        Purchase purchase = new Purchase(5, LocalDateTime.now(), PurchaseStatus.RECEIVED,
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
        Purchase purchase = new Purchase(5, LocalDateTime.now(), PurchaseStatus.RECEIVED,
                BigDecimal.ZERO, ACME, user(), List.of());

        viewModel.selectPurchase(purchase);

        assertEquals(0, viewModel.selectedPurchaseLots().size());
        assertEquals(PurchasesViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Una recarga fallida tras confirmar la compra no conserva el borrador")
    void postCommitReloadFailureCannotResubmitPurchase() throws Exception {
        selectSupplier();
        addLot("Teclado", "1", "10.00");
        purchaseRepository.failNext("findAll");

        viewModel.save();

        assertTrue(viewModel.errorMessageProperty().get().contains("Compra #1 guardada"));
        assertEquals(0, viewModel.draftLots().size());
        assertEquals(1, purchaseRepository.findAll().size());

        viewModel.save();

        assertEquals(PurchasesViewModel.LOTS_REQUIRED, viewModel.lotsErrorProperty().get());
        assertEquals(1, purchaseRepository.findAll().size());
    }

    @Test
    @DisplayName("La compra acepta la cantidad máxima de INT firmado")
    void addLotAcceptsMaximumSignedIntQuantity() {
        Product zeroPrice = product(1, "Teclado");
        viewModel.selectedProductProperty().set(zeroPrice);
        viewModel.quantityProperty().set(String.valueOf(Integer.MAX_VALUE));
        viewModel.purchasePriceProperty().set("0");

        viewModel.addLot();

        assertEquals(Integer.MAX_VALUE, viewModel.draftLots().get(0).getInitialQuantity());
    }

    @Test
    @DisplayName("La cantidad mayor que INT firmado se rechaza sin desbordar")
    void addLotRejectsQuantityOverflow() {
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        viewModel.quantityProperty().set("2147483648");
        viewModel.purchasePriceProperty().set("0");

        viewModel.addLot();

        assertEquals(PurchasesViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
        assertEquals(0, viewModel.draftLots().size());
    }

    @Test
    @DisplayName("La compra rechaza un subtotal superior al máximo DECIMAL persistible")
    void addLotRejectsUnpersistableTotal() {
        viewModel.selectedProductProperty().set(viewModel.products().get(0));
        viewModel.quantityProperty().set("2");
        viewModel.purchasePriceProperty().set("50000000.00");

        viewModel.addLot();

        assertEquals("El total de la compra no puede superar 99.999.999,99 €.",
                viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.draftLots().size());
    }

    // --- Status machine -------------------------------------------------

    private Purchase selectStoredPurchase(PurchaseStatus status) {
        Purchase purchase = new Purchase(0, LocalDateTime.now(), status,
                new BigDecimal("20.00"), ACME, user(), List.of());
        purchaseRepository.withPurchase(purchase)
                .withLot(purchase.getId(),
                        new Stock(product(1, "Teclado"), 2, new BigDecimal("10.00")));
        viewModel.load();
        Purchase selected = viewModel.purchases().get(0);
        viewModel.selectedPurchaseProperty().set(selected);
        viewModel.selectPurchase(selected);
        return selected;
    }

    @Test
    @DisplayName("availableTransitions vacío sin selección y completo para una compra pending")
    void availableTransitionsReflectTheSelectedPurchase() {
        assertEquals(List.of(), viewModel.availableTransitions());

        selectStoredPurchase(PurchaseStatus.PENDING);

        assertEquals(List.of(PurchaseStatus.RECEIVED, PurchaseStatus.CANCELLED),
                viewModel.availableTransitions());
    }

    @Test
    @DisplayName("availableTransitions de una compra recibida solo ofrece devolver")
    void receivedPurchaseOnlyOffersReturn() {
        selectStoredPurchase(PurchaseStatus.RECEIVED);

        assertEquals(List.of(PurchaseStatus.RETURNED), viewModel.availableTransitions());
    }

    @Test
    @DisplayName("availableTransitions vacío para una compra terminal")
    void availableTransitionsAreEmptyForTerminalPurchases() {
        selectStoredPurchase(PurchaseStatus.RETURNED);

        assertEquals(List.of(), viewModel.availableTransitions());
    }

    @Test
    @DisplayName("load limpia availableTransitions junto con la selección")
    void loadClearsAvailableTransitions() {
        selectStoredPurchase(PurchaseStatus.PENDING);
        assertEquals(2, viewModel.availableTransitions().size());

        viewModel.load();

        assertEquals(0, viewModel.availableTransitions().size());
    }

    @Test
    @DisplayName("changeStatus sin compra seleccionada avisa y no toca el repositorio")
    void changeStatusWithoutSelectionFails() {
        purchaseRepository.failOn("changeStatus");

        viewModel.changeStatus(PurchaseStatus.RECEIVED);

        assertEquals(PurchasesViewModel.PURCHASE_REQUIRED, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changeStatus con transición no permitida avisa y no toca el repositorio")
    void changeStatusWithInvalidTransitionFailsBeforeTheRepository() {
        selectStoredPurchase(PurchaseStatus.RECEIVED);
        purchaseRepository.failOn("changeStatus");

        viewModel.changeStatus(PurchaseStatus.CANCELLED);

        assertEquals(String.format(PurchasesViewModel.INVALID_TRANSITION, "Recibida", "Cancelada"),
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("changeStatus válido persiste, recarga el histórico y reselecciona")
    void changeStatusPersistsAndReloads() {
        Purchase selected = selectStoredPurchase(PurchaseStatus.PENDING);
        int id = selected.getId();

        viewModel.changeStatus(PurchaseStatus.RECEIVED);

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("Compra #" + id));
        assertTrue(viewModel.statusMessageProperty().get().contains("Recibida"));
        Purchase reloaded = viewModel.selectedPurchaseProperty().get();
        assertEquals(PurchaseStatus.RECEIVED, reloaded.getStatus());
        assertEquals(List.of(PurchaseStatus.RETURNED), viewModel.availableTransitions());
        assertEquals(2, viewModel.selectedPurchaseLots().get(0).getAvailableQuantity());
    }

    @Test
    @DisplayName("changeStatus con guarda optimista rota recarga y avisa del conflicto")
    void changeStatusReportsConflict() {
        Purchase selected = selectStoredPurchase(PurchaseStatus.PENDING);
        // Someone else moved the purchase: the stored status no longer matches.
        purchaseRepository.withPurchase(new Purchase(selected.getId(), LocalDateTime.now(),
                PurchaseStatus.CANCELLED, selected.getTotal(), ACME, user(), List.of()));

        viewModel.changeStatus(PurchaseStatus.RECEIVED);

        assertEquals(PurchasesViewModel.CONFLICT, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Devolver una compra con unidades vendidas muestra el motivo del rechazo")
    void returnRejectedWhenStockWasConsumed() {
        Purchase purchase = new Purchase(0, LocalDateTime.now(), PurchaseStatus.RECEIVED,
                new BigDecimal("20.00"), ACME, user(), List.of());
        purchaseRepository.withPurchase(purchase)
                .withLot(purchase.getId(), new Stock(11, 2, 1, new BigDecimal("10.00"),
                        LocalDateTime.now(), product(1, "Teclado"), null));
        viewModel.load();
        Purchase selected = viewModel.purchases().get(0);
        viewModel.selectedPurchaseProperty().set(selected);
        viewModel.selectPurchase(selected);

        viewModel.changeStatus(PurchaseStatus.RETURNED);

        assertEquals("No se puede devolver la compra #" + purchase.getId()
                + ": 1 unidad ya se ha vendido.", viewModel.errorMessageProperty().get());
        assertEquals(PurchaseStatus.RECEIVED,
                viewModel.selectedPurchaseProperty().get().getStatus());
    }

    @Test
    @DisplayName("changeStatus con la base de datos caída muestra el error de conexión")
    void changeStatusReportsDatabaseError() {
        selectStoredPurchase(PurchaseStatus.PENDING);
        purchaseRepository.failOn("changeStatus");

        viewModel.changeStatus(PurchaseStatus.RECEIVED);

        assertEquals(PurchasesViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }

    // --- History filters (queried in the database) ----------------------

    private Purchase storePurchase(LocalDate date, Supplier supplier, PurchaseStatus status) {
        Purchase purchase = new Purchase(0, date.atTime(10, 0), status,
                new BigDecimal("20.00"), supplier, user(), List.of());
        purchaseRepository.withPurchase(purchase);
        return purchase;
    }

    /** Three purchases in two months, from three suppliers, in three statuses. */
    private void loadThreePurchases() {
        storePurchase(LocalDate.of(2024, 1, 15), ACME, PurchaseStatus.PENDING);
        storePurchase(LocalDate.of(2024, 2, 15), GLOBEX, PurchaseStatus.RECEIVED);
        storePurchase(LocalDate.of(2024, 2, 29), INACTIVE, PurchaseStatus.RETURNED);
        viewModel.load();
    }

    @Test
    @DisplayName("El filtro de fechas viaja a la consulta y respeta los dos extremos por día")
    void dateRangeFilterIsQueriedInclusive() {
        loadThreePurchases();

        viewModel.fromDateProperty().set(LocalDate.of(2024, 2, 15));
        viewModel.toDateProperty().set(LocalDate.of(2024, 2, 29));

        assertEquals(2, viewModel.purchases().size());
        assertEquals(PurchaseFilter.of(LocalDate.of(2024, 2, 15), LocalDate.of(2024, 2, 29),
                null, null), purchaseRepository.lastFilter());
    }

    @Test
    @DisplayName("Un rango invertido avisa, vacía la tabla y no consulta la base de datos")
    void invertedRangeIsRejectedWithoutQuerying() {
        loadThreePurchases();
        viewModel.fromDateProperty().set(LocalDate.of(2024, 3, 1));
        int calls = purchaseRepository.findAllCalls();

        viewModel.toDateProperty().set(LocalDate.of(2024, 2, 1));

        assertEquals(PurchasesViewModel.PERIOD_ERROR, viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.purchases().size());
        assertEquals(calls, purchaseRepository.findAllCalls());
    }

    @Test
    @DisplayName("Corregir el rango limpia el error y vuelve a consultar")
    void correctedRangeClearsTheError() {
        loadThreePurchases();
        viewModel.fromDateProperty().set(LocalDate.of(2024, 1, 1));
        viewModel.toDateProperty().set(LocalDate.of(2023, 12, 31));
        assertEquals(PurchasesViewModel.PERIOD_ERROR, viewModel.errorMessageProperty().get());

        viewModel.toDateProperty().set(LocalDate.of(2024, 12, 31));

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(3, viewModel.purchases().size());
    }

    @Test
    @DisplayName("El filtro de proveedor consulta por id, incluso uno dado de baja")
    void supplierFilterIsQueriedById() {
        loadThreePurchases();

        viewModel.supplierFilterProperty().set(INACTIVE);

        assertEquals(1, viewModel.purchases().size());
        assertEquals(INACTIVE.getId(), purchaseRepository.lastFilter().supplierId());
    }

    @Test
    @DisplayName("El filtro de estado consulta por el valor del dominio")
    void statusFilterIsQueried() {
        loadThreePurchases();

        viewModel.statusFilterProperty().set(PurchaseStatus.RECEIVED);

        assertEquals(1, viewModel.purchases().size());
        assertEquals(PurchaseStatus.RECEIVED, purchaseRepository.lastFilter().status());
    }

    @Test
    @DisplayName("Los tres criterios se combinan en una única consulta")
    void filtersCombineInOneQuery() {
        loadThreePurchases();
        int calls = purchaseRepository.findAllCalls();

        viewModel.fromDateProperty().set(LocalDate.of(2024, 2, 1));
        viewModel.supplierFilterProperty().set(GLOBEX);
        viewModel.statusFilterProperty().set(PurchaseStatus.RECEIVED);

        assertEquals(1, viewModel.purchases().size());
        assertEquals(calls + 3, purchaseRepository.findAllCalls());
        assertEquals(PurchaseFilter.of(LocalDate.of(2024, 2, 1), null, GLOBEX.getId(),
                PurchaseStatus.RECEIVED), purchaseRepository.lastFilter());
    }

    @Test
    @DisplayName("clearFilters borra los criterios y hace una sola consulta")
    void clearFiltersDropsEveryCriterionWithOneQuery() {
        loadThreePurchases();
        viewModel.supplierFilterProperty().set(GLOBEX);
        int calls = purchaseRepository.findAllCalls();

        viewModel.clearFilters();

        assertNull(viewModel.fromDateProperty().get());
        assertNull(viewModel.toDateProperty().get());
        assertNull(viewModel.supplierFilterProperty().get());
        assertNull(viewModel.statusFilterProperty().get());
        assertEquals(3, viewModel.purchases().size());
        assertEquals(PurchaseFilter.all(), purchaseRepository.lastFilter());
        assertEquals(calls + 1, purchaseRepository.findAllCalls());
    }

    @Test
    @DisplayName("Filtrar deja sin selección una compra que ya no se muestra")
    void filteringDropsTheSelectionOfAHiddenPurchase() {
        loadThreePurchases();
        Purchase pending = viewModel.purchases().stream()
                .filter(purchase -> PurchaseStatus.PENDING.equals(purchase.getStatus()))
                .findFirst().orElseThrow();
        viewModel.selectedPurchaseProperty().set(pending);
        viewModel.selectPurchase(pending);
        assertEquals(2, viewModel.availableTransitions().size());

        viewModel.statusFilterProperty().set(PurchaseStatus.RECEIVED);

        assertNull(viewModel.selectedPurchaseProperty().get());
        assertEquals(0, viewModel.availableTransitions().size());
        assertEquals(0, viewModel.selectedPurchaseLots().size());
    }

    @Test
    @DisplayName("La consulta de filtros fallida vacía la tabla y muestra el error de conexión")
    void filterQueryFailureReportsDatabaseError() {
        loadThreePurchases();
        purchaseRepository.failOn("findAll");

        viewModel.statusFilterProperty().set(PurchaseStatus.RECEIVED);

        assertEquals(PurchasesViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.purchases().size());
    }

    @Test
    @DisplayName("Guardar mantiene el filtro activo en la recarga del histórico")
    void saveReloadsWithTheActiveFilter() {
        viewModel.statusFilterProperty().set(PurchaseStatus.PENDING);
        selectSupplier();
        addLot("Teclado", "1", "10.00");

        viewModel.save();

        assertEquals(1, viewModel.purchases().size());
        assertEquals(PurchaseStatus.PENDING, purchaseRepository.lastFilter().status());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Cambiar el estado mantiene el filtro: la compra puede desaparecer del histórico")
    void changeStatusReloadsWithTheActiveFilter() {
        storePurchase(LocalDate.of(2024, 1, 15), ACME, PurchaseStatus.PENDING);
        viewModel.statusFilterProperty().set(PurchaseStatus.PENDING);
        Purchase selected = viewModel.purchases().get(0);
        viewModel.selectedPurchaseProperty().set(selected);

        viewModel.changeStatus(PurchaseStatus.RECEIVED);

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.purchases().size());
        assertTrue(viewModel.statusMessageProperty().get().contains("Recibida"));
        assertEquals(PurchaseStatus.PENDING, purchaseRepository.lastFilter().status());
    }

    @Test
    @DisplayName("Las opciones del filtro son todos los proveedores y los cuatro estados")
    void filterOptionsCoverEverySupplierAndStatus() {
        assertEquals(List.of("Acme", "Globex", "Old"),
                viewModel.supplierFilterOptions().stream().map(Supplier::getName).toList());
        assertEquals(List.of(PurchaseStatus.PENDING, PurchaseStatus.RECEIVED,
                        PurchaseStatus.CANCELLED, PurchaseStatus.RETURNED),
                viewModel.statusFilterOptions());
    }
}
