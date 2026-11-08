package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSalesOrderRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeStockRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReportsViewModel} (RF-12 / CU-09).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with the
 * in-memory fakes, which mimic the period filter and the ordering of the
 * adapters.</p>
 */
class ReportsViewModelTest {

    private static final LocalDateTime JANUARY = LocalDateTime.of(2025, 1, 15, 10, 0);

    private FakeStockRepository stockRepository;
    private FakeSalesOrderRepository salesOrderRepository;
    private ReportsViewModel viewModel;

    @BeforeEach
    void setUp() {
        stockRepository = new FakeStockRepository().withLot(1, lot());
        salesOrderRepository = new FakeSalesOrderRepository()
                .withOrder(order(1, SalesOrder.STATUS_PENDING, 3, "10.00"))
                .withOrder(order(2, SalesOrder.STATUS_CANCELLED, 50, "10.00"));
        viewModel = new ReportsViewModel(salesOrderRepository, stockRepository);
    }

    private static Product product() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        return product;
    }

    private static Stock lot() {
        Purchase purchase = new Purchase(5, null, null, null, null, null, null);
        return new Stock(11, 10, 7, new BigDecimal("1.50"), JANUARY, product(), purchase);
    }

    private static Stock lot(int id, int initial, int available, String purchaseStatus) {
        Purchase purchase = new Purchase(5, null, purchaseStatus, null, null, null, null);
        return new Stock(id, initial, available, new BigDecimal("1.50"), JANUARY, product(), purchase);
    }

    private static SalesOrder order(int id, String status, int quantity, String price) {
        Product product = product();
        SalesOrderLine line = new SalesOrderLine(quantity, new BigDecimal(price), product);
        Customer customer = new Customer(1, "Acme", null, null, null);
        BigDecimal total = new BigDecimal(price).multiply(BigDecimal.valueOf(quantity));
        return new SalesOrder(id, JANUARY, status, total, customer, null, List.of(line));
    }

    @Test
    @DisplayName("load publica las dos listas y los totales cuadrados")
    void loadPublishesListsAndTotals() {
        viewModel.load();

        assertEquals(1, viewModel.stockLots().size());
        assertEquals(1, viewModel.salesRows().size());
        assertEquals("Lotes: 1 · Excluidos: 0 · Unidades disponibles: 7 · Valor del stock: 10,50 €",
                viewModel.stockTotalsProperty().get());
        assertEquals("Pedidos: 1 · Pedidos cancelados: 1 · Unidades vendidas: 3 · Facturación: 30,00 €",
                viewModel.salesTotalsProperty().get());
    }

    @Test
    @DisplayName("Un pedido cancelado no aparece en el informe de ventas (D3)")
    void cancelledOrderIsExcluded() {
        viewModel.load();

        assertEquals(1, viewModel.salesRows().size());
        assertEquals(3, viewModel.salesRows().get(0).getUnitsSold());
        assertEquals(0, new BigDecimal("30.00")
                .compareTo(viewModel.salesRows().get(0).getRevenue()));
    }

    @Test
    @DisplayName("El período filtra ambos informes por día, de forma inclusiva")
    void periodFiltersBothReports() {
        viewModel.fromDateProperty().set(LocalDate.of(2025, 1, 15));
        viewModel.toDateProperty().set(LocalDate.of(2025, 1, 15));
        viewModel.load();

        assertEquals(1, viewModel.stockLots().size());
        assertEquals(1, viewModel.salesRows().size());

        viewModel.fromDateProperty().set(LocalDate.of(2025, 2, 1));
        viewModel.toDateProperty().set(null);
        viewModel.load();

        assertEquals(0, viewModel.stockLots().size());
        assertEquals(0, viewModel.salesRows().size());
        assertEquals("Lotes: 0 · Excluidos: 0 · Unidades disponibles: 0 · Valor del stock: 0,00 €",
                viewModel.stockTotalsProperty().get());
    }

    @Test
    @DisplayName("Un período inválido avisa y no consulta la base de datos")
    void invertedPeriodDoesNotQuery() {
        // throwOnAccess turns any repository call into a DATABASE_ERROR, so seeing
        // PERIOD_ERROR proves the query was never issued.
        ReportsViewModel failing = new ReportsViewModel(
                new FakeSalesOrderRepository().throwOnAccess(),
                new FakeStockRepository().throwOnAccess());
        failing.fromDateProperty().set(LocalDate.of(2025, 3, 1));
        failing.toDateProperty().set(LocalDate.of(2025, 1, 1));

        failing.load();

        assertEquals(ReportsViewModel.PERIOD_ERROR, failing.errorMessageProperty().get());
        assertEquals(0, failing.stockLots().size());
        assertEquals(0, failing.salesRows().size());
    }

    @Test
    @DisplayName("Un rango inválido limpia y no permite exportar el informe anterior")
    void invalidPeriodClearsPreviouslyLoadedReport() {
        viewModel.load();
        assertEquals(1, viewModel.salesRows().size());
        assertTrue(viewModel.salesReportCsv().contains("Teclado"));
        viewModel.fromDateProperty().set(LocalDate.of(2025, 3, 1));
        viewModel.toDateProperty().set(LocalDate.of(2025, 1, 1));

        viewModel.load();

        assertEquals(ReportsViewModel.PERIOD_ERROR, viewModel.errorMessageProperty().get());
        assertEquals(0, viewModel.stockLots().size());
        assertEquals(0, viewModel.salesRows().size());
        assertFalse(viewModel.salesReportCsv().contains("Teclado"));
        assertTrue(viewModel.stockTotalsProperty().get().startsWith("Lotes: 0"));
    }

    @Test
    @DisplayName("Una caída de la base de datos muestra el error de conexión y vacía las tablas")
    void databaseFailureShowsConnectionError() {
        ReportsViewModel failing = new ReportsViewModel(
                new FakeSalesOrderRepository(), new FakeStockRepository().throwOnAccess());

        failing.load();

        assertEquals(ReportsViewModel.DATABASE_ERROR, failing.errorMessageProperty().get());
        assertEquals(0, failing.stockLots().size());
        assertEquals(0, failing.salesRows().size());
    }

    @Test
    @DisplayName("clearPeriod deja las fechas vacías y recarga todo el histórico")
    void clearPeriodResetsDatesAndReloads() {
        viewModel.fromDateProperty().set(LocalDate.of(2025, 2, 1));
        viewModel.load();
        assertEquals(0, viewModel.stockLots().size());

        viewModel.clearPeriod();

        assertEquals(null, viewModel.fromDateProperty().get());
        assertEquals(null, viewModel.toDateProperty().get());
        assertEquals(1, viewModel.stockLots().size());
    }

    @Test
    @DisplayName("Una sesión desactivada no puede seguir exportando el informe cargado")
    void deactivatedSessionCannotExportPreviouslyLoadedData() throws Exception {
        User current = new User("Ana", "ana", "hash", User.ROLE_EMPLOYEE, true);
        FakeUserRepository users = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        ReportsViewModel guarded = new ReportsViewModel(salesOrderRepository, stockRepository,
                new SessionValidator(session, users));
        guarded.load();
        assertTrue(guarded.salesReportCsv().contains("Teclado"));
        users.deactivate(current.getId());

        String csv = guarded.salesReportCsv();

        assertFalse(csv.contains("Teclado"));
        assertEquals(0, guarded.salesRows().size());
        assertEquals(null, session.currentUser());
    }

    @Test
    @DisplayName("Los CSV contienen las filas cargadas y sus totales")
    void csvContainsLoadedRows() {
        viewModel.load();

        String stockCsv = viewModel.stockReportCsv();
        assertTrue(stockCsv.contains("Teclado;SKU-1"));
        assertTrue(stockCsv.contains("Valor del stock;10,50"));

        String salesCsv = viewModel.salesReportCsv();
        assertTrue(salesCsv.contains("Teclado;SKU-1;3;30,00"));
        assertTrue(salesCsv.contains("Facturación;30,00"));
    }

    @Test
    @DisplayName("Un lote de compra devuelta se muestra marcado pero no suma en el total")
    void returnedLotIsShownButNotTotaled() {
        stockRepository = new FakeStockRepository()
                .withLot(1, lot(1, 10, 4, Purchase.STATUS_RECEIVED))
                .withLot(1, lot(2, 5, 0, Purchase.STATUS_RETURNED))
                .withLot(1, lot(3, 2, 0, Purchase.STATUS_CANCELLED));
        viewModel = new ReportsViewModel(salesOrderRepository, stockRepository);

        viewModel.load();

        assertEquals(3, viewModel.stockLots().size());
        assertEquals("Lotes: 1 · Excluidos: 2 · Unidades disponibles: 4 · Valor del stock: 6,00 €",
                viewModel.stockTotalsProperty().get());
        String csv = viewModel.stockReportCsv();
        assertTrue(csv.contains(";Devuelta;Devuelta al proveedor"));
        assertTrue(csv.contains(";Cancelada;Compra cancelada"));
    }

    @Test
    @DisplayName("Un producto solo con pedidos cancelados aparece marcado y no suma en ventas")
    void onlyCancelledProductIsShownButNotTotaled() {
        salesOrderRepository = new FakeSalesOrderRepository()
                .withOrder(order(1, SalesOrder.STATUS_PENDING, 3, "10.00"))
                .withOrder(order(2, SalesOrder.STATUS_CANCELLED, 50, "10.00"));
        viewModel = new ReportsViewModel(salesOrderRepository, stockRepository);

        viewModel.load();

        assertEquals(1, viewModel.salesRows().size());
        assertEquals("Lotes: 1 · Excluidos: 0 · Unidades disponibles: 7 · Valor del stock: 10,50 €",
                viewModel.stockTotalsProperty().get());
        assertTrue(viewModel.salesReportCsv().contains("Teclado;SKU-1;3;30,00;50;"));
    }
}
