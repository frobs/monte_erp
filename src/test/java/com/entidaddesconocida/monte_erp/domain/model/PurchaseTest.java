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
        return new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
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

        assertEquals(Purchase.STATUS_PENDING, purchase.getStatus());
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
    @DisplayName("getStatusLabel y labelFor traducen los estados y dejan el crudo si es desconocido")
    void statusLabelsAreTranslated() {
        assertEquals("Pendiente", Purchase.labelFor(Purchase.STATUS_PENDING));
        assertEquals("Recibida", Purchase.labelFor(Purchase.STATUS_RECEIVED));
        assertEquals("Cancelada", Purchase.labelFor(Purchase.STATUS_CANCELLED));
        assertEquals("Devuelta", Purchase.labelFor(Purchase.STATUS_RETURNED));
        assertEquals("desconocido", Purchase.labelFor("desconocido"));
        assertEquals("null", Purchase.labelFor(null));
    }

    private static Purchase purchaseWithStatus(String status) {
        return new Purchase(1, LocalDateTime.now(), status, BigDecimal.ZERO,
                supplier(), user(), List.of());
    }

    @Test
    @DisplayName("allowedTransitions: pendiente puede recibirse o cancelarse")
    void pendingTransitions() {
        assertEquals(List.of(Purchase.STATUS_RECEIVED, Purchase.STATUS_CANCELLED),
                purchaseWithStatus(Purchase.STATUS_PENDING).allowedTransitions());
    }

    @Test
    @DisplayName("allowedTransitions: una compra recibida solo puede devolverse, nunca cancelarse")
    void receivedTransitionsCannotCancel() {
        Purchase purchase = purchaseWithStatus(Purchase.STATUS_RECEIVED);

        assertEquals(List.of(Purchase.STATUS_RETURNED), purchase.allowedTransitions());
        assertFalse(purchase.canTransitionTo(Purchase.STATUS_CANCELLED));
        assertTrue(purchase.canTransitionTo(Purchase.STATUS_RETURNED));
    }

    @Test
    @DisplayName("allowedTransitions vacío para los estados terminales")
    void terminalTransitionsAreEmpty() {
        for (String status : List.of(Purchase.STATUS_CANCELLED, Purchase.STATUS_RETURNED)) {
            Purchase purchase = purchaseWithStatus(status);
            assertEquals(List.of(), purchase.allowedTransitions(), "estado: " + status);
            assertTrue(purchase.isTerminal(), "estado: " + status);
        }
    }

    @Test
    @DisplayName("isReturnedOrCancelled marca los estados que ya no son stock (RF-12)")
    void returnedOrCancelledAreNoLongerStock() {
        assertFalse(purchaseWithStatus(Purchase.STATUS_PENDING).isReturnedOrCancelled());
        assertFalse(purchaseWithStatus(Purchase.STATUS_RECEIVED).isReturnedOrCancelled());
        assertTrue(purchaseWithStatus(Purchase.STATUS_RETURNED).isReturnedOrCancelled());
        assertTrue(purchaseWithStatus(Purchase.STATUS_CANCELLED).isReturnedOrCancelled());
    }

    @Test
    @DisplayName("canTransitionTo rechaza repetición, retroceso, destino desconocido y null")
    void canTransitionToRejectsInvalidTargets() {
        Purchase pending = purchaseWithStatus(Purchase.STATUS_PENDING);
        Purchase received = purchaseWithStatus(Purchase.STATUS_RECEIVED);

        assertFalse(pending.canTransitionTo(Purchase.STATUS_PENDING));
        assertFalse(pending.canTransitionTo(Purchase.STATUS_RETURNED));
        assertFalse(received.canTransitionTo(Purchase.STATUS_PENDING));
        assertFalse(received.canTransitionTo("desconocido"));
        assertFalse(received.canTransitionTo(null));
    }

    @Test
    @DisplayName("changeStatus mueve la compra por el pipeline válido")
    void changeStatusFollowsThePipeline() {
        Purchase purchase = purchaseWithStatus(Purchase.STATUS_PENDING);

        purchase.changeStatus(Purchase.STATUS_RECEIVED);
        assertEquals(Purchase.STATUS_RECEIVED, purchase.getStatus());

        purchase.changeStatus(Purchase.STATUS_RETURNED);
        assertEquals(Purchase.STATUS_RETURNED, purchase.getStatus());
    }

    @Test
    @DisplayName("changeStatus rechaza cancelar una compra recibida con un mensaje explícito")
    void changeStatusRejectsCancellingAReceivedPurchase() {
        Purchase purchase = purchaseWithStatus(Purchase.STATUS_RECEIVED);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> purchase.changeStatus(Purchase.STATUS_CANCELLED));

        assertEquals("Transición de estado no permitida: \"received\" → \"cancelled\".",
                error.getMessage());
        assertEquals(Purchase.STATUS_RECEIVED, purchase.getStatus());
    }

    @Test
    @DisplayName("Una compra terminal no puede volver a cambiar de estado")
    void terminalPurchaseCannotChangeAgain() {
        Purchase purchase = purchaseWithStatus(Purchase.STATUS_RETURNED);

        assertThrows(IllegalStateException.class,
                () -> purchase.changeStatus(Purchase.STATUS_RECEIVED));
        assertEquals(Purchase.STATUS_RETURNED, purchase.getStatus());
    }

    @Test
    @DisplayName("getLots nunca es null (ni con null en el constructor de mapeo)")
    void getLotsIsNeverNull() {
        Purchase purchase = new Purchase(1, LocalDateTime.now(), Purchase.STATUS_RECEIVED,
                BigDecimal.ZERO, supplier(), user(), null);

        assertNotNull(purchase.getLots());
        assertTrue(purchase.getLots().isEmpty());

        purchase.setLots(null);
        assertNotNull(purchase.getLots());
    }
}
