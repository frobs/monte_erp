package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
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
     * Stock levels matching the screen filters, ordered by name. The filtering
     * happens <strong>in the query</strong>: the text is compared with the
     * product name/SKU and {@code onlyBelowMinimum} keeps only the products whose
     * aggregated available quantity is below their minimum (RF-10).
     */
    List<StockLevel> findLevels(String search, boolean onlyBelowMinimum) throws SQLException;

    /**
     * Number of active products below their minimum stock, regardless of the
     * screen filters; feeds the footer alert counter (RF-10).
     */
    int countBelowMinimum() throws SQLException;

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

    /**
     * Every lot inside the given period, with its product and origin purchase,
     * ordered by product name and then FIFO ({@code entry_date, id}): the
     * projection of the stock report (RF-12 / CU-09, decision D8). The period
     * filters {@code STOCK.entry_date} with inclusive bounds and an empty period
     * means no filter. A lot at 0 is still listed: the report totals decide
     * whether it adds value.
     */
    List<Stock> findAllLots(ReportPeriod period) throws SQLException;
}
