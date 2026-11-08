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
 * <p>The purchase is created <strong>{@code pending}</strong>: its lots exist from
 * the start but are not available in the stock until the purchase is received, so
 * a purchase that never arrives never sells anything. The {@code user_id} is the
 * signed-in employee and the total is derived from the lots
 * ({@link #calculateTotal()}, decision D5) and must never be typed by the user.
 * The {@code purchase_date} is filled by the database default, so the history
 * reloads from the database instead of trusting the in-memory date.</p>
 *
 * <p>The status is not a free string: the pipeline is a domain invariant through
 * {@link #allowedTransitions()}, {@link #canTransitionTo(String)} and
 * {@link #changeStatus(String)}. The only way to move a purchase is
 * {@code changeStatus(...)}, which rejects retrogressions, leaving a terminal
 * state ({@code returned}/{@code cancelled}), repeating the current state and any
 * unknown/null target. A received purchase can never be cancelled: it can only be
 * returned to the supplier. {@code setStatus(String)} is deliberately not public:
 * the database rehydration goes through the full constructor with the trusted ENUM
 * value.</p>
 *
 * <p>Each line of the purchase is a {@link Stock} lot with its quantity and its
 * purchase price: one lot per product and purchase (decision D2). Writing the
 * header and its lots is atomic (decision D5) and belongs to the repository, not
 * to this entity.</p>
 */
public class Purchase {

    /** State of a freshly created purchase: its lots are not available yet. */
    public static final String STATUS_PENDING = "pending";

    /** State of a received purchase: its lots are available in the stock (D1). */
    public static final String STATUS_RECEIVED = "received";

    /** State of a cancelled purchase: only reachable while it is still pending. */
    public static final String STATUS_CANCELLED = "cancelled";

    /** State of a purchase given back to the supplier: terminal, stock removed. */
    public static final String STATUS_RETURNED = "returned";

    /** Spanish labels of the four ENUM values, shared with the user interface. */
    private static final Map<String, String> STATUS_LABELS = Map.of(
            STATUS_PENDING, "Pendiente",
            STATUS_RECEIVED, "Recibida",
            STATUS_CANCELLED, "Cancelada",
            STATUS_RETURNED, "Devuelta");

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
     * Builds a brand new purchase: {@code pending}, created now, with the total
     * computed from the lots. Every lot is linked back to this purchase.
     */
    public Purchase(Supplier supplier, User user, List<Stock> lots) {
        this.supplier = supplier;
        this.user = user;
        this.lots = lots == null ? new ArrayList<>() : new ArrayList<>(lots);
        this.status = STATUS_PENDING;
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

    /**
     * Transitions allowed from the current status, in button order:
     * {@code pending → received/cancelled} and {@code received → returned}. A
     * received purchase can never be cancelled, only returned. Empty for a
     * terminal status ({@code returned}/{@code cancelled}) or an unknown/absent
     * one. The returned list is immutable, so the rule cannot be mutated from
     * outside.
     */
    public List<String> allowedTransitions() {
        if (STATUS_PENDING.equals(status)) {
            return List.of(STATUS_RECEIVED, STATUS_CANCELLED);
        }
        if (STATUS_RECEIVED.equals(status)) {
            return List.of(STATUS_RETURNED);
        }
        return List.of();
    }

    /**
     * Whether the purchase can move to {@code target} from its current status.
     * Repeatedly asking for the current status, a regression, leaving a terminal
     * status, an unknown target or {@code null} are all rejected.
     */
    public boolean canTransitionTo(String target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** Whether the status is final: no transition leaves {@code returned}/{@code cancelled}. */
    public boolean isTerminal() {
        return isReturnedOrCancelled();
    }

    /**
     * Whether the purchase no longer feeds the stock: it was returned to the
     * supplier ({@code returned}, its lots were emptied) or cancelled before
     * arriving ({@code cancelled}, its lots never became available). Its lots are
     * kept for traceability, but they are no longer stock, so the reports show
     * them marked and leave them out of the totals (RF-12).
     */
    public boolean isReturnedOrCancelled() {
        return STATUS_RETURNED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    /**
     * Single gate for status changes: validates the transition against
     * {@link #allowedTransitions()} and moves the purchase, or throws
     * {@link IllegalStateException} naming the origin and the rejected target.
     */
    public void changeStatus(String target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transición de estado no permitida: \""
                    + status + "\" → \"" + target + "\".");
        }
        this.status = target;
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
