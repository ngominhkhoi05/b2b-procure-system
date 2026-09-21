# STEP 3 — CALLBACK AUDIT #2

> **Audit performed:** 2026-09-21
> **Mode:** Read-only, no code modified
> **Sources:** Official ZaloPay documentation (PDF), `src/main/`, `src/test/`
> **Browser verification:** Official ZaloPay docs at `docs.zalopay.vn`, `developers.zalopay.vn`

---

## 1. Official ZaloPay Documentation

**Documents:**

| # | Document | URL | Key Content |
|---|---|---|---|
| 1 | ZaloPay APIs Integration Document | `https://developers.zalopay.vn/downloads/api/ZaloPay-APIs-Integration-Document.pdf` | Create Order, Callback, Query Order Status, Refund |
| 2 | Callback API | `https://docs.zalopay.vn/docs/specs/callback-api/` | Callback specification, data structure |
| 3 | Knowledge Base — Callback | `https://docs.zalopay.vn/docs/developer-tools/knowledge-base/callback` | Callback mechanism explanation |
| 4 | GitHub quickstart example | `https://github.com/zalopay-samples/quickstart-payment-gateway` | Callback integration code sample |

**Documentation version/date:**
- PDFs downloaded via `developers.zalopay.vn` — no explicit date in document, but content references v2 API (`/v2/create`, `/v2/query`).
- Docs at `docs.zalopay.vn` — last updated visible on page.

**Relevant sections:**

| Document | Section | Key Quote |
|---|---|---|
| PDF | "2.Callback" (Page 11) | *"If ZaloPay successfully get money from user, then ZaloPay Server will notify to MerchantServer via CallbackURL..."* |
| PDF | "2.Callback" | *"callback_data: is data requested by ZaloPay to Merchant's callback API when ZaloPay has successfully collected money from customers."* |
| PDF | "2.Callback" | *"After 15 minutes from the time of the order establishment, if merchant still do not receive callback from ZaloPay, merchant need to call API Get order status proactively to get the final result."* |
| PDF | "3.Query Order Status" | *"When the payment is successful, ZaloPay will call the callback (notify) to the Merchant, then the Merchant updates the status of Successful orders on the Merchant's system. But in reality, the callback may be missed due to Network timeout..."* |
| docs.zalopay.vn | callback-api | *"If ZaloPay successfully get money from user, then ZaloPay Server will notify to Merchant Server via Callback URL which had been registered with ZaloPay or from callback_url."* |
| docs.zalopay.vn | callback-api | *"After 15 minutes from the time of the order establishment, if you still do not receive a callback from ZaloPay, the merchant needs to call QueryOrder API proactively to get the final result."* |
| PDF | "3.Query Order Status" response | `return_code`: `1` : SUCCESS, `2` : FAIL, `3` : PROCESSING |
| docs.zalopay.vn | callback-api | `return_code` (in merchant response): `1`: Success, `2`: Invalid, `0`: callback again (max 3 times), `<>`: failure (no retry) |

---

## 2. Callback HTTP Contract

**HTTP method:** `POST`

**Endpoint:** Configured by merchant (in `callback_url` field of Create Order request, or registered with ZaloPay in Merchant Portal). Backend current: `POST /api/v1/payments/zalopay/callback` — this is correct.

**Request body:**
```json
{
  "data": "{\"app_id\":2553,\"app_trans_id\":\"260921_orderId_ABCDEF\",...}",
  "mac": "d8d33baf449b31d7f9b94fa50d7c942c08cd4d83f28fa185557da21acb104f67",
  "type": 1
}
```

**Headers:** `Content-Type: application/json`

**Response expected from merchant (to ZaloPay server):**
```json
{
  "return_code": 1,
  "return_message": "success"
}
```

Note: The `type` field in request: `1` = Order callback, `2` = Agreement callback. Backend accepts both (no validation on `type`).

---

## 3. Callback `data` JSON

**Actual documented structure (from PDF Page 12-13, field list):**

