package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.Stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ReportLabels} (RF-12): the stock report must explain why a
 * lot is no longer stock (returned or cancelled) and stay silent for the rest.
 */
class ReportLabelsTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2025, 1, 15, 10, 30);

    private static Stock lot(String purchaseStatus, int available) {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        Purchase purchase = purchaseStatus == null ? null
                : new Purchase(5, ENTRY, purchaseStatus, BigDecimal.ZERO, null, null, null);
        return new Stock(1, 10, available, new BigDecimal("1.50"), ENTRY, product, purchase);
    }

    @Test
    @DisplayName("El estado de la compra se traduce al español")
    void purchaseStatusIsTranslated() {
        assertEquals("Pendiente", ReportLabels.purchaseStatus(lot(Purchase.STATUS_PENDING, 0)));
        assertEquals("Recibida", ReportLabels.purchaseStatus(lot(Purchase.STATUS_RECEIVED, 4)));
        assertEquals("Cancelada", ReportLabels.purchaseStatus(lot(Purchase.STATUS_CANCELLED, 0)));
        assertEquals("Devuelta", ReportLabels.purchaseStatus(lot(Purchase.STATUS_RETURNED, 0)));
    }

    @Test
    @DisplayName("Sin compra o sin estado el texto queda vacío, nunca 'null'")
    void missingPurchaseOrStatusYieldsEmptyText() {
        assertEquals("", ReportLabels.purchaseStatus(null));
        assertEquals("", ReportLabels.purchaseStatus(lot(null, 3)));
        assertEquals("", ReportLabels.availabilityReason(null));
        assertEquals("", ReportLabels.availabilityReason(lot(null, 3)));
    }

    @Test
    @DisplayName("El motivo solo se rellena para devueltas o canceladas")
    void reasonOnlyExplainsReturnedOrCancelled() {
        assertEquals(ReportLabels.REASON_RETURNED,
                ReportLabels.availabilityReason(lot(Purchase.STATUS_RETURNED, 0)));
        assertEquals(ReportLabels.REASON_CANCELLED,
                ReportLabels.availabilityReason(lot(Purchase.STATUS_CANCELLED, 0)));
    }

    @Test
    @DisplayName("Recibidas (con o sin unidades) y pendientes dejan el motivo vacío")
    void otherStatusesHaveNoReason() {
        assertEquals("", ReportLabels.availabilityReason(lot(Purchase.STATUS_RECEIVED, 4)));
        assertEquals("", ReportLabels.availabilityReason(lot(Purchase.STATUS_RECEIVED, 0)));
        assertEquals("", ReportLabels.availabilityReason(lot(Purchase.STATUS_PENDING, 0)));
    }

    private static SalesReportRow salesRow(Product product, int unitsSold, String revenue,
                                           int cancelledUnits, String cancelledRevenue) {
        return new SalesReportRow(product, unitsSold, new BigDecimal(revenue), cancelledUnits,
                new BigDecimal(cancelledRevenue));
    }

    @Test
    @DisplayName("El motivo de ventas solo se rellena si hay unidades canceladas")
    void salesReasonOnlyExplainsCancellations() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);

        assertEquals("", ReportLabels.salesReason(null));
        assertEquals("", ReportLabels.salesReason(
                new SalesReportRow(product, 3, new BigDecimal("30.00"))));
        assertEquals(ReportLabels.REASON_ALL_ORDERS_CANCELLED,
                ReportLabels.salesReason(salesRow(product, 0, "0.00", 4, "40.00")));
        assertEquals(ReportLabels.REASON_PARTLY_CANCELLED,
                ReportLabels.salesReason(salesRow(product, 2, "20.00", 4, "40.00")));
    }
}
