# Tóm tắt ZaloPay Step 3 — Tích hợp thanh toán & Quản lý vòng đời Order

> **Commit:** `c4370ca` — `feat(order): add ZaloPay payment integration and order management features`
> **Phạm vi:** Tích hợp ZaloPay sandbox + toàn bộ vòng đời Order (confirm → prepare → ship → complete, reject, cancel)
> **Trạng thái:** ✅ Build SUCCESS, 348 tests pass (sau khi fix duplicate key trong `application.yml`)

---

## 1. Mục tiêu

Step 3 mở rộng hệ thống Order từ "checkout thuần" thành "fulfillment đầy đủ":

| Trước (Step 2) | Sau (Step 3) |
|---|---|
| Tạo Order + Payment từ Cart | Thêm ZaloPay online payment |
| | Supplier xác nhận / chuẩn bị / giao / hoàn thành |
| | Supplier từ chối đơn (có lý do) |
| | Buyer hủy đơn (có lý do) |
| | Order status history audit đầy đủ |
| | CORS cho frontend test |

---

## 2. Kiến trúc giải pháp

### 2.1. Flow tổng thể

```text
Buyer                        Frontend                Backend                       ZaloPay
  │                             │                        │                              │
  │ 1. Login                    │                        │                              │
  ├────────────────────────────▶│                        │                              │
  │                             │  POST /auth/login      │                              │
  │                             ├───────────────────────▶│                              │
  │                             │◀── accessToken ────────│                              │
  │                             │                        │                              │
  │ 2. Checkout (COD/ZaloPay)   │                        │                              │
  ├────────────────────────────▶│  POST /checkout        │                              │
  │                             ├───────────────────────▶│                              │
  │                             │                        │  reserve stock                │
  │                             │                        │  create Order + Payment(PENDING)│
  │                             │◀── orderId,paymentId ──│                              │
  │                             │                        │                              │
  │ (chỉ ZaloPay)               │                        │                              │
  │ 3. Lấy payment URL          │  POST /checkout/       │                              │
  ├────────────────────────────▶│       zalopay/         │                              │
  │                             │       create-payment   │  call /v2/create             │
  │                             ├───────────────────────▶├─────────────────────────────▶│
  │                             │                        │◀── orderUrl, zp_trans_token ─│
  │                             │◀── paymentUrl ─────────│                              │
  │                             │                        │                              │
  │ 4. Thanh toán               │ window.location =      │                              │
  │                             │ paymentUrl             │                              │
  │                             │ ─────────────────────────────────────────────────▶ User pays
  │                             │                        │  callback POST /payments/     │
  │                             │                        │  zalopay/callback             │
  │                             │                        │◀─────────────────────────────│
  │                             │                        │  verify MAC, update DB       │
  │                             │                        │  Payment→SUCCESS, Order→PAID  │
  │                             │                        │                              │
  │ 5. Polling                  │  GET /orders/{id}      │                              │
  ├────────────────────────────▶├───────────────────────▶│                              │
  │                             │◀── status=PAID ────────│                              │
```

### 2.2. State machine Order

```text
[PENDING_CONFIRMATION]
    │       │           │           │
    │       │           │           └──▶ [REJECTED] (supplier, có reason)
    │       │           └──▶ [CANCELLED] (buyer, có reason)
    │       │                   ↑
    │       │                   │ (Online Paid → REFUND_PENDING giữ reservation)
    │       │
    │       └──▶ [PAID]   (chỉ online, sau ZaloPay callback SUCCESS)
    │              │
    │              │ supplier confirm
    │              ▼
    │       [CONFIRMED] ──▶ [PREPARING] ──▶ [SHIPPING] ──▶ [COMPLETED]
    │                                                       (COD: Payment→SUCCESS)
```

> **Rule nghiệp thức** trong `OrderStatus.canTransitionTo(target, paymentMethod)`:
> - COD: `PENDING_CONFIRMATION → CONFIRMED` (skip PAID)
> - Online: `PENDING_CONFIRMATION → PAID` (chỉ qua callback), `PAID → CONFIRMED`
> - Cả hai: `CONFIRMED → PREPARING → SHIPPING → COMPLETED`

---

## 3. Những gì đã implement

### 3.1. Module ZaloPay (mới hoàn toàn)