| Field | Type | Description | In `ZaloPayCallbackData.java` |
|---|---|---|---|
| `app_id` | int | The app identifier provided by ZaloPay | ✅ `long appId` |
| `app_trans_id` | string | Order's transaction code (format: yymmdd_xxx) | ✅ `String appTransId` |
| `app_time` | long | Order's creation time (milliseconds) | ❌ **Missing** — field not declared in DTO |
| `app_user` | string | Order's app_user | ❌ **Missing** — field not declared in DTO |
| `amount` | long | Amount received (VND) | ✅ `long amount` |
| `embed_data` | string | JSON string of extra data | ❌ **Missing** — field not declared in DTO |
| `item` | string | JSON array string of items | ❌ **Missing** — field not declared in DTO |
| `zp_trans_id` | long | ZaloPay's transaction code | ✅ `long zpTransId` |
| `server_time` | long | ZaloPay's transaction time (unix timestamp ms) | ✅ `long serverTime` |
| `channel` | int | Payment channel | ✅ `int channel` |
| `merchant_user_id` / `user_fee_amount` / `discount_amount` | mixed | ZaloPay user info per app_id, fee, discount | ✅ (all present) |
| **`return_code`** | — | **NOT PRESENT in callback data** | ❌ **Not a bug — does not exist** |

**Evidence:**

From official PDF (Page 12, "Callback_order_data" section):
```
No Parameter Data Type Description
1 app_id        int    The app identifier
2 app_trans_id  String Order's app_trans_id
3 app_time      Long   Order's app_time
4 app_user      String Order's app_user
5 amount        Long   Amount received (VND)
6 embed_data    String Order's embed_data
7 item          String JSON array string
8 zp_trans_id   Long   ZaloPay's Transaction code
9 server_time   Long   unix timestamp in milliseconds
10 channel      Int    Payment channel
11 merchant_user_id String ZaloPay user per app_id
12 user_fee_amount Long Fee (VND)
13 discount_amount Long Discount (VND)
```

**Sample from official docs (docs.zalopay.vn):**
```json
{
  "data": "{\"app_id\":2553,\"app_trans_id\":\"200904_2553_1598435687208\",\"app_time\":1599189392817,
    \"app_user\":\"demo\",\"amount\":10000,\"embed_data\":\"{}\",
    \"item\":\"[]\",\"zp_trans_id\":200904000000389,
    \"server_time\":1599189413498,\"channel\":38,
    \"merchant_user_id\":\"...\",\"user_fee_amount\":0,\"discount_amount\":0}",
  "mac": "...",
  "type": 1
}
```

**Confirmed: `return_code` is NOT a field inside the callback `data`.**

---

## 4. `return_code` Analysis

### Table: `return_code` in different contexts

| Context | Has return_code? | Location | Meaning | Value |
|---|---|---|---|---|
| **Create Order response** (ZaloPay → Merchant) | ✅ YES | JSON root | API call result (did ZaloPay accept the order?) | `1`=Success, `2`=Failure |
| **Callback request** (ZaloPay → Merchant) | ❌ **NO** | — | — | — |
| **Callback response** (Merchant → ZaloPay) | ✅ YES | JSON root | Did merchant receive and process the callback? | `1`=Success, `2`=Invalid, `0`=Retry (max 3x), `<>`=Failure (no retry) |
| **Query Order Status response** (ZaloPay → Merchant) | ✅ YES | JSON root | Actual payment status | `1`=SUCCESS, `2`=FAIL, `3`=PROCESSING |

### Critical clarification

From official docs:

> *"callback_data: is data requested by ZaloPay to Merchant's callback API **when ZaloPay has successfully collected money from customers**."*

> *"ZaloPay will notify to this URL **only when the payment is success**"*

> *"After 15 minutes from the time of the order establishment, if merchant still do not receive callback from ZaloPay, merchant need to call API Get order status proactively to get the final result."*

### Conclusion

The callback is a **unidirectional notification that payment succeeded**. It is only sent by ZaloPay when money has been successfully collected. There is **no field inside the callback `data` that indicates payment success/failure** — the receipt of the callback itself IS the success signal.

The only way to know if payment failed or is still processing is to call **`/v2/query`** (Query Order Status API) after 15 minutes without a callback.

