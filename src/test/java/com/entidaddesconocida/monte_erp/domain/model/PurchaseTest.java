package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Purchase} (RF-14 / CU-10): derived total, {@code received}
 * state (decision D1) and status labels.
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
    @DisplayName("El constructor de creación fija estado received, usuario, fecha y total")
    void creationConstructorSetsReceivedState() {
        User author = user();
        Supplier supplier = supplier();
        Purchase purchase = new Purchase(supplier, author, List.of(lot(1, 2, "10.00")));

        assertEquals(Purchase.STATUS_RECEIVED, purchase.getStatus());
        assertEquals("Recibida", purchase.getStatusLabel());
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
        assertEquals("desconocido", Purchase.labelFor("desconocido"));
        assertEquals("null", Purchase.labelFor(null));
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
