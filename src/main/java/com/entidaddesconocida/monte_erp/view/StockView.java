package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.viewmodel.StockViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Code-behind of the stock FXML (RF-09 / RF-10 / CU-08).
 *
 * <p>Master-detail read-only screen: the left table lists every product with its
 * available quantity and its minimum-stock alert, and the detail shows the lots of
 * the selected product. The rules live in {@link StockViewModel}; the view only
 * paints and forwards events, and navigation is delegated to the {@link AppShell}.</p>
 */
public class StockView implements FxmlView<StockViewModel> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppShell appShell;

    @FXML
    private TableView<StockLevel> levelsTable;

    @FXML
    private TableColumn<StockLevel, String> levelProductColumn;

    @FXML
    private TableColumn<StockLevel, String> levelSkuColumn;

    @FXML
    private TableColumn<StockLevel, String> levelMinimumColumn;

    @FXML
    private TableColumn<StockLevel, String> levelAvailableColumn;

    @FXML
    private TableColumn<StockLevel, String> levelAlertColumn;

    @FXML
    private TextField searchField;

    @FXML
    private CheckBox onlyBelowMinimumCheck;

    @FXML
    private Label alertCountLabel;

    @FXML
    private Label countLabel;

    @FXML
    private TableView<Stock> lotsTable;

    @FXML
    private TableColumn<Stock, String> lotEntryDateColumn;

    @FXML
    private TableColumn<Stock, String> lotInitialColumn;

    @FXML
    private TableColumn<Stock, String> lotAvailableColumn;

    @FXML
    private TableColumn<Stock, String> lotPurchasePriceColumn;

    @FXML
    private TableColumn<Stock, String> lotPurchaseColumn;

    @FXML
    private Label errorLabel;

    @InjectViewModel
    private StockViewModel viewModel;

    public StockView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        setupFields();
        bindSelection();
        bindMessages();
        viewModel.load();
    }

    private void setupFields() {
        levelProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getName()));
        levelSkuColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getProduct() == null ? "" : cell.getValue().getProduct().getSku()));
        levelMinimumColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getMinimumStock())));
        levelAvailableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getAvailableQuantity())));
        levelAlertColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().alertLabel()));
        // The alert stands out with the existing "error" style only when it fires.
        levelAlertColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove("error");
                StockLevel level = empty || getTableRow() == null
                        ? null : getTableRow().getItem();
                if (level != null && level.isBelowMinimum()) {
                    getStyleClass().add("error");
                }
            }
        });

        lotEntryDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getEntryDate() == null
                        ? "" : DATE_FORMAT.format(cell.getValue().getEntryDate())));
        lotInitialColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getInitialQuantity())));
        lotAvailableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getAvailableQuantity())));
        lotPurchasePriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getPurchasePrice())));
        lotPurchaseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getPurchase() == null || cell.getValue().getPurchase().getId() == 0
                        ? "" : "#" + cell.getValue().getPurchase().getId()));

        levelsTable.setItems(viewModel.filteredLevels());
        lotsTable.setItems(viewModel.selectedLots());

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        onlyBelowMinimumCheck.selectedProperty()
                .bindBidirectional(viewModel.onlyBelowMinimumProperty());
        alertCountLabel.textProperty().bind(viewModel.alertCountProperty());
        countLabel.textProperty().bind(Bindings.size(viewModel.filteredLevels()).asString("%d productos"));
    }

    private void bindSelection() {
        // TableView's selection model is read-only, so it is kept in sync with
        // the ViewModel with listeners in both directions (as in CrudView).
        levelsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> viewModel.selectedLevelProperty().set(newValue));
        viewModel.selectedLevelProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                levelsTable.getSelectionModel().clearSelection();
            } else {
                levelsTable.getSelectionModel().select(newValue);
            }
            viewModel.selectLevel(newValue);
        });
    }

    private void bindMessages() {
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
    }

    @FXML
    private void onBack() {
        appShell.showMain();
    }

    private static String formatMoney(BigDecimal amount) {
        return amount == null ? "" : String.format(Locale.ROOT, "%.2f €",
                amount.setScale(2, RoundingMode.HALF_UP));
    }
}
