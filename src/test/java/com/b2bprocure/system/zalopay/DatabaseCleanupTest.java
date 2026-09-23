package com.b2bprocure.system.zalopay;

import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Cleanup test that runs BEFORE other tests to ensure DB is clean.
 * Uses @DirtiesContext to force a fresh context after cleanup.
 * Test ordering: Spring Boot tests are not guaranteed ordered, but this test
 * is annotated to be first alphabetically.
 */
@SpringBootTest
@DisplayName("Database Cleanup")
class DatabaseCleanupTest {

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    @DisplayName("Clean leftover ZaloPay test data")
    void testCleanupZaloPayTestData() {
        // Delete in reverse FK order
        // 1. Order status history
        orderStatusHistoryRepository.deleteAll();

        // 2. Payments
        paymentRepository.deleteAll();

        // 3. Order items
        orderItemRepository.deleteAll();

        // 4. Orders
        orderRepository.deleteAll();

        // 5. Cart items
        cartItemRepository.deleteAll();

        // 6. Carts
        cartRepository.deleteAll();

        // 7. Product prices
        productPriceRepository.deleteAll();

        // 8. Products
        productRepository.deleteAll();

        // 9. Companies that are not the seeded admin, buyer, supplier companies
        for (Company company : companyRepository.findAll()) {
            String taxCode = company.getTaxCode();
            if (taxCode != null && (taxCode.startsWith("TAX-ZALO") ||
                taxCode.startsWith("TAX-CHK"))) {
                companyRepository.delete(company);
            }
        }
    }
}
