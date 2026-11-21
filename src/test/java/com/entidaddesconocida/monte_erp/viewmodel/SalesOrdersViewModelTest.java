package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.OrderStatus;
import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCustomerRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeProductRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSalesOrderRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeStockRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SalesOrdersViewModel} (RF-07 / CU-07, total from RF-13).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with the
 * in-memory doubles. The per-field error properties replace ValidatorFX, so the
 * rules are asserted without a toolkit.</p>
 */
class SalesOrdersViewModelTest {

    private static final Customer ACME = new Customer(1, "Acme", null, null, null);
    private static final Customer GLOBEX = new Customer(2, "Globex", null, null, null);
    private static final Customer INACTIVE = new Customer(3, "Old", null, null, null, false);

    private FakeCustomerRepository customerRepository;
    private FakeProductRepository productRepository;
    private FakeSalesOrderRepository salesOrderRepository;
    private FakeStockRepository stockRepository;
    private SalesOrdersViewModel viewModel;

    private Product teclado;
    private Product raton;

    @BeforeEach
    void setUp() {
        customerRepository = new FakeCustomerRepository()
                .withCustomer(ACME).withCustomer(GLOBEX).withCustomer(INACTIVE);
        productRepository = new FakeProductRepository()
                .withProduct(product(1, "Teclado", "10.00"))
                .withProduct(product(2, "Ratón", "5.50"))
                .withProduct(product(3, "Antiguo", "1.00", false));
        salesOrderRepository = new FakeSalesOrderRepository();
        // Plenty of stock so the RF-09 pre-check does not disturb the RF-07 tests.
        stockRepository = new FakeStockRepository()
                .withLot(1, lot(product(1, "Teclado", "10.00"), 100))
                .withLot(2, lot(product(2, "Ratón", "5.50"), 100));
        viewModel = new SalesOrdersViewModel(salesOrderRepository, customerRepository,
                productRepository, stockRepository, sessionUser());
        viewModel.load();
    }

    private static Stock lot(Product product, int available) {
        Stock lot = new Stock(1, available, available, new BigDecimal("1.00"),
                LocalDateTime.now(), product, null);
        return lot;
    }

    private static Product product(int id, String name, String price) {
        return product(id, name, price, true);
    }

