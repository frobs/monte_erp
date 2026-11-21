package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.service.FifoAllocator;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Draft of the order being typed in the sales screen (RF-07 / CU-07, total from
 * RF-13): combos, chosen customer, lines and the stock pre-warning.
 *
 * <p>Extracted from {@link SalesOrdersViewModel} (A2) so that the ViewModel only
 * orchestrates: this class owns the draft state and its rules — one line per
 * product (D3), price snapshot when the line is added (D4), total always derived
 * from the lines (D6) and the limit of the stored money column.</p>
 *
 * <p>Stock is checked twice (RF-09/RF-15, decision D4): {@link #addLine()}
 * pre-warns with {@link StockRepository#findAvailable(int)} — off the JavaFX
 * thread — and does not add an impossible line, while the save is still the
 * authority (the repository re-reads the lots under lock and may reject the
 * order). A line is added only in the callback, so a slow stock query never
 * leaves a half-validated line; the form is cleared only when it still holds the
 * product that was checked.</p>
 *
 * <p>Free of ValidatorFX: the per-field rules are exposed as one error property
 * per field, so it stays testable with plain JUnit and no JavaFX toolkit.</p>
 */
public final class SalesOrderDraft {

    private static final Logger LOGGER = Logger.getLogger(SalesOrderDraft.class.getName());

    /** Largest total the {@code DECIMAL(10,2)} column can store. */
    static final BigDecimal MAX_STORED_TOTAL = new BigDecimal("99999999.99");

    /** Shown when the resulting total does not fit in the stored column. */
    static final String TOTAL_LIMIT_ERROR =
            "El total del pedido no puede superar 99.999.999,99 €.";

    /** No customer chosen when saving an order. */
    static final String CUSTOMER_REQUIRED = "Selecciona un cliente.";

    /** No product chosen when adding a line. */
    static final String PRODUCT_REQUIRED = "Selecciona un producto.";

    /** Quantity is empty, not an integer or not positive. */
    static final String QUANTITY_INVALID =
            "La cantidad debe ser un número entero mayor que 0.";

    /** A product can only appear once in the order (decision D3). */
    static final String DUPLICATED_PRODUCT = "El producto ya está en el pedido.";

    /** Prefix of the stock pre-warning message (RF-09, decision D4). */
    static final String STOCK_INSUFFICIENT = "Stock insuficiente";

    /** An order needs at least one line to be saved. */
    static final String LINES_REQUIRED = "Añade al menos una línea al pedido.";

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final BackgroundTasks backgroundTasks;
    private final Feedback feedback;

    /**
     * Session check of the owning ViewModel: an expired session must stop the
     * stock query, and its cleanup resets this draft.
     */
    private final BooleanSupplier sessionActive;

    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final ObjectProperty<Customer> selectedCustomer = new SimpleObjectProperty<>();
    private final ObjectProperty<Product> selectedProduct = new SimpleObjectProperty<>();
    private final StringProperty quantity = new SimpleStringProperty("");

    /** Available units of the selected product, refreshed when the selection changes (RF-09). */
    private final ReadOnlyIntegerWrapper availableStock = new ReadOnlyIntegerWrapper(0);

    private final DraftEditor<SalesOrderLine> draft = new DraftEditor<>(MAX_STORED_TOTAL,
            line -> line.getProduct() == null ? -1 : line.getProduct().getId(),
            SalesOrderLine::subtotal);

    /**
     * Guards the pre-check of {@link #addLine()}: while the stock query runs, a
     * second click must not submit it twice (it would add the line twice).
     */
    private boolean lineCheckInFlight;

    /**
     * Sequence of the available-stock queries: the response of a product the
     * user has already left behind is discarded.
     */
    private long stockGeneration;

    private final ReadOnlyStringWrapper customerError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper productError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper quantityError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper stockError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper linesError = new ReadOnlyStringWrapper("");

    /**
     * @param sessionActive check of the signed-in session, called before the
     *                      stock query and before a line is added
     */
    public SalesOrderDraft(CustomerRepository customerRepository,
            ProductRepository productRepository, StockRepository stockRepository,
            BackgroundTasks backgroundTasks, Feedback feedback, BooleanSupplier sessionActive) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.backgroundTasks = backgroundTasks;
        this.feedback = feedback;
        this.sessionActive = sessionActive;
        // The view shows the available units of the chosen product and caps the
        // quantity field with them, so the stock is refreshed on every change.
        selectedProduct.addListener((observable, previous, product) -> refreshAvailableStock(product));
    }

    /** Loads the combos offered by the form (active customers, every product). */
    public void loadCombos() throws SQLException {
        customers.setAll(customerRepository.findActive());
        products.setAll(productRepository.findAll());
    }

    /** Active customers offered in the combo (an inactive one cannot be assigned). */
    public ObservableList<Customer> customers() {
        return customers;
    }

    /** Active products offered in the combo. */
    public ObservableList<Product> products() {
        return products;
    }

    public ObjectProperty<Customer> selectedCustomerProperty() {
        return selectedCustomer;
    }

    public ObjectProperty<Product> selectedProductProperty() {
        return selectedProduct;
    }

    /** Quantity typed by the user; parsed in {@link #addLine()}. */
    public StringProperty quantityProperty() {
        return quantity;
    }

    /**
     * Available units of the product currently selected in the combo (RF-09). It
     * is {@code 0} when there is no selection, and the view uses it both to show
     * the stock and to cap what can be typed in the quantity field.
     */
    public ReadOnlyIntegerProperty availableStockProperty() {
        return availableStock.getReadOnlyProperty();
    }

    /** Lines of the order being drafted. */
    public ObservableList<SalesOrderLine> lines() {
        return draft.lines();
    }

    /** Total of the draft order, recomputed from the lines (RF-13). */
    public ReadOnlyObjectProperty<BigDecimal> totalProperty() {
        return draft.totalProperty();
    }

    public ReadOnlyStringProperty customerErrorProperty() {
        return customerError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty productErrorProperty() {
        return productError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty quantityErrorProperty() {
        return quantityError.getReadOnlyProperty();
    }

    /** Stock pre-warning of {@link #addLine()} (RF-09, decision D4). */
    public ReadOnlyStringProperty stockErrorProperty() {
        return stockError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty linesErrorProperty() {
        return linesError.getReadOnlyProperty();
    }

    /**
     * Drops the draft so a brand new order can be typed in. It does not clear
     * the messages nor the field errors: the owner decided when to show them.
     */
    public void reset() {
        selectedCustomer.set(null);
        selectedProduct.set(null);
        quantity.set("");
        draft.clear();
    }

    /** Clears every per-field error. */
    public void clearFieldErrors() {
        customerError.set("");
        productError.set("");
        quantityError.set("");
        stockError.set("");
        linesError.set("");
    }

    /**
     * Validates the fields the save needs: a chosen customer and at least one
     * line. Every error is published in its own property.
     *
     * @return the summary to show, or the empty string when the draft is valid
     */
    public String validateForSave() {
        clearFieldErrors();
        if (selectedCustomer.get() == null) {
            customerError.set(CUSTOMER_REQUIRED);
        }
        if (draft.isEmpty()) {
            linesError.set(LINES_REQUIRED);
        }
        return hasFieldErrors() ? ViewMessages.join(customerError.get(), linesError.get()) : "";
    }

    /** Whether the stored total still fits in the money column (defensive check on save). */
    public boolean totalWithinLimit() {
        return draft.totalWithinLimit();
    }

    /** Builds the order to persist from the current draft (decision D5: signed-in user). */
    public SalesOrder buildOrder(User user) {
        return new SalesOrder(selectedCustomer.get(), user, draft.lines());
    }

    /**
     * Adds the chosen product to the draft order with the given quantity and the
     * product price snapshot. Validates the product, the quantity and the "one
     * line per product" rule (D3) on the UI thread, then pre-checks the available
     * lots off it: the line is added in the callback.
     */
    public void addLine() {
        feedback.clear();
        clearFieldErrors();

        Product product = selectedProduct.get();
        if (product == null) {
            productError.set(PRODUCT_REQUIRED);
        }

        Integer parsedQuantity = parseQuantity();
        if (parsedQuantity == null || parsedQuantity <= 0) {
            quantityError.set(QUANTITY_INVALID);
        }

        if (product != null && draft.containsProduct(product.getId())) {
            productError.set(DUPLICATED_PRODUCT);
        }

        if (hasFieldErrors()) {
            feedback.fail(ViewMessages.join(productError.get(), quantityError.get()));
            return;
        }

        if (lineCheckInFlight) {
            return;
        }
        int requested = parsedQuantity;
        int productId = product.getId();
        // The pre-check reads the lots with a query, so it runs in the background
        // and the line is added once the answer arrives.
        lineCheckInFlight = true;
        backgroundTasks.submit(
                () -> FifoAllocator.availableTotal(stockRepository.findAvailable(productId)),
                available -> {
                    lineCheckInFlight = false;
                    onStockChecked(product, requested, available);
                },
                failure -> {
                    lineCheckInFlight = false;
                    LOGGER.log(Level.SEVERE, "Could not check the available stock", failure);
                    feedback.fail(ViewMessages.DATABASE_ERROR);
                });
    }

    /**
     * Adds the line once the pre-check of {@link #addLine()} confirms that the
     * available lots cover the quantity. The duplicate rule is checked again
     * because a line could have been added while the query was running.
     */
    private void onStockChecked(Product product, int requested, int available) {
        if (draft.containsProduct(product.getId())) {
            productError.set(DUPLICATED_PRODUCT);
            feedback.fail(DUPLICATED_PRODUCT);
            return;
        }
        if (available < requested) {
            String message = InsufficientStockException.formatMessage(product.getName(),
                    requested, available);
            stockError.set(message);
            feedback.fail(message);
            return;
        }
        SalesOrderLine line = new SalesOrderLine(requested, product.getPrice(), product);
        if (!draft.add(line)) {
            feedback.fail(TOTAL_LIMIT_ERROR);
            return;
        }
        clearFieldErrors();
        // The form is cleared only when it still holds what was checked: the user
        // may have chosen another product while the query was running.
        if (selectedProduct.get() == product) {
            selectedProduct.set(null);
            quantity.set("");
        }
        feedback.succeed("Línea añadida.");
    }

    /** Removes a line from the draft order and recomputes the total. */
    public void removeLine(SalesOrderLine line) {
        draft.remove(line);
    }

    private boolean hasFieldErrors() {
        return !customerError.get().isBlank() || !productError.get().isBlank()
                || !quantityError.get().isBlank() || !stockError.get().isBlank()
                || !linesError.get().isBlank();
    }

    private Integer parseQuantity() {
        String value = quantity.get() == null ? "" : quantity.get().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reloads the available units of the given product (0 for {@code null}) and
     * caps the typed quantity to it. The query runs in the background; a response
     * for a product that is no longer selected is discarded. A database failure is
     * reported as a connection error and leaves the stock at 0 so no impossible
     * line is added.
     */
    private void refreshAvailableStock(Product product) {
        long generation = ++stockGeneration;
        if (product == null) {
            availableStock.set(0);
            return;
        }
        if (!sessionActive.getAsBoolean()) {
            availableStock.set(0);
            return;
        }
        int productId = product.getId();
        backgroundTasks.submit(
                () -> FifoAllocator.availableTotal(stockRepository.findAvailable(productId)),
                available -> {
                    if (generation == stockGeneration) {
                        applyAvailableStock(available);
                    }
                },
                failure -> {
                    if (generation != stockGeneration) {
                        return;
                    }
                    LOGGER.log(Level.SEVERE, "Could not load the available stock", failure);
                    availableStock.set(0);
                    feedback.fail(ViewMessages.DATABASE_ERROR);
                });
    }

    /** Publishes the stock of the selected product and caps the typed quantity. */
    private void applyAvailableStock(int available) {
        availableStock.set(available);
        // The field mask stops the user from typing more than the stock, but a
        // product change can leave an already typed quantity above the new stock.
        Integer typed = parseQuantity();
        if (typed != null && typed > available) {
            quantity.set(available > 0 ? String.valueOf(available) : "");
        }
    }
}
