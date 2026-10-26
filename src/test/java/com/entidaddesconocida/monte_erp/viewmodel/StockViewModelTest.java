package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeStockRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link StockViewModel} (RF-09 / RF-10 / CU-08).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with the
 * in-memory {@link FakeStockRepository}.</p>
 */
class StockViewModelTest {

    private FakeStockRepository stockRepository;
    private StockViewModel viewModel;

    private StockLevel tecladoBelow;
    private StockLevel ratonOk;

    @BeforeEach
    void setUp() {
        tecladoBelow = new StockLevel(product(1, "Teclado", "SKU-1", 10), 4);
        ratonOk = new StockLevel(product(2, "Ratón", "SKU-2", 0), 7);
        stockRepository = new FakeStockRepository()
                .withLevel(tecladoBelow)
                .withLevel(ratonOk)
                .withLot(1, lot(11, 10, 4));
        viewModel = new StockViewModel(stockRepository);
        viewModel.load();
    }

    private static Product product(int id, String name, String sku, int minStock) {
        Product product = new Product(name, sku, null, new BigDecimal("10.00"), minStock,
                null, true);
        product.setId(id);
        return product;
    }

    private static Stock lot(int id, int initial, int available) {
        return new Stock(id, initial, available, new BigDecimal("1.00"), LocalDateTime.now(),
                product(1, "Teclado", "SKU-1", 10), null);
    }

    @Test
    @DisplayName("load carga los niveles ordenados por nombre y cuenta los que están bajo mínimo")
    void loadFillsLevelsAndCountsAlerts() {
        assertEquals(2, viewModel.levels().size());
        assertEquals("Ratón", viewModel.levels().get(0).getProduct().getName());
        assertEquals("Teclado", viewModel.levels().get(1).getProduct().getName());
        assertEquals("Productos bajo mínimo: 1", viewModel.alertCountProperty().get());
    }

    @Test
    @DisplayName("El filtro 'solo bajo mínimo' acota la tabla a los productos en alerta")
    void onlyBelowMinimumFiltersTheTable() {
        viewModel.onlyBelowMinimumProperty().set(true);

        assertEquals(1, viewModel.filteredLevels().size());
        assertEquals("Teclado", viewModel.filteredLevels().get(0).getProduct().getName());
    }

    @Test
    @DisplayName("La búsqueda por nombre o SKU acota la tabla")
    void searchFiltersByNameOrSku() {
        viewModel.searchTextProperty().set("rat");
        assertEquals(1, viewModel.filteredLevels().size());
        assertEquals("Ratón", viewModel.filteredLevels().get(0).getProduct().getName());

        viewModel.searchTextProperty().set("SKU-1");
        assertEquals(1, viewModel.filteredLevels().size());
        assertEquals("Teclado", viewModel.filteredLevels().get(0).getProduct().getName());

        viewModel.searchTextProperty().set("");
        assertEquals(2, viewModel.filteredLevels().size());
    }

    @Test
    @DisplayName("El buscador de stock es insensible a acentos: 'raton' encuentra 'Ratón'")
    void searchIsAccentInsensitive() {
        viewModel.searchTextProperty().set("raton");

        assertEquals(1, viewModel.filteredLevels().size());
        assertEquals("Ratón", viewModel.filteredLevels().get(0).getProduct().getName());
    }

    @Test
    @DisplayName("selectLevel carga los lotes del producto y null los vacía")
    void selectLevelLoadsAndClearsLots() {
        viewModel.selectLevel(tecladoBelow);
        assertEquals(1, viewModel.selectedLots().size());
        assertEquals(11, viewModel.selectedLots().get(0).getId());

        viewModel.selectLevel(null);
        assertEquals(0, viewModel.selectedLots().size());
    }

    @Test
    @DisplayName("load con la base de datos caída muestra el error de conexión")
    void loadReportsDatabaseError() {
        StockViewModel failing = new StockViewModel(new FakeStockRepository().throwOnAccess());

        failing.load();

        assertEquals(StockViewModel.DATABASE_ERROR, failing.errorMessageProperty().get());
        assertEquals(0, failing.levels().size());
    }

    @Test
    @DisplayName("selectLevel con la base de datos caída vacía el detalle y avisa")
    void selectLevelReportsDatabaseError() {
        stockRepository.failOn("findLots");

        viewModel.selectLevel(tecladoBelow);

        assertEquals(0, viewModel.selectedLots().size());
        assertEquals(StockViewModel.DATABASE_ERROR, viewModel.errorMessageProperty().get());
    }
}
