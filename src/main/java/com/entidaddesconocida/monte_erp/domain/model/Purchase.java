package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
 * <p>The status is a {@link PurchaseStatus}, not a free string: the pipeline is a
 * domain invariant through {@link PurchaseStatus#transitions()}. The only way to
 * move a purchase is {@link #changeStatus(PurchaseStatus)}, which rejects
 * retrogressions, leaving a terminal state ({@code returned}/{@code cancelled}),
 * repeating the current state and a {@code null} target. A received purchase can
 * never be cancelled: it can only be returned to the supplier. There is
 * deliberately no {@code setStatus}: the database rehydration goes through the
 * full constructor with the value parsed by
 * {@link PurchaseStatus#fromDb(String)}.</p>
 *
 * <p>Each line of the purchase is a {@link Stock} lot with its quantity and its
 * purchase price: one lot per product and purchase (decision D2). Writing the
 * header and its lots is atomic (decision D5) and belongs to the repository, not
 * to this entity.</p>
 */
public class Purchase {

    private int id;
    private LocalDateTime purchaseDate;
    private PurchaseStatus status;
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
        this.status = PurchaseStatus.PENDING;
        this.purchaseDate = LocalDateTime.now();
        this.total = calculateTotal();
        for (Stock lot : this.lots) {
            lot.setPurchase(this);
        }
    }

    /** Full constructor, used when mapping a {@code PURCHASE} row. */
    public Purchase(int id, LocalDateTime purchaseDate, PurchaseStatus status, BigDecimal total,
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

    public PurchaseStatus getStatus() {
        return status;
    }

    /**
     * Transitions allowed from the current status, in button order. The rule lives
     * in {@link PurchaseStatus#transitions()}, so it cannot drift from the state
     * machine.
     */
    public List<PurchaseStatus> allowedTransitions() {
        return status == null ? List.of() : status.transitions();
    }

    /**
     * Whether the purchase can move to {@code target} from its current status.
     * Repeating the current status, a regression, leaving a terminal status, an
     * unknown target or {@code null} are all rejected.
     */
    public boolean canTransitionTo(PurchaseStatus target) {
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
        return status != null && status.isReturnedOrCancelled();
    }

    /**
     * Single gate for status changes: validates the transition against
     * {@link #allowedTransitions()} and moves the purchase, or throws
     * {@link IllegalStateException} naming the origin and the rejected target.
     */
    public void changeStatus(PurchaseStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transición de estado no permitida: \""
                    + dbValueOf(status) + "\" → \"" + dbValueOf(target) + "\".");
        }
        this.status = target;
    }

    /** Database value of a status, or {@code null} as text when there is none. */
    private static String dbValueOf(PurchaseStatus status) {
        return status == null ? "null" : status.dbValue();
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

    /**
     * Lots (one per line) the purchase generates; never {@code null}, because the
     * constructors and {@link #setLots(List)} guarantee it.
     */
    public List<Stock> getLots() {
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
        return getLots().stream()
                .map(Stock::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Human readable status label, for the user interface (Spanish). */
    public String getStatusLabel() {
        return status == null ? "" : status.label();
    }

    @Override
    public String toString() {
        String supplierName = supplier == null ? "?" : supplier.getName();
        return "Purchase{id=" + id + ", status='" + status + "', total=" + total
                + ", supplier=" + supplierName + '}';
    }
}