---

## 5. Payment Success Flow

**Official flow (from PDF):**
```
1. Merchant calls POST /v2/create → ZaloPay returns order_url/qr_code
2. User pays via ZaloPay app / QR scan
3. If ZaloPay successfully collects money:
   → ZaloPay POSTs to callback_url with {data, mac, type=1}
4. Merchant verifies MAC
5. Merchant updates order status
6. Merchant returns {return_code: 1} to ZaloPay
```

**Backend current flow (ZaloPayServiceImpl.handleCallback):**
```
1. Validate data/mac presence
2. Verify MAC using key2
3. Parse data → ZaloPayCallbackData
4. Find Payment by app_trans_id (pessimistic lock)
5. Idempotency check (if already SUCCESS → return {1})
6. Validate payment.status == PENDING
7. Validate amount match
8. payment.status = SUCCESS, paidAt = now
9. order.status = PAID (only if PENDING_CONFIRMATION)
10. Record OrderStatusHistory
11. Return {return_code: 1, return_message: "success"}
```

**Match: YES** ✅ — Backend correctly treats every valid callback as a success notification, matching the official protocol.

---

## 6. Payment Failed / Cancelled Flow

**Official ZaloPay behavior:**

> "ZaloPay will notify to this URL **only when the payment is success**"

ZaloPay **does NOT send a callback** for:
- User cancelled payment
- Payment failed
- Payment expired
- Payment timed out (15 minutes)
- User abandoned the payment flow

**How is failure represented?**

1. **No callback at all** — this is the primary mechanism. If the merchant never receives a callback within 15 minutes, the payment is NOT successful.
2. **Query Order Status API** (`/v2/query`) — the official solution to check payment status when callback is missed. Response: `return_code`: `1`=SUCCESS, `2`=FAIL, `3`=PROCESSING.

**Backend current implementation:**

The current backend does **NOT** implement any mechanism to detect failed payments:

- `ZaloPayServiceImpl` has no method to call `/v2/query`
- No scheduled job to detect expired payments (payments with `PENDING` status older than 15 minutes)
- No mechanism to poll for payment status
- `Payment.expiredAt` exists in the entity but is not used for timeout detection

**Match: NO** ❌ — Backend is missing failure detection. The comment in `CheckoutServiceImpl` sets `expiredAt` but there is no scheduled task or polling that uses it.

