package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionGuard;
import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the purchase screen (RF-14 / CU-10).
 *
 * <p>It is <strong>not</strong> a CRUD: a purchase is created and then read as
 * history, never edited or deleted. It is a master-detail screen that creates a
 * purchase from several lots (product, quantity, purchase price) and shows the
 * read-only history with the detail of the selected purchase.</p>
 *
 * <p>The responsibilities are delegated (A2): the draft — combos, supplier,
 * lots, total and duplicate rule — lives in {@link PurchaseDraft}; the history
 * criteria in {@link HistoryFilter}; the status pipeline in
 * {@link StatusChanger}. This class wires them, runs the insert and reads the
 * lots of the selection.</p>
 *
 * <p>Every line generates a {@link Stock} lot that stays unavailable until the
 * purchase is received (decision D1/D6): the total is computed from the lots
 * (decision D5) and the purchase is saved as {@code pending} with the signed-in
 * user, then moves through {@code pending → received → returned} (or
 * {@code pending → cancelled}) as the only mutation of the screen. The screen
 * only <strong>writes</strong> lots: reading the stock of a product, the alerts
 * and the FIFO allocation belong to RF-09/RF-10/RF-15 (decision D4).</p>
 *
 * <p>The history can be filtered by date range, supplier and status. The criteria
 * travel to the database as a {@link PurchaseFilter}: every change runs
 * {@code findAll(filter)}, so nothing is filtered in memory. An inverted range is
 * rejected in the domain before any query is issued, and every reload drops the
 * selection so a filtered-out purchase never keeps its detail on screen.</p>
 *
 * <p>Like the CRUD ViewModels it is free of ValidatorFX: the per-field rules are
 * exposed as one error property per field, so it stays testable with plain JUnit
 * and no JavaFX toolkit.</p>
 */
