package com.entidaddesconocida.monte_erp.domain.service;

/**
 * Raised when a received purchase cannot be returned because part of its stock
 * has already been sold (RF-14 extension).
 *
 * <p>A return gives the whole purchase back to the supplier, so it is only
 * possible while every lot still has its full initial quantity. Returning a
 * partially sold purchase would leave sold units pointing at stock the supplier
 * already took back, so the operation is rejected instead of silently
 * corrupted. It is a <em>checked</em> exception (like
 * {@link InsufficientStockException}) so callers cannot forget it: the
 * repository propagates it after rolling the transaction back and the ViewModel
 * translates it into a user message.</p>
 */
public class PurchaseStockConsumedException extends Exception {

    private final int purchaseId;
    private final int consumedUnits;

    public PurchaseStockConsumedException(int purchaseId, int consumedUnits) {
        super(formatMessage(purchaseId, consumedUnits));
        this.purchaseId = purchaseId;
        this.consumedUnits = consumedUnits;
    }

    /**
     * User-facing message of a rejected return, shared by the exception and the
     * screen so both show the same text.
     */
    public static String formatMessage(int purchaseId, int consumedUnits) {
        return "No se puede devolver la compra #" + purchaseId + ": " + consumedUnits
                + (consumedUnits == 1 ? " unidad ya se ha vendido." : " unidades ya se han vendido.");
    }

    /** Purchase that could not be returned. */
    public int getPurchaseId() {
        return purchaseId;
    }

    /** Units already sold from the purchase lots. */
    public int getConsumedUnits() {
        return consumedUnits;
    }
}