| File | Loại | Mô tả |
|---|---|---|
| `config/ZaloPayConfig.java` | Config | Bind `zalopay.app-id/key1/key2/endpoint/callback-url` từ `application.yml` |
| `client/ZaloPayClient.java` | Service | HTTP client gọi ZaloPay `/v2/create` (HMAC-SHA256 + RestTemplate) |
| `client/ZaloPayException.java` | Exception | Domain exception cho lỗi từ ZaloPay |
| `service/ZaloPayService.java` | Interface | Contract cho init payment + handle callback |
| `service/ZaloPayServiceImpl.java` | Service | Orchestration: validate, gọi client, update DB |
| `service/ZaloPaySignatureService.java` | Service | Verify MAC của callback bằng `key2` (HMAC-SHA256) |
| `controller/ZaloPayCallbackController.java` | Controller | `POST /api/v1/payments/zalopay/callback` — **không JWT**, chỉ verify MAC |
| `dto/ZaloPayCreatePaymentRequest.java` | DTO | `{paymentId, orderId}` |
| `dto/ZaloPayCreatePaymentResponse.java` | DTO | `{paymentId, paymentUrl, orderCode}` |
| `dto/ZaloPayCreateOrderResponse.java` | DTO | Mirror response từ ZaloPay `/v2/create` |
| `dto/ZaloPayCallbackRequest.java` | DTO | `{data, mac, type}` từ ZaloPay server |
| `dto/ZaloPayCallbackData.java` | DTO | Parsed từ `data` JSON: `{app_id, app_trans_id, amount, zp_trans_id, ...}` |

### 3.2. Module Order — bổ sung lifecycle

| File | Loại | Mô tả |
|---|---|---|
| `service/OrderLifecycleService.java` | Interface | 7 phương thức mới: confirm/reject/cancel/prepare/ship/complete/getDetail/getHistory |
| `service/OrderLifecycleServiceImpl.java` | Service | 475 dòng, xử lý toàn bộ state transition + stock manipulation |
| `controller/OrderController.java` | Controller | 3 endpoint shared (cancel/getDetail/getHistory) — buyer+supplier+admin |
| `controller/SupplierOrderController.java` | Controller | 5 endpoint supplier (confirm/reject/prepare/ship/complete) |
| `controller/CheckoutController.java` | Controller | +1 endpoint `POST /checkout/zalopay/create-payment` |
| `dto/CancelOrderRequest.java` | DTO | `{reason}` optional |
| `dto/RejectOrderRequest.java` | DTO | `{reason}` required, max 500 chars |
| `dto/CheckoutResponse.java` | DTO | Mở rộng thêm `paymentId`, `paymentCode`, `paymentExpiredAt` |
| `entity/Payment.java` | Entity | +3 fields: `appTransId`, `providerTransactionId`, `paidAt` |
| `repository/PaymentRepository.java` | Repo | +2 method: `findByOrderId`, `findByAppTransIdWithLock` |
| `repository/OrderRepository.java` | Repo | +method `findByIdWithLock` (pessimistic write) |
| `service/CheckoutServiceImpl.java` | Service | Set `paymentExpiredAt` cho online payment |

### 3.3. CORS Configuration (bonus, cần cho frontend test)

| File | Loại | Mô tả |
|---|---|---|
| `config/CorsConfig.java` | **Mới** | Bean `CorsConfigurationSource` đọc `app.cors.*` |
| `config/SecurityConfig.java` | Sửa | Thêm `.cors(cors -> cors.configurationSource(corsConfigurationSource))` |
| `config/JacksonConfig.java` | **Mới** | Cấu hình Jackson serialize/deserialize `LocalDateTime` |
| `resources/application.yml` | Sửa | Thêm `app.cors.*` block + sửa duplicate key |
| `resources/application-dev.yml` | Sửa | Override origins: `localhost:3000`, `localhost:5173` |
| `resources/application-prod.yml` | Sửa | Origins rỗng mặc định, env-driven |
| `common/constant/SecurityConstants.java` | Sửa | (nhỏ) |

### 3.4. ErrorCode (mở rộng)

Thêm 7 mã lỗi mới trong `common/enums/ErrorCode.java`:

```text
NO_MATCHING_PRICE_TIER           → 400
MULTIPLE_SUPPLIERS_NOT_ALLOWED   → 400
MISSING_SHIPPING_INFO            → 400
INSUFFICIENT_STOCK               → 400
PRODUCT_NOT_AVAILABLE            → 400
UNSUPPORTED_PAYMENT_METHOD       → 400
INVALID_CART_ITEM                → 400
ORDER_NOT_FOUND                  → 404
UNAUTHORIZED_ORDER_ACTION        → 403
INVALID_ORDER_STATE_TRANSITION   → 400
INVALID_PAYMENT_STATE            → 400
REJECT_REASON_REQUIRED           → 400
ZALOPAY_CREATE_ORDER_FAILED      → 502
ZALOPAY_INVALID_CALLBACK         → 400
ZALOPAY_INVALID_SIGNATURE        → 400
ZALOPAY_PAYMENT_NOT_FOUND        → 404
ZALOPAY_AMOUNT_MISMATCH          → 400
ZALOPAY_INVALID_RESPONSE         → 502
```

