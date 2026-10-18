package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Supplier purchase (the {@code PURCHASE} table of the E-R diagram), introduced
 * by RF-14 / CU-10.
 *
 * <p>The purchase is created <strong>{@code received}</strong> (decision D1): the
 * CU-10 says that each line leaves a lot available in the stock, so US-13 does not
 * implement a purchase state machine. The {@code user_id} is the signed-in
 * employee and the total is derived from the lots
 * ({@link #calculateTotal()}, decision D5) and must never be typed by the user.
 * The {@code purchase_date} is filled by the database default, so the history
 * reloads from the database instead of trusting the in-memory date.</p>
 *
 * <p>Each line of the purchase is a {@link Stock} lot with its quantity and its
 * purchase price: one lot per product and purchase (decision D2). Writing the
 * header and its lots is atomic (decision D5) and belongs to the repository, not
 * to this entity.</p>
 */
public class Purchase {

    /** State of a purchase that has not been received yet (present in the ENUM). */
    public static final String STATUS_PENDING = "pending";

    /** State of a freshly created purchase: its lots are already available (D1). */
    public static final String STATUS_RECEIVED = "received";

    /** State of a cancelled purchase (present in the ENUM). */
    public static final String STATUS_CANCELLED = "cancelled";

    /** Spanish labels of the three ENUM values, shared with the user interface. */
    private static final Map<String, String> STATUS_LABELS = Map.of(
            STATUS_PENDING, "Pendiente",
            STATUS_RECEIVED, "Recibida",
            STATUS_CANCELLED, "Cancelada");

    private int id;
    private LocalDateTime purchaseDate;
    private String status;
    private BigDecimal total;
    private Supplier supplier;
    private User user;
    private List<Stock> lots;

    public Purchase() {
        this.lots = new ArrayList<>();
        this.total = BigDecimal.ZERO;
    }

    /**
     * Builds a brand new purchase: {@code received}, created now, with the total
     * computed from the lots. Every lot is linked back to this purchase.
     */
    public Purchase(Supplier supplier, User user, List<Stock> lots) {
        this.supplier = supplier;
        this.user = user;
        this.lots = lots == null ? new ArrayList<>() : new ArrayList<>(lots);
        this.status = STATUS_RECEIVED;
        this.purchaseDate = LocalDateTime.now();
        this.total = calculateTotal();
        for (Stock lot : this.lots) {
            lot.setPurchase(this);
        }
    }

    /** Full constructor, used when mapping a {@code PURCHASE} row. */
    public Purchase(int id, LocalDateTime purchaseDate, String status, BigDecimal total,
                    Supplier supplier, User user, List<Stock> lots) {
        this.id = id;
        this.purchaseDate = purchaseDate;
        this.status = status;
        this.total = total;
        this.supplier = supplier;
        this.user = user;
        this.lots = lots == null ? new ArrayList<>() : new ArrayList<>(lots);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDateTime purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    /** Supplier the purchase is made to (the {@code supplier_id} foreign key). */
    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    /** Employee that registered the purchase (the {@code user_id} foreign key). */
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /** Lots (one per line) the purchase generates; never {@code null}. */
    public List<Stock> getLots() {
        if (lots == null) {
            lots = new ArrayList<>();
        }
        return lots;
    }

    public void setLots(List<Stock> lots) {
        this.lots = lots == null ? new ArrayList<>() : new ArrayList<>(lots);
    }

    /**
     * Total of the purchase: the sum of the lot subtotals (decision D5). Zero when
     * the purchase has no lots.
     */
    public BigDecimal calculateTotal() {
        BigDecimal result = BigDecimal.ZERO;
        for (Stock lot : getLots()) {
            result = result.add(lot.subtotal());
        }
        return result;
    }

    /** Human readable status label, for the user interface (Spanish). */
    public String getStatusLabel() {
        return labelFor(status);
    }

    /**
     * Human readable label of a status value, shared with the messages of the
     * ViewModel. An unknown/absent value is rendered raw so a diagnostic message
     * never lies about the state.
     *
     * <p>The {@code null} check is needed because the immutable map rejects a
     * {@code null} key: without it, {@code labelFor(null)} would throw instead of
     * returning the raw value.</p>
     */
    public static String labelFor(String status) {
        if (status == null) {
            return String.valueOf(status);
        }
        return STATUS_LABELS.getOrDefault(status, status);
    }

    @Override
    public String toString() {
        String supplierName = supplier == null ? "?" : supplier.getName();
        return "Purchase{id=" + id + ", status='" + status + "', total=" + total
                + ", supplier=" + supplierName + '}';
    }
}
