package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

import javafx.beans.property.ObjectProperty;
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

/**
 * Draft of the purchase being typed in the purchase screen (RF-14 / CU-10,
 * total from decision D5): combos, chosen supplier and the lots.
 *
 * <p>Extracted from {@link PurchasesViewModel} (A2). It owns the rules of the
 * draft — one lot per product (D2), quantity and purchase price, total always
 * derived from the lots and the limit of the stored money column — while the
 * ViewModel keeps the insert, the history and the status pipeline.</p>
 *
 * <p>The screen only <strong>writes</strong> lots: reading the stock of a
 * product, the alerts and the FIFO allocation belong to RF-09/RF-10/RF-15
 * (decision D4), so adding a lot never queries the database and stays on the UI
 * thread.</p>
 *
 * <p>Free of ValidatorFX: the per-field rules are exposed as one error property
 * per field, so it stays testable with plain JUnit and no JavaFX toolkit.</p>
 */
public final class PurchaseDraft {

    /** Largest total the {@code DECIMAL(10,2)} column can store. */
    static final BigDecimal MAX_STORED_TOTAL = new BigDecimal("99999999.99");

    /** Shown when the resulting total does not fit in the stored column. */
    static final String TOTAL_LIMIT_ERROR =
            "El total de la compra no puede superar 99.999.999,99 €.";

    /** No supplier chosen when saving a purchase. */
    static final String SUPPLIER_REQUIRED = "Selecciona un proveedor.";

    /** No product chosen when adding a lot. */
    static final String PRODUCT_REQUIRED = "Selecciona un producto.";

    /** Quantity is empty, not an integer or not positive. */
    static final String QUANTITY_INVALID =
            "La cantidad debe ser un número entero mayor que 0.";

    /** Purchase price is empty, not a number or negative. */
    static final String PRICE_INVALID =
            "El precio de compra debe ser un número mayor o igual que 0.";

    /** A product can only appear once in the purchase (decision D2). */
    static final String DUPLICATED_PRODUCT = "El producto ya está en la compra.";

    /** A purchase needs at least one lot to be saved. */
    static final String LOTS_REQUIRED = "Añade al menos un lote a la compra.";

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final Feedback feedback;

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final ObjectProperty<Supplier> selectedSupplier = new SimpleObjectProperty<>();
    private final ObjectProperty<Product> selectedProduct = new SimpleObjectProperty<>();
    private final StringProperty quantity = new SimpleStringProperty("");
    private final StringProperty purchasePrice = new SimpleStringProperty("");

    private final DraftEditor<Stock> draft = new DraftEditor<>(MAX_STORED_TOTAL,
            lot -> lot.getProduct() == null ? -1 : lot.getProduct().getId(),
            Stock::subtotal);

    private final ReadOnlyStringWrapper supplierError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper productError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper quantityError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper priceError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper lotsError = new ReadOnlyStringWrapper("");

    public PurchaseDraft(SupplierRepository supplierRepository,
            ProductRepository productRepository, Feedback feedback) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.feedback = feedback;
    }

    /** Loads the combos offered by the form (active suppliers, every product). */
    public void loadCombos() throws SQLException {
        suppliers.setAll(supplierRepository.findActive());
        products.setAll(productRepository.findAll());
    }

    /** Active suppliers offered in the combo (an inactive one cannot be assigned). */
    public ObservableList<Supplier> suppliers() {
        return suppliers;
    }

    /** Products offered in the combo. */
    public ObservableList<Product> products() {
        return products;
    }

    public ObjectProperty<Supplier> selectedSupplierProperty() {
        return selectedSupplier;
    }

    public ObjectProperty<Product> selectedProductProperty() {
        return selectedProduct;
    }

    /** Quantity typed by the user; parsed in {@link #addLot()}. */
    public StringProperty quantityProperty() {
        return quantity;
    }

    /** Purchase price typed by the user; parsed in {@link #addLot()}. */
    public StringProperty purchasePriceProperty() {
        return purchasePrice;
    }

    /** Lots of the purchase being drafted. */
    public ObservableList<Stock> lots() {
        return draft.lines();
    }

    /** Total of the draft purchase, recomputed from the lots (decision D5). */
    public ReadOnlyObjectProperty<BigDecimal> totalProperty() {
        return draft.totalProperty();
    }

    public ReadOnlyStringProperty supplierErrorProperty() {
        return supplierError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty productErrorProperty() {
        return productError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty quantityErrorProperty() {
        return quantityError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty priceErrorProperty() {
        return priceError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty lotsErrorProperty() {
        return lotsError.getReadOnlyProperty();
    }

    /**
     * Drops the draft so a brand new purchase can be typed in. It does not clear
     * the messages nor the field errors: the owner decided when to show them.
     */
    public void reset() {
        selectedSupplier.set(null);
        selectedProduct.set(null);
        quantity.set("");
        purchasePrice.set("");
        draft.clear();
    }

    /** Clears every per-field error. */
    public void clearFieldErrors() {
        supplierError.set("");
        productError.set("");
        quantityError.set("");
        priceError.set("");
        lotsError.set("");
    }

    /**
     * Validates the fields the save needs: a chosen supplier and at least one
     * lot. Every error is published in its own property.
     *
     * @return the summary to show, or the empty string when the draft is valid
     */
    public String validateForSave() {
        clearFieldErrors();
        if (selectedSupplier.get() == null) {
            supplierError.set(SUPPLIER_REQUIRED);
        }
        if (draft.isEmpty()) {
            lotsError.set(LOTS_REQUIRED);
        }
        return hasFieldErrors() ? ViewMessages.join(supplierError.get(), lotsError.get()) : "";
    }

    /** Whether the stored total still fits in the money column (defensive check on save). */
    public boolean totalWithinLimit() {
        return draft.totalWithinLimit();
    }

    /** Builds the purchase to persist from the current draft (header and lots). */
    public Purchase buildPurchase(User user) {
        return new Purchase(selectedSupplier.get(), user, draft.lines());
    }

    /**
     * Adds the chosen product to the draft purchase as a new lot with the given
     * quantity and purchase price. Validates the product, the quantity, the price
     * and the "one lot per product" rule (D2).
     */
    public void addLot() {
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

        BigDecimal parsedPrice = parsePrice();
        if (parsedPrice == null || parsedPrice.signum() < 0) {
            priceError.set(PRICE_INVALID);
        }

        if (product != null && draft.containsProduct(product.getId())) {
            productError.set(DUPLICATED_PRODUCT);
        }

        if (hasFieldErrors()) {
            feedback.fail(ViewMessages.join(productError.get(), quantityError.get(),
                    priceError.get()));
            return;
        }

        Stock lot = new Stock(product, parsedQuantity, parsedPrice);
        if (!draft.add(lot)) {
            feedback.fail(TOTAL_LIMIT_ERROR);
            return;
        }
        clearFieldErrors();
        selectedProduct.set(null);
        quantity.set("");
        purchasePrice.set("");
        feedback.succeed("Lote añadido.");
    }

    /** Removes a lot from the draft purchase and recomputes the total. */
    public void removeLot(Stock lot) {
        draft.remove(lot);
    }

    private boolean hasFieldErrors() {
        return !supplierError.get().isBlank() || !productError.get().isBlank()
                || !quantityError.get().isBlank() || !priceError.get().isBlank()
                || !lotsError.get().isBlank();
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

    private BigDecimal parsePrice() {
        String value = purchasePrice.get() == null ? "" : purchasePrice.get().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            // The input mask accepts both '1.5' and '1,5'; normalise the Spanish
            // comma so the parsed value matches the product form (RF-03).
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
