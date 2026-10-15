package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link SalesOrder} (RF-07 / RF-13).
 */
class SalesOrderTest {

    private static Product product(int id, String name, String price) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal(price), 0, null, true);
        product.setId(id);
        return product;
    }

    private static SalesOrderLine line(int quantity, String price, Product product) {
        return new SalesOrderLine(quantity, new BigDecimal(price), product);
    }

    private static Customer customer() {
        return new Customer(1, "Acme", null, null, null);
    }

    private static User user() {
        return new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
    }

    @Test
    @DisplayName("calculateTotal suma los subtotales de varias líneas (RF-13)")
    void calculateTotalSumsLines() {
        SalesOrder order = new SalesOrder(customer(), user(), List.of(
                line(2, "10.00", product(1, "Teclado", "10.00")),
                line(3, "5.50", product(2, "Ratón", "5.50"))));

        assertEquals(0, new BigDecimal("36.50").compareTo(order.calculateTotal()));
    }

    @Test
    @DisplayName("calculateTotal sin líneas es 0")
    void calculateTotalWithoutLinesIsZero() {
        SalesOrder order = new SalesOrder(customer(), user(), List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(order.calculateTotal()));
    }

    @Test
    @DisplayName("El constructor de creación fija estado pending, usuario, fecha y total")
    void creationConstructorSetsPendingState() {
        User author = user();
        SalesOrder order = new SalesOrder(customer(), author, List.of(
                line(2, "10.00", product(1, "Teclado", "10.00"))));

        assertEquals(SalesOrder.STATUS_PENDING, order.getStatus());
        assertEquals("Pendiente", order.getStatusLabel());
        assertSame(author, order.getUser());
        assertNotNull(order.getOrderDate());
        assertEquals(0, new BigDecimal("20.00").compareTo(order.getTotal()));
    }

    @Test
    @DisplayName("getStatusLabel traduce los cuatro estados")
    void statusLabelsAreTranslated() {
        assertEquals("Pendiente", label(SalesOrder.STATUS_PENDING));
        assertEquals("Enviado", label(SalesOrder.STATUS_SHIPPED));
        assertEquals("Entregado", label(SalesOrder.STATUS_DELIVERED));
        assertEquals("Cancelado", label(SalesOrder.STATUS_CANCELLED));
    }

    private static String label(String status) {
        SalesOrder order = new SalesOrder();
        order.setStatus(status);
        return order.getStatusLabel();
    }
}
