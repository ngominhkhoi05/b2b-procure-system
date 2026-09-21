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
    INVALID_CART_ITEM("Invalid cart item", HttpStatus.BAD_REQUEST);

    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
