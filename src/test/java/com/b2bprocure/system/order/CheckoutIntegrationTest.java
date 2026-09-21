package com.b2bprocure.system.order;

import com.b2bprocure.system.cart.entity.Cart;
import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.dto.CheckoutRequest;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("Checkout Integration Tests")
public class CheckoutIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String buyerToken;
    private String buyer2Token;
    private String supplierToken;
    private String adminToken;

    private User buyerUser;
    private User buyer2User;
    private Company buyerCompany;
    private Company supplierCompany1;
    private Company supplierCompany2;
    private Category category;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Setup Companies
        supplierCompany1 = companyRepository.save(new Company(null, "Test Supplier 1", "TAX-CHK-S1-" + System.currentTimeMillis(),
                "chk-sup1@example.com", "0911111111", "456 Supplier St 1", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        supplierCompany2 = companyRepository.save(new Company(null, "Test Supplier 2", "TAX-CHK-S2-" + System.currentTimeMillis(),
                "chk-sup2@example.com", "0922222222", "789 Supplier St 2", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        category = categoryRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(null, "Checkout Test Cat " + System.currentTimeMillis(),
                        "Desc", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));

        // 2. Setup Users & Tokens
        buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        buyerCompany = buyerUser.getCompany();
        buyerCompany.setName("B2B Retail Corporation");
        buyerCompany.setPhone("0901234567");
        buyerCompany.setAddress("123 Nguyen Trai, Ha Noi");
        buyerCompany = companyRepository.save(buyerCompany);

        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));

        User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

        User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));

        buyer2User = userRepository.findByUsernameWithRoleAndCompany("buyer2").orElseGet(() -> {
            User u = new User();
            u.setUsername("buyer2");
            u.setEmail("buyer2@example.com");
            u.setPassword(buyerUser.getPassword());
            u.setFullName("Buyer 2 Test");
            u.setRole(buyerUser.getRole());
            u.setCompany(buyerCompany);
            u.setStatus("ACTIVE");
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
        buyer2User = userRepository.findByIdWithRoleAndCompany(buyer2User.getId()).orElseThrow();
        buyer2Token = jwtTokenProvider.generateToken(UserPrincipal.create(buyer2User));

        // Clean existing cart items for buyer
        cartRepository.findByUserId(buyerUser.getId()).ifPresent(cart -> {
            cartItemRepository.deleteAll(cartItemRepository.findByCartIdWithProductDetails(cart.getId()));
        });
        cartRepository.findByUserId(buyer2User.getId()).ifPresent(cart -> {
            cartItemRepository.deleteAll(cartItemRepository.findByCartIdWithProductDetails(cart.getId()));
        });
    }

    @AfterEach
    void tearDown() {
        // Clean created orders and cascade components
        for (Long orderId : createdOrderIds) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        createdOrderIds.clear();

        // Clean created cart items and carts
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        // Clean created products
        for (Long productId : createdProductIds) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(productId));
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();

        companyRepository.delete(supplierCompany1);
        companyRepository.delete(supplierCompany2);

        userRepository.findByUsername("buyer2").ifPresent(userRepository::delete);
    }

    @org.junit.jupiter.api.AfterAll
    static void cleanUpAll(
            @Autowired CartItemRepository cartItemRepository,
            @Autowired CartRepository cartRepository,
            @Autowired UserRepository userRepository
    ) {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        userRepository.findByUsername("buyer2").ifPresent(userRepository::delete);
    }

    private Product createProduct(Company supplier, String name, int stock, int reserved, String status, Category cat) {
        Product p = new Product();
        p.setSupplierCompany(supplier);
        p.setCategory(cat != null ? cat : category);
        p.setSku("CHK-SKU-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        p.setName(name);
        p.setStockQuantity(stock);
        p.setReservedQuantity(reserved);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(p);
        createdProductIds.add(saved.getId());
        return saved;
    }

    private void createPriceTier(Product product, int min, Integer max, BigDecimal price) {
        ProductPrice pp = new ProductPrice();
        pp.setProduct(product);
        pp.setMinQuantity(min);
        pp.setMaxQuantity(max);
        pp.setUnitPrice(price);
        pp.setCreatedAt(LocalDateTime.now());
        pp.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(pp);
    }

    private CartItem addItemToUserCart(User user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart c = Cart.builder().user(user).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            return cartRepository.save(c);
        });

        CartItem item = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return cartItemRepository.save(item);
    }

    // =========================================================================
    // SUCCESS TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 1: Successful Checkout with COD payment method")
    void testCheckout_COD_Success() throws Exception {
        Product p1 = createProduct(supplierCompany1, "Product 1", 100, 10, "ACTIVE", category);
        createPriceTier(p1, 1, 10, new BigDecimal("100000.00"));

        Product p2 = createProduct(supplierCompany1, "Product 2", 50, 5, "ACTIVE", category);
        createPriceTier(p2, 1, null, new BigDecimal("200000.00"));

        CartItem item1 = addItemToUserCart(buyerUser, p1, 5);
        CartItem item2 = addItemToUserCart(buyerUser, p2, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item1.getId(), item2.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderCode", notNullValue()))
                .andExpect(jsonPath("$.data.paymentMethod", is("COD")))
                .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                .andExpect(jsonPath("$.data.orderStatus", is("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.data.subtotal", is(900000.00)))
                .andExpect(jsonPath("$.data.totalAmount", is(900000.00)))
                .andExpect(jsonPath("$.data.paymentExpiredAt", nullValue()))
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        // Verify Database Changes
        // 1. Products reserved quantity increased, stock unchanged
        Product updatedP1 = productRepository.findById(p1.getId()).orElseThrow();
        assertThat(updatedP1.getReservedQuantity()).isEqualTo(15);
        assertThat(updatedP1.getStockQuantity()).isEqualTo(100);
        assertThat(updatedP1.getAvailableQuantity()).isEqualTo(85);

        Product updatedP2 = productRepository.findById(p2.getId()).orElseThrow();
        assertThat(updatedP2.getReservedQuantity()).isEqualTo(7);
        assertThat(updatedP2.getStockQuantity()).isEqualTo(50);
        assertThat(updatedP2.getAvailableQuantity()).isEqualTo(43);

        // 2. CartItems deleted
        assertThat(cartItemRepository.findById(item1.getId())).isEmpty();
        assertThat(cartItemRepository.findById(item2.getId())).isEmpty();

        // 3. Order created with snapshot shipping info
        Order order = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(order.getShippingCompanyName()).isEqualTo("B2B Retail Corporation");
        assertThat(order.getShippingPhone()).isEqualTo("0901234567");
        assertThat(order.getShippingAddress()).isEqualTo("123 Nguyen Trai, Ha Noi");
        assertThat(order.getCommissionRate()).isNull();
        assertThat(order.getCommissionAmount()).isNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo(order.getSubtotal());

        // 4. OrderStatusHistory created
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(history.get(0).getChangedBy().getId()).isEqualTo(buyerUser.getId());

        // 5. OrderItems created with snapshots
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        assertThat(items).hasSize(2);

        // 6. Payment created
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("900000.00");
        assertThat(payment.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("Case 2: Successful Checkout with Online payment method (ZALOPAY) has expiredAt")
    void testCheckout_ZALOPAY_Success() throws Exception {
        Product p = createProduct(supplierCompany1, "ZaloPay Product", 100, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("50000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 10);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.ZALOPAY)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.paymentMethod", is("ZALOPAY")))
                .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                .andExpect(jsonPath("$.data.paymentExpiredAt", notNullValue()))
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.ZALOPAY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getExpiredAt()).isAfter(LocalDateTime.now());
    }

    // =========================================================================
    // FAILURE TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 3: Empty cartItemIds is rejected with 400")
    void testCheckout_EmptyCartItemIds_Rejected() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of())
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Case 4: Non-existent cartItem is rejected with 404")
    void testCheckout_CartItemNotFound_Rejected() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(999999L))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("CartItem")));
    }

    @Test
    @DisplayName("Case 5: CartItem belonging to another buyer is rejected with 400")
    void testCheckout_CartItemBelongsToAnotherBuyer_Rejected() throws Exception {
        Product p = createProduct(supplierCompany1, "Other Buyer Prod", 50, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("10000.00"));
        CartItem itemOfBuyer2 = addItemToUserCart(buyer2User, p, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(itemOfBuyer2.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken) // buyerUser tries to checkout buyer2User's item
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to the current user's cart")));
    }

    @Test
    @DisplayName("Case 6: Mixed suppliers in one checkout is rejected with 400")
    void testCheckout_MixedSuppliers_Rejected() throws Exception {
        Product p1 = createProduct(supplierCompany1, "Sup 1 Product", 50, 0, "ACTIVE", category);
        createPriceTier(p1, 1, null, new BigDecimal("10000.00"));

        Product p2 = createProduct(supplierCompany2, "Sup 2 Product", 50, 0, "ACTIVE", category);
        createPriceTier(p2, 1, null, new BigDecimal("20000.00"));

        CartItem item1 = addItemToUserCart(buyerUser, p1, 1);
        CartItem item2 = addItemToUserCart(buyerUser, p2, 1);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item1.getId(), item2.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot contain products from multiple suppliers")));
    }

    @Test
    @DisplayName("Case 7: Inactive product is rejected with 400")
    void testCheckout_InactiveProduct_Rejected() throws Exception {
        Product inactiveProduct = createProduct(supplierCompany1, "Inactive Prod", 50, 0, "INACTIVE", category);
        createPriceTier(inactiveProduct, 1, null, new BigDecimal("10000.00"));
        CartItem item = addItemToUserCart(buyerUser, inactiveProduct, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive or unavailable")));
    }

    @Test
    @DisplayName("Case 8: Inactive category is rejected with 400")
    void testCheckout_InactiveCategory_Rejected() throws Exception {
        Category inactiveCategory = categoryRepository.save(new Category(null, "Inactive Cat " + System.currentTimeMillis(),
                "Desc", "INACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        Product p = createProduct(supplierCompany1, "Inac Cat Prod", 50, 0, "ACTIVE", inactiveCategory);
        createPriceTier(p, 1, null, new BigDecimal("10000.00"));
        CartItem item = addItemToUserCart(buyerUser, p, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("category is inactive")));
    }

    @Test
    @DisplayName("Case 9: Insufficient stock is rejected with 400")
    void testCheckout_InsufficientStock_Rejected() throws Exception {
        Product p = createProduct(supplierCompany1, "Low Stock Prod", 10, 8, "ACTIVE", category); // available = 2
        createPriceTier(p, 1, null, new BigDecimal("10000.00"));
        CartItem item = addItemToUserCart(buyerUser, p, 5); // requested 5 > available 2

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify reserved_quantity is NOT changed
        Product after = productRepository.findById(p.getId()).orElseThrow();
        assertThat(after.getReservedQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("Case 10: Missing shipping info in buyer company is rejected with 400")
    void testCheckout_MissingShippingInfo_Rejected() throws Exception {
        buyerCompany.setAddress(null); // Missing address
        companyRepository.save(buyerCompany);

        Product p = createProduct(supplierCompany1, "Normal Prod", 50, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("10000.00"));
        CartItem item = addItemToUserCart(buyerUser, p, 1);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Missing shipping information")));

        // Restore shipping address for subsequent tests
        buyerCompany.setAddress("123 Nguyen Trai, Ha Noi");
        companyRepository.save(buyerCompany);
    }

    @Test
    @DisplayName("Case 11: No matching price tier for requested quantity is rejected with 400")
    void testCheckout_NoMatchingPriceTier_Rejected() throws Exception {
        Product p = createProduct(supplierCompany1, "Tier Prod", 100, 0, "ACTIVE", category);
        createPriceTier(p, 10, 50, new BigDecimal("10000.00")); // Tier starts at 10
        CartItem item = addItemToUserCart(buyerUser, p, 2); // Quantity = 2 < 10

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No matching price tier")));
    }

    @Test
    @DisplayName("Case 12: Unauthenticated user is rejected with 401")
    void testCheckout_Unauthenticated_Rejected() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(1L))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Case 13: Non-buyer role (Supplier, Admin) is rejected with 403")
    void testCheckout_NonBuyerRole_Rejected() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(1L))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // ROLLBACK TEST
    // =========================================================================

    @Test
    @DisplayName("Case 14: Transaction rollback leaves stock, cart, and orders completely unchanged on failure")
    void testCheckout_RollbackOnFailure() throws Exception {
        Product p1 = createProduct(supplierCompany1, "P1 Enough Stock", 100, 10, "ACTIVE", category);
        createPriceTier(p1, 1, null, new BigDecimal("100000.00"));

        Product p2 = createProduct(supplierCompany1, "P2 Out Of Stock", 5, 5, "ACTIVE", category); // available = 0
        createPriceTier(p2, 1, null, new BigDecimal("50000.00"));

        CartItem item1 = addItemToUserCart(buyerUser, p1, 10);
        CartItem item2 = addItemToUserCart(buyerUser, p2, 5);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item1.getId(), item2.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        // Must fail because p2 has 0 available stock
        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify rollback on p1: reserved quantity must remain 10 (not 20!)
        Product checkP1 = productRepository.findById(p1.getId()).orElseThrow();
        assertThat(checkP1.getReservedQuantity()).isEqualTo(10);
        assertThat(checkP1.getStockQuantity()).isEqualTo(100);

        // Verify both cart items still exist
        assertThat(cartItemRepository.findById(item1.getId())).isPresent();
        assertThat(cartItemRepository.findById(item2.getId())).isPresent();

        // Verify no order was created
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedBy().getId().equals(buyerUser.getId()) && o.getSubtotal() != null && o.getSubtotal().compareTo(new BigDecimal("1000000.00")) == 0)
                .toList();
        assertThat(orders).isEmpty();
    }

    // =========================================================================
    // SELECTIVE CART ITEM DELETION TEST
    // =========================================================================

    @Test
    @DisplayName("Case 15: Only selected CartItems are deleted; unselected items remain in Cart")
    void testCheckout_SelectedCartItemsDeleted_OthersRetained() throws Exception {
        Product p1 = createProduct(supplierCompany1, "Item A", 100, 0, "ACTIVE", category);
        createPriceTier(p1, 1, null, new BigDecimal("10000.00"));

        Product p2 = createProduct(supplierCompany1, "Item B", 100, 0, "ACTIVE", category);
        createPriceTier(p2, 1, null, new BigDecimal("20000.00"));

        Product p3 = createProduct(supplierCompany1, "Item C", 100, 0, "ACTIVE", category);
        createPriceTier(p3, 1, null, new BigDecimal("30000.00"));

        CartItem itemA = addItemToUserCart(buyerUser, p1, 1);
        CartItem itemB = addItemToUserCart(buyerUser, p2, 1);
        CartItem itemC = addItemToUserCart(buyerUser, p3, 1);

        // Checkout only [A, C]
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(itemA.getId(), itemC.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        // Verify A and C are deleted, while B remains
        assertThat(cartItemRepository.findById(itemA.getId())).isEmpty();
        assertThat(cartItemRepository.findById(itemC.getId())).isEmpty();
        assertThat(cartItemRepository.findById(itemB.getId())).isPresent();
    }

    // =========================================================================
    // PRICE TIER BOUNDARY TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 16: Price tier boundaries correctly match unit prices at 10, 11, 50, 51")
    void testCheckout_TierBoundaryPricing() throws Exception {
        Product p = createProduct(supplierCompany1, "Tier Test Prod", 200, 0, "ACTIVE", category);
        // Tiers: 1-10 -> 100,000; 11-50 -> 90,000; 51+ -> 80,000
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 50, new BigDecimal("90000.00"));
        createPriceTier(p, 51, null, new BigDecimal("80000.00"));

        // Boundary 1: Quantity = 10 -> Tier 1 (100,000) -> total = 1,000,000
        CartItem item10 = addItemToUserCart(buyerUser, p, 10);
        MvcResult res10 = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(List.of(item10.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(1000000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res10.getResponse().getContentAsString()).path("data").path("orderId").asLong());

        // Boundary 2: Quantity = 11 -> Tier 2 (90,000) -> total = 990,000
        CartItem item11 = addItemToUserCart(buyerUser, p, 11);
        MvcResult res11 = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(List.of(item11.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(990000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res11.getResponse().getContentAsString()).path("data").path("orderId").asLong());

        // Boundary 3: Quantity = 50 -> Tier 2 (90,000) -> total = 4,500,000
        CartItem item50 = addItemToUserCart(buyerUser, p, 50);
        MvcResult res50 = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(List.of(item50.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(4500000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res50.getResponse().getContentAsString()).path("data").path("orderId").asLong());

        // Boundary 4: Quantity = 51 -> Tier 3 (80,000) -> total = 4,080,000
        CartItem item51 = addItemToUserCart(buyerUser, p, 51);
        MvcResult res51 = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(List.of(item51.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(4080000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res51.getResponse().getContentAsString()).path("data").path("orderId").asLong());
    }

    // =========================================================================
    // RESERVATION CALCULATION TEST
    // =========================================================================

    @Test
    @DisplayName("Case 17: Stock reservation increases reserved_quantity and maintains invariants")
    void testCheckout_StockReservationCalculation() throws Exception {
        // Stock = 100, Reserved = 30 -> Available = 70
        Product p = createProduct(supplierCompany1, "Reservation Math Prod", 100, 30, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("50000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 20);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        Product after = productRepository.findById(p.getId()).orElseThrow();
        assertThat(after.getStockQuantity()).isEqualTo(100);
        assertThat(after.getReservedQuantity()).isEqualTo(50);
        assertThat(after.getAvailableQuantity()).isEqualTo(50);
    }

    // =========================================================================
    // PRICE TIER NO MATCH TEST (BUG FIX 2)
    // =========================================================================

    @Test
    @DisplayName("Case 18: Checkout fails when quantity does not match any price tier (no fallback, rollback occurs)")
    void testCheckout_PriceTierNoMatch_Rejected() throws Exception {
        Product p = createProduct(supplierCompany1, "Tier Gap Prod", 100, 0, "ACTIVE", category);
        // Tiers: 1-10 -> 100,000; 20-50 -> 90,000; 51+ -> 80,000 (gap between 11 and 19)
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 20, 50, new BigDecimal("90000.00"));
        createPriceTier(p, 51, null, new BigDecimal("80000.00"));

        // Quantity = 15 does not match any tier
        CartItem item = addItemToUserCart(buyerUser, p, 15);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No matching price tier found")));

        // Verify rollback: reserved quantity must remain 0
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(0);

        // Verify CartItem is not deleted
        assertThat(cartItemRepository.findById(item.getId())).isPresent();

        // Verify no order was created
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedBy().getId().equals(buyerUser.getId()) && o.getStatus() == OrderStatus.PENDING_CONFIRMATION)
                .toList();
        assertThat(orders).isEmpty();
    }

    // =========================================================================
    // COMMISSION HANDLING TEST (BUG FIX 1)
    // =========================================================================

    @Test
    @DisplayName("Case 19: Order created at Checkout has null commissionRate and commissionAmount")
    void testCheckout_CommissionNotCalculatedAtCheckout() throws Exception {
        Product p = createProduct(supplierCompany1, "Commission Check Prod", 50, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("200000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getCommissionRate()).isNull();
        assertThat(order.getCommissionAmount()).isNull();
        assertThat(order.getSubtotal()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
    }

    // =========================================================================
    // DUPLICATE CART ITEM IDS DEDUPLICATION TEST (CHECK 8)
    // =========================================================================

    @Test
    @DisplayName("Case 20: Duplicate CartItem IDs in request are deduplicated and do not cause double reservation")
    void testCheckout_DuplicateCartItemIds_DeduplicatedSafely() throws Exception {
        Product p = createProduct(supplierCompany1, "Deduplicate Prod", 100, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("50000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 5);

        // Pass duplicate cart item ID: [item.getId(), item.getId()]
        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId(), item.getId()))
                .paymentMethod(PaymentMethod.COD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        createdOrderIds.add(orderId);

        // Reserved quantity must be 5, NOT 10
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(5);

        // Only 1 OrderItem should be created
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(5);

        // CartItem must be deleted
        assertThat(cartItemRepository.findById(item.getId())).isEmpty();
    }

    // =========================================================================
    // UNSUPPORTED PAYMENT METHOD TEST (CHECK 9)
    // =========================================================================

    @Test
    @DisplayName("Case 21: Unsupported payment method (MOMO) is rejected with 400 Bad Request")
    void testCheckout_UnsupportedPaymentMethod_Rejected() throws Exception {
        Product p = createProduct(supplierCompany1, "Momo Prod", 50, 0, "ACTIVE", category);
        createPriceTier(p, 1, null, new BigDecimal("100000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 1);

        CheckoutRequest request = CheckoutRequest.builder()
                .cartItemIds(List.of(item.getId()))
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is not supported for checkout")));

        // Verify no order created and cart item untouched
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(0);
        assertThat(cartItemRepository.findById(item.getId())).isPresent();
    }

    // =========================================================================
    // PRICE TIER FALLBACK TESTS (last tier's max is a marker, not a cap)
    // =========================================================================

    /**
     * Helper: extract the subtotal of the most recent order created by the
     * current checkout call. Used to assert the unit-price fallback rule.
     */
    private BigDecimal extractSubtotal(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("subtotal").decimalValue();
    }

    @Test
    @DisplayName("Case 22: Quantity 31 (exceeds last tier max=30) uses last tier price 80,000")
    void testCheckout_FallbackLastTier_Quantity31() throws Exception {
        Product p = createProduct(supplierCompany1, "Fallback Tier Prod 31", 200, 0, "ACTIVE", category);
        // Tiers: 1-10 -> 100,000; 11-20 -> 90,000; 21-30 -> 80,000
        // No tier has max_quantity = null. Last tier is 21-30.
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 20, new BigDecimal("90000.00"));
        createPriceTier(p, 21, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 31);
        MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.subtotal", is(31 * 80000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("Case 23: Quantity 41 (exceeds last tier max=30) uses last tier price 80,000")
    void testCheckout_FallbackLastTier_Quantity41() throws Exception {
        Product p = createProduct(supplierCompany1, "Fallback Tier Prod 41", 200, 0, "ACTIVE", category);
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 20, new BigDecimal("90000.00"));
        createPriceTier(p, 21, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 41);
        MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(41 * 80000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("Case 24: Quantity 100 (way above last tier max=30) uses last tier price 80,000")
    void testCheckout_FallbackLastTier_Quantity100() throws Exception {
        Product p = createProduct(supplierCompany1, "Fallback Tier Prod 100", 200, 0, "ACTIVE", category);
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 20, new BigDecimal("90000.00"));
        createPriceTier(p, 21, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 100);
        MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(100 * 80000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("Case 25: Quantity 30 (exact last tier max=30) still uses last tier price 80,000 (no fallback needed)")
    void testCheckout_LastTierMaxBoundary_Quantity30() throws Exception {
        Product p = createProduct(supplierCompany1, "Exact Max Boundary", 200, 0, "ACTIVE", category);
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 20, new BigDecimal("90000.00"));
        createPriceTier(p, 21, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 30);
        MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(30 * 80000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("Case 26: Quantity 5 (within first tier) uses first tier price 100,000 (regression check)")
    void testCheckout_FirstTier_Quantity5() throws Exception {
        Product p = createProduct(supplierCompany1, "First Tier Prod", 200, 0, "ACTIVE", category);
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 11, 20, new BigDecimal("90000.00"));
        createPriceTier(p, 21, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 5);
        MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal", is(5 * 100000.00)))
                .andReturn();
        createdOrderIds.add(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("Case 27: Quantity below all tiers (e.g. 5 when min=10) is still rejected (no fallback)")
    void testCheckout_BelowAllTiers_StillRejected() throws Exception {
        // Tiers start at 10. quantity=5 falls below all tiers; not > max of last
        // tier, so no fallback applies. Should still throw NO_MATCHING_PRICE_TIER.
        Product p = createProduct(supplierCompany1, "Below All Tiers Prod", 100, 0, "ACTIVE", category);
        createPriceTier(p, 10, 50, new BigDecimal("100000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 5);
        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No matching price tier")));

        // Verify rollback: reservation is zero, cart item remains.
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(0);
        assertThat(cartItemRepository.findById(item.getId())).isPresent();
    }

    @Test
    @DisplayName("Case 28: Quantity in tier gap (e.g. 15 with tiers 1-10, 20-30) is still rejected (no fallback)")
    void testCheckout_QuantityInGap_StillRejected() throws Exception {
        // Tiers: 1-10 and 20-30. No tier covers 11-19.
        // quantity=15 is not > 30 (last max) → no fallback.
        Product p = createProduct(supplierCompany1, "Gap Prod", 100, 0, "ACTIVE", category);
        createPriceTier(p, 1, 10, new BigDecimal("100000.00"));
        createPriceTier(p, 20, 30, new BigDecimal("80000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 15);
        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No matching price tier")));

        // Verify rollback
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Case 29: Quantity 0 (invalid) is rejected (no fallback)")
    void testCheckout_QuantityZero_StillRejected() throws Exception {
        // Tier covers 1-100, quantity = 0 is invalid input → caught earlier
        // by INVALID_CART_ITEM validation, not converted into fallback tier.
        Product p = createProduct(supplierCompany1, "Zero Qty Prod", 100, 0, "ACTIVE", category);
        createPriceTier(p, 1, 100, new BigDecimal("50000.00"));

        CartItem item = addItemToUserCart(buyerUser, p, 0);
        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(List.of(item.getId()), PaymentMethod.COD))))
                .andExpect(status().isBadRequest());

        // Verify rollback
        Product checkP = productRepository.findById(p.getId()).orElseThrow();
        assertThat(checkP.getReservedQuantity()).isEqualTo(0);
    }

}
