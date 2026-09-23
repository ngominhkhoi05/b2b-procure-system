package com.b2bprocure.system.order;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Order Status History Audit Tests")
class OrderStatusHistoryAuditTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private Order testOrder;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();

        Company buyerCompany = companyRepository.findAll().stream()
                .filter(c -> "BUYER".equalsIgnoreCase(c.getCompanyType()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(new Company(null, "Audit Buyer Co", "TAX-AUD-B-" + System.currentTimeMillis(),
                        "auditb@example.com", "0901234567", "123 Buyer St", "BUYER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        Company supplierCompany = companyRepository.findAll().stream()
                .filter(c -> "SUPPLIER".equalsIgnoreCase(c.getCompanyType()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(new Company(null, "Audit Supplier Co", "TAX-AUD-S-" + System.currentTimeMillis(),
                        "audits@example.com", "0907654321", "456 Supplier St", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        testOrder = orderRepository.save(Order.builder()
                .orderCode("AUD-ORD-" + System.currentTimeMillis())
                .buyerCompany(buyerCompany)
                .supplierCompany(supplierCompany)
                .createdBy(testUser)
                .status(OrderStatus.PENDING_CONFIRMATION)
                .subtotal(new BigDecimal("100000.00"))
                .totalAmount(new BigDecimal("100000.00"))
                .shippingCompanyName("Audit Buyer Co")
                .shippingPhone("0901234567")
                .shippingAddress("123 Buyer St")
                .build());
    }

    @AfterEach
    void tearDown() {
        if (testOrder != null && testOrder.getId() != null) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(testOrder.getId()));
            orderRepository.delete(testOrder);
        }
    }

    @Test
    @DisplayName("User action records changed_by referencing the user")
    void testUserAction_HasChangedBy() {
        OrderStatusHistory userHistory = OrderStatusHistory.builder()
                .order(testOrder)
                .changedBy(testUser)
                .status(OrderStatus.CONFIRMED)
                .note("Supplier confirmed order")
                .build();

        OrderStatusHistory saved = orderStatusHistoryRepository.save(userHistory);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getChangedBy()).isNotNull();
        assertThat(saved.getChangedBy().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(saved.getNote()).isEqualTo("Supplier confirmed order");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("System action allows changed_by = NULL (e.g. Supplier timeout auto-reject)")
    void testSystemAction_AllowsNullChangedBy() {
        OrderStatusHistory systemHistory = OrderStatusHistory.builder()
                .order(testOrder)
                .changedBy(null) // System-generated action
                .status(OrderStatus.REJECTED)
                .note("Supplier confirmation timeout (system auto-reject)")
                .build();

        OrderStatusHistory saved = orderStatusHistoryRepository.save(systemHistory);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getChangedBy()).isNull();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.getNote()).contains("system auto-reject");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
