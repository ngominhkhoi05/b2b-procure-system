package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    NO_MATCHING_PRICE_TIER("No matching price tier found for requested quantity", HttpStatus.BAD_REQUEST),
    MULTIPLE_SUPPLIERS_NOT_ALLOWED("Checkout cannot contain products from multiple suppliers", HttpStatus.BAD_REQUEST),
    MISSING_SHIPPING_INFO("Missing shipping information in buyer company profile", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_STOCK("Insufficient stock for requested product", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_AVAILABLE("Product or category is inactive or unavailable", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_PAYMENT_METHOD("Payment method is not supported for checkout", HttpStatus.BAD_REQUEST),
    INVALID_CART_ITEM("Invalid cart item", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND("Order not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ORDER_ACTION("User is not authorized to perform action on this order", HttpStatus.FORBIDDEN),
    INVALID_ORDER_STATE_TRANSITION("Invalid order status transition", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATE("Payment status is invalid for this order transition", HttpStatus.BAD_REQUEST),
    REJECT_REASON_REQUIRED("Reject reason is required and cannot be blank", HttpStatus.BAD_REQUEST),
    ZALOPAY_CREATE_ORDER_FAILED("ZaloPay order creation failed", HttpStatus.BAD_GATEWAY),
    ZALOPAY_INVALID_CALLBACK("ZaloPay callback verification failed", HttpStatus.BAD_REQUEST),
    ZALOPAY_INVALID_SIGNATURE("ZaloPay callback signature invalid", HttpStatus.BAD_REQUEST),
    ZALOPAY_PAYMENT_NOT_FOUND("Payment not found for ZaloPay callback", HttpStatus.NOT_FOUND),
    ZALOPAY_AMOUNT_MISMATCH("ZaloPay callback amount mismatch", HttpStatus.BAD_REQUEST),
    ZALOPAY_INVALID_RESPONSE("ZaloPay API returned invalid response", HttpStatus.BAD_GATEWAY);

    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