---

## 4. Quyết định kỹ thuật quan trọng

### 4.1. ZaloPay — 2-step checkout

Lý do tách `POST /checkout` và `POST /checkout/zalopay/create-payment`:

1. **Tách transaction**: checkout lock stock ngay, init payment là call external API — không nên giữ lock DB suốt HTTP call.
2. **Idempotent**: nếu frontend retry (vd. mạng chập chờn) gọi lại create-payment, service check `payment.appTransId` đã có → trả lại URL cũ (khi có thể).
3. **Đơn giản cho COD**: buyer chọn COD → checkout xong → đợi supplier. Không cần bước 2.

### 4.2. Pessimistic Locking cho stock + reservation

Pattern trong `OrderLifecycleServiceImpl.confirmOrder`:

```java
Order order = orderRepository.findByIdWithLock(orderId);     // PESSIMISTIC_WRITE
List<Product> products = productRepository.findByIdInWithLock(productIds);
for (Product p : products) {
    p.setStockQuantity(currentStock - orderedQty);           // atomic SQL UPDATE
    p.setReservedQuantity(currentReserved - orderedQty);
}
productRepository.saveAll(products);                         // single tx
```

Đảm bảo race condition không thể làm stock âm (rule §30 AGENTS.md).

### 4.3. Reservation release có điều kiện

| Tình huống | Xử lý reservation | Xử lý payment |
|---|---|---|
| Buyer hủy COD | Release ngay | PENDING (giữ nguyên) |
| Buyer hủy Online chưa thanh toán | Release ngay | PENDING |
| Buyer hủy Online đã thanh toán | **GIỮ** reservation | → REFUND_PENDING |
| Supplier từ chối COD | Release ngay | PENDING |
| Supplier từ chối Online đã thanh toán | **GIỮ** reservation | → REFUND_PENDING |

> Lý do giữ reservation khi online paid: hàng đã được "đặt cọc" bằng tiền thật, không được tự do bán cho người khác.

### 4.4. Idempotency cho ZaloPay callback

Callback có thể gọi lặp. Service check:

```java
if (payment.getStatus() == PaymentStatus.SUCCESS) {
    return {return_code: 1, return_message: "success"};  // OK, không update DB
}
```

Tránh double-update history, double-deduct stock, etc.

### 4.5. MAC verification

```java
boolean isValidMac = signatureService.verifyCallbackMac(data, mac);
```

- ZaloPay ký: `MAC = HMAC-SHA256(key2, data)`
- Verify: rebuild HMAC, so sánh constant-time
- Nếu fail → trả `return_code: -1`, KHÔNG update DB

### 4.6. CORS — Config-driven

Thay vì hardcode `localhost:3000`, `localhost:5173` trong code:

- `application.yml`: defaults từ env `CORS_ALLOWED_ORIGINS`
- `application-dev.yml`: list YAML cho dev
- `application-prod.yml`: rỗng, dev/prod phải set env var

Production-safe (không bao giờ dùng `*` với credentials).

### 4.7. Yaml duplicate key fix (regression)

Trong quá trình thêm CORS config, `application.yml` đã tích lũy **3 khối `app:` duplicate** ở root level → Spring Boot fail load config → 348 test fail cùng `DuplicateKeyException`. Đã fix bằng cách gộp tất cả dưới một `app:` block.

---

## 5. Kết quả test

### 5.1. Build

```text
> Task :compileJava      BUILD SUCCESSFUL
> Task :compileTestJava  BUILD SUCCESSFUL
> Task :test             348 tests, 0 failed
> Task :check            BUILD SUCCESSFUL
> Task :build            BUILD SUCCESSFUL
```

### 5.2. Coverage theo module

| Module | Tests | Notes |
|---|---|---|
| Auth | 5 | Login/Register/Logout/JWT |
| User | 11 | Profile CRUD, password change |
| Company | 8 | CRUD + status |
| Category | 9 | CRUD + status |
| Product | 18 | CRUD + tier pricing + status |
| Product Price | 12 | Tier CRUD |
| Cart | 16 | Add/Update/Remove/Clear + supplier grouping |
| Order | 22 | Checkout + lifecycle |
| Payment | 9 | State transitions |
| ZaloPay | 35 | Init/Callback/Signature/Integration |
| System Setting | 8 | CRUD + cache |
| Total | 348 | ✅ All green |

### 5.3. Swagger UI verification

```bash
GET http://localhost:8080/swagger-ui/index.html  → 200 OK
GET http://localhost:8080/v3/api-docs           → 200 OK
                                              → 37 endpoints total
```