public class PurchasesViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(PurchasesViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;
    private static final String SESSION_EXPIRED = ViewMessages.SESSION_EXPIRED;
    private static final String SAVE_RELOAD_ERROR =
            "Compra #%d guardada, pero no se pudo actualizar el historial. Pulsa 'Actualizar'.";

    /** Message shown when the "desde" date is after the "hasta" date. */
    protected static final String PERIOD_ERROR = ViewMessages.PERIOD_ERROR;

    /** Shown when the purchase was saved but the filter hides the history. */
    private static final String SAVED_WITH_BAD_FILTER =
            "Compra #%d guardada correctamente. Corrige el filtro para verla en el historial.";

    /** No supplier chosen when saving a purchase. */
    protected static final String SUPPLIER_REQUIRED = PurchaseDraft.SUPPLIER_REQUIRED;

    /** No product chosen when adding a lot. */
    protected static final String PRODUCT_REQUIRED = PurchaseDraft.PRODUCT_REQUIRED;

    /** Quantity is empty, not an integer or not positive. */
    protected static final String QUANTITY_INVALID = PurchaseDraft.QUANTITY_INVALID;

    /** Purchase price is empty, not a number or negative. */
    protected static final String PRICE_INVALID = PurchaseDraft.PRICE_INVALID;

    /** A product can only appear once in the purchase (decision D2). */
    protected static final String DUPLICATED_PRODUCT = PurchaseDraft.DUPLICATED_PRODUCT;

    /** A purchase needs at least one lot to be saved. */
    protected static final String LOTS_REQUIRED = PurchaseDraft.LOTS_REQUIRED;

    /** No purchase selected to change its status. */
    protected static final String PURCHASE_REQUIRED = "Selecciona una compra.";

    /** The transition is not allowed by the domain (origin, target). */
    protected static final String INVALID_TRANSITION =
            "La compra no puede pasar de \"%s\" a \"%s\".";

    /** The optimistic guard did not match; someone else moved the purchase. */
    protected static final String CONFLICT =
            "La compra ha cambiado desde que se cargó; se ha actualizado el historial.";

    /** Texts of the RF-14 pipeline. */
    private static final StatusChanger.Messages<PurchaseStatus> STATUS_MESSAGES =
            new StatusChanger.Messages<>(PURCHASE_REQUIRED, INVALID_TRANSITION, CONFLICT,
                    (id, status) -> "Compra #" + id + " marcada como " + status.label() + ".");

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final SessionUser currentUser;
    private final SessionGuard sessionGuard;
    private final BackgroundTasks backgroundTasks;

    private final Feedback feedback = new Feedback();
    private final WriteGuard writeGuard = new WriteGuard();

    private final PurchaseDraft draft;
    private final HistoryFilter<Supplier, PurchaseStatus> filter;
    private final StatusChanger<Purchase, PurchaseStatus> statusChanger;

    private final ObservableList<Purchase> purchases = FXCollections.observableArrayList();
    private final ObjectProperty<Purchase> selectedPurchase = new SimpleObjectProperty<>();
    private final ObservableList<Stock> selectedPurchaseLots = FXCollections.observableArrayList();

    public PurchasesViewModel(PurchaseRepository purchaseRepository,
                              SupplierRepository supplierRepository,
                              ProductRepository productRepository,
                              SessionUser currentUser) {
        this(purchaseRepository, supplierRepository, productRepository, currentUser, null,
                BackgroundTasks.synchronous());
    }

    public PurchasesViewModel(PurchaseRepository purchaseRepository,
                              SupplierRepository supplierRepository,
                              ProductRepository productRepository,
                              SessionUser currentUser,
                              SessionValidator sessionValidator) {
        this(purchaseRepository, supplierRepository, productRepository, currentUser,
                sessionValidator, BackgroundTasks.synchronous());
    }

    /**
     * @param backgroundTasks executor of the insert and the status change (both
     *                        lock rows and can wait for another session); {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    public PurchasesViewModel(PurchaseRepository purchaseRepository,
                              SupplierRepository supplierRepository,
                              ProductRepository productRepository,
                              SessionUser currentUser,
                              SessionValidator sessionValidator,
                              BackgroundTasks backgroundTasks) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.currentUser = currentUser;
        this.sessionGuard = new SessionGuard(LOGGER, sessionValidator,
                DATABASE_ERROR, SESSION_EXPIRED);
        this.backgroundTasks = backgroundTasks;
        this.draft = new PurchaseDraft(supplierRepository, productRepository, feedback);
        // Every filter change runs one query.
        this.filter = new HistoryFilter<>(PurchaseStatus.values(), this::reloadHistory);
        this.statusChanger = new StatusChanger<>(backgroundTasks, writeGuard, feedback,
                purchaseRepository::changeStatus, new StatusChanger.Screen<>() {
                    @Override
                    public boolean reloadHistory() {
                        return PurchasesViewModel.this.reloadHistory();
                    }

                    @Override
                    public Purchase findById(int id) {
                        return PurchasesViewModel.this.findById(id);
                    }

                    @Override
                    public void select(Purchase purchase) {
                        selectedPurchase.set(purchase);
                        selectPurchase(purchase);
                    }
                }, this::onStatusChangeFailure, STATUS_MESSAGES);
    }

    /** Active suppliers offered in the combo (an inactive one cannot be assigned). */
    public ObservableList<Supplier> suppliers() {
        return draft.suppliers();
    }

    /** Products offered in the combo. */
    public ObservableList<Product> products() {
        return draft.products();
    }

    public ObjectProperty<Supplier> selectedSupplierProperty() {
        return draft.selectedSupplierProperty();
    }

    public ObjectProperty<Product> selectedProductProperty() {
        return draft.selectedProductProperty();
    }

    /** Quantity typed by the user; parsed by {@link PurchaseDraft#addLot()}. */
    public StringProperty quantityProperty() {
        return draft.quantityProperty();
    }

    /** Purchase price typed by the user; parsed by {@link PurchaseDraft#addLot()}. */
    public StringProperty purchasePriceProperty() {
        return draft.purchasePriceProperty();
    }

    /** Lots of the purchase being drafted. */
    public ObservableList<Stock> draftLots() {
        return draft.lots();
    }

    /** Total of the draft purchase, recomputed from the lots (decision D5). */
    public ReadOnlyObjectProperty<BigDecimal> purchaseTotalProperty() {
        return draft.totalProperty();
    }

    /** Read-only history matching the active filter, most recent first. */
    public ObservableList<Purchase> purchases() {
        return purchases;
    }

    public ObjectProperty<Purchase> selectedPurchaseProperty() {
        return selectedPurchase;
    }

    /** Lots of the selected purchase, loaded from the repository. */
    public ObservableList<Stock> selectedPurchaseLots() {
        return selectedPurchaseLots;
    }

    /** Lower bound of the history filter; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> fromDateProperty() {
        return filter.fromDateProperty();
    }

    /** Upper bound of the history filter; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> toDateProperty() {
        return filter.toDateProperty();
    }

    /** Supplier filter; {@code null} means "every supplier". */
    public ObjectProperty<Supplier> supplierFilterProperty() {
        return filter.entityProperty();
    }

    /** Status filter; {@code null} means "every status". */
    public ObjectProperty<PurchaseStatus> statusFilterProperty() {
        return filter.statusProperty();
    }

    /**
     * Suppliers offered by the filter: every one, active or not, so an old
     * purchase of a supplier given up can still be listed (like RF-11).
     */
    public ObservableList<Supplier> supplierFilterOptions() {
        return filter.entityOptions();
    }

    /** Statuses offered by the filter, in domain order. */
    public ObservableList<PurchaseStatus> statusFilterOptions() {
        return filter.statusOptions();
    }

    /**
     * Statuses the selected purchase can move to, in button order. Empty when
     * there is no selection or the purchase is in a terminal state, so the view
     * can bind the visibility of each action button to it.
     */
    public ObservableList<PurchaseStatus> availableTransitions() {
        return statusChanger.availableTransitions();
    }

    public StringProperty errorMessageProperty() {
        return feedback.errorMessageProperty();
    }

    public StringProperty statusMessageProperty() {
        return feedback.statusMessageProperty();
    }

    public ReadOnlyStringProperty supplierErrorProperty() {
        return draft.supplierErrorProperty();
    }

    public ReadOnlyStringProperty productErrorProperty() {
        return draft.productErrorProperty();
    }

    public ReadOnlyStringProperty quantityErrorProperty() {
        return draft.quantityErrorProperty();
    }

    public ReadOnlyStringProperty priceErrorProperty() {
        return draft.priceErrorProperty();
    }

    public ReadOnlyStringProperty lotsErrorProperty() {
        return draft.lotsErrorProperty();
    }

    /** Loads the combos, the filter options and the filtered history. */
    public void load() {
        feedback.clear();
        if (!ensureActiveSession()) {
            return;
        }
        try {
            draft.loadCombos();
            // The filter lists every supplier: an inactive one still sold to us.
            supplierFilterOptions().setAll(supplierRepository.findAll());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purchases", e);
            feedback.fail(DATABASE_ERROR);
            return;
        }
        draft.reset();
        draft.clearFieldErrors();
        reloadHistory();
    }

    /** Drops every filter and re-queries the whole history. */
    public void clearFilters() {
        filter.reset();
        reloadHistory();
    }

    /**
     * Runs the query of the current filter and drops the selection, because a
     * filtered-out purchase must not stay selected with its detail on screen.
     * Returns {@code false} when the history could not be refreshed: the error
     * message is already set and the caller decides how to report its write.
     */
    private boolean reloadHistory() {
        PurchaseFilter current;
        try {
            current = currentFilter();
        } catch (IllegalArgumentException e) {
            purchases.clear();
            clearSelection();
            feedback.fail(PERIOD_ERROR);
            return false;
        }
        // A corrected range must clear the message that rejected the old one.
        if (PERIOD_ERROR.equals(feedback.error())) {
            feedback.clearError();
        }
        try {
            purchases.setAll(purchaseRepository.findAll(current));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purchases", e);
            purchases.clear();
            clearSelection();
            feedback.fail(DATABASE_ERROR);
            return false;
        }
        clearSelection();
        return true;
    }

    private PurchaseFilter currentFilter() {
        Supplier supplier = filter.entityProperty().get();
        return PurchaseFilter.of(filter.fromDateProperty().get(), filter.toDateProperty().get(),
                supplier == null ? null : supplier.getId(), filter.statusProperty().get());
    }

    private void clearSelection() {
        selectedPurchase.set(null);
        selectedPurchaseLots.clear();
        statusChanger.availableTransitions().clear();
    }

    /** Adds the chosen product to the draft purchase (see {@link PurchaseDraft#addLot()}). */
    public void addLot() {
        draft.addLot();
    }

    /** Removes a lot from the draft purchase and recomputes the total. */
    public void removeLot(Stock lot) {
        draft.removeLot(lot);
    }

    /** Clears the draft purchase so a brand new one can be typed in. */
    public void newPurchase() {
        feedback.clear();
        draft.reset();
        statusChanger.availableTransitions().clear();
        draft.clearFieldErrors();
    }

    /**
     * Saves the draft purchase: validates the supplier and at least one lot,
     * then inserts the purchase as {@code pending} (its lots are not available
     * yet) for the signed-in user with the total computed from the lots. The
     * header and its lots are written in one transaction.
     */
    public void save() {
        if (!ensureActiveSession()) {
            return;
        }
        String invalid = draft.validateForSave();
        if (!invalid.isEmpty()) {
            feedback.fail(invalid);
            return;
        }
        if (!draft.totalWithinLimit()) {
            feedback.fail(PurchaseDraft.TOTAL_LIMIT_ERROR);
            return;
        }

        if (!writeGuard.tryBegin()) {
            return;
        }
        Purchase purchase = draft.buildPurchase(currentUser.toEntityReference());
        // The insert locks the supplier, the products and the lots: off the UI thread.
        backgroundTasks.submit(
                () -> purchaseRepository.insert(purchase),
                id -> {
                    writeGuard.end();
                    onPurchaseSaved(id);
                },
                failure -> {
                    writeGuard.end();
                    onPurchaseSaveFailure(failure);
                });
    }

    /**
     * Applies a committed purchase: the draft is discarded before the independent
     * history read, so a reload failure cannot cause a duplicate.
     */
    private void onPurchaseSaved(int id) {
        draft.reset();
        draft.clearFieldErrors();
        if (reloadHistory()) {
            feedback.succeed("Compra #" + id + " guardada correctamente.");
        } else if (PERIOD_ERROR.equals(feedback.error())) {
            // The write succeeded; only the filter hides the history.
            feedback.status(String.format(SAVED_WITH_BAD_FILTER, id));
        } else {
            LOGGER.log(Level.WARNING, "Purchase was saved but its history could not be reloaded");
            feedback.fail(String.format(SAVE_RELOAD_ERROR, id));
        }
    }

    /** Reports a rejected insert, keeping the draft so it can be corrected. */
    private void onPurchaseSaveFailure(Throwable failure) {
        if (failure instanceof InactiveEntityException) {
            feedback.fail(failure.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not save the purchase", failure);
        feedback.fail(DATABASE_ERROR);
    }

    /** Loads the lots of the selected purchase; a {@code null} purchase clears them. */
    public void selectPurchase(Purchase purchase) {
        if (!ensureActiveSession()) {
            return;
        }
        statusChanger.updateFor(purchase);
        if (purchase == null) {
            selectedPurchaseLots.clear();
            return;
        }
        try {
            selectedPurchaseLots.setAll(purchaseRepository.findLots(purchase.getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purchase lots", e);
            selectedPurchaseLots.clear();
            feedback.fail(DATABASE_ERROR);
        }
    }

    /**
     * Moves the selected purchase to {@code newStatus} through the shared
     * pipeline (domain validation, optimistic guard and conflict reload).
     */
    public void changeStatus(PurchaseStatus newStatus) {
        if (!ensureActiveSession()) {
            return;
        }
        draft.clearFieldErrors();
        statusChanger.change(selectedPurchase.get(), newStatus);
    }

    /** A returned purchase whose lots were already sold cannot be given back. */
    private void onStatusChangeFailure(Throwable failure) {
        if (failure instanceof PurchaseStockConsumedException) {
            feedback.fail(failure.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not change the purchase status", failure);
        feedback.fail(DATABASE_ERROR);
    }

    private Purchase findById(int id) {
        return purchases.stream()
                .filter(purchase -> purchase.getId() == id)
                .findFirst()
                .orElse(null);
    }

    private boolean ensureActiveSession() {
        return sessionGuard.requireActive(this::clearSessionState, feedback::fail);
    }

    /** Drops the history, the selection and the draft of a session that is over. */
    private void clearSessionState() {
        purchases.clear();
        clearSelection();
        draft.reset();
    }
}
