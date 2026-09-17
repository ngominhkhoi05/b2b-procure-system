package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum SettingKey {

    PAYMENT_TIMEOUT_MINUTES("Maximum time allowed for Buyer to complete online payment."),
    SUPPLIER_CONFIRM_TIMEOUT_HOURS("Maximum time allowed for Supplier to Confirm or Reject an Order.");

    private final String description;

    public static Optional<SettingKey> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(key.trim()))
                .findFirst();
    }

    public static boolean isValidKey(String key) {
        return fromKey(key).isPresent();
    }
}
