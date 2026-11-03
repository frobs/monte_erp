package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.service.SalesReportCalculator;
import com.entidaddesconocida.monte_erp.domain.service.StockReportCalculator;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the reports screen (RF-12 / CU-09).
 *
 * <p>It exposes the two reports at once, selected with a {@code TabPane} in the
 * view: the stock-by-lot report (one row per lot) and the sales-by-product report
 * (one aggregated row per product, cancelled orders excluded). The shared period
 * filters both, {@link #load()} aggregates with the pure calculators and the CSV
 * text is produced here so the view only writes the chosen file.</p>
 *
 * <p>The period is validated <strong>before</strong> querying: an inverted range
 * fills {@link #errorMessageProperty()} and leaves the repository untouched. Like
 * the other ViewModels it is free of the JavaFX toolkit and of ValidatorFX, so it
 * is unit tested with in-memory fakes.</p>
 */
public class ReportsViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(ReportsViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";

    /** Message shown when the "desde" date is after the "hasta" date. */
    protected static final String PERIOD_ERROR =
            "La fecha «desde» no puede ser posterior a la fecha «hasta».";
    private static final String SESSION_EXPIRED = "La sesión ya no está activa. Inicia sesión de nuevo.";

    private final SalesOrderRepository salesOrderRepository;
    private final StockRepository stockRepository;
    private final SessionValidator sessionValidator;

    private final ObjectProperty<LocalDate> fromDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> toDate = new SimpleObjectProperty<>();
    private final ObservableList<Stock> stockLots = FXCollections.observableArrayList();
    private final ObservableList<SalesReportRow> salesRows = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<StockReportSummary> stockSummary =
            new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<SalesReportSummary> salesSummary =
            new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper stockTotals = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper salesTotals = new ReadOnlyStringWrapper("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    public ReportsViewModel(SalesOrderRepository salesOrderRepository,
                            StockRepository stockRepository) {
        this(salesOrderRepository, stockRepository, null);
    }

    public ReportsViewModel(SalesOrderRepository salesOrderRepository,
                            StockRepository stockRepository,
                            SessionValidator sessionValidator) {
        this.salesOrderRepository = salesOrderRepository;
        this.stockRepository = stockRepository;
        this.sessionValidator = sessionValidator;
        publish(StockReportSummary.empty(), SalesReportSummary.empty());
    }

    /** Lower bound of the period; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> fromDateProperty() {
        return fromDate;
    }

    /** Upper bound of the period; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> toDateProperty() {
        return toDate;
    }

    /** Lots of the stock report, ordered like the query (product, FIFO). */
    public ObservableList<Stock> stockLots() {
        return stockLots;
    }

    /** Aggregated rows of the sales report, ordered by revenue descending. */
    public ObservableList<SalesReportRow> salesRows() {
        return salesRows;
    }

    public ReadOnlyObjectProperty<StockReportSummary> stockSummaryProperty() {
        return stockSummary.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<SalesReportSummary> salesSummaryProperty() {
        return salesSummary.getReadOnlyProperty();
    }

    /** Footer text of the stock tab with its totals. */
    public ReadOnlyStringProperty stockTotalsProperty() {
        return stockTotals.getReadOnlyProperty();
    }

    /** Footer text of the sales tab with its totals. */
    public ReadOnlyStringProperty salesTotalsProperty() {
        return salesTotals.getReadOnlyProperty();
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /** Period currently selected, or {@code null} bounds when empty. */
    public ReportPeriod period() {
        return ReportPeriod.of(fromDate.get(), toDate.get());
    }

    /**
     * Loads both reports for the selected period.
     *
     * <p>An inverted period is rejected in the domain and reported without
     * touching the database. A {@link SQLException} clears the tables and shows
     * the standard connection message, as the other ViewModels do.</p>
     */
    public void load() {
        errorMessage.set("");
        if (!ensureActiveSession()) {
            return;
        }
        ReportPeriod period;
        try {
            period = period();
        } catch (IllegalArgumentException e) {
            clearReports();
            errorMessage.set(PERIOD_ERROR);
            return;
        }
        try {
            List<Stock> lots = stockRepository.findAllLots(period);
            List<SalesReportLine> lines = salesOrderRepository.findReportLines(period);
            StockReportSummary stock = StockReportCalculator.summarize(lots);
            SalesReportSummary sales = SalesReportCalculator.summarize(lines);
            stockLots.setAll(lots);
            salesRows.setAll(sales.rows());
            publish(stock, sales);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the reports", e);
            clearReports();
            errorMessage.set(DATABASE_ERROR);
        }
    }

    private void clearReports() {
        stockLots.clear();
        salesRows.clear();
        publish(StockReportSummary.empty(), SalesReportSummary.empty());
    }

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
            clearReports();
            errorMessage.set(DATABASE_ERROR);
            return false;
        }
        clearReports();
        errorMessage.set(SESSION_EXPIRED);
        return false;
    }

    /** Clears both dates (whole history) and reloads. */
    public void clearPeriod() {
        fromDate.set(null);
        toDate.set(null);
        load();
    }

    /** CSV text of the stock report currently loaded. */
    public String stockReportCsv() {
        if (!ensureActiveSession()) {
            return ReportCsv.stockReport(stockLots, stockSummary.get());
        }
        return ReportCsv.stockReport(stockLots, stockSummary.get());
    }

    /** CSV text of the sales report currently loaded. */
    public String salesReportCsv() {
        if (!ensureActiveSession()) {
            return ReportCsv.salesReport(salesRows, salesSummary.get());
        }
        return ReportCsv.salesReport(salesRows, salesSummary.get());
    }

    private void publish(StockReportSummary stock, SalesReportSummary sales) {
        stockSummary.set(stock);
        salesSummary.set(sales);
        stockTotals.set("Lotes: " + stock.lotCount()
                + " · Unidades disponibles: " + stock.totalAvailableUnits()
                + " · Valor del stock: " + money(stock.totalStockValue()));
        salesTotals.set("Pedidos: " + sales.orderCount()
                + " · Unidades vendidas: " + sales.totalUnits()
                + " · Facturación: " + money(sales.totalRevenue()));
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }
}
