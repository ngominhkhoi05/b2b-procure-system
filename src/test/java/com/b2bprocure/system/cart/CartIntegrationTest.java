package com.b2bprocure.system.cart;

import com.b2bprocure.system.cart.dto.AddToCartRequest;
import com.b2bprocure.system.cart.dto.UpdateCartItemRequest;
import com.b2bprocure.system.cart.entity.Cart;
import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CartIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

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
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String adminToken;
    private static String buyer1Token;
    private static String buyer2Token;
    private static String buyer3Token;
    private static String supplierToken;

    private static Long buyer1UserId;
    private static Long buyer2UserId;
    private static Long buyer3UserId;

    private static Long activeProductId1;
    private static Long activeProductId2;
    private static Long inactiveProductId;
    private static Long inactiveCatProductId;
    private static Long boundedTierProductId;

    private static boolean initialized = false;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        if (!initialized) {
            cartItemRepository.deleteAll();
            cartRepository.deleteAll();

            // Chỉ xóa các sản phẩm test của riêng CartIntegrationTest (theo SKU), KHÔNG xóa sản phẩm của người dùng tạo trên Swagger
            List<String> testSkus = List.of("CART-SKU-001", "CART-SKU-002", "CART-SKU-INACTIVE", "CART-SKU-INAC-CAT", "CART-SKU-BOUNDED-005");
            for (String sku : testSkus) {
                productRepository.findAll().stream()
                        .filter(p -> sku.equalsIgnoreCase(p.getSku()))
                        .findFirst()
                        .ifPresent(p -> {
                            productPriceRepository.deleteAll(productPriceRepository.findByProductId(p.getId()));
                            productRepository.delete(p);
                        });
            }

            User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
            User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
            User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

            buyer1UserId = buyerUser.getId();

            adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
            buyer1Token = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
            supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

            // Setup Buyer 2
            User buyer2User = userRepository.findByUsernameWithRoleAndCompany("buyer2").orElseGet(() -> {
                User u = new User();
                u.setUsername("buyer2");
                u.setEmail("buyer2@procure.com");
                u.setPassword(buyerUser.getPassword());
                u.setFullName("Buyer 2 Test");
                u.setPhone("0901234567");
                u.setRole(buyerUser.getRole());
                u.setCompany(buyerUser.getCompany());
                u.setStatus("ACTIVE");
                u.setCreatedAt(LocalDateTime.now());
                u.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(u);
            });
            buyer2UserId = buyer2User.getId();
            buyer2Token = jwtTokenProvider.generateToken(UserPrincipal.create(buyer2User));

            // Setup Buyer 3 (fresh buyer with no cart)
            User buyer3User = userRepository.findByUsernameWithRoleAndCompany("buyer3").orElseGet(() -> {
                User u = new User();
                u.setUsername("buyer3");
                u.setEmail("buyer3@procure.com");
                u.setPassword(buyerUser.getPassword());
                u.setFullName("Buyer 3 Test");
                u.setPhone("0907654321");
                u.setRole(buyerUser.getRole());
                u.setCompany(buyerUser.getCompany());
                u.setStatus("ACTIVE");
                u.setCreatedAt(LocalDateTime.now());
                u.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(u);
            });
            buyer3UserId = buyer3User.getId();
            buyer3Token = jwtTokenProvider.generateToken(UserPrincipal.create(buyer3User));

            // Setup Supplier 2 company
            Company supplier2Company = companyRepository.findByTaxCode("0109999888").orElseGet(() -> {
                Company c = new Company();
                c.setName("Supplier 2 Logistics Co");
                c.setTaxCode("0109999888");
                c.setEmail("supplier2@logistics.com");
                c.setPhone("0908888777");
                c.setCompanyType("SUPPLIER");
                c.setStatus("ACTIVE");
                c.setCreatedAt(LocalDateTime.now());
                c.setUpdatedAt(LocalDateTime.now());
                return companyRepository.save(c);
            });

            // Active Category
            Category activeCategory = categoryRepository.findAll().stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                    .findFirst()
                    .orElseGet(() -> categoryRepository.save(new Category(null, "Cart Active Cat " + System.currentTimeMillis(), "Description", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

            // Inactive Category
            Category inactiveCategory = categoryRepository.findAll().stream()
                    .filter(c -> "INACTIVE".equalsIgnoreCase(c.getStatus()))
                    .findFirst()
                    .orElseGet(() -> categoryRepository.save(new Category(null, "Cart Inactive Cat " + System.currentTimeMillis(), "Description", "INACTIVE", LocalDateTime.now(), LocalDateTime.now())));

            // Product 1 (Active, 100 stock, Supplier 1)
            Product p1 = new Product();
            p1.setSupplierCompany(supplierUser.getCompany());
            p1.setCategory(activeCategory);
            p1.setSku("CART-SKU-001");
            p1.setName("Enterprise Laptop");
            p1.setDescription("Business laptop for enterprise");
            p1.setImageUrl("https://example.com/laptop.png");
            p1.setStockQuantity(100);
            p1.setStatus("ACTIVE");
            p1.setCreatedAt(LocalDateTime.now());
            p1.setUpdatedAt(LocalDateTime.now());
            p1 = productRepository.save(p1);
            activeProductId1 = p1.getId();

            // Product 1 Price Tiers:
            // 1-49 -> 100,000; 50-99 -> 95,000; 100+ -> 90,000
            ProductPrice pp1 = new ProductPrice(null, p1, 1, 49, new BigDecimal("100000.00"), LocalDateTime.now(), LocalDateTime.now());
            ProductPrice pp2 = new ProductPrice(null, p1, 50, 99, new BigDecimal("95000.00"), LocalDateTime.now(), LocalDateTime.now());
            ProductPrice pp3 = new ProductPrice(null, p1, 100, null, new BigDecimal("90000.00"), LocalDateTime.now(), LocalDateTime.now());
            productPriceRepository.saveAll(List.of(pp1, pp2, pp3));

            // Product 2 (Active, 50 stock, Supplier 2)
            Product p2 = new Product();
            p2.setSupplierCompany(supplier2Company);
            p2.setCategory(activeCategory);
            p2.setSku("CART-SKU-002");
            p2.setName("4K Monitor");
            p2.setDescription("Professional 4K Display");
            p2.setImageUrl("https://example.com/monitor.png");
            p2.setStockQuantity(50);
            p2.setStatus("ACTIVE");
            p2.setCreatedAt(LocalDateTime.now());
            p2.setUpdatedAt(LocalDateTime.now());
            p2 = productRepository.save(p2);
            activeProductId2 = p2.getId();

            // Product 2 Price Tier:
            // 1+ -> 200,000
            ProductPrice pp4 = new ProductPrice(null, p2, 1, null, new BigDecimal("200000.00"), LocalDateTime.now(), LocalDateTime.now());
            productPriceRepository.save(pp4);

            // Inactive Product
            Product p3 = new Product();
            p3.setSupplierCompany(supplierUser.getCompany());
            p3.setCategory(activeCategory);
            p3.setSku("CART-SKU-INACTIVE");
            p3.setName("Inactive Product");
            p3.setDescription("Discontinued item");
            p3.setStockQuantity(50);
            p3.setStatus("INACTIVE");
            p3.setCreatedAt(LocalDateTime.now());
            p3.setUpdatedAt(LocalDateTime.now());
            p3 = productRepository.save(p3);
            inactiveProductId = p3.getId();

            // Inactive Category Product
            Product p4 = new Product();
            p4.setSupplierCompany(supplierUser.getCompany());
            p4.setCategory(inactiveCategory);
            p4.setSku("CART-SKU-INAC-CAT");
            p4.setName("Product with Inactive Cat");
            p4.setDescription("Inactive category item");
            p4.setStockQuantity(50);
            p4.setStatus("ACTIVE");
            p4.setCreatedAt(LocalDateTime.now());
            p4.setUpdatedAt(LocalDateTime.now());
            p4 = productRepository.save(p4);
            inactiveCatProductId = p4.getId();

            // Product 5 (Bounded Tiers: 1-49: 100k, 50-99: 95k, 100-500: 90k, Stock: 1000)
            Product p5 = new Product();
            p5.setSupplierCompany(supplierUser.getCompany());
            p5.setCategory(activeCategory);
            p5.setSku("CART-SKU-BOUNDED-005");
            p5.setName("Bulk Workstation");
            p5.setDescription("Enterprise workstation for bulk orders");
            p5.setImageUrl("https://example.com/workstation.png");
            p5.setStockQuantity(1000);
            p5.setStatus("ACTIVE");
            p5.setCreatedAt(LocalDateTime.now());
            p5.setUpdatedAt(LocalDateTime.now());
            p5 = productRepository.save(p5);
            boundedTierProductId = p5.getId();

            ProductPrice pp5_1 = new ProductPrice(null, p5, 1, 49, new BigDecimal("100000.00"), LocalDateTime.now(), LocalDateTime.now());
            ProductPrice pp5_2 = new ProductPrice(null, p5, 50, 99, new BigDecimal("95000.00"), LocalDateTime.now(), LocalDateTime.now());
            ProductPrice pp5_3 = new ProductPrice(null, p5, 100, 500, new BigDecimal("90000.00"), LocalDateTime.now(), LocalDateTime.now());
            productPriceRepository.saveAll(List.of(pp5_1, pp5_2, pp5_3));

            initialized = true;
        }
    }

    // =========================================================================
    // Scenario 1: Buyer get empty cart
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("1. Buyer get empty cart when no items exist")
    void testBuyerGetEmptyCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalAmount", is(0)))
                .andExpect(jsonPath("$.data.totalItems", is(0)));
    }

    // =========================================================================
    // Scenario 2: Buyer add product successfully
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("2. Buyer add product successfully (201 Created)")
    void testBuyerAddProductSuccessfully() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProductId1)
                .quantity(20)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(activeProductId1.intValue())))
                .andExpect(jsonPath("$.data.quantity", is(20)))
                .andExpect(jsonPath("$.data.unitPrice", is(100000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(2000000.00)))
                .andExpect(jsonPath("$.data.available", is(true)));
    }

    // =========================================================================
    // Scenario 3: Add same product twice -> quantity accumulated, no duplicate
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("3. Add same product twice accumulates quantity without duplicate CartItem")
    void testAddSameProductTwiceAccumulatesQuantity() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProductId1)
                .quantity(30)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quantity", is(50)))
                .andExpect(jsonPath("$.data.unitPrice", is(95000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(4750000.00)));

        // Verify only 1 item in cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].quantity", is(50)))
                .andExpect(jsonPath("$.data.totalItems", is(1)));
    }

    // =========================================================================
    // Scenario 4: Add quantity <= stock -> success and stock not deducted
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("4. Add quantity <= stock succeeds and does not deduct stock")
    void testAddQuantityLessThanOrEqualToStock() throws Exception {
        // Active Product 2 has stock 50. Add 30 items.
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProductId2)
                .quantity(30)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantity", is(30)));

        // Verify product 2 stock in DB is STILL 50
        Product p2 = productRepository.findById(activeProductId2).orElseThrow();
        assertEquals(50, p2.getStockQuantity());
    }

    // =========================================================================
    // Scenario 5: Add quantity > stock -> reject (400)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("5. Add quantity > stock rejects with 400 Bad Request")
    void testAddQuantityExceedsStockRejected() throws Exception {
        // Product 2 currently has 30 in cart, stock is 50. Adding 25 more would make total 55 > 50.
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProductId2)
                .quantity(25)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("exceeds available stock")));

        // Verify Product 2 stock is still 50
        Product p2 = productRepository.findById(activeProductId2).orElseThrow();
        assertEquals(50, p2.getStockQuantity());
    }

    // =========================================================================
    // Scenario 6: Update quantity successfully
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("6. Update quantity successfully")
    void testUpdateQuantitySuccessfully() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(40)
                .build();

        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId2)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quantity", is(40)))
                .andExpect(jsonPath("$.data.unitPrice", is(200000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(8000000.00)))
                .andExpect(jsonPath("$.data.stockQuantity", is(50)))
                .andExpect(jsonPath("$.data.available", is(true)));
    }

    // =========================================================================
    // Scenario 7: Update quantity > stock -> reject (400)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("7. Update quantity > stock rejects with 400 Bad Request")
    void testUpdateQuantityExceedsStockRejected() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(999)
                .build();

        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId2)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("exceeds available stock")));

        // Quantity in cart remains 40
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId2 + ")].quantity").value(40));
    }

    // =========================================================================
    // Scenario 8: Get Cart returns correct unitPrice according to tier
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("8. Get Cart returns correct unitPrice according to ProductPrice tier")
    void testGetCartReturnsCorrectUnitPricePerTier() throws Exception {
        // Product 1 has quantity 50. Tier 50-99 -> 95,000
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].unitPrice").value(95000.00));
    }

    // =========================================================================
    // Scenario 9: Get Cart calculates correct subtotal
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("9. Get Cart calculates correct subtotal (quantity * unitPrice)")
    void testGetCartCalculatesCorrectSubtotal() throws Exception {
        // Product 1: 50 * 95,000 = 4,750,000
        // Product 2: 40 * 200,000 = 8,000,000
        // Total = 12,750,000
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].subtotal").value(4750000.00))
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId2 + ")].subtotal").value(8000000.00))
                .andExpect(jsonPath("$.data.totalAmount", is(12750000.00)))
                .andExpect(jsonPath("$.data.totalItems", is(2)));
    }

    // =========================================================================
    // Scenario 10: Change quantity -> price tier changes accordingly
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("10. Changing quantity triggers tier transition and price recalculation")
    void testChangeQuantityTriggersTierTransition() throws Exception {
        // Update Product 1 quantity to 100 (Tier 100+ -> 90,000)
        UpdateCartItemRequest request100 = UpdateCartItemRequest.builder().quantity(100).build();
        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId1)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(9000000.00)));

        // Update Product 1 quantity to 10 (Tier 1-49 -> 100,000)
        UpdateCartItemRequest request10 = UpdateCartItemRequest.builder().quantity(10).build();
        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId1)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(100000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(1000000.00)));
    }

    // =========================================================================
    // Scenario 11: Product ACTIVE -> available = true if enough stock
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("11. Product ACTIVE + Category ACTIVE + enough stock -> available = true")
    void testProductActiveAndStockSufficientIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].available").value(true));
    }

    // =========================================================================
    // Scenario 12: Product INACTIVE -> CartItem retained, available = false
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("12. Product INACTIVE -> CartItem is retained and available = false")
    void testProductInactiveRetainsCartItemWithAvailableFalse() throws Exception {
        // Change Product 1 status to INACTIVE in DB
        Product p1 = productRepository.findById(activeProductId1).orElseThrow();
        p1.setStatus("INACTIVE");
        productRepository.save(p1);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].productStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].available").value(false));

        // Restore Product 1 status to ACTIVE for subsequent tests
        p1.setStatus("ACTIVE");
        productRepository.save(p1);
    }

    // =========================================================================
    // Scenario 13: Category INACTIVE -> CartItem retained, available = false
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("13. Category INACTIVE -> CartItem is retained and available = false")
    void testCategoryInactiveRetainsCartItemWithAvailableFalse() throws Exception {
        // Change Product 1's Category to INACTIVE
        Product p1 = productRepository.findByIdWithDetails(activeProductId1).orElseThrow();
        Category cat = categoryRepository.findById(p1.getCategory().getId()).orElseThrow();
        cat.setStatus("INACTIVE");
        categoryRepository.save(cat);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].categoryStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId1 + ")].available").value(false));

        // Restore category to ACTIVE
        cat.setStatus("ACTIVE");
        categoryRepository.save(cat);
    }

    // =========================================================================
    // Scenario 14: Stock less than Cart quantity -> available = false
    // =========================================================================
    @Test
    @Order(14)
    @DisplayName("14. Stock reduced below Cart quantity -> available = false")
    void testStockDepletedBelowCartQuantityAvailableFalse() throws Exception {
        // Cart has quantity 40 for Product 2. Reduce stock of Product 2 to 20 in DB.
        Product p2 = productRepository.findById(activeProductId2).orElseThrow();
        p2.setStockQuantity(20);
        productRepository.save(p2);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId2 + ")].stockQuantity").value(20))
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId2 + ")].available").value(false));

        // Restore Product 2 stock
        p2.setStockQuantity(50);
        productRepository.save(p2);
    }

    // =========================================================================
    // Scenario 15: Get Cart returns current stockQuantity
    // =========================================================================
    @Test
    @Order(15)
    @DisplayName("15. Get Cart returns real-time stockQuantity")
    void testGetCartReturnsCurrentStockQuantity() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId == " + activeProductId2 + ")].stockQuantity").value(50));
    }

    // =========================================================================
    // Scenario 16: Buyer cannot access or mutate another Buyer's cart
    // =========================================================================
    @Test
    @Order(16)
    @DisplayName("16. Buyer cannot access or mutate another Buyer's cart")
    void testBuyerCannotAccessAnotherBuyersCart() throws Exception {
        // Buyer 2 has empty cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // Buyer 2 tries to update Product 1 which is in Buyer 1's cart
        UpdateCartItemRequest updateReq = UpdateCartItemRequest.builder().quantity(10).build();
        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId1)
                        .header("Authorization", "Bearer " + buyer2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // Buyer 2 tries to delete Product 1 which is in Buyer 1's cart
        mockMvc.perform(delete("/api/v1/cart/items/" + activeProductId1)
                        .header("Authorization", "Bearer " + buyer2Token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Scenario 17: Supplier cannot access Cart (403 Forbidden)
    // =========================================================================
    @Test
    @Order(17)
    @DisplayName("17. Supplier is forbidden from using Cart APIs (403)")
    void testSupplierForbiddenFromCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden());

        AddToCartRequest addReq = AddToCartRequest.builder().productId(activeProductId1).quantity(5).build();
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 18: Admin cannot access Cart (403 Forbidden)
    // =========================================================================
    @Test
    @Order(18)
    @DisplayName("18. Admin is forbidden from using Cart APIs (403)")
    void testAdminForbiddenFromCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        AddToCartRequest addReq = AddToCartRequest.builder().productId(activeProductId1).quantity(5).build();
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 19: Remove Cart Item successfully
    // =========================================================================
    @Test
    @Order(19)
    @DisplayName("19. Remove Cart Item successfully")
    void testRemoveCartItemSuccessfully() throws Exception {
        // Remove Product 2 from Buyer 1's cart
        mockMvc.perform(delete("/api/v1/cart/items/" + activeProductId2)
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify Product 2 is no longer in cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productId", is(activeProductId1.intValue())));
    }

    // =========================================================================
    // Scenario 20: Remove Cart Item not found -> 404
    // =========================================================================
    @Test
    @Order(20)
    @DisplayName("20. Remove non-existent Cart Item returns 404 Not Found")
    void testRemoveNonExistentCartItemReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/999999")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // =========================================================================
    // Scenario 21: Clear Cart successfully
    // =========================================================================
    @Test
    @Order(21)
    @DisplayName("21. Clear Cart successfully")
    void testClearCartSuccessfully() throws Exception {
        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalAmount", is(0)))
                .andExpect(jsonPath("$.data.totalItems", is(0)));
    }

    // =========================================================================
    // Scenario 22: Clear Cart does not affect stock
    // =========================================================================
    @Test
    @Order(22)
    @DisplayName("22. Clear Cart does not alter stock quantity")
    void testClearCartDoesNotAlterStock() {
        Product p1 = productRepository.findById(activeProductId1).orElseThrow();
        Product p2 = productRepository.findById(activeProductId2).orElseThrow();

        assertEquals(100, p1.getStockQuantity());
        assertEquals(50, p2.getStockQuantity());
    }

    // =========================================================================
    // Scenario 23: Cart does not store unitPrice/subtotal in database
    // =========================================================================
    @Test
    @Order(23)
    @DisplayName("23. Database schema verification: carts and cart_items have no unit_price or subtotal columns")
    void testCartTablesDoNotContainPriceColumns() {
        // Query PostgreSQL information_schema to ensure no price/subtotal columns exist
        Integer countPriceInCarts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'carts' AND column_name IN ('unit_price', 'subtotal', 'total_amount')",
                Integer.class
        );
        assertEquals(0, countPriceInCarts);

        Integer countPriceInCartItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'cart_items' AND column_name IN ('unit_price', 'subtotal', 'total_amount')",
                Integer.class
        );
        assertEquals(0, countPriceInCartItems);
    }

    // =========================================================================
    // Scenario 24: Buyer without Cart -> Add Item automatically creates Cart
    // =========================================================================
    @Test
    @Order(24)
    @DisplayName("24. Buyer without Cart record automatically has Cart created upon Add Item")
    void testBuyerWithoutCartAutoCreatesCartOnAddItem() throws Exception {
        // Ensure Buyer 3 has no Cart in DB
        assertFalse(cartRepository.existsByUserId(buyer3UserId));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProductId1)
                .quantity(15)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer3Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantity", is(15)));

        // Verify Cart now exists in DB
        assertTrue(cartRepository.existsByUserId(buyer3UserId));
        Cart buyer3Cart = cartRepository.findByUserId(buyer3UserId).orElseThrow();
        assertNotNull(buyer3Cart.getId());
    }

    // =========================================================================
    // Scenario 25: Database does not allow duplicate (cart_id, product_id)
    // =========================================================================
    @Test
    @Order(25)
    @DisplayName("25. Database enforces UNIQUE(cart_id, product_id) constraint")
    void testDatabaseEnforcesUniqueCartProductConstraint() {
        Cart buyer3Cart = cartRepository.findByUserId(buyer3UserId).orElseThrow();
        Product product = productRepository.findById(activeProductId1).orElseThrow();

        // There is already a CartItem for (buyer3Cart, product). Attempting direct JDBC insert must fail.
        assertThrows(DataIntegrityViolationException.class, () -> {
            CartItem duplicateItem = CartItem.builder()
                    .cart(buyer3Cart)
                    .product(product)
                    .quantity(5)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            cartItemRepository.saveAndFlush(duplicateItem);
        });
    }

    // =========================================================================
    // Scenario 26: Product not found -> 404
    // =========================================================================
    @Test
    @Order(26)
    @DisplayName("26. Adding non-existent product returns 404 Not Found")
    void testAddNonExistentProductReturns404() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(999999L)
                .quantity(5)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // =========================================================================
    // Scenario 27: Category inactive when adding -> reject (400)
    // =========================================================================
    @Test
    @Order(27)
    @DisplayName("27. Adding product with inactive category returns 400 Bad Request")
    void testAddProductWithInactiveCategoryRejected() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(inactiveCatProductId)
                .quantity(5)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("category is inactive")));
    }

    // =========================================================================
    // Scenario 28: Product inactive when adding -> reject (400)
    // =========================================================================
    @Test
    @Order(28)
    @DisplayName("28. Adding inactive product returns 400 Bad Request")
    void testAddInactiveProductRejected() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(inactiveProductId)
                .quantity(5)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Product is inactive")));
    }

    // =========================================================================
    // Scenario 29: Quantity <= 0 -> validation error (400)
    // =========================================================================
    @Test
    @Order(29)
    @DisplayName("29. Quantity <= 0 returns validation error (400 Bad Request)")
    void testInvalidQuantityRejected() throws Exception {
        // Add with 0 quantity
        AddToCartRequest addZero = AddToCartRequest.builder().productId(activeProductId1).quantity(0).build();
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addZero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        // Add with negative quantity
        AddToCartRequest addNegative = AddToCartRequest.builder().productId(activeProductId1).quantity(-5).build();
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addNegative)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        // Update with 0 quantity
        UpdateCartItemRequest updateZero = UpdateCartItemRequest.builder().quantity(0).build();
        mockMvc.perform(put("/api/v1/cart/items/" + activeProductId1)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateZero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // =========================================================================
    // Scenario 30: Multi-supplier Cart items coexist without conflict
    // =========================================================================
    @Test
    @Order(30)
    @DisplayName("30. Cart successfully holds products from multiple distinct suppliers")
    void testCartHoldsProductsFromMultipleSuppliers() throws Exception {
        // Clear buyer 1's cart first
        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk());

        // Add Product 1 (Supplier 1)
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AddToCartRequest.builder().productId(activeProductId1).quantity(10).build())))
                .andExpect(status().isCreated());

        // Add Product 2 (Supplier 2)
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AddToCartRequest.builder().productId(activeProductId2).quantity(5).build())))
                .andExpect(status().isCreated());

        // Verify cart has both products with distinct suppliers
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalItems", is(2)))
                .andExpect(jsonPath("$.data.totalAmount", is(2000000.00))); // 10 * 100,000 + 5 * 200,000 = 2,000,000
    }

    // =========================================================================
    // Scenario 31: Quantity on exact boundaries of tiers (min and max)
    // =========================================================================
    @Test
    @Order(31)
    @DisplayName("31. Quantity exactly on minQuantity or maxQuantity resolves to correct tier")
    void testExactTierBoundaries() throws Exception {
        // Clear cart first
        mockMvc.perform(delete("/api/v1/cart").header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk());

        // Add 1 (min of tier 1) -> 100,000
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AddToCartRequest.builder().productId(boundedTierProductId).quantity(1).build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice", is(100000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(100000.00)));

        // Update to 49 (max of tier 1) -> 100,000
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(49).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(100000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(4900000.00)));

        // Update to 50 (min of tier 2) -> 95,000
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(50).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(95000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(4750000.00)));

        // Update to 99 (max of tier 2) -> 95,000
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(99).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(95000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(9405000.00)));

        // Update to 100 (min of tier 3) -> 90,000
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(100).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(9000000.00)));

        // Update to 500 (max of tier 3) -> 90,000
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(500).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(45000000.00)));
    }

    // =========================================================================
    // Scenario 32: Quantity exceeds maxQuantity of last tier -> fallback to last tier
    // =========================================================================
    @Test
    @Order(32)
    @DisplayName("32. Quantity = 501 (exceeds max 500 of last tier) falls back to last tier price 90,000")
    void testQuantityExceedsLastTierFallsBackToLastTierPrice() throws Exception {
        // Update to 501 (exceeds tier 3 maxQuantity 500)
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(501).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quantity", is(501)))
                .andExpect(jsonPath("$.data.unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(45090000.00)))
                .andExpect(jsonPath("$.data.stockQuantity", is(1000)))
                .andExpect(jsonPath("$.data.available", is(true)));
    }

    // =========================================================================
    // Scenario 33: GET Cart with quantity exceeding last tier returns non-null prices
    // =========================================================================
    @Test
    @Order(33)
    @DisplayName("33. GET /cart with quantity exceeding last tier returns non-null unitPrice and subtotal")
    void testGetCartWithQuantityExceedingLastTier() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[0].quantity", is(501)))
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.items[0].subtotal", is(45090000.00)))
                .andExpect(jsonPath("$.data.totalAmount", is(45090000.00)))
                .andExpect(jsonPath("$.data.items[0].available", is(true)));
    }

    // =========================================================================
    // Scenario 34: Quantity much larger than max of last tier (quantity = 1000)
    // =========================================================================
    @Test
    @Order(34)
    @DisplayName("34. Quantity much larger than last tier max (1000 > 500) still uses last tier price when stock allows")
    void testQuantityMuchLargerThanLastTierUsesLastTierPrice() throws Exception {
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(1000).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity", is(1000)))
                .andExpect(jsonPath("$.data.unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.subtotal", is(90000000.00)))
                .andExpect(jsonPath("$.data.available", is(true)));
    }

    // =========================================================================
    // Scenario 35: Quantity exceeding last tier but also exceeding stock -> rejected
    // =========================================================================
    @Test
    @Order(35)
    @DisplayName("35. Quantity exceeding stock (1001 > 1000) is rejected according to stock rule")
    void testQuantityExceedingStockRejectedEvenWithFallback() throws Exception {
        mockMvc.perform(put("/api/v1/cart/items/" + boundedTierProductId)
                        .header("Authorization", "Bearer " + buyer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateCartItemRequest.builder().quantity(1001).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("exceeds available stock")));
    }

    // =========================================================================
    // Scenario 36: Product inactive with quantity exceeding last tier
    // =========================================================================
    @Test
    @Order(36)
    @DisplayName("36. Product INACTIVE with quantity > last tier retains price calculation but available = false")
    void testInactiveProductWithQuantityExceedingLastTier() throws Exception {
        Product p5 = productRepository.findById(boundedTierProductId).orElseThrow();
        p5.setStatus("INACTIVE");
        productRepository.save(p5);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.items[0].subtotal", is(90000000.00)))
                .andExpect(jsonPath("$.data.items[0].productStatus", is("INACTIVE")))
                .andExpect(jsonPath("$.data.items[0].available", is(false)));

        // Restore
        p5.setStatus("ACTIVE");
        productRepository.save(p5);
    }

    // =========================================================================
    // Scenario 37: Category inactive with quantity exceeding last tier
    // =========================================================================
    @Test
    @Order(37)
    @DisplayName("37. Category INACTIVE with quantity > last tier retains price calculation but available = false")
    void testInactiveCategoryWithQuantityExceedingLastTier() throws Exception {
        Product p5 = productRepository.findByIdWithDetails(boundedTierProductId).orElseThrow();
        Category cat = categoryRepository.findById(p5.getCategory().getId()).orElseThrow();
        cat.setStatus("INACTIVE");
        categoryRepository.save(cat);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(90000.00)))
                .andExpect(jsonPath("$.data.items[0].subtotal", is(90000000.00)))
                .andExpect(jsonPath("$.data.items[0].categoryStatus", is("INACTIVE")))
                .andExpect(jsonPath("$.data.items[0].available", is(false)));

        // Restore category
        cat.setStatus("ACTIVE");
        categoryRepository.save(cat);

        // Clean up cart items at the end of test class
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @AfterAll
    static void tearDown(
            @Autowired CartItemRepository cartItemRepository,
            @Autowired CartRepository cartRepository,
            @Autowired ProductPriceRepository productPriceRepository,
            @Autowired ProductRepository productRepository
    ) {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        // Chỉ xóa sản phẩm test của riêng test class này, không xóa dữ liệu người dùng
        List<String> testSkus = List.of("CART-SKU-001", "CART-SKU-002", "CART-SKU-INACTIVE", "CART-SKU-INAC-CAT", "CART-SKU-BOUNDED-005");
        for (String sku : testSkus) {
            productRepository.findAll().stream()
                    .filter(p -> sku.equalsIgnoreCase(p.getSku()))
                    .findFirst()
                    .ifPresent(p -> {
                        productPriceRepository.deleteAll(productPriceRepository.findByProductId(p.getId()));
                        productRepository.delete(p);
                    });
        }
    }

}
