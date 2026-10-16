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
        assertEquals("Pendiente", SalesOrder.labelFor(SalesOrder.STATUS_PENDING));
        assertEquals("Enviado", SalesOrder.labelFor(SalesOrder.STATUS_SHIPPED));
        assertEquals("Entregado", SalesOrder.labelFor(SalesOrder.STATUS_DELIVERED));
        assertEquals("Cancelado", SalesOrder.labelFor(SalesOrder.STATUS_CANCELLED));
    }

    @Test
    @DisplayName("getStatusLabel devuelve el valor crudo para un estado desconocido o null")
    void unknownStatusLabelsFallBackToTheRawValue() {
        assertEquals("desconocido", SalesOrder.labelFor("desconocido"));
        assertEquals("null", SalesOrder.labelFor(null));
    }

    // --- RF-08: status state machine ------------------------------------

    @Test
    @DisplayName("RF-08: allowedTransitions sigue el pipeline y vacía los terminales")
    void allowedTransitionsFollowThePipeline() {
        assertEquals(List.of(SalesOrder.STATUS_SHIPPED, SalesOrder.STATUS_CANCELLED),
                orderWithStatus(SalesOrder.STATUS_PENDING).allowedTransitions());
        assertEquals(List.of(SalesOrder.STATUS_DELIVERED, SalesOrder.STATUS_CANCELLED),
                orderWithStatus(SalesOrder.STATUS_SHIPPED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus(SalesOrder.STATUS_DELIVERED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus(SalesOrder.STATUS_CANCELLED).allowedTransitions());
        assertEquals(List.of(), orderWithStatus("desconocido").allowedTransitions());
        assertEquals(List.of(), orderWithStatus(null).allowedTransitions());
    }

    @Test
    @DisplayName("RF-08: allowedTransitions es inmutable")
    void allowedTransitionsIsImmutable() {
        List<String> transitions = orderWithStatus(SalesOrder.STATUS_PENDING).allowedTransitions();

        assertThrows(UnsupportedOperationException.class,
                () -> transitions.add(SalesOrder.STATUS_DELIVERED));
    }

    @Test
    @DisplayName("RF-08: canTransitionTo acepta el pipeline y rechaza retrocesos, terminales, repetición y null")
    void canTransitionToOnlyAllowsThePipeline() {
        SalesOrder pending = orderWithStatus(SalesOrder.STATUS_PENDING);
        assertTrue(pending.canTransitionTo(SalesOrder.STATUS_SHIPPED));
        assertTrue(pending.canTransitionTo(SalesOrder.STATUS_CANCELLED));
        assertFalse(pending.canTransitionTo(SalesOrder.STATUS_DELIVERED));
        assertFalse(pending.canTransitionTo(SalesOrder.STATUS_PENDING));
        assertFalse(pending.canTransitionTo(null));
        assertFalse(pending.canTransitionTo("desconocido"));

        SalesOrder shipped = orderWithStatus(SalesOrder.STATUS_SHIPPED);
        assertTrue(shipped.canTransitionTo(SalesOrder.STATUS_DELIVERED));
        assertTrue(shipped.canTransitionTo(SalesOrder.STATUS_CANCELLED));
        assertFalse(shipped.canTransitionTo(SalesOrder.STATUS_PENDING));

        assertFalse(orderWithStatus(SalesOrder.STATUS_DELIVERED)
                .canTransitionTo(SalesOrder.STATUS_CANCELLED));
        assertFalse(orderWithStatus(SalesOrder.STATUS_CANCELLED)
                .canTransitionTo(SalesOrder.STATUS_PENDING));
    }

    @Test
    @DisplayName("RF-08: changeStatus avanza el pipeline y actualiza la etiqueta")
    void changeStatusAdvancesThePipeline() {
        SalesOrder order = orderWithStatus(SalesOrder.STATUS_PENDING);

        order.changeStatus(SalesOrder.STATUS_SHIPPED);
        assertEquals(SalesOrder.STATUS_SHIPPED, order.getStatus());
        assertEquals("Enviado", order.getStatusLabel());

        order.changeStatus(SalesOrder.STATUS_DELIVERED);
        assertEquals(SalesOrder.STATUS_DELIVERED, order.getStatus());
        assertEquals("Entregado", order.getStatusLabel());
    }

    @Test
    @DisplayName("RF-08: changeStatus permite cancelar desde pending y desde shipped")
    void changeStatusAllowsEarlyCancellation() {
        SalesOrder pending = orderWithStatus(SalesOrder.STATUS_PENDING);
        pending.changeStatus(SalesOrder.STATUS_CANCELLED);
        assertEquals(SalesOrder.STATUS_CANCELLED, pending.getStatus());
        assertEquals("Cancelado", pending.getStatusLabel());

        SalesOrder shipped = orderWithStatus(SalesOrder.STATUS_SHIPPED);
        shipped.changeStatus(SalesOrder.STATUS_CANCELLED);
        assertEquals(SalesOrder.STATUS_CANCELLED, shipped.getStatus());
    }

    @Test
    @DisplayName("RF-08: changeStatus rechaza retrocesos, terminales, repetición y desconocidos")
    void changeStatusRejectsForbiddenTransitions() {
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_SHIPPED).changeStatus(SalesOrder.STATUS_PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_DELIVERED).changeStatus(SalesOrder.STATUS_SHIPPED));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_DELIVERED).changeStatus(SalesOrder.STATUS_CANCELLED));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_CANCELLED).changeStatus(SalesOrder.STATUS_PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_PENDING).changeStatus(SalesOrder.STATUS_PENDING));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus(SalesOrder.STATUS_PENDING).changeStatus(null));
        assertThrows(IllegalStateException.class, () ->
                orderWithStatus("desconocido").changeStatus(SalesOrder.STATUS_SHIPPED));
    }

    @Test
    @DisplayName("RF-08: changeStatus no deja retroceder tras entregar")
    void changeStatusRejectsRegressionAfterDelivery() {
        SalesOrder order = orderWithStatus(SalesOrder.STATUS_SHIPPED);
        order.changeStatus(SalesOrder.STATUS_DELIVERED);

        assertThrows(IllegalStateException.class,
                () -> order.changeStatus(SalesOrder.STATUS_SHIPPED));
        assertEquals(SalesOrder.STATUS_DELIVERED, order.getStatus());
    }

    @Test
    @DisplayName("RF-08: isTerminal es verdadero solo para delivered y cancelled")
    void isTerminalOnlyForFinalStates() {
        assertFalse(orderWithStatus(SalesOrder.STATUS_PENDING).isTerminal());
        assertFalse(orderWithStatus(SalesOrder.STATUS_SHIPPED).isTerminal());
        assertTrue(orderWithStatus(SalesOrder.STATUS_DELIVERED).isTerminal());
        assertTrue(orderWithStatus(SalesOrder.STATUS_CANCELLED).isTerminal());
    }

    private static SalesOrder orderWithStatus(String status) {
        return new SalesOrder(1, LocalDateTime.now(), status, BigDecimal.ZERO,
                customer(), user(), List.of());
    }
}
