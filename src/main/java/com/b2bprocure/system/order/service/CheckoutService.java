package com.b2bprocure.system.order.service;

import com.b2bprocure.system.order.dto.CheckoutRequest;
import com.b2bprocure.system.order.dto.CheckoutResponse;

public interface CheckoutService {

    /**
     * Executes the checkout transaction for a Buyer.
     *
     * @param request the checkout request containing cartItemIds and paymentMethod
     * @return CheckoutResponse with details of created order and payment
     */
    CheckoutResponse checkout(CheckoutRequest request);

}