**What this means practically:**
- User opens ZaloPay, views order, then closes without paying → callback never comes → Payment stays `PENDING` forever, Order stays `PENDING_CONFIRMATION` forever.
- The Order can be "stuck" indefinitely.
- Supplier will never see these orders progress (they are stuck before the supplier's workflow).

---

## 7. MAC Verification

**Official algorithm (from PDF Page 8, 13):**

```
MAC input:  callback_data (the raw string value of the "data" field)
Key:        callbackkey (key2, provided by ZaloPay)
Algorithm:  HMAC-SHA256
Encoding:   lowercase hexadecimal (64 chars)
Formula:    reqmac = HMAC(hmac_algorithm, callbackkey, callback_data.data)
```

**Backend implementation (ZaloPaySignatureService.java):**

```java
// verifyCallbackMac(String callbackData, String providedMac)
// callbackData = the raw "data" string value from the request
// key2        = from ZaloPayConfig
String computedMac = computeMac(callbackData, zaloPayConfig.getKey2());
// computeMac: HMAC-SHA256 → bytesToHex → lowercase hex string
```

**Verification in handleCallback:**
```java
boolean isValidMac = signatureService.verifyCallbackMac(
    callbackRequest.getData(),   // raw JSON string of the data field
    callbackRequest.getMac()       // provided MAC from ZaloPay
);
```

**Match: YES** ✅ — Implementation exactly matches official documentation:
- Uses `data` string (not the parsed JSON object)
- Uses `key2` (not `key1`)
- Uses `HMAC-SHA256`
- Returns lowercase hex

---

## 8. Current Code Audit

### ZaloPayCallbackRequest.java

```java
@JsonProperty("data") private String data;   // ✅ raw JSON string
@JsonProperty("mac")  private String mac;    // ✅ MAC from ZaloPay
@JsonProperty("type") private int type;      // ✅ type=1 (Order), type=2 (Agreement)
```
**Assessment:** ✅ Correct structure matches official callback request format.

---

### ZaloPayCallbackData.java

Fields present: `appId`, `appTransId`, `amount`, `zpTransId`, `serverTime`, `channel`, `merchantUserId`, `userFeeAmount`, `discountAmount`, `embedData`, `item` ✅

Fields **missing** (from official spec):
- `appTime` — Order's creation time (milliseconds)
- `appUser` — Order's app_user

These are NOT used in business logic (not validated or stored) so missing fields do not cause behavioral bugs. Non-critical.

---

### ZaloPayServiceImpl.java (handleCallback)

**MAC verification:** Lines 183-195
- ✅ Verified BEFORE data parsing
- ✅ Throws `return_code: -1` on MAC failure
- ✅ Throws `return_code: -1` on empty data/mac

**Payment lookup:** Line 213-222
- ✅ Uses `app_trans_id` as lookup key
- ✅ Pessimistic write lock via `findByAppTransIdWithLock`
- ✅ Throws `return_code: -1` on payment not found

**Idempotency:** Lines 220-225
- ✅ Checks `payment.status == SUCCESS` before any mutation
- ✅ Returns `return_code: 1` without side effects if already processed

**Status validation:** Lines 230-235
- ✅ Validates `payment.status == PENDING`
- ✅ Returns `return_code: 0` if not PENDING

**Amount validation:** Lines 240-245
- ✅ Compares callback `amount` with stored `payment.amount`
- ✅ Returns `return_code: 0` on mismatch

**SUCCESS path:** Lines 248-270
- ✅ Sets `payment.status = SUCCESS`
- ✅ Sets `order.status = PAID` (only if `PENDING_CONFIRMATION`)
- ✅ Records `OrderStatusHistory`

**FAILURE path:** ❌ **DOES NOT EXIST**
- No branch for payment failure/cancellation
- If callback is received → always sets SUCCESS

---

### ZaloPayCallbackController.java

```java
@PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
public Map<String, Object> handleCallback(@RequestBody ZaloPayCallbackRequest callbackRequest)
```
**Assessment:** ✅ Correct — no JWT, accepts JSON, returns raw `Map<String, Object>` (not wrapped in `ApiResponse`) — correct because ZaloPay expects a direct JSON response, not a standard API wrapper.

---

### ZaloPayIntegrationTest.java

All 14 tests and their purpose:

| Nested Class | Test | What it tests |
|---|---|---|
| **ApiEndpointTests** | `testCheckout_ZaloPay_ReturnsPaymentIdAndOrderId` | Full checkout creates payment + order |
| | `testCreatePayment_Unauthenticated_401` | create-payment requires JWT |
| | `testCallback_EndpointExists` | Callback endpoint returns JSON |
| **CallbackProcessingTests** | `testCallback_InvalidSignature_ReturnsError` | MAC verification fails → return_code=-1 |
| | `testCallback_EmptyData_ReturnsError` | Empty data → return_code=-1 |
| | `testCallback_EmptyMac_ReturnsError` | Empty MAC → return_code=-1 |
| **PaymentStateTransitionTests** | `testPayment_AlreadySuccess_Idempotent` | Duplicate callback on SUCCESS is idempotent |
| | `testPayment_NotFound_ReturnsError` | Unknown app_trans_id → return_code=-1 |
| **DatabaseStateTests** | `testPayment_HasAppTransIdField` | Payment entity stores app_trans_id |
| | `testOrder_PaidStatus_CorrectState` | Order can be in PAID status |
| **SignatureServiceTests** | `testSignature_HMAC_SHA256_Valid` | MAC generation produces 64-char hex |
| | `testSignature_CallbackVerification` | MAC verification works |
| | `testSignature_InvalidMac_Rejected` | Invalid MAC → false |
| | `testSignature_DifferentData_DifferentMac` | Different data → different MAC |

**Missing tests:** No test for:
- Callback from failed payment (impossible — ZaloPay doesn't send this)
- Missing callback timeout (expired payment detection)
- Query Order Status API

---

### ZaloPayServiceTest.java

All 9 tests:

| Nested Class | Test | What it tests |
|---|---|---|
| **InitiatePaymentTests** | `testInitiatePayment_Success` | Mocked ZaloPay success → payment updated |
| | `testInitiatePayment_ApiFailure` | HTTP error → exception |
| | `testInitiatePayment_NonSuccessResponse` | ZaloPay returnCode != 1 → exception |
| | `testInitiatePayment_MissingOrderUrl` | Missing order_url → exception |
| | `testInitiatePayment_PaymentNotFound` | Wrong payment ID → exception |
| | `testInitiatePayment_OrderIdMismatch` | Payment/order mismatch → exception |
| **HandleCallbackTests** | `testHandleCallback_EmptyData` | Empty data → return_code=-1 |
| | `testHandleCallback_EmptyMac` | Empty MAC → return_code=-1 |
| | `testHandleCallback_InvalidMac` | Invalid MAC → return_code=-1 |

**Missing tests:** No test for:
- Callback processing with valid data → SUCCESS transition
- Callback with payment already SUCCESS → idempotent

---

## 9. Sandbox Verification

**Performed:** NO

**Reason:**
- `bootRun` is active in terminal but ngrok-free.dev tunnel may or may not be active
- Sandbox verification would require:
  1. Valid `callback_url` reachable from ZaloPay sandbox servers
  2. Actual money flow simulation
  3. Payment cancellation/abandonment simulation
- This cannot be safely verified without modifying configuration and exposing the local server
- No evidence of prior sandbox test runs in git history or docs

---

## 10. Audit #1 Re-evaluation

**Audit #1 claim:**

> *"Callback FAILED is not implemented because return_code is missing in callback data."*

**Verdict: PARTIALLY CORRECT — but the reasoning was wrong**

**Correct part:** The backend does NOT have a mechanism to detect failed/cancelled payments.

**Wrong part:** The reasoning that `return_code` is "missing" from the callback `data` is **correct fact, but wrong interpretation**. The `return_code` is NOT in the callback data because ZaloPay **does not send a callback for failed payments**. The callback data only contains transaction details of a **confirmed successful payment**. There is no need for a `return_code` inside the callback data because the very existence of the callback is the success signal.

**What Audit #1 correctly identified:**
- ❌ Backend missing failure detection (true — no polling/timeout mechanism exists)

**What Audit #1 incorrectly concluded:**
- ❌ "Need to add `return_code` field to `ZaloPayCallbackData`" (false — field does not exist in the official spec and would never be sent by ZaloPay for failures)
- ❌ "Callback unconditionally sets SUCCESS for every callback" (partially misleading — this is actually CORRECT behavior per official spec; every callback = successful payment)

**Root cause of Audit #1 error:**
The agent confused the `return_code` inside the **Create Order API response** (which indicates if ZaloPay accepted the order creation request) with the **Callback** (which indicates payment outcome). The callback data contains transaction details, not a status code.

---

## 11. Required Correction

**NO CODE CHANGE JUSTIFIED AT THIS TIME** based on the official callback contract.

However, a separate gap exists that is NOT about callback structure:

**File:** `ZaloPayServiceImpl.java` (or new class)
**Gap:** No mechanism to detect failed/expired payments when callback is not received
**Business impact:** Orders stuck in `PENDING_CONFIRMATION` forever when buyer abandons ZaloPay without paying

**Recommended approach (NOT implemented, for future steps):**

```
1. Add scheduled job or on-demand check:
   - For payments with status=PENDING and expiredAt < now()
   - Call ZaloPay /v2/query API (Query Order Status)
   - Response contains return_code: 1=SUCCESS, 2=FAIL, 3=PROCESSING
   - Update Payment/Order accordingly

2. Alternative (simpler for MVP):
   - Add a background job that marks payments as EXPIRED
     after expiredAt + grace period
   - Payment.status = EXPIRED, Order.status = EXPIRED_CANCELLED (or CANCELLED)
   - Release reservation immediately
   - Reservation still held during pending window (no free stock race)
```

This is a **scheduling/polling concern**, NOT a callback structure concern. It is outside the scope of "callback implementation" and belongs in a future step (e.g., "Step 4: Payment timeout handling").

---

## 12. Final Conclusion

**CRITICAL:**
- None. The callback implementation is correct per official specification.

**NON-CRITICAL:**
- **Missing failure detection**: No scheduled job or polling to detect failed payments when callback is not received within 15 minutes. Orders can be stuck in `PENDING_CONFIRMATION` indefinitely if buyer abandons payment. This is a **future step concern** (scheduled job / Query API integration), not a callback implementation bug.
- **Minor DTO fields missing**: `appTime` and `appUser` in `ZaloPayCallbackData` are not declared (unused in business logic, no behavioral impact).
- **Missing callback timeout test**: No test covers the 15-minute timeout scenario (would require mocking time or a dedicated test for expired payment detection).

**NO ISSUE:**
- ✅ Callback request structure (`{data, mac, type}`) matches official spec exactly
- ✅ Callback `data` does NOT contain `return_code` — this is correct (ZaloPay only sends callback for successful payments)
- ✅ MAC verification uses `HMAC-SHA256(data, key2)` — matches official spec
- ✅ Every valid callback sets `Payment → SUCCESS` and `Order → PAID` — this is correct behavior
- ✅ Idempotency check prevents duplicate processing
- ✅ Amount validation prevents tampered callbacks
- ✅ Callback response `return_code` semantics are correct
- ✅ `app_trans_id` used as unique lookup key with pessimistic lock
- ✅ No `return_code` field needed in `ZaloPayCallbackData` (it does not exist in the spec)
- ✅ `RestClient` (not `RestTemplate`) is used — matches modern Spring Boot 4.x
- ✅ Callback endpoint is unauthenticated (no JWT) — correct per ZaloPay contract

**OPEN QUESTION:**
- The `callback_url` is set to `https://panther-flatness-unzip.ngrok-free.dev/callback` in `application.yml`. This URL must be publicly reachable from ZaloPay sandbox servers. If ngrok tunnel is not running or URL has changed, ZaloPay cannot reach the callback endpoint. Verify the tunnel is active before testing with real sandbox payments.

---

## Files Inspected (complete list)

| File | Lines | Purpose |
|---|---|---|
| `src/main/java/.../zalopay/client/ZaloPayClient.java` | 150 | Verify HTTP client (RestClient), request/response handling |
| `src/main/java/.../zalopay/controller/ZaloPayCallbackController.java` | 62 | Verify endpoint, no-JWT auth, JSON response |
| `src/main/java/.../zalopay/dto/ZaloPayCallbackRequest.java` | 29 | Verify request DTO fields |
| `src/main/java/.../zalopay/dto/ZaloPayCallbackData.java` | 59 | Verify parsed callback data fields |
| `src/main/java/.../zalopay/service/ZaloPayServiceImpl.java` | 359 | Verify full callback handling logic |
| `src/main/java/.../zalopay/service/ZaloPaySignatureService.java` | 80 | Verify MAC algorithm |
| `src/main/java/.../payment/entity/Payment.java` | 79 | Verify appTransId field mapping |
| `src/main/java/.../payment/repository/PaymentRepository.java` | 35 | Verify findByAppTransIdWithLock |
| `src/main/java/.../common/enums/ErrorCode.java` | 30 | Verify error codes |
| `src/test/java/.../zalopay/ZaloPayIntegrationTest.java` | 598 | Verify all integration tests |
| `src/test/java/.../zalopay/ZaloPayServiceTest.java` | 372 | Verify all unit tests |
| `src/main/resources/application.yml` | 66 | Verify ZaloPay config keys |
| `C:\...\agent-tools\a6855fb4...txt` | 811 | Official ZaloPay PDF (downloaded via WebSearch) |

**Working tree mutation:** 0 (read-only).
**No commits created.**
**No code modified.**
