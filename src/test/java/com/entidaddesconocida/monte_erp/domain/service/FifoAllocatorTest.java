package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests of the pure FIFO allocation (RF-09 / RF-15, decision D5). They are
 * the key business invariant: order, no partial assignment, no mutation.
 */
class FifoAllocatorTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2024, 1, 1, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2024, 2, 1, 10, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2024, 3, 1, 10, 0);

    private static Product product() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0,
                null, true);
        product.setId(1);
        return product;
    }

    private static Stock lot(int id, int initial, int available, LocalDateTime entryDate) {
        return new Stock(id, initial, available, new BigDecimal("1.00"), entryDate, product(), null);
    }

    private static SalesOrderLine line(int quantity) {
        return new SalesOrderLine(quantity, new BigDecimal("10.00"), product());
    }

    @Test
    @DisplayName("Reparte en orden FIFO aunque la lista llegue desordenada")
    void allocatesOldestFirstRegardlessOfInputOrder() throws InsufficientStockException {
        Stock newest = lot(3, 10, 10, T3);
        Stock oldest = lot(1, 10, 10, T1);
        Stock middle = lot(2, 10, 10, T2);

        List<StockIssue> issues = FifoAllocator.allocate(line(15),
                List.of(newest, middle, oldest));

        assertEquals(2, issues.size());
        assertSame(oldest, issues.get(0).getStock());
        assertEquals(10, issues.get(0).getQuantity());
        assertSame(middle, issues.get(1).getStock());
        assertEquals(5, issues.get(1).getQuantity());
    }

    @Test
    @DisplayName("Desempata por id cuando la fecha de entrada coincide")
    void breaksTiesById() throws InsufficientStockException {
        Stock second = lot(2, 10, 10, T1);
        Stock first = lot(1, 10, 10, T1);

        List<StockIssue> issues = FifoAllocator.allocate(line(15), List.of(second, first));

        assertSame(first, issues.get(0).getStock());
        assertSame(second, issues.get(1).getStock());
    }

    @Test
    @DisplayName("La suma de lo asignado es exactamente la cantidad de la línea")
    void assignedTotalMatchesTheLine() throws InsufficientStockException {
        List<StockIssue> issues = FifoAllocator.allocate(line(7),
                List.of(lot(1, 3, 3, T1), lot(2, 5, 5, T2)));

        int total = issues.stream().mapToInt(StockIssue::getQuantity).sum();
        assertEquals(7, total);
    }

    @Test
    @DisplayName("Un lote con disponible 0 se ignora y disponible == pedido cubre justo")
    void ignoresEmptyLotsAndCoversExactly() throws InsufficientStockException {
        Stock empty = lot(1, 5, 0, T1);
        Stock exact = lot(2, 5, 5, T2);

        List<StockIssue> issues = FifoAllocator.allocate(line(5), List.of(empty, exact));

        assertEquals(1, issues.size());
        assertSame(exact, issues.get(0).getStock());
        assertEquals(5, issues.get(0).getQuantity());
    }

    @Test
    @DisplayName("Insuficiencia: excepción y ninguna asignación parcial")
    void insufficientStockThrowsWithoutPartialAssignments() {
        SalesOrderLine line = line(10);
        List<Stock> lots = List.of(lot(1, 2, 2, T1), lot(2, 3, 3, T2));

        InsufficientStockException exception = assertThrows(InsufficientStockException.class,
                () -> FifoAllocator.allocate(line, lots));

        assertEquals("Teclado", exception.getProductName());
        assertEquals(10, exception.getRequested());
        assertEquals(5, exception.getAvailable());
        assertEquals("Stock insuficiente de «Teclado»: se piden 10 y solo hay 5.",
                exception.getMessage());
    }

    @Test
    @DisplayName("No muta los lotes de entrada (efecto puro, reutilizable)")
    void doesNotMutateTheLots() throws InsufficientStockException {
        Stock lot = lot(1, 5, 5, T1);

        FifoAllocator.allocate(line(3), List.of(lot));

        assertEquals(5, lot.getAvailableQuantity());
    }

    @Test
    @DisplayName("availableTotal solo suma los disponibles positivos y tolera null")
    void availableTotalSumsPositiveLots() {
        assertEquals(8, FifoAllocator.availableTotal(List.of(lot(1, 5, 3, T1), lot(2, 5, 5, T2))));
        assertEquals(0, FifoAllocator.availableTotal(null));
        assertEquals(0, FifoAllocator.availableTotal(List.of()));
    }
}
