package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;

import java.sql.SQLException;
import java.util.List;

/**
 * Read-only port over the stock (RF-09 / RF-10 / CU-08).
 *
 * <p>The stock screen only <strong>reads</strong>: the writing of stock (the
 * FIFO consumption of a sale and the reversal of a cancellation) lives in
 * {@link SalesOrderRepository} because it must be atomic with the order
 * (decision D6, a conscious deviation from the {@code updateQuantity(Stock)} of
 * the class diagram). Keeping this port read-only makes it impossible for the UI
 * to desynchronise the stock outside a sale.</p>
 */
public interface StockRepository {

    /**
     * Stock level of every active product, ordered by name: the product plus the
     * sum of its available lots (a product with no lots shows 0).
     */
    List<StockLevel> findLevels() throws SQLException;

    /**
     * Every lot of a product, oldest first, with its origin purchase; used by the
     * detail of the stock screen (a lot at 0 is still visible as history).
     */
    List<Stock> findLots(int productId) throws SQLException;

    /**
     * Available lots of a product ({@code available_quantity > 0}) in FIFO order
     * (entry date, id): the input of the {@code FifoAllocator} pre-check in the
     * sale screen.
     */
    List<Stock> findAvailable(int productId) throws SQLException;
}
