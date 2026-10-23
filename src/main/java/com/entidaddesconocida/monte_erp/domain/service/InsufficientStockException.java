package com.entidaddesconocida.monte_erp.domain.service;

/**
 * Raised when a sales line cannot be served from the available lots (RF-09 /
 * CU-11): the whole allocation is rejected, no partial consumption is kept.
 *
 * <p>It is a <em>checked</em> exception (like {@link SetupException}) so callers
 * cannot forget it: the repository propagates it after rolling the transaction
 * back and the ViewModel translates it into a user message.</p>
 */
public class InsufficientStockException extends Exception {

    private final String productName;
    private final int requested;
    private final int available;

    public InsufficientStockException(String productName, int requested, int available) {
        super(formatMessage(productName, requested, available));
        this.productName = productName;
        this.requested = requested;
        this.available = available;
    }

    /**
     * User-facing message of an insufficient stock situation, shared by the
     * exception and the pre-check of the sale screen so both show the same text.
     */
    public static String formatMessage(String productName, int requested, int available) {
        return "Stock insuficiente de «" + productName + "»: se piden " + requested
                + " y solo hay " + available + ".";
    }

    /** Name of the product that could not be served. */
    public String getProductName() {
        return productName;
    }

    /** Units the line asked for. */
    public int getRequested() {
        return requested;
    }

    /** Units actually available in the product lots. */
    public int getAvailable() {
        return available;
    }
}