    private static Product product(int id, String name, String price, boolean active) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal(price), 0, null, active);
        product.setId(id);
        return product;
    }

    private static User user() {
        User user = new User("Bea", "bea", "hash", Role.EMPLOYEE, true);
        user.setId(9);
        return user;
    }

    /** Signed-in snapshot handed to the ViewModel: same account, no credentials. */
    private static SessionUser sessionUser() {
        return SessionUser.of(user());
    }

    private void selectProduct(int index) {
        teclado = viewModel.products().get(index);
        viewModel.selectedProductProperty().set(teclado);
    }

    private void addLine(String productName, String quantity) {
        Product product = viewModel.products().stream()
                .filter(candidate -> candidate.getName().equals(productName))
                .findFirst().orElseThrow();
        viewModel.selectedProductProperty().set(product);
        viewModel.quantityProperty().set(quantity);
        viewModel.addLine();
    }

    @Test
    @DisplayName("load carga solo clientes y productos activos, y el histórico")
    void loadFillsActiveCombosAndHistory() {
        assertEquals(List.of("Acme", "Globex"),
                viewModel.customers().stream().map(Customer::getName).toList());
        assertEquals(List.of("Ratón", "Teclado"),
                viewModel.products().stream().map(Product::getName).toList());
        assertEquals(0, viewModel.orders().size());
    }

    @Test
    @DisplayName("load con la base de datos caída muestra el error de conexión")
    void loadReportsDatabaseError() {
        SalesOrdersViewModel failing = new SalesOrdersViewModel(
                new FakeSalesOrderRepository().throwOnAccess(), customerRepository,
                productRepository, stockRepository, sessionUser());

        failing.load();

        assertEquals(SalesOrdersViewModel.DATABASE_ERROR, failing.errorMessageProperty().get());
    }

    @Test
    @DisplayName("load limpia la selección del histórico y su detalle")
    void loadClearsSelectedOrder() {
        SalesOrder order = new SalesOrder(5, java.time.LocalDateTime.now(),
                OrderStatus.PENDING, BigDecimal.ZERO, ACME, user(), List.of());
        salesOrderRepository.withOrder(order)
                .withLine(5, new SalesOrderLine(1, new BigDecimal("10.00"), product(1, "Teclado", "10.00")));
        viewModel.selectedOrderProperty().set(order);
        viewModel.selectOrder(order);
        assertEquals(1, viewModel.selectedOrderLines().size());

        viewModel.load();

        assertNull(viewModel.selectedOrderProperty().get());
        assertEquals(0, viewModel.selectedOrderLines().size());
    }

    @Test
    @DisplayName("addLine válido toma el precio del producto y calcula subtotal y total")
    void addLineUsesProductPriceSnapshot() {
        addLine("Teclado", "2");

        assertEquals(1, viewModel.draftLines().size());
        SalesOrderLine line = viewModel.draftLines().get(0);
        assertEquals(2, line.getQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(line.getUnitPrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(line.subtotal()));
        assertEquals(0, new BigDecimal("20.00").compareTo(viewModel.orderTotalProperty().get()));
        assertNull(viewModel.selectedProductProperty().get());
        assertEquals("", viewModel.quantityProperty().get());
    }

    @Test
    @DisplayName("addLine sin producto muestra error de campo y no añade la línea")
    void addLineWithoutProductFails() {
        viewModel.quantityProperty().set("2");

        viewModel.addLine();

        assertEquals(SalesOrdersViewModel.PRODUCT_REQUIRED, viewModel.productErrorProperty().get());
        assertEquals(0, viewModel.draftLines().size());
    }

    @Test
    @DisplayName("addLine con cantidad vacía muestra error de campo")
    void addLineWithEmptyQuantityFails() {
        selectProduct(0);

        viewModel.addLine();

        assertEquals(SalesOrdersViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
        assertEquals(0, viewModel.draftLines().size());
    }

    @Test
    @DisplayName("addLine con cantidad no numérica muestra error de campo")
    void addLineWithNonNumericQuantityFails() {
        selectProduct(0);
        viewModel.quantityProperty().set("dos");

        viewModel.addLine();

        assertEquals(SalesOrdersViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
    }

    @Test
    @DisplayName("addLine con cantidad 0 muestra error de campo")
    void addLineWithZeroQuantityFails() {
        selectProduct(0);
        viewModel.quantityProperty().set("0");

        viewModel.addLine();

        assertEquals(SalesOrdersViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
    }

    @Test
    @DisplayName("addLine con cantidad negativa muestra error de campo")
    void addLineWithNegativeQuantityFails() {
        selectProduct(0);
        viewModel.quantityProperty().set("-3");

        viewModel.addLine();

        assertEquals(SalesOrdersViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
    }

    @Test
    @DisplayName("addLine con producto repetido se rechaza (decisión D3)")
    void addLineRejectsDuplicatedProduct() {
        addLine("Teclado", "1");

        addLine("Teclado", "1");

        assertEquals(SalesOrdersViewModel.DUPLICATED_PRODUCT, viewModel.productErrorProperty().get());
        assertEquals(1, viewModel.draftLines().size());
    }

    @Test
    @DisplayName("removeLine quita la línea y recalcula el total")
    void removeLineRecomputesTotal() {
        addLine("Teclado", "2");
        addLine("Ratón", "3");
        assertEquals(0, new BigDecimal("36.50").compareTo(viewModel.orderTotalProperty().get()));

        viewModel.removeLine(viewModel.draftLines().get(0));

        assertEquals(1, viewModel.draftLines().size());
        assertEquals(0, new BigDecimal("16.50").compareTo(viewModel.orderTotalProperty().get()));
    }

    @Test
    @DisplayName("newOrder vacía el borrador y el total")
    void newOrderClearsTheDraft() {
        addLine("Teclado", "2");

        viewModel.newOrder();

        assertEquals(0, viewModel.draftLines().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(viewModel.orderTotalProperty().get()));
        assertNull(viewModel.selectedCustomerProperty().get());
    }

    @Test
    @DisplayName("save sin cliente muestra error de campo")
    void saveWithoutCustomerFails() {
        addLine("Teclado", "1");

        viewModel.save();

        assertEquals(SalesOrdersViewModel.CUSTOMER_REQUIRED, viewModel.customerErrorProperty().get());
        assertEquals(0, viewModel.orders().size());
    }

    @Test
    @DisplayName("save sin líneas muestra error de campo")
    void saveWithoutLinesFails() {
        viewModel.selectedCustomerProperty().set(ACME);

        viewModel.save();

        assertEquals(SalesOrdersViewModel.LINES_REQUIRED, viewModel.linesErrorProperty().get());
        assertEquals(0, viewModel.orders().size());
    }

    @Test
    @DisplayName("save válido inserta el pedido pending, con usuario y total, y limpia el formulario")
    void saveValidOrder() {
        viewModel.selectedCustomerProperty().set(ACME);
        addLine("Teclado", "2");
        addLine("Ratón", "1");

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.draftLines().size());
        assertNull(viewModel.selectedCustomerProperty().get());

        assertEquals(1, viewModel.orders().size());
        SalesOrder saved = viewModel.orders().get(0);
        assertTrue(viewModel.statusMessageProperty().get().contains("Pedido #" + saved.getId()));
        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertEquals("Acme", saved.getCustomer().getName());
        assertEquals(9, saved.getUser().getId());
        assertEquals(0, new BigDecimal("25.50").compareTo(saved.getTotal()));
    }

    @Test
    @DisplayName("save con fallo de base de datos muestra el error de conexión")
    void saveReportsDatabaseError() {
        salesOrderRepository.failOn("insert");
        viewModel.selectedCustomerProperty().set(ACME);
        addLine("Teclado", "1");

        viewModel.save();

        assertEquals(SalesOrdersViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("selectOrder carga las líneas y null las vacía")
    void selectOrderLoadsAndClearsLines() {
        Product product = product(1, "Teclado", "10.00");
        SalesOrder order = new SalesOrder(5, java.time.LocalDateTime.now(),
                OrderStatus.PENDING, new BigDecimal("20.00"), ACME, user(), List.of());
        salesOrderRepository.withOrder(order)
                .withLine(5, new SalesOrderLine(2, new BigDecimal("10.00"), product));

        viewModel.selectOrder(order);
        assertEquals(1, viewModel.selectedOrderLines().size());
        assertEquals("Teclado", viewModel.selectedOrderLines().get(0).getProduct().getName());

        viewModel.selectOrder(null);
        assertEquals(0, viewModel.selectedOrderLines().size());
    }

    @Test
    @DisplayName("selectOrder con la base de datos caída vacía el detalle y avisa")
    void selectOrderReportsDatabaseError() {
        salesOrderRepository.failOn("findLines");
        SalesOrder order = new SalesOrder(5, java.time.LocalDateTime.now(),
                OrderStatus.PENDING, BigDecimal.ZERO, ACME, user(), List.of());

        viewModel.selectOrder(order);

        assertEquals(0, viewModel.selectedOrderLines().size());
        assertEquals(SalesOrdersViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Dos clics seguidos en Guardar envían un solo pedido")
    void doubleClickSavesOnlyOnce() throws Exception {
        Deque<Runnable> pending = new ArrayDeque<>();
        SalesOrdersViewModel deferred = new SalesOrdersViewModel(salesOrderRepository,
                customerRepository, productRepository, stockRepository, sessionUser(), null,
                new BackgroundTasks() {
                    @Override
                    public <T> void submit(Callable<T> task, Consumer<T> onSuccess,
                                           Consumer<Throwable> onFailure) {
                        pending.add(() -> {
                            try {
                                onSuccess.accept(task.call());
                            } catch (Exception failure) {
                                onFailure.accept(failure);
                            }
                        });
                    }
                });
        deferred.load();
        deferred.selectedCustomerProperty().set(customerRepository.findActive().get(0));
        deferred.selectedProductProperty().set(productRepository.findAll().get(0));
        deferred.quantityProperty().set("1");
        deferred.addLine();
        int before = salesOrderRepository.findAll().size();

        deferred.save();
        deferred.save();

        assertEquals(1, pending.size());
        pending.removeFirst().run();
        assertEquals(before + 1, salesOrderRepository.findAll().size());
    }

    // --- RF-08: status change -------------------------------------------

    private SalesOrder selectStoredOrder(OrderStatus status) {
        salesOrderRepository.withOrder(new SalesOrder(0, LocalDateTime.now(), status,
                new BigDecimal("20.00"), ACME, user(), List.of()));
        viewModel.load();
        SalesOrder selected = viewModel.orders().get(0);
        viewModel.selectedOrderProperty().set(selected);
        viewModel.selectOrder(selected);
        return selected;
    }

    @Test
    @DisplayName("RF-08: availableTransitions vacío sin selección y completo para un pedido pending")
    void availableTransitionsReflectTheSelectedOrder() {
        assertEquals(List.of(), viewModel.availableTransitions());

        selectStoredOrder(OrderStatus.PENDING);

        assertEquals(List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                viewModel.availableTransitions());
    }

    @Test
    @DisplayName("RF-08: availableTransitions vacío para un pedido terminal")
    void availableTransitionsAreEmptyForTerminalOrders() {
        selectStoredOrder(OrderStatus.DELIVERED);

        assertEquals(List.of(), viewModel.availableTransitions());
    }

    @Test
    @DisplayName("RF-08: load limpia availableTransitions junto con la selección")
    void loadClearsAvailableTransitions() {
        selectStoredOrder(OrderStatus.PENDING);
        assertEquals(2, viewModel.availableTransitions().size());

        viewModel.load();

        assertEquals(0, viewModel.availableTransitions().size());
    }

    @Test
    @DisplayName("RF-08: changeStatus sin pedido seleccionado avisa y no toca el repositorio")
    void changeStatusWithoutSelectionFails() {
        salesOrderRepository.failOn("changeStatus");

        viewModel.changeStatus(OrderStatus.SHIPPED);

        assertEquals(SalesOrdersViewModel.ORDER_REQUIRED, viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("RF-08: changeStatus con transición no permitida avisa y no toca el repositorio")
    void changeStatusWithInvalidTransitionFailsBeforeTheRepository() {
        selectStoredOrder(OrderStatus.DELIVERED);
        salesOrderRepository.failOn("changeStatus");

        viewModel.changeStatus(OrderStatus.CANCELLED);

        assertEquals(String.format(SalesOrdersViewModel.INVALID_TRANSITION, "Entregado", "Cancelado"),
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("RF-08: changeStatus válido persiste, recarga el histórico, re-selecciona y recalcula")
    void changeStatusPersistsAndReloads() {
        SalesOrder selected = selectStoredOrder(OrderStatus.PENDING);
        int id = selected.getId();

        viewModel.changeStatus(OrderStatus.SHIPPED);

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("Pedido #" + id));
        assertTrue(viewModel.statusMessageProperty().get().contains("Enviado"));
        assertEquals(1, viewModel.orders().size());
        assertEquals(OrderStatus.SHIPPED, viewModel.orders().get(0).getStatus());
        assertEquals(id, viewModel.selectedOrderProperty().get().getId());
        assertEquals(OrderStatus.SHIPPED, viewModel.selectedOrderProperty().get().getStatus());
        assertEquals(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                viewModel.availableTransitions());
    }

    @Test
    @DisplayName("RF-08: changeStatus con 0 filas avisa del conflicto y recarga el histórico")
    void changeStatusWithZeroRowsReportsConflict() throws SQLException {
        SalesOrder selected = selectStoredOrder(OrderStatus.PENDING);
        // A concurrent transaction moves the order after it was loaded.
        salesOrderRepository.changeStatus(selected.getId(), OrderStatus.PENDING,
                OrderStatus.SHIPPED);

        viewModel.changeStatus(OrderStatus.CANCELLED);

        assertEquals(SalesOrdersViewModel.CONFLICT, viewModel.errorMessageProperty().get());
        assertEquals(OrderStatus.SHIPPED, viewModel.orders().get(0).getStatus());
    }

    @Test
    @DisplayName("RF-09: addLine con stock insuficiente avisa y no añade la línea")
    void addLineWithInsufficientStockIsRejected() {
        FakeStockRepository lowStock = new FakeStockRepository()
                .withLot(1, lot(product(1, "Teclado", "10.00"), 1));
        SalesOrdersViewModel vm = new SalesOrdersViewModel(salesOrderRepository,
                customerRepository, productRepository, lowStock, sessionUser());
        vm.load();
        Product tecladoProduct = vm.products().stream()
                .filter(candidate -> candidate.getId() == 1).findFirst().orElseThrow();
        vm.selectedProductProperty().set(tecladoProduct);
        vm.quantityProperty().set("5");

        vm.addLine();

        assertEquals(0, vm.draftLines().size());
        assertEquals("Stock insuficiente de «Teclado»: se piden 5 y solo hay 1.",
                vm.stockErrorProperty().get());
        assertEquals(vm.stockErrorProperty().get(), vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("RF-09: addLine con stock suficiente añade la línea")
    void addLineWithEnoughStockIsAccepted() {
        addLine("Teclado", "100");

        assertEquals(1, viewModel.draftLines().size());
        assertEquals("", viewModel.stockErrorProperty().get());
    }

    @Test
    @DisplayName("RF-09: al seleccionar un producto se muestran sus unidades disponibles")
    void selectingProductShowsItsAvailableStock() {
        viewModel.selectedProductProperty().set(productById(viewModel, 1));
        assertEquals(100, viewModel.availableStockProperty().get());

        viewModel.selectedProductProperty().set(null);
        assertEquals(0, viewModel.availableStockProperty().get());
    }

    @Test
    @DisplayName("RF-09: un producto sin lotes disponibles muestra 0 unidades")
    void productWithoutAvailableLotsShowsZero() {
        FakeStockRepository onlyTeclado = new FakeStockRepository()
                .withLot(1, lot(product(1, "Teclado", "10.00"), 4));
        SalesOrdersViewModel vm = viewModel(onlyTeclado);

        vm.selectedProductProperty().set(productById(vm, 2));

        assertEquals(0, vm.availableStockProperty().get());
    }

    @Test
    @DisplayName("RF-09: al bajar de producto la cantidad escrita se recorta al stock")
    void changingProductClampsTypedQuantity() {
        FakeStockRepository lowStock = new FakeStockRepository()
                .withLot(1, lot(product(1, "Teclado", "10.00"), 3))
                .withLot(2, lot(product(2, "Ratón", "5.50"), 0));
        SalesOrdersViewModel vm = viewModel(lowStock);
        vm.quantityProperty().set("10");

        vm.selectedProductProperty().set(productById(vm, 1));
        assertEquals("3", vm.quantityProperty().get());

        vm.selectedProductProperty().set(productById(vm, 2));
        assertEquals("", vm.quantityProperty().get());
    }

    @Test
    @DisplayName("RF-09: si falla la consulta de stock al seleccionar, avisa y deja 0")
    void selectingProductWithDatabaseFailureReportsError() {
        SalesOrdersViewModel vm = viewModel(new FakeStockRepository().failOn("findAvailable"));

        vm.selectedProductProperty().set(productById(vm, 1));

        assertEquals(0, vm.availableStockProperty().get());
        assertEquals(SalesOrdersViewModel.DATABASE_ERROR, vm.errorMessageProperty().get());
    }

    private SalesOrdersViewModel viewModel(FakeStockRepository stock) {
        SalesOrdersViewModel vm = new SalesOrdersViewModel(salesOrderRepository,
                customerRepository, productRepository, stock, sessionUser());
        vm.load();
        return vm;
    }

    private static Product productById(SalesOrdersViewModel vm, int id) {
        return vm.products().stream()
                .filter(candidate -> candidate.getId() == id)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("RF-09: save con insuficiencia muestra el mensaje y conserva el borrador")
    void saveWithInsufficientStockKeepsTheDraft() {
        viewModel.selectedCustomerProperty().set(ACME);
        addLine("Teclado", "2");
        salesOrderRepository.failOnInsufficientStock("Teclado", 2, 0);

        viewModel.save();

        assertEquals("Stock insuficiente de «Teclado»: se piden 2 y solo hay 0.",
                viewModel.errorMessageProperty().get());
        assertEquals(1, viewModel.draftLines().size());
        assertEquals(0, viewModel.orders().size());
    }

    @Test
    @DisplayName("El pedido acepta la cantidad máxima de INT firmado")
    void addLineAcceptsMaximumSignedIntQuantity() {
        FakeStockRepository stock = new FakeStockRepository().withLot(
                1, lot(product(1, "Teclado", "0.00"), Integer.MAX_VALUE));
        SalesOrdersViewModel maxQuantityViewModel = viewModel(stock);
        Product zeroPrice = maxQuantityViewModel.products().stream()
                .filter(product -> product.getId() == 1)
                .findFirst().orElseThrow();
        zeroPrice.setPrice(BigDecimal.ZERO);
        maxQuantityViewModel.selectedProductProperty().set(zeroPrice);
        maxQuantityViewModel.quantityProperty().set(String.valueOf(Integer.MAX_VALUE));

        maxQuantityViewModel.addLine();

        assertEquals(Integer.MAX_VALUE,
                maxQuantityViewModel.draftLines().get(0).getQuantity());
    }

    @Test
    @DisplayName("La cantidad mayor que INT firmado se rechaza sin desbordar")
    void addLineRejectsQuantityOverflow() {
        addLine("Teclado", "2147483648");

        assertEquals(SalesOrdersViewModel.QUANTITY_INVALID, viewModel.quantityErrorProperty().get());
        assertEquals(0, viewModel.draftLines().size());
    }

    @Test
    @DisplayName("El pedido rechaza un total superior al máximo DECIMAL persistible")
    void addLineRejectsUnpersistableTotal() {
        Product expensive = viewModel.products().get(0);
        expensive.setPrice(new BigDecimal("50000000.00"));
        viewModel.selectedProductProperty().set(expensive);
        viewModel.quantityProperty().set("2");

        viewModel.addLine();

        assertEquals("El total del pedido no puede superar 99.999.999,99 €.",
                viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.draftLines().size());
    }

    @Test
    @DisplayName("RF-15: selectOrder carga los lotes consumidos y null los limpia")
    void selectOrderLoadsAndClearsIssues() {
        Product product = product(1, "Teclado", "10.00");
        SalesOrderLine line = new SalesOrderLine(4, 2, new BigDecimal("10.00"), product);
        SalesOrder order = new SalesOrder(5, LocalDateTime.now(), OrderStatus.PENDING,
                new BigDecimal("20.00"), ACME, user(), List.of());
        salesOrderRepository.withOrder(order).withLine(5, line)
                .withIssue(5, new StockIssue(2, line, lot(product, 10)));

        viewModel.selectOrder(order);
        assertEquals(1, viewModel.selectedOrderIssues().size());
        assertEquals(2, viewModel.selectedOrderIssues().get(0).getQuantity());

        viewModel.selectOrder(null);
        assertEquals(0, viewModel.selectedOrderIssues().size());
    }

    @Test
    @DisplayName("RF-09/D2: cancelar un pedido limpia sus lotes consumidos")
    void cancellingRemovesTheIssues() throws SQLException {
        SalesOrder selected = selectStoredOrder(OrderStatus.PENDING);
        Product product = product(1, "Teclado", "10.00");
        SalesOrderLine line = new SalesOrderLine(4, 2, new BigDecimal("10.00"), product);
        salesOrderRepository.withIssue(selected.getId(), new StockIssue(2, line, lot(product, 10)));
        viewModel.selectOrder(selected);
        assertEquals(1, viewModel.selectedOrderIssues().size());

        viewModel.changeStatus(OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, viewModel.orders().get(0).getStatus());
        assertEquals(0, viewModel.selectedOrderIssues().size());
    }

    @Test
    @DisplayName("Una recarga fallida tras confirmar el pedido no conserva el borrador")
    void postCommitReloadFailureCannotResubmitOrder() throws SQLException {
        viewModel.selectedCustomerProperty().set(ACME);
        addLine("Teclado", "1");
        salesOrderRepository.failNext("findAll");

        viewModel.save();

        assertTrue(viewModel.errorMessageProperty().get().contains("Pedido #1 guardado"));
        assertEquals(0, viewModel.draftLines().size());
        assertEquals(1, salesOrderRepository.findAll().size());

        viewModel.save();

        assertEquals(SalesOrdersViewModel.LINES_REQUIRED, viewModel.linesErrorProperty().get());
        assertEquals(1, salesOrderRepository.findAll().size());
    }

    // --- History filters (queried in the database) ----------------------

    private SalesOrder storeOrder(LocalDate date, Customer customer, OrderStatus status) {
        SalesOrder order = new SalesOrder(0, date.atTime(10, 0), status,
                new BigDecimal("20.00"), customer, user(), List.of());
        salesOrderRepository.withOrder(order);
        return order;
    }

    /** Three orders in two months, from three customers, in three statuses. */
    private void loadThreeOrders() {
        storeOrder(LocalDate.of(2024, 1, 15), ACME, OrderStatus.PENDING);
        storeOrder(LocalDate.of(2024, 2, 15), GLOBEX, OrderStatus.SHIPPED);
        storeOrder(LocalDate.of(2024, 2, 29), INACTIVE, OrderStatus.DELIVERED);
        viewModel.load();
    }

    @Test
    @DisplayName("El filtro de fechas viaja a la consulta y respeta los dos extremos por día")
    void dateRangeFilterIsQueriedInclusive() {
        loadThreeOrders();

        viewModel.fromDateProperty().set(LocalDate.of(2024, 2, 15));
        viewModel.toDateProperty().set(LocalDate.of(2024, 2, 29));

        assertEquals(2, viewModel.orders().size());
        assertEquals(SalesOrderFilter.of(LocalDate.of(2024, 2, 15), LocalDate.of(2024, 2, 29),
                null, null), salesOrderRepository.lastFilter());
    }

    @Test
    @DisplayName("Un rango invertido avisa, vacía la tabla y no consulta la base de datos")
    void invertedRangeIsRejectedWithoutQuerying() {
        loadThreeOrders();
        viewModel.fromDateProperty().set(LocalDate.of(2024, 3, 1));
        int calls = salesOrderRepository.findAllCalls();

        viewModel.toDateProperty().set(LocalDate.of(2024, 2, 1));

        assertEquals(SalesOrdersViewModel.PERIOD_ERROR, viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.orders().size());
        assertEquals(calls, salesOrderRepository.findAllCalls());
    }

    @Test
    @DisplayName("Corregir el rango limpia el error y vuelve a consultar")
    void correctedRangeClearsTheError() {
        loadThreeOrders();
        viewModel.fromDateProperty().set(LocalDate.of(2024, 1, 1));
        viewModel.toDateProperty().set(LocalDate.of(2023, 12, 31));
        assertEquals(SalesOrdersViewModel.PERIOD_ERROR, viewModel.errorMessageProperty().get());

        viewModel.toDateProperty().set(LocalDate.of(2024, 12, 31));

        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(3, viewModel.orders().size());
    }

    @Test
    @DisplayName("El filtro de cliente consulta por id, incluso uno dado de baja")
    void customerFilterIsQueriedById() {
        loadThreeOrders();
        assertTrue(viewModel.customerFilterOptions().stream()
                .anyMatch(customer -> customer.getName().equals("Old")));

        viewModel.customerFilterProperty().set(INACTIVE);

        assertEquals(1, viewModel.orders().size());
        assertEquals(INACTIVE.getId(), salesOrderRepository.lastFilter().customerId());
    }

    @Test
    @DisplayName("El filtro de estado consulta por el valor del dominio")
    void statusFilterIsQueried() {
        loadThreeOrders();

        viewModel.statusFilterProperty().set(OrderStatus.SHIPPED);

        assertEquals(1, viewModel.orders().size());
        assertEquals(OrderStatus.SHIPPED, salesOrderRepository.lastFilter().status());
    }

    @Test
    @DisplayName("Los tres criterios se combinan en una única consulta")
    void filtersCombineInOneQuery() {
        loadThreeOrders();
        int calls = salesOrderRepository.findAllCalls();

        viewModel.fromDateProperty().set(LocalDate.of(2024, 2, 1));
        viewModel.customerFilterProperty().set(GLOBEX);
        viewModel.statusFilterProperty().set(OrderStatus.SHIPPED);

        assertEquals(1, viewModel.orders().size());
        assertEquals(calls + 3, salesOrderRepository.findAllCalls());
        assertEquals(SalesOrderFilter.of(LocalDate.of(2024, 2, 1), null, GLOBEX.getId(),
                OrderStatus.SHIPPED), salesOrderRepository.lastFilter());
    }

    @Test
    @DisplayName("clearFilters borra los criterios y hace una sola consulta")
    void clearFiltersDropsEveryCriterionWithOneQuery() {
        loadThreeOrders();
        viewModel.customerFilterProperty().set(GLOBEX);
        int calls = salesOrderRepository.findAllCalls();

        viewModel.clearFilters();

        assertNull(viewModel.fromDateProperty().get());
        assertNull(viewModel.toDateProperty().get());
        assertNull(viewModel.customerFilterProperty().get());
        assertNull(viewModel.statusFilterProperty().get());
        assertEquals(3, viewModel.orders().size());
        assertEquals(SalesOrderFilter.all(), salesOrderRepository.lastFilter());
        assertEquals(calls + 1, salesOrderRepository.findAllCalls());
    }

    @Test
    @DisplayName("Filtrar deja sin selección un pedido que ya no se muestra")
    void filteringDropsTheSelectionOfAHiddenOrder() {
        loadThreeOrders();
        SalesOrder pending = viewModel.orders().stream()
                .filter(order -> OrderStatus.PENDING.equals(order.getStatus()))
                .findFirst().orElseThrow();
        viewModel.selectedOrderProperty().set(pending);
        viewModel.selectOrder(pending);
        assertEquals(2, viewModel.availableTransitions().size());

        viewModel.statusFilterProperty().set(OrderStatus.SHIPPED);

        assertNull(viewModel.selectedOrderProperty().get());
        assertEquals(0, viewModel.availableTransitions().size());
        assertEquals(0, viewModel.selectedOrderLines().size());
    }

    @Test
    @DisplayName("La consulta de filtros fallida vacía la tabla y muestra el error de conexión")
    void filterQueryFailureReportsDatabaseError() {
        loadThreeOrders();
        salesOrderRepository.failOn("findAll");

        viewModel.statusFilterProperty().set(OrderStatus.SHIPPED);

        assertEquals(SalesOrdersViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.orders().size());
    }

    @Test
    @DisplayName("Guardar mantiene el filtro activo en la recarga del histórico")
    void saveReloadsWithTheActiveFilter() {
        viewModel.statusFilterProperty().set(OrderStatus.PENDING);
        viewModel.selectedCustomerProperty().set(ACME);
        addLine("Teclado", "1");

        viewModel.save();

        assertEquals(1, viewModel.orders().size());
        assertEquals(OrderStatus.PENDING, salesOrderRepository.lastFilter().status());
    }

    @Test
    @DisplayName("Cambiar el estado recarga respetando el filtro y confirma aunque oculte la fila")
    void changeStatusKeepsTheActiveFilter() {
        loadThreeOrders();
        viewModel.statusFilterProperty().set(OrderStatus.PENDING);
        SalesOrder pending = viewModel.orders().get(0);
        viewModel.selectedOrderProperty().set(pending);
        viewModel.selectOrder(pending);

        viewModel.changeStatus(OrderStatus.SHIPPED);

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("marcado como Enviado"));
        assertEquals(0, viewModel.orders().size());
        assertEquals(OrderStatus.PENDING, salesOrderRepository.lastFilter().status());
    }
}
