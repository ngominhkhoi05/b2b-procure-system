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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("Product Reservation Invariant Tests")
class ProductReservationInvariantTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Company testCompany;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCompany = companyRepository.findAll().stream()
                .filter(c -> "SUPPLIER".equalsIgnoreCase(c.getCompanyType()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(new Company(null, "Test Supplier Invariant", "TAX-INV-" + System.currentTimeMillis(),
                        "inv@example.com", "0912345678", "123 Street", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        testCategory = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(null, "Test Cat Invariant " + System.currentTimeMillis(),
                        "Desc", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));
    }

    @AfterEach
    void tearDown() {
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("INV-TEST-"))
                .forEach(p -> {
                    productPriceRepository.deleteAll(productPriceRepository.findByProductId(p.getId()));
                    productRepository.delete(p);
                });
    }

    @Nested
    @DisplayName("1. Unit / Invariant Logic Tests")
    class InvariantLogicTests {

        @Test
        @DisplayName("Available quantity = stock - reserved")
        void testAvailableQuantity_Correct() {
            Product product = new Product();
            product.setStockQuantity(100);
            product.setReservedQuantity(20);
            assertThat(product.getAvailableQuantity()).isEqualTo(80);

            // After reservation +30
            product.setReservedQuantity(50);
            assertThat(product.getAvailableQuantity()).isEqualTo(50);

            // After confirm 30: stock -30, reserved -30
            product.setStockQuantity(70);
            product.setReservedQuantity(20);
            assertThat(product.getAvailableQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("validateInvariants() passes when reserved <= stock and both >= 0")
        void testValidateInvariants_Valid() {
            Product product = new Product();
            product.setStockQuantity(50);
            product.setReservedQuantity(50);
            product.validateInvariants(); // Should not throw

            product.setReservedQuantity(0);
            product.validateInvariants(); // Should not throw
        }

        @Test
        @DisplayName("validateInvariants() throws when reserved > stock")
        void testValidateInvariants_ReservedExceedsStock_Throws() {
            Product product = new Product();
            product.setStockQuantity(10);
            product.setReservedQuantity(11);
            assertThatThrownBy(product::validateInvariants)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot exceed stock quantity");
        }

        @Test
        @DisplayName("validateInvariants() throws when stock < 0")
        void testValidateInvariants_NegativeStock_Throws() {
            Product product = new Product();
            product.setStockQuantity(-1);
            product.setReservedQuantity(0);
            assertThatThrownBy(product::validateInvariants)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Stock quantity cannot be negative");
        }

        @Test
        @DisplayName("validateInvariants() throws when reserved < 0")
        void testValidateInvariants_NegativeReserved_Throws() {
            Product product = new Product();
            product.setStockQuantity(10);
            product.setReservedQuantity(-1);
            assertThatThrownBy(product::validateInvariants)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reserved quantity cannot be negative");
        }
    }

    @Nested
    @DisplayName("2. Database Constraint & Lifecycle Verification")
    class DatabaseConstraintTests {

        @Test
        @DisplayName("Saving product with valid stock and reserved succeeds")
        void testSaveProduct_Valid_Succeeds() {
            Product product = new Product();
            product.setSupplierCompany(testCompany);
            product.setCategory(testCategory);
            product.setSku("INV-TEST-" + System.currentTimeMillis());
            product.setName("Invariant Test Product");
            product.setStockQuantity(100);
            product.setReservedQuantity(20);
            product.setStatus("ACTIVE");

            Product saved = productRepository.save(product);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getStockQuantity()).isEqualTo(100);
            assertThat(saved.getReservedQuantity()).isEqualTo(20);
            assertThat(saved.getAvailableQuantity()).isEqualTo(80);
        }

        @Test
        @DisplayName("Saving product where reserved_quantity > stock_quantity is rejected")
        void testSaveProduct_ReservedGreaterThanStock_Rejected() {
            Product product = new Product();
            product.setSupplierCompany(testCompany);
            product.setCategory(testCategory);
            product.setSku("INV-TEST-FAIL-" + System.currentTimeMillis());
            product.setName("Fail Invariant Product");
            product.setStockQuantity(10);
            product.setReservedQuantity(15);
            product.setStatus("ACTIVE");

            assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                    .isInstanceOf(Exception.class);
        }
    }
}
