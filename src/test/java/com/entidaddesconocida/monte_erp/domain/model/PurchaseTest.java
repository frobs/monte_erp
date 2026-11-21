package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Purchase} (RF-14 / CU-10): derived total, status pipeline
 * ({@code pending → received → returned/cancelled}) and status labels.
 */
class PurchaseTest {

    private static Product product(int id, String name) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal("1.00"), 0, null, true);
        product.setId(id);
        return product;
    }

    private static Stock lot(int id, int quantity, String price) {
        return new Stock(product(id, "Producto " + id), quantity, new BigDecimal(price));
    }

    private static Supplier supplier() {
        return new Supplier(1, "Acme", null, null, null, true);
    }

    private static User user() {
        return new User("Bea", "bea", "hash", Role.EMPLOYEE, true);
    }

    @Test
    @DisplayName("calculateTotal suma los subtotales de varios lotes (D5)")
    void calculateTotalSumsLots() {
        Purchase purchase = new Purchase(supplier(), user(), List.of(
                lot(1, 2, "10.00"),
                lot(2, 3, "5.50")));

        assertEquals(0, new BigDecimal("36.50").compareTo(purchase.calculateTotal()));
    }

    @Test
    @DisplayName("calculateTotal sin lotes es 0")
    void calculateTotalWithoutLotsIsZero() {
        Purchase purchase = new Purchase(supplier(), user(), List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(purchase.calculateTotal()));
    }

    @Test
    @DisplayName("El constructor de creación fija estado pending, usuario, fecha y total")
    void creationConstructorSetsPendingState() {
        User author = user();
        Supplier supplier = supplier();
        Purchase purchase = new Purchase(supplier, author, List.of(lot(1, 2, "10.00")));

        assertEquals(PurchaseStatus.PENDING, purchase.getStatus());
        assertEquals("Pendiente", purchase.getStatusLabel());
        assertSame(author, purchase.getUser());
        assertSame(supplier, purchase.getSupplier());
        assertNotNull(purchase.getPurchaseDate());
        assertEquals(0, new BigDecimal("20.00").compareTo(purchase.getTotal()));
    }

    @Test
    @DisplayName("El alta enlaza cada lote con la compra")
    void creationLinksEachLotBackToThePurchase() {
        Stock first = lot(1, 1, "10.00");
        Stock second = lot(2, 1, "5.00");

        Purchase purchase = new Purchase(supplier(), user(), List.of(first, second));

        assertSame(purchase, first.getPurchase());
        assertSame(purchase, second.getPurchase());
    }

    @Test
    @DisplayName("getStatusLabel traduce los estados y el enum expone su valor de base de datos")
    void statusLabelsAreTranslated() {
        assertEquals("Pendiente", statusLabelOf(PurchaseStatus.PENDING));
        assertEquals("Recibida", statusLabelOf(PurchaseStatus.RECEIVED));
        assertEquals("Cancelada", statusLabelOf(PurchaseStatus.CANCELLED));
        assertEquals("Devuelta", statusLabelOf(PurchaseStatus.RETURNED));
        assertEquals("pending", PurchaseStatus.PENDING.dbValue());
        assertEquals("received", PurchaseStatus.RECEIVED.dbValue());
        assertEquals("cancelled", PurchaseStatus.CANCELLED.dbValue());
        assertEquals("returned", PurchaseStatus.RETURNED.dbValue());
    }

    private static String statusLabelOf(PurchaseStatus status) {
        return purchaseWithStatus(status).getStatusLabel();
    }

    private static Purchase purchaseWithStatus(PurchaseStatus status) {
        return new Purchase(1, LocalDateTime.now(), status, BigDecimal.ZERO,
                supplier(), user(), List.of());
    }

    @Test
    @DisplayName("allowedTransitions: pendiente puede recibirse o cancelarse")
    void pendingTransitions() {
        assertEquals(List.of(PurchaseStatus.RECEIVED, PurchaseStatus.CANCELLED),
                purchaseWithStatus(PurchaseStatus.PENDING).allowedTransitions());
    }

    @Test
    @DisplayName("allowedTransitions: una compra recibida solo puede devolverse, nunca cancelarse")
    void receivedTransitionsCannotCancel() {
        Purchase purchase = purchaseWithStatus(PurchaseStatus.RECEIVED);

        assertEquals(List.of(PurchaseStatus.RETURNED), purchase.allowedTransitions());
        assertFalse(purchase.canTransitionTo(PurchaseStatus.CANCELLED));
        assertTrue(purchase.canTransitionTo(PurchaseStatus.RETURNED));
    }

    @Test
    @DisplayName("allowedTransitions vacío para los estados terminales")
    void terminalTransitionsAreEmpty() {
        for (PurchaseStatus status : List.of(PurchaseStatus.CANCELLED, PurchaseStatus.RETURNED)) {
            Purchase purchase = purchaseWithStatus(status);
            assertEquals(List.of(), purchase.allowedTransitions(), "estado: " + status);
            assertTrue(purchase.isTerminal(), "estado: " + status);
        }
    }

    @Test
    @DisplayName("isReturnedOrCancelled marca los estados que ya no son stock (RF-12)")
    void returnedOrCancelledAreNoLongerStock() {
        assertFalse(purchaseWithStatus(PurchaseStatus.PENDING).isReturnedOrCancelled());
        assertFalse(purchaseWithStatus(PurchaseStatus.RECEIVED).isReturnedOrCancelled());
        assertTrue(purchaseWithStatus(PurchaseStatus.RETURNED).isReturnedOrCancelled());
        assertTrue(purchaseWithStatus(PurchaseStatus.CANCELLED).isReturnedOrCancelled());
    }

    @Test
    @DisplayName("canTransitionTo rechaza repetición, retroceso, destino desconocido y null")
    void canTransitionToRejectsInvalidTargets() {
        Purchase pending = purchaseWithStatus(PurchaseStatus.PENDING);
        Purchase received = purchaseWithStatus(PurchaseStatus.RECEIVED);

        assertFalse(pending.canTransitionTo(PurchaseStatus.PENDING));
        assertFalse(pending.canTransitionTo(PurchaseStatus.RETURNED));
        assertFalse(received.canTransitionTo(PurchaseStatus.PENDING));
        assertFalse(received.canTransitionTo(null));
    }

    @Test
    @DisplayName("changeStatus mueve la compra por el pipeline válido")
    void changeStatusFollowsThePipeline() {
        Purchase purchase = purchaseWithStatus(PurchaseStatus.PENDING);

        purchase.changeStatus(PurchaseStatus.RECEIVED);
        assertEquals(PurchaseStatus.RECEIVED, purchase.getStatus());

        purchase.changeStatus(PurchaseStatus.RETURNED);
        assertEquals(PurchaseStatus.RETURNED, purchase.getStatus());
    }

    @Test
    @DisplayName("changeStatus rechaza cancelar una compra recibida con un mensaje explícito")
    void changeStatusRejectsCancellingAReceivedPurchase() {
        Purchase purchase = purchaseWithStatus(PurchaseStatus.RECEIVED);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> purchase.changeStatus(PurchaseStatus.CANCELLED));

        assertEquals("Transición de estado no permitida: \"received\" → \"cancelled\".",
                error.getMessage());
        assertEquals(PurchaseStatus.RECEIVED, purchase.getStatus());
    }

    @Test
    @DisplayName("Una compra terminal no puede volver a cambiar de estado")
    void terminalPurchaseCannotChangeAgain() {
        Purchase purchase = purchaseWithStatus(PurchaseStatus.RETURNED);

        assertThrows(IllegalStateException.class,
                () -> purchase.changeStatus(PurchaseStatus.RECEIVED));
        assertEquals(PurchaseStatus.RETURNED, purchase.getStatus());
    }

    @Test
    @DisplayName("getLots nunca es null (ni con null en el constructor de mapeo)")
    void getLotsIsNeverNull() {
        Purchase purchase = new Purchase(1, LocalDateTime.now(), PurchaseStatus.RECEIVED,
                BigDecimal.ZERO, supplier(), user(), null);

        assertNotNull(purchase.getLots());
        assertTrue(purchase.getLots().isEmpty());

        purchase.setLots(null);
        assertNotNull(purchase.getLots());
    }

    @Test
    @DisplayName("getLots es de solo lectura: no se puede alterar el agregado por la puerta de atrás")
    void getLotsIsReadOnly() {
        Purchase purchase = new Purchase(supplier(), user(), List.of(lot(1, 2, "10.00")));

        List<Stock> lots = purchase.getLots();

        assertThrows(UnsupportedOperationException.class, lots::clear);
        assertThrows(UnsupportedOperationException.class, () -> lots.add(null));
        assertThrows(UnsupportedOperationException.class, () -> lots.remove(0));
        assertEquals(1, purchase.getLots().size());
    }

    @Test
    @DisplayName("setLots sigue copiando y el total se recalcula sobre la lista nueva")
    void setLotsCopiesAndFeedsCalculateTotal() {
        Purchase purchase = new Purchase(supplier(), user(), List.of());

        purchase.setLots(List.of(lot(1, 2, "10.00")));

        assertEquals(0, new BigDecimal("20.00").compareTo(purchase.calculateTotal()));
        assertThrows(UnsupportedOperationException.class,
                () -> purchase.getLots().add(lot(2, 1, "10.00")));
    }
}
