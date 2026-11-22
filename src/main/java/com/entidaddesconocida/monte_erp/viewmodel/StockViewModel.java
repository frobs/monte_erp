package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionGuard;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the stock screen (RF-09 / RF-10 / CU-08).
 *
 * <p>Master-detail: the left table lists every active product with its aggregated
 * available quantity and its minimum-stock alert (RF-10), and the detail shows the
 * lots of the selected product, oldest first (RF-09). The search text and the
 * "only below minimum" toggle travel to the database through
 * {@link StockRepository#findLevels(String, boolean)}, so the aggregation is
 * filtered in the query and not in memory.</p>
 *
 * <p>It is a read-only screen: writing stock belongs to a sale
 * ({@code SalesOrderRepository}, decision D6), so the port it receives is
 * {@link StockRepository}, read-only on purpose. Like the other ViewModels it is
 * free of ValidatorFX and of the JavaFX toolkit.</p>
 */
public class StockViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(StockViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;
    private static final String SESSION_EXPIRED = ViewMessages.SESSION_EXPIRED;

    private final StockRepository stockRepository;
    private final SessionGuard sessionGuard;
    private final BackgroundTasks backgroundTasks;

    private final ObservableList<StockLevel> levels = FXCollections.observableArrayList();
    private final StringProperty searchText = new SimpleStringProperty("");
    private final BooleanProperty onlyBelowMinimum = new SimpleBooleanProperty(false);
    private final ObjectProperty<StockLevel> selectedLevel = new SimpleObjectProperty<>();
    private final ObservableList<Stock> selectedLots = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper alertCount = new ReadOnlyStringWrapper("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /**
     * Sequence of the level queries. Only the newest submission is applied: with
     * the asynchronous reload a slow query for a previous search text could
     * otherwise overwrite the rows of the current one.
     */
    private long levelQueryGeneration;

    public StockViewModel(StockRepository stockRepository) {
        this(stockRepository, null, BackgroundTasks.synchronous());
    }

    public StockViewModel(StockRepository stockRepository, SessionValidator sessionValidator) {
        this(stockRepository, sessionValidator, BackgroundTasks.synchronous());
    }

    /**
     * @param backgroundTasks executor of the blocking queries; {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    public StockViewModel(StockRepository stockRepository, SessionValidator sessionValidator,
                          BackgroundTasks backgroundTasks) {
        this.stockRepository = stockRepository;
        this.sessionGuard = new SessionGuard(LOGGER, sessionValidator,
                DATABASE_ERROR, SESSION_EXPIRED);
        this.backgroundTasks = backgroundTasks;
        searchText.addListener((observable, oldValue, newValue) -> reloadLevels());
        onlyBelowMinimum.addListener((observable, oldValue, newValue) -> reloadLevels());
    }

    /**
     * Master list of stock levels (product + available quantity), bound to the
     * table. The search text and the "below minimum" toggle are applied in the
     * query, so this is not a filtered view.
     */
    public ObservableList<StockLevel> levels() {
        return levels;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    /** When {@code true}, only the products below their minimum are shown (RF-10). */
    public BooleanProperty onlyBelowMinimumProperty() {
        return onlyBelowMinimum;
    }

    public ObjectProperty<StockLevel> selectedLevelProperty() {
        return selectedLevel;
    }

    /** Lots of the selected product, oldest first (RF-09). */
    public ObservableList<Stock> selectedLots() {
        return selectedLots;
    }

    /** Footer text with the number of products below their minimum (RF-10). */
    public ReadOnlyStringProperty alertCountProperty() {
        return alertCount.getReadOnlyProperty();
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /** Loads the levels and resets the selection and its lot detail. */
    public void load() {
        errorMessage.set("");
        if (!ensureActiveSession()) {
            return;
        }
        String search = searchText.get();
        boolean onlyBelowMin = onlyBelowMinimum.get();
        // The queries run off the UI thread; only the state update comes back.
        long generation = ++levelQueryGeneration;
        backgroundTasks.submit(
                () -> new LevelSnapshot(stockRepository.findLevels(search, onlyBelowMin),
                        stockRepository.countBelowMinimum()),
                snapshot -> {
                    if (generation != levelQueryGeneration) {
                        return;
                    }
                    levels.setAll(snapshot.levels());
                    alertCount.set("Productos bajo mínimo: " + snapshot.belowMinimum());
                    selectedLevel.set(null);
                    selectedLots.clear();
                },
                failure -> {
                    if (generation == levelQueryGeneration) {
                        reportLoadFailure(failure);
                    }
                });
    }

    /**
     * Loads the lots of the selected level; a {@code null} level clears them. A
     * database failure empties the detail and shows the connection error.
     */
    public void selectLevel(StockLevel level) {
        if (!ensureActiveSession()) {
            return;
        }
        if (level == null) {
            selectedLots.clear();
            return;
        }
        int productId = level.getProduct().getId();
        backgroundTasks.submit(
                () -> stockRepository.findLots(productId),
                selectedLots::setAll,
                failure -> {
                    LOGGER.log(Level.SEVERE, "Could not load the stock lots", failure);
                    selectedLots.clear();
                    errorMessage.set(DATABASE_ERROR);
                });
    }

    /**
     * Revalidates the session before a query. A dead session or a database
     * failure during the check empties the table, the detail and the counter.
     */
    private boolean ensureActiveSession() {
        return sessionGuard.requireActive(this::clearStock, this::clearStock,
                errorMessage::set);
    }

    /** Drops the levels, the selection and the alert counter of a dead session. */
    private void clearStock() {
        levels.clear();
        selectedLevel.set(null);
        selectedLots.clear();
        alertCount.set("Productos bajo mínimo: 0");
    }

    /**
     * Re-runs the level query with the current filters. A row that no longer
     * matches is dropped from the selection so its detail does not stay on
     * screen. The global alert counter is not touched: the filters do not change
     * how many products are below their minimum.
     */
    private void reloadLevels() {
        if (!ensureActiveSession()) {
            return;
        }
        String search = searchText.get();
        boolean onlyBelowMin = onlyBelowMinimum.get();
        long generation = ++levelQueryGeneration;
        backgroundTasks.submit(
                () -> stockRepository.findLevels(search, onlyBelowMin),
                loaded -> {
                    if (generation != levelQueryGeneration) {
                        return;
                    }
                    levels.setAll(loaded);
                    StockLevel selected = selectedLevel.get();
                    if (selected != null
                            && levels.stream().noneMatch(level -> sameProduct(level, selected))) {
                        selectedLevel.set(null);
                        selectedLots.clear();
                    }
                },
                failure -> {
                    if (generation != levelQueryGeneration) {
                        return;
                    }
                    LOGGER.log(Level.SEVERE, "Could not reload the filtered stock levels", failure);
                    errorMessage.set(DATABASE_ERROR);
                });
    }

    /** Reports a failed level query: the table is emptied and the cause is logged. */
    private void reportLoadFailure(Throwable failure) {
        LOGGER.log(Level.SEVERE, "Could not load the stock levels", failure);
        levels.clear();
        alertCount.set("Productos bajo mínimo: 0");
        selectedLevel.set(null);
        selectedLots.clear();
        errorMessage.set(DATABASE_ERROR);
    }

    /** Rows and global alert count read together, so they are applied atomically. */
    private record LevelSnapshot(List<StockLevel> levels, int belowMinimum) {
    }

    private static boolean sameProduct(StockLevel first, StockLevel second) {
        return first.getProduct() != null && second.getProduct() != null
                && first.getProduct().getId() == second.getProduct().getId();
    }
}
