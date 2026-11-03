package com.entidaddesconocida.monte_erp.viewmodel;

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
import javafx.collections.transformation.FilteredList;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the stock screen (RF-09 / RF-10 / CU-08).
 *
 * <p>Master-detail: the left table lists every active product with its aggregated
 * available quantity and its minimum-stock alert (RF-10), and the detail shows the
 * lots of the selected product, oldest first (RF-09). The search text and the
 * "only below minimum" toggle are in-memory filters over the loaded levels.</p>
 *
 * <p>It is a read-only screen: writing stock belongs to a sale
 * ({@code SalesOrderRepository}, decision D6), so the port it receives is
 * {@link StockRepository}, read-only on purpose. Like the other ViewModels it is
 * free of ValidatorFX and of the JavaFX toolkit.</p>
 */
public class StockViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(StockViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";
    private static final String SESSION_EXPIRED = "La sesión ya no está activa. Inicia sesión de nuevo.";

    private final StockRepository stockRepository;
    private final SessionValidator sessionValidator;

    private final ObservableList<StockLevel> levels = FXCollections.observableArrayList();
    private final FilteredList<StockLevel> filteredLevels = new FilteredList<>(levels);
    private final StringProperty searchText = new SimpleStringProperty("");
    private final BooleanProperty onlyBelowMinimum = new SimpleBooleanProperty(false);
    private final ObjectProperty<StockLevel> selectedLevel = new SimpleObjectProperty<>();
    private final ObservableList<Stock> selectedLots = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper alertCount = new ReadOnlyStringWrapper("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    public StockViewModel(StockRepository stockRepository) {
        this(stockRepository, null);
    }

    public StockViewModel(StockRepository stockRepository, SessionValidator sessionValidator) {
        this.stockRepository = stockRepository;
        this.sessionValidator = sessionValidator;
        searchText.addListener((observable, oldValue, newValue) -> applyFilter());
        onlyBelowMinimum.addListener((observable, oldValue, newValue) -> applyFilter());
    }

    /** Master list of stock levels (product + available quantity). */
    public ObservableList<StockLevel> levels() {
        return levels;
    }

    /** Filtered view bound to the table. */
    public FilteredList<StockLevel> filteredLevels() {
        return filteredLevels;
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
        try {
            levels.setAll(stockRepository.findLevels());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the stock levels", e);
            levels.clear();
            errorMessage.set(DATABASE_ERROR);
        }
        selectedLevel.set(null);
        selectedLots.clear();
        applyFilter();
        updateAlertCount();
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
        try {
            selectedLots.setAll(stockRepository.findLots(level.getProduct().getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the stock lots", e);
            selectedLots.clear();
            errorMessage.set(DATABASE_ERROR);
        }
    }

    /** Whether the level passes the text search and the "only below minimum" toggle. */
    private boolean ensureActiveSession() {
        if (sessionValidator == null) {
            return true;
        }
        try {
            if (sessionValidator.refresh()) {
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not revalidate the signed-in user", e);
            levels.clear();
            selectedLots.clear();
            errorMessage.set(DATABASE_ERROR);
            updateAlertCount();
            return false;
        }
        levels.clear();
        selectedLevel.set(null);
        selectedLots.clear();
        applyFilter();
        updateAlertCount();
        errorMessage.set(SESSION_EXPIRED);
        return false;
    }

    private boolean matches(StockLevel level) {
        if (level == null || level.getProduct() == null) {
            return false;
        }
        if (onlyBelowMinimum.get() && !level.isBelowMinimum()) {
            return false;
        }
        // Case- and accent-insensitive over name/SKU, like the product screen.
        return SearchText.matches(searchText.get(), level.getProduct().getName(),
                level.getProduct().getSku());
    }

    private void applyFilter() {
        filteredLevels.setPredicate(this::matches);
    }

    private void updateAlertCount() {
        long alerts = levels.stream().filter(StockLevel::isBelowMinimum).count();
        alertCount.set("Productos bajo mínimo: " + alerts);
    }
}
