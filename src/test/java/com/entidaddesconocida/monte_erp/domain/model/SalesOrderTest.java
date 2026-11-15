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
 * Unit tests for {@link SalesOrder} (RF-07 / RF-13, state machine of RF-08).
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
        return new User("Bea", "bea", "hash", Role.EMPLOYEE, true);
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

        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals("Pendiente", order.getStatusLabel());
        assertSame(author, order.getUser());
        assertNotNull(order.getOrderDate());
        assertEquals(0, new BigDecimal("20.00").compareTo(order.getTotal()));
    }

    @Test
    @DisplayName("getStatusLabel traduce los cuatro estados")
    void statusLabelsAreTranslated() {
        assertEquals("Pendiente", statusLabelOf(OrderStatus.PENDING));
        assertEquals("Enviado", statusLabelOf(OrderStatus.SHIPPED));
        assertEquals("Entregado", statusLabelOf(OrderStatus.DELIVERED));
        assertEquals("Cancelado", statusLabelOf(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("El enum expone el valor almacenado en la base de datos")
    void statusesExposeTheirDatabaseValue() {
        assertEquals("pending", OrderStatus.PENDING.dbValue());
        assertEquals("shipped", OrderStatus.SHIPPED.dbValue());
        assertEquals("delivered", OrderStatus.DELIVERED.dbValue());
        assertEquals("cancelled", OrderStatus.CANCELLED.dbValue());
        assertEquals(OrderStatus.PENDING, OrderStatus.fromDb("pending"));
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.fromDb("desconocido"));
    }

    private static String statusLabelOf(OrderStatus status) {
        return orderWithStatus(status).getStatusLabel();
    }

    // --- RF-08: status state machine ------------------------------------

    @Test
    @DisplayName("RF-08: allowedTransitions sigue el pipeline y vacía los terminales")
    void allowedTransitionsFollowThePipeline() {
        assertEquals(List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                orderWithStatus(OrderStatus.PENDING).allowedTransitions());
        assertEquals(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                orderWithStatus(OrderStatus.SHIPPED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus(OrderStatus.DELIVERED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus(OrderStatus.CANCELLED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus(null).allowedTransitions());
    }

    @Test
    @DisplayName("RF-08: allowedTransitions es inmutable")
    void allowedTransitionsIsImmutable() {
        List<OrderStatus> transitions = orderWithStatus(OrderStatus.PENDING).allowedTransitions();

        assertThrows(UnsupportedOperationException.class,
                () -> transitions.add(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("RF-08: canTransitionTo acepta el pipeline y rechaza retrocesos, terminales, repetición y null")
    void canTransitionToOnlyAllowsThePipeline() {
        SalesOrder pending = orderWithStatus(OrderStatus.PENDING);
        assertTrue(pending.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(pending.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(pending.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(pending.canTransitionTo(OrderStatus.PENDING));
        assertFalse(pending.canTransitionTo(null));

        SalesOrder shipped = orderWithStatus(OrderStatus.SHIPPED);
        assertTrue(shipped.canTransitionTo(OrderStatus.DELIVERED));
        assertTrue(shipped.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(shipped.canTransitionTo(OrderStatus.PENDING));

        assertFalse(orderWithStatus(OrderStatus.DELIVERED)
                .canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(orderWithStatus(OrderStatus.CANCELLED)
                .canTransitionTo(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("RF-08: changeStatus avanza el pipeline y actualiza la etiqueta")
    void changeStatusAdvancesThePipeline() {
        SalesOrder order = orderWithStatus(OrderStatus.PENDING);

        order.changeStatus(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals("Enviado", order.getStatusLabel());

        order.changeStatus(OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals("Entregado", order.getStatusLabel());
    }

    @Test
    @DisplayName("RF-08: changeStatus permite cancelar desde pending y desde shipped")
    void changeStatusAllowsEarlyCancellation() {
        SalesOrder pending = orderWithStatus(OrderStatus.PENDING);
        pending.changeStatus(OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, pending.getStatus());
        assertEquals("Cancelado", pending.getStatusLabel());

        SalesOrder shipped = orderWithStatus(OrderStatus.SHIPPED);
        shipped.changeStatus(OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, shipped.getStatus());
    }

    @Test
    @DisplayName("RF-08: changeStatus rechaza retrocesos, terminales, repetición y desconocidos")
    void changeStatusRejectsForbiddenTransitions() {
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.SHIPPED).changeStatus(OrderStatus.PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.DELIVERED).changeStatus(OrderStatus.SHIPPED));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.DELIVERED).changeStatus(OrderStatus.CANCELLED));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.CANCELLED).changeStatus(OrderStatus.PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.PENDING).changeStatus(OrderStatus.PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(OrderStatus.PENDING).changeStatus(null));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(null).changeStatus(OrderStatus.SHIPPED));
    }

    @Test
    @DisplayName("RF-08: changeStatus no deja retroceder tras entregar")
    void changeStatusRejectsRegressionAfterDelivery() {
        SalesOrder order = orderWithStatus(OrderStatus.SHIPPED);
        order.changeStatus(OrderStatus.DELIVERED);

        assertThrows(IllegalStateException.class,
                () -> order.changeStatus(OrderStatus.SHIPPED));
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    @DisplayName("RF-08: isTerminal es verdadero solo para delivered y cancelled")
    void isTerminalOnlyForFinalStates() {
        assertFalse(orderWithStatus(OrderStatus.PENDING).isTerminal());
        assertFalse(orderWithStatus(OrderStatus.SHIPPED).isTerminal());
        assertTrue(orderWithStatus(OrderStatus.DELIVERED).isTerminal());
        assertTrue(orderWithStatus(OrderStatus.CANCELLED).isTerminal());
    }

    private static SalesOrder orderWithStatus(OrderStatus status) {
        return new SalesOrder(1, LocalDateTime.now(), status, BigDecimal.ZERO,
                customer(), user(), List.of());
    }
}
