package com.entidaddesconocida.monte_erp.ui.support;

/**
 * Facts of {@code db/seed/demo_seed.sql} the UI tests assert on.
 *
 * <p>They are kept in one place so a change in the seed fails loudly in
 * {@link TestDatabase#provision()} instead of producing a confusing failure in
 * some unrelated screen test.</p>
 */
public final class SeedFacts {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "Admin1234";
    public static final String EMPLOYEE_USERNAME = "ana.garcia";
    public static final String EMPLOYEE_PASSWORD = "Demo1234";
    public static final String INACTIVE_USERNAME = "marta.sanz";

    public static final int USERS = 4;
    public static final int ACTIVE_USERS = 3;

    public static final int CATEGORIES = 8;
    public static final int ACTIVE_CATEGORIES = 6;

    public static final int SUPPLIERS = 7;
    public static final int ACTIVE_SUPPLIERS = 6;

    public static final int CUSTOMERS = 12;
    public static final int ACTIVE_CUSTOMERS = 10;

    public static final int PRODUCTS = 25;
    public static final int ACTIVE_PRODUCTS = 23;

    public static final int PURCHASES = 18;
    public static final int RECEIVED_PURCHASES = 13;
    public static final int PENDING_PURCHASES = 2;
    public static final int CANCELLED_PURCHASES = 2;
    public static final int RETURNED_PURCHASES = 1;

    public static final int STOCK_LOTS = 44;

    public static final int SALES_ORDERS = 30;
    public static final int DELIVERED_ORDERS = 14;
    public static final int SHIPPED_ORDERS = 7;
    public static final int PENDING_ORDERS = 5;
    public static final int CANCELLED_ORDERS = 4;

    public static final int SALES_ORDER_LINES = 73;
    public static final int STOCK_ISSUES = 65;

    /** Products whose available stock is below their minimum (two of them at zero). */
    public static final int STOCK_ALERTS = 9;

    /** Purchase fully consumed by sales, so it can never be returned. */
    public static final int FULLY_CONSUMED_PURCHASE_ID = 6;

    private SeedFacts() {
    }
}
