package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;

import java.util.Objects;

/**
 * The eight persistence ports the composition root hands to {@link AppContext}.
 *
 * <p>They used to be eight consecutive constructor parameters of the same
 * "shape" (a repository), where swapping two of them compiled and only failed at
 * runtime, in an unrelated screen. Grouping them in a record gives every one a
 * name at the call site and fails fast when one is missing.</p>
 *
 * @param users       users and their credentials
 * @param categories  product categories
 * @param suppliers   suppliers
 * @param products    products and their sale price
 * @param customers   customers
 * @param salesOrders sales orders and their lines
 * @param purchases   purchases and their lines
 * @param stock       stock lots and levels
 */
public record Repositories(UserRepository users,
                           CategoryRepository categories,
                           SupplierRepository suppliers,
                           ProductRepository products,
                           CustomerRepository customers,
                           SalesOrderRepository salesOrders,
                           PurchaseRepository purchases,
                           StockRepository stock) {

    public Repositories {
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(categories, "categories");
        Objects.requireNonNull(suppliers, "suppliers");
        Objects.requireNonNull(products, "products");
        Objects.requireNonNull(customers, "customers");
        Objects.requireNonNull(salesOrders, "salesOrders");
        Objects.requireNonNull(purchases, "purchases");
        Objects.requireNonNull(stock, "stock");
    }
}
