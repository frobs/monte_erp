package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;

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
import java.sql.SQLException;
import java.util.List;
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
 * <p>Every line generates a {@link Stock} lot that stays unavailable until the
 * purchase is received (decision D1/D6): the total is computed from the lots
 * (decision D5) and only one lot per product is allowed (decision D2), rejected
 * with a message. The purchase is saved as {@code pending} with the signed-in
 * user and can then move through the status pipeline {@code pending → received →
 * returned} (or {@code pending → cancelled}) with {@link #changeStatus(String)},
 * the only mutation of the screen. The screen only <strong>writes</strong> lots:
 * reading the stock of a product, the alerts and the FIFO allocation belong to
 * RF-09/RF-10/RF-15 (decision D4).</p>
 *
 * <p>Like the CRUD ViewModels it is free of ValidatorFX: the per-field rules are
 * exposed as one error property per field, so it stays testable with plain JUnit
 * and no JavaFX toolkit.</p>
 */
public class PurchasesViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(PurchasesViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";
    private static final String SESSION_EXPIRED = "La sesión ya no está activa. Inicia sesión de nuevo.";
    private static final String SAVE_RELOAD_ERROR =
            "Compra #%d guardada, pero no se pudo actualizar el historial. Pulsa 'Actualizar'.";
    private static final BigDecimal MAX_STORED_TOTAL = new BigDecimal("99999999.99");
    private static final String TOTAL_LIMIT_ERROR =
            "El total de la compra no puede superar 99.999.999,99 €.";

    /** No supplier chosen when saving a purchase. */
    protected static final String SUPPLIER_REQUIRED = "Selecciona un proveedor.";

    /** No product chosen when adding a lot. */
    protected static final String PRODUCT_REQUIRED = "Selecciona un producto.";

    /** Quantity is empty, not an integer or not positive. */
    protected static final String QUANTITY_INVALID =
            "La cantidad debe ser un número entero mayor que 0.";

    /** Purchase price is empty, not a number or negative. */
    protected static final String PRICE_INVALID =
            "El precio de compra debe ser un número mayor o igual que 0.";

    /** A product can only appear once in the purchase (decision D2). */
    protected static final String DUPLICATED_PRODUCT = "El producto ya está en la compra.";

    /** A purchase needs at least one lot to be saved. */
    protected static final String LOTS_REQUIRED = "Añade al menos un lote a la compra.";

    /** No purchase selected to change its status. */
    protected static final String PURCHASE_REQUIRED = "Selecciona una compra.";

    /** The transition is not allowed by the domain (origin, target). */
    protected static final String INVALID_TRANSITION =
            "La compra no puede pasar de \"%s\" a \"%s\".";

    /** The optimistic guard did not match; someone else moved the purchase. */
    protected static final String CONFLICT =
            "La compra ha cambiado desde que se cargó; se ha actualizado el historial.";

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final User currentUser;
    private final SessionValidator sessionValidator;

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final ObjectProperty<Supplier> selectedSupplier = new SimpleObjectProperty<>();
    private final ObjectProperty<Product> selectedProduct = new SimpleObjectProperty<>();
    private final StringProperty quantity = new SimpleStringProperty("");
    private final StringProperty purchasePrice = new SimpleStringProperty("");

    private final ObservableList<Stock> draftLots = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<BigDecimal> purchaseTotal =
            new ReadOnlyObjectWrapper<>(BigDecimal.ZERO);

    private final ObservableList<Purchase> purchases = FXCollections.observableArrayList();
    private final ObjectProperty<Purchase> selectedPurchase = new SimpleObjectProperty<>();
    private final ObservableList<Stock> selectedPurchaseLots = FXCollections.observableArrayList();

    /** Statuses reachable from the selected purchase, in button order. */
    private final ObservableList<String> availableTransitions = FXCollections.observableArrayList();

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyStringWrapper supplierError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper productError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper quantityError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper priceError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper lotsError = new ReadOnlyStringWrapper("");

    public PurchasesViewModel(PurchaseRepository purchaseRepository,
                              SupplierRepository supplierRepository,
                              ProductRepository productRepository,
                              User currentUser) {
        this(purchaseRepository, supplierRepository, productRepository, currentUser, null);
    }

    public PurchasesViewModel(PurchaseRepository purchaseRepository,
                              SupplierRepository supplierRepository,
                              ProductRepository productRepository,
                              User currentUser,
                              SessionValidator sessionValidator) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.currentUser = currentUser;
        this.sessionValidator = sessionValidator;
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
    public ObservableList<Stock> draftLots() {
        return draftLots;
    }

    /** Total of the draft purchase, recomputed from the lots (decision D5). */
    public ReadOnlyObjectProperty<BigDecimal> purchaseTotalProperty() {
        return purchaseTotal.getReadOnlyProperty();
    }

    /** Read-only history of saved purchases, most recent first. */
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

    /**
     * Statuses the selected purchase can move to, in button order. Empty when
     * there is no selection or the purchase is in a terminal state, so the view
     * can bind the visibility of each action button to it.
     */
    public ObservableList<String> availableTransitions() {
        return availableTransitions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
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

    /** Loads the combos and the history, and resets the draft purchase. */
    public void load() {
        errorMessage.set("");
        statusMessage.set("");
        if (!ensureActiveSession()) {
            return;
        }
        try {
            suppliers.setAll(supplierRepository.findActive());
            products.setAll(productRepository.findAll());
            purchases.setAll(purchaseRepository.findAll());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purchases", e);
            fail(DATABASE_ERROR);
            return;
        }
        resetDraft();
        // The history is reloaded, so a previously selected purchase is no longer
        // guaranteed to be in the table: drop the selection and its detail.
        selectedPurchase.set(null);
        selectedPurchaseLots.clear();
        availableTransitions.clear();
        clearFieldErrors();
    }

    /**
     * Adds the chosen product to the draft purchase as a new lot with the given
     * quantity and purchase price. Validates the product, the quantity, the price
     * and the "one lot per product" rule (D2).
     */
    public void addLot() {
        errorMessage.set("");
        statusMessage.set("");
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

        if (product != null && containsProduct(product)) {
            productError.set(DUPLICATED_PRODUCT);
        }

        if (hasFieldErrors()) {
            fail(joinedErrors(productError.get(), quantityError.get(), priceError.get()));
            return;
        }

        Stock lot = new Stock(product, parsedQuantity, parsedPrice);
        BigDecimal nextTotal = calculateTotal().add(lot.subtotal());
        if (nextTotal.compareTo(MAX_STORED_TOTAL) > 0) {
            fail(TOTAL_LIMIT_ERROR);
            return;
        }
        draftLots.add(lot);
        purchaseTotal.set(nextTotal);
        clearFieldErrors();
        selectedProduct.set(null);
        quantity.set("");
        purchasePrice.set("");
        succeed("Lote añadido.");
    }

    /** Removes a lot from the draft purchase and recomputes the total. */
    public void removeLot(Stock lot) {
        if (lot != null && draftLots.remove(lot)) {
            recomputeTotal();
        }
    }

    /** Clears the draft purchase so a brand new one can be typed in. */
    public void newPurchase() {
        errorMessage.set("");
        statusMessage.set("");
        resetDraft();
        availableTransitions.clear();
        clearFieldErrors();
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
        clearFieldErrors();
        if (selectedSupplier.get() == null) {
            supplierError.set(SUPPLIER_REQUIRED);
        }
        if (draftLots.isEmpty()) {
            lotsError.set(LOTS_REQUIRED);
        }
        if (hasFieldErrors()) {
            fail(joinedErrors(supplierError.get(), lotsError.get()));
            return;
        }
        if (calculateTotal().compareTo(MAX_STORED_TOTAL) > 0) {
            fail(TOTAL_LIMIT_ERROR);
            return;
        }

        Purchase purchase = new Purchase(selectedSupplier.get(), currentUser, draftLots);
        int id;
        try {
            id = purchaseRepository.insert(purchase);
        } catch (InactiveEntityException e) {
            fail(e.getMessage());
            return;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the purchase", e);
            fail(DATABASE_ERROR);
            return;
        }

        // The insert is committed at this point: discard the draft before the
        // independent history read so a reload failure cannot cause a duplicate.
        resetDraft();
        clearFieldErrors();
        try {
            purchases.setAll(purchaseRepository.findAll());
            succeed("Compra #" + id + " guardada correctamente.");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Purchase was saved but its history could not be reloaded", e);
            fail(String.format(SAVE_RELOAD_ERROR, id));
        }
    }

    /** Loads the lots of the selected purchase; a {@code null} purchase clears them. */
    public void selectPurchase(Purchase purchase) {
        if (!ensureActiveSession()) {
            return;
        }
        updateAvailableTransitions(purchase);
        if (purchase == null) {
            selectedPurchaseLots.clear();
            return;
        }
        try {
            selectedPurchaseLots.setAll(purchaseRepository.findLots(purchase.getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purchase lots", e);
            selectedPurchaseLots.clear();
            fail(DATABASE_ERROR);
        }
    }

    /**
     * Moves the selected purchase to {@code newStatus}. The transition is
     * validated in the domain first, then persisted with the optimistic guard;
     * a 0-row update means someone else moved the purchase in the meantime, so
     * the history is reloaded and the user is warned instead of overwriting it.
     * A return rejected because part of the stock was already sold is reported
     * with the message of {@link PurchaseStockConsumedException}.
     */
    public void changeStatus(String newStatus) {
        if (!ensureActiveSession()) {
            return;
        }
        clearFieldErrors();
        Purchase purchase = selectedPurchase.get();
        if (purchase == null) {
            fail(PURCHASE_REQUIRED);
            return;
        }
        if (!purchase.canTransitionTo(newStatus)) {
            fail(String.format(INVALID_TRANSITION, Purchase.labelFor(purchase.getStatus()),
                    Purchase.labelFor(newStatus)));
            return;
        }
        try {
            int updated = purchaseRepository.changeStatus(purchase.getId(),
                    purchase.getStatus(), newStatus);
            if (updated == 0) {
                purchases.setAll(purchaseRepository.findAll());
                fail(CONFLICT);
                return;
            }
            purchase.changeStatus(newStatus);
            purchases.setAll(purchaseRepository.findAll());
            Purchase reloaded = findById(purchase.getId());
            selectedPurchase.set(reloaded);
            selectPurchase(reloaded);
            succeed("Compra #" + purchase.getId() + " marcada como "
                    + Purchase.labelFor(newStatus) + ".");
        } catch (PurchaseStockConsumedException e) {
            fail(e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not change the purchase status", e);
            fail(DATABASE_ERROR);
        }
    }

    private void updateAvailableTransitions(Purchase purchase) {
        if (purchase == null) {
            availableTransitions.clear();
        } else {
            availableTransitions.setAll(purchase.allowedTransitions());
        }
    }

    private Purchase findById(int id) {
        return purchases.stream()
                .filter(purchase -> purchase.getId() == id)
                .findFirst()
                .orElse(null);
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

    private boolean containsProduct(Product product) {
        return draftLots.stream()
                .anyMatch(lot -> lot.getProduct() != null
                        && lot.getProduct().getId() == product.getId());
    }

    private BigDecimal calculateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (Stock lot : draftLots) {
            total = total.add(lot.subtotal());
        }
        return total;
    }

    private void recomputeTotal() {
        purchaseTotal.set(calculateTotal());
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
            fail(DATABASE_ERROR);
            return false;
        }
        purchases.clear();
        selectedPurchase.set(null);
        selectedPurchaseLots.clear();
        availableTransitions.clear();
        resetDraft();
        fail(SESSION_EXPIRED);
        return false;
    }

    private void resetDraft() {
        selectedSupplier.set(null);
        selectedProduct.set(null);
        quantity.set("");
        purchasePrice.set("");
        draftLots.clear();
        purchaseTotal.set(BigDecimal.ZERO);
    }

    private void clearFieldErrors() {
        supplierError.set("");
        productError.set("");
        quantityError.set("");
        priceError.set("");
        lotsError.set("");
    }

    private boolean hasFieldErrors() {
        return !supplierError.get().isBlank() || !productError.get().isBlank()
                || !quantityError.get().isBlank() || !priceError.get().isBlank()
                || !lotsError.get().isBlank();
    }

    private void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    private void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    private static String joinedErrors(String... messages) {
        return List.of(messages).stream()
                .filter(message -> !message.isBlank())
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
    }
}
