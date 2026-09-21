package com.b2bprocure.system.product;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Product Pessimistic Lock Integration Tests")
class ProductLockIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        Company supplierCompany = companyRepository.findAll().stream()
                .filter(c -> "SUPPLIER".equalsIgnoreCase(c.getCompanyType()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(new Company(null, "Lock Test Supplier", "TAX-LCK-" + System.currentTimeMillis(),
                        "lock@example.com", "0934567890", "789 Street", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        Category category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(null, "Lock Test Cat " + System.currentTimeMillis(),
                        "Desc", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        Product p1 = new Product();
        p1.setSupplierCompany(supplierCompany);
        p1.setCategory(category);
        p1.setSku("LCK-PROD-001-" + System.currentTimeMillis());
        p1.setName("Lock Product 1");
        p1.setStockQuantity(100);
        p1.setReservedQuantity(10);
        p1.setStatus("ACTIVE");
        product1 = productRepository.save(p1);

        Product p2 = new Product();
        p2.setSupplierCompany(supplierCompany);
        p2.setCategory(category);
        p2.setSku("LCK-PROD-002-" + System.currentTimeMillis());
        p2.setName("Lock Product 2");
        p2.setStockQuantity(50);
        p2.setReservedQuantity(5);
        p2.setStatus("ACTIVE");
        product2 = productRepository.save(p2);
    }

    @AfterEach
    void tearDown() {
        if (product1 != null && product1.getId() != null) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(product1.getId()));
            productRepository.delete(product1);
        }
        if (product2 != null && product2.getId() != null) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(product2.getId()));
            productRepository.delete(product2);
        }
    }

    @Test
    @Transactional
    @DisplayName("findByIdWithLock successfully acquires lock and returns product")
    void testFindByIdWithLock_Success() {
        Optional<Product> locked = productRepository.findByIdWithLock(product1.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(product1.getId());
        assertThat(locked.get().getStockQuantity()).isEqualTo(100);
        assertThat(locked.get().getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @Transactional
    @DisplayName("findByIdInWithLock successfully acquires lock and returns products sorted by ID ASC (deadlock prevention)")
    void testFindByIdInWithLock_SortedByIdAsc() {
        // Pass IDs in reverse order [product2.id, product1.id]
        List<Long> requestedIds = List.of(product2.getId(), product1.getId());
        List<Product> lockedList = productRepository.findByIdInWithLock(requestedIds);

        assertThat(lockedList).hasSize(2);
        // Verify results are strictly ordered by ID ASC to prevent deadlock in multi-item transactions
        assertThat(lockedList.get(0).getId()).isLessThan(lockedList.get(1).getId());
    }
}
