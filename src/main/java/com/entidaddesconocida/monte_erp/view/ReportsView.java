package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.viewmodel.ReportLabels;
import com.entidaddesconocida.monte_erp.viewmodel.ReportsViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Code-behind of the reports FXML (RF-12 / CU-09).
 *
 * <p>Read-only screen with two tabs (stock by lot and sales by product) that
 * materialise the "select type" of the use case. The period filters both reports
 * and the export buttons write the CSV text or the PDF bytes produced by
 * {@link ReportsViewModel} into the file the user chooses, so the serialisation
 * stays unit tested in the ViewModel and here only the {@link FileChooser} and
 * the disk write live.</p>
 */
public class ReportsView implements FxmlView<ReportsViewModel> {

    private static final Logger LOGGER = Logger.getLogger(ReportsView.class.getName());

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Pseudo-class of an excluded row, either a lot or a product (see app.css). */
    private static final PseudoClass EXCLUDED_ROW = PseudoClass.getPseudoClass("excluded");

    private final AppShell appShell;

    @FXML
    private DatePicker fromPicker;

    @FXML
    private DatePicker toPicker;

    @FXML
    private Label errorLabel;

    @FXML
    private TabPane reportsTabPane;

    @FXML
    private Label countLabel;

    @FXML
    private TableView<Stock> stockLotsTable;

    @FXML
    private TableColumn<Stock, String> stockProductColumn;

    @FXML
    private TableColumn<Stock, String> stockSkuColumn;

    @FXML
    private TableColumn<Stock, String> stockEntryDateColumn;

    @FXML
    private TableColumn<Stock, String> stockInitialColumn;

    @FXML
    private TableColumn<Stock, String> stockAvailableColumn;

    @FXML
    private TableColumn<Stock, String> stockPurchasePriceColumn;

    @FXML
    private TableColumn<Stock, String> stockPurchaseColumn;

    @FXML
    private TableColumn<Stock, String> stockPurchaseStatusColumn;

    @FXML
    private TableColumn<Stock, String> stockReasonColumn;

    @FXML
    private Label stockTotalsLabel;

    @FXML
    private TableView<SalesReportRow> salesTable;

    @FXML
    private TableColumn<SalesReportRow, String> salesProductColumn;

    @FXML
    private TableColumn<SalesReportRow, String> salesSkuColumn;

    @FXML
    private TableColumn<SalesReportRow, String> salesUnitsColumn;

    @FXML
    private TableColumn<SalesReportRow, String> salesRevenueColumn;

    @FXML
    private TableColumn<SalesReportRow, String> salesCancelledUnitsColumn;

    @FXML
    private TableColumn<SalesReportRow, String> salesReasonColumn;

    @FXML
    private Label salesTotalsLabel;

    @InjectViewModel
    private ReportsViewModel viewModel;

    public ReportsView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        setupStockColumns();
        setupSalesColumns();
        highlightExcludedRows(stockLotsTable, Stock::countsInStockReport);
        highlightExcludedRows(salesTable, SalesReportRow::countsInSalesReport);
        stockLotsTable.setItems(viewModel.stockLots());
        salesTable.setItems(viewModel.salesRows());

        fromPicker.valueProperty().bindBidirectional(viewModel.fromDateProperty());
        toPicker.valueProperty().bindBidirectional(viewModel.toDateProperty());
        stockTotalsLabel.textProperty().bind(viewModel.stockTotalsProperty());
        salesTotalsLabel.textProperty().bind(viewModel.salesTotalsProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        // The footer count follows the active tab and both result lists, so it is
        // refreshed both when the user switches tabs and when a report is generated.
        countLabel.textProperty().bind(Bindings.createStringBinding(
                () -> (reportsTabPane.getSelectionModel().getSelectedIndex() == 1
                        ? viewModel.salesRows().size() : viewModel.stockLots().size()) + " filas",
                reportsTabPane.getSelectionModel().selectedIndexProperty(),
                viewModel.stockLots(), viewModel.salesRows()));

        viewModel.load();
    }

    private void setupStockColumns() {
        stockProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getName()));
        stockSkuColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getSku()));
        stockEntryDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getEntryDate() == null
                        ? "" : DATE_FORMAT.format(cell.getValue().getEntryDate())));
        stockInitialColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getInitialQuantity())));
        stockAvailableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getAvailableQuantity())));
        stockPurchasePriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getPurchasePrice())));
        stockPurchaseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getPurchase() == null || cell.getValue().getPurchase().getId() == 0
                        ? "" : "#" + cell.getValue().getPurchase().getId()));
        stockPurchaseStatusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(ReportLabels.purchaseStatus(cell.getValue())));
        stockReasonColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(ReportLabels.availabilityReason(cell.getValue())));
    }

    /**
     * Paints in red the rows that do not count in the totals: the lots whose
     * purchase was returned or cancelled and the products whose orders were all
     * cancelled. They stay in the table (that is the point, they explain the 0
     * available/sold) and the row says so at first glance.
     *
     * <p>The flag is set with a {@link PseudoClass} instead of adding/removing a
     * style class: a pseudo-class is the JavaFX way to reflect a state on a reused
     * row, it keeps the style class list untouched and it does not force a full
     * restyle of the row every time {@code updateItem} runs.</p>
     */
    private static <T> void highlightExcludedRows(TableView<T> table, Predicate<T> counts) {
        table.setRowFactory(view -> new TableRow<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(EXCLUDED_ROW, !empty && item != null && !counts.test(item));
            }
        });
    }

    private void setupSalesColumns() {
        salesProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getName()));
        salesSkuColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getSku()));
        salesUnitsColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(String.valueOf(cell.getValue().getUnitsSold())));
        salesRevenueColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getRevenue())));
        salesCancelledUnitsColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(String.valueOf(cell.getValue().getCancelledUnits())));
        salesReasonColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(ReportLabels.salesReason(cell.getValue())));
    }

    @FXML
    private void onGenerate() {
        viewModel.load();
    }

    @FXML
    private void onClear() {
        viewModel.clearPeriod();
    }

    @FXML
    private void onExportStock() {
        exportCsv("informe-stock.csv", viewModel.stockReportCsv());
    }

    @FXML
    private void onExportSales() {
        exportCsv("informe-ventas.csv", viewModel.salesReportCsv());
    }

    @FXML
    private void onBack() {
        appShell.showMain();
    }

    /** Asks for a destination file and writes the CSV; a cancel does nothing. */
    private void exportCsv(String defaultFileName, String content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar informe CSV");
        chooser.setInitialFileName(defaultFileName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"));
        File file = chooser.showSaveDialog(stockLotsTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            // UTF-8 keeps the accents and the BOM makes Excel open the columns.
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            showExported(file);
        } catch (IOException e) {
            showExportError(file, e);
        }
    }

    private void showExported(File file) {
        showAlert(Alert.AlertType.INFORMATION, "Informe exportado",
                "Se ha guardado el informe en:\n" + file.getAbsolutePath());
    }

    private void showExportError(File file, IOException e) {
        LOGGER.log(Level.SEVERE, "Could not export the report to {0}", file);
        LOGGER.log(Level.FINE, "Export failure", e);
        showAlert(Alert.AlertType.ERROR, "No se pudo guardar el informe",
                "Comprueba que tienes permisos de escritura en la carpeta elegida.");
    }

    private void showAlert(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle("Monte ERP");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static String formatMoney(BigDecimal amount) {
        return amount == null ? "" : String.format(Locale.ROOT, "%.2f €",
                amount.setScale(2, RoundingMode.HALF_UP));
    }
}