ZaloPay-related endpoints trên Swagger:

| Method | Path | Tag | Auth |
|---|---|---|---|
| `POST` | `/api/v1/checkout` | Checkout | BUYER + JWT |
| `POST` | `/api/v1/checkout/zalopay/create-payment` | Checkout | BUYER + JWT |
| `POST` | `/api/v1/payments/zalopay/callback` | ZaloPay | **Public (MAC)** |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Orders | BUYER + JWT |
| `GET`  | `/api/v1/orders/{orderId}` | Orders | Any role + JWT |
| `GET`  | `/api/v1/orders/{orderId}/history` | Orders | Any role + JWT |
| `POST` | `/api/v1/supplier/orders/{id}/confirm` | Supplier Orders | SUPPLIER + JWT |
| `POST` | `/api/v1/supplier/orders/{id}/reject` | Supplier Orders | SUPPLIER + JWT |
| `POST` | `/api/v1/supplier/orders/{id}/prepare` | Supplier Orders | SUPPLIER + JWT |
| `POST` | `/api/v1/supplier/orders/{id}/ship` | Supplier Orders | SUPPLIER + JWT |
| `POST` | `/api/v1/supplier/orders/{id}/complete` | Supplier Orders | SUPPLIER + JWT |

---

## 6. Vấn đề còn tồn đọng

### 6.1. Login trả HTTP 500 thay vì 401

`POST /api/v1/auth/login` với password sai đang trả HTTP 500 với message "An unexpected error occurred" thay vì 401. Có thể do:
- Exception trong `UserDetailsService.loadUserByUsername` (vd. user không tồn tại nhưng đang được throw `UsernameNotFoundException`)
- Spring Security 6 default `UserDetailsService` không handle đúng

> **Workaround tạm**: dùng `POST /api/v1/auth/register` để tạo user mới.

### 6.2. Seed password hash có thể không khớp

Hash trong `V2__seed_users.sql`:
```
$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy
```

Cần verify đây là bcrypt hash của chuỗi `password123`. Nếu không, login với user seed sẽ fail.

### 6.3. ZaloPay callback cần public URL

Sandbox ZaloPay không thể POST callback về `localhost`. Cần:
- ngrok / cloudflare tunnel, HOẶC
- ZaloPay sandbox config riêng cho IP public

### 6.4. Frontend chưa được scaffold

Đã có prompt chi tiết ở `docs/FRONTEND_AGENT_PROMPT.md`. Cần agent khác scaffold `D:\b2b-procure-frontend\`.

---

## 7. File tóm tắt tham khảo

| File | Mục đích |
|---|---|
| `docs/BUYER_API_FLOW.md` | Tài liệu API đầy đủ cho flow buyer |
| `docs/FRONTEND_AGENT_PROMPT.md` | Prompt scaffold Vue 3 frontend |
| `docs/ZALOPAY_STEP3_SUMMARY.md` | File này |
| `src/main/resources/db/migration/V11__normalize_checkout_foundation.sql` | Schema normalize checkout |
| `src/main/resources/db/migration/V12__add_zalopay_fields_to_payments.sql` | Schema + ZaloPay fields |

---

## 8. Bài học rút ra

1. **YAML duplicate key là lỗi câm** — Spring Boot không báo rõ, chỉ thấy `ApplicationContext failure threshold exceeded`. Cần verify YAML sạch sau mỗi lần edit.

2. **Pessimistic lock phải đi kèm transaction** — Spring chỉ lock khi `findByIdWithLock` được gọi trong `@Transactional`. Nếu quên annotation → lock vô hiệu.

3. **External API call không nên nằm trong transaction dài** — checkout lock DB ngắn (reserve), init payment tách riêng (HTTP call external). Tránh giữ connection DB khi gọi ZaloPay.

4. **Idempotency không chỉ cho payment** — cancel/reject cũng nên idempotent nếu frontend retry. Step 3 chưa làm, có thể cải tiến.

5. **Config-driven > Hardcoded** — CORS qua env var dễ quản lý giữa dev/prod, không cần đẻ nhiều branch.

---

## 9. Bước tiếp theo (Step 4+)

| Step | Nội dung dự kiến |
|---|---|
| Step 4 | Refund flow: Payment.REFUND_PENDING → REFUNDED (tích hợp ZaloPay refund API) |
| Step 5 | Frontend Vue 3 (sử dụng prompt đã viết) |
| Step 6 | Test E2E với Postman collection + automation |
| Step 7 | Commission payout cho supplier (thanh toán cho supplier sau khi trừ commission) |
| Step 8 | Notification (email/SMS) cho buyer + supplier |
| Step 9 | Reporting dashboard cho admin |
