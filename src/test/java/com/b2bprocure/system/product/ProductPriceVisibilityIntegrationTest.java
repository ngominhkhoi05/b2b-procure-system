package com.b2bprocure.system.product;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Product visibility rule:
 * Buyer should ONLY see products that have at least 1 price_product.
 *
 * Uses @Transactional to auto-rollback after each test for isolation.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
public class ProductPriceVisibilityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String buyerToken;
    private String supplierToken;

    private Long activeCategoryId;
    private Long supplierCompanyId;

    // Test product IDs - not static to ensure fresh per test
    private Long productWithOnePriceId;
    private Long productWithMultiplePricesId;
    private Long productWithNoPricesId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Generate tokens
        User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

        supplierCompanyId = supplierUser.getCompany().getId();

        // Category - find or create
        activeCategoryId = categoryRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .findFirst()
                .map(Category::getId)
                .orElseGet(() -> {
                    Category cat = new Category(null, "Price Test Cat " + UUID.randomUUID().toString().substring(0, 8), "Test category", "ACTIVE", LocalDateTime.now(), LocalDateTime.now());
                    return categoryRepository.save(cat).getId();
                });

        // Use unique prefix for all SKUs to avoid conflicts
        String uniquePrefix = UUID.randomUUID().toString().substring(0, 8);

        // Product with ONE price_product -> should be visible to Buyer
        Product p1 = new Product();
        p1.setSupplierCompany(companyRepository.findById(supplierCompanyId).orElseThrow());
        p1.setCategory(categoryRepository.findById(activeCategoryId).orElseThrow());
        p1.setSku("ONE-PRICE-" + uniquePrefix);
        p1.setName("Product With One Price");
        p1.setStockQuantity(100);
        p1.setStatus("ACTIVE");
        p1.setCreatedAt(LocalDateTime.now());
        p1.setUpdatedAt(LocalDateTime.now());
        productWithOnePriceId = productRepository.save(p1).getId();

        ProductPrice price1 = new ProductPrice();
        price1.setProduct(productRepository.findById(productWithOnePriceId).orElseThrow());
        price1.setMinQuantity(1);
        price1.setMaxQuantity(10);
        price1.setUnitPrice(new BigDecimal("100000.00"));
        price1.setCreatedAt(LocalDateTime.now());
        price1.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(price1);

        // Product with MULTIPLE price_products -> should be visible, no duplicates
        Product p2 = new Product();
        p2.setSupplierCompany(companyRepository.findById(supplierCompanyId).orElseThrow());
        p2.setCategory(categoryRepository.findById(activeCategoryId).orElseThrow());
        p2.setSku("MULTI-PRICE-" + uniquePrefix);
        p2.setName("Product With Multiple Prices");
        p2.setStockQuantity(200);
        p2.setStatus("ACTIVE");
        p2.setCreatedAt(LocalDateTime.now());
        p2.setUpdatedAt(LocalDateTime.now());
        productWithMultiplePricesId = productRepository.save(p2).getId();

        // Add 3 price tiers
        for (int i = 1; i <= 3; i++) {
            ProductPrice price = new ProductPrice();
            price.setProduct(productRepository.findById(productWithMultiplePricesId).orElseThrow());
            price.setMinQuantity((i - 1) * 10 + 1);
            price.setMaxQuantity(i * 10);
            price.setUnitPrice(new BigDecimal("100000.00").subtract(new BigDecimal("5000.00").multiply(new BigDecimal(i))));
            price.setCreatedAt(LocalDateTime.now());
            price.setUpdatedAt(LocalDateTime.now());
            productPriceRepository.save(price);
        }

        // Product with ZERO price_products -> should NOT be visible to Buyer
        Product p3 = new Product();
        p3.setSupplierCompany(companyRepository.findById(supplierCompanyId).orElseThrow());
        p3.setCategory(categoryRepository.findById(activeCategoryId).orElseThrow());
        p3.setSku("NO-PRICE-" + uniquePrefix);
        p3.setName("Product Without Price");
        p3.setStockQuantity(50);
        p3.setStatus("ACTIVE");
        p3.setCreatedAt(LocalDateTime.now());
        p3.setUpdatedAt(LocalDateTime.now());
        productWithNoPricesId = productRepository.save(p3).getId();
    }

    // ========================================================================
    // Case 1: Product with 1 price_product -> visible to Buyer
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Case 1: Buyer sees product with 1 price_product in listing")
    void testBuyerSeesProductWithOnePrice() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithOnePriceId + ")]", not(empty())));
    }

    @Test
    @Order(2)
    @DisplayName("Case 1b: Buyer can GET product with 1 price_product by ID")
    void testBuyerCanGetProductWithOnePriceById() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + productWithOnePriceId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productWithOnePriceId.intValue())));
    }

    // ========================================================================
    // Case 2: Product with 3 price_products -> visible, no duplicates
    // ========================================================================

    @Test
    @Order(3)
    @DisplayName("Case 2: Buyer sees product with multiple prices - no duplicates")
    void testBuyerSeesProductWithMultiplePrices_NoDuplicates() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn();

        // Count how many times productWithMultiplePricesId appears
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
        com.fasterxml.jackson.databind.JsonNode content = root.path("data").path("content");

        long count = 0;
        for (com.fasterxml.jackson.databind.JsonNode product : content) {
            if (product.path("id").asLong() == productWithMultiplePricesId) {
                count++;
            }
        }

        // Should appear exactly once (no duplicates)
        org.junit.jupiter.api.Assertions.assertEquals(1, count, "Product should appear exactly once despite having multiple price_products");
    }

    // ========================================================================
    // Case 3: Product with 0 price_products -> NOT visible to Buyer
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("Case 3: Buyer does NOT see product with 0 price_products in listing")
    void testBuyerDoesNotSeeProductWithoutPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", empty()));
    }

    @Test
    @Order(5)
    @DisplayName("Case 3b: Buyer GET product with 0 price_products -> 404 Not Found")
    void testBuyerCannotGetProductWithoutPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + productWithNoPricesId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound());
    }

    // ========================================================================
    // Case 4: Pagination counts only products with price_products
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("Case 4: Pagination counts products AFTER filtering those without prices")
    void testPaginationAfterPriceFilter() throws Exception {
        // We have 3 products created in setup, but only 2 have price_products
        // So totalElements should be 2, not 3
        mockMvc.perform(get("/api/v1/products?page=0&size=10")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.content.length()", is(2)));
    }

    @Test
    @Order(7)
    @DisplayName("Case 4b: Pagination page 1 with page size 1 returns correct content")
    void testPaginationPageSizeOne() throws Exception {
        mockMvc.perform(get("/api/v1/products?page=0&size=1")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize", is(1)))
                .andExpect(jsonPath("$.data.pageNo", is(0)))
                .andExpect(jsonPath("$.data.content.length()", is(1)))
                .andExpect(jsonPath("$.data.totalElements", is(2))); // Still 2 total, showing 1
    }

    // ========================================================================
    // Case 5: Search/filter respects price requirement
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("Case 5: Search for product name with price -> found")
    void testSearchFindsProductWithPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products?keyword=Multiple")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithMultiplePricesId + ")]", not(empty())));
    }

    @Test
    @Order(9)
    @DisplayName("Case 5b: Search for product name without price -> not found")
    void testSearchDoesNotFindProductWithoutPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products?keyword=Without Price")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", empty()));
    }

    // ========================================================================
    // Admin/Supplier see all products (no price requirement)
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("Admin sees all products regardless of price_product")
    void testAdminSeesAllProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // Admin should see the product without prices too
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", not(empty())));
    }

    @Test
    @Order(11)
    @DisplayName("Admin can GET product without price by ID")
    void testAdminCanGetProductWithoutPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + productWithNoPricesId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productWithNoPricesId.intValue())));
    }

    @Test
    @Order(12)
    @DisplayName("Supplier sees own products regardless of price_product")
    void testSupplierSeesAllOwnProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                // Supplier should see the product without prices (their own product)
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", not(empty())));
    }

    // ========================================================================
    // Dynamic test: Add price to product -> becomes visible
    // ========================================================================

    @Test
    @Order(13)
    @DisplayName("Dynamic: Add price to product -> Buyer can now see it")
    void testAddPriceMakesProductVisible() throws Exception {
        // Verify product is NOT visible before adding price
        mockMvc.perform(get("/api/v1/products?keyword=Without Price")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // Add a price_product
        ProductPrice newPrice = new ProductPrice();
        newPrice.setProduct(productRepository.findById(productWithNoPricesId).orElseThrow());
        newPrice.setMinQuantity(1);
        newPrice.setMaxQuantity(100);
        newPrice.setUnitPrice(new BigDecimal("50000.00"));
        newPrice.setCreatedAt(LocalDateTime.now());
        newPrice.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(newPrice);

        // Now the product should be visible
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", not(empty())));

        // And accessible by ID
        mockMvc.perform(get("/api/v1/products/" + productWithNoPricesId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productWithNoPricesId.intValue())));
    }

    @Test
    @Order(14)
    @DisplayName("Dynamic: Remove last price from product -> Buyer can no longer see it")
    void testRemovePriceHidesProduct() throws Exception {
        // First add a price (in case previous test's price was deleted)
        ProductPrice price = new ProductPrice();
        price.setProduct(productRepository.findById(productWithNoPricesId).orElseThrow());
        price.setMinQuantity(1);
        price.setMaxQuantity(100);
        price.setUnitPrice(new BigDecimal("50000.00"));
        price.setCreatedAt(LocalDateTime.now());
        price.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(price);

        // Verify visible
        mockMvc.perform(get("/api/v1/products/" + productWithNoPricesId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        // Delete all prices
        productPriceRepository.deleteByProductId(productWithNoPricesId);

        // Now should not be visible
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + productWithNoPricesId + ")]", empty()));

        // And not accessible by ID
        mockMvc.perform(get("/api/v1/products/" + productWithNoPricesId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound());
    }
}
