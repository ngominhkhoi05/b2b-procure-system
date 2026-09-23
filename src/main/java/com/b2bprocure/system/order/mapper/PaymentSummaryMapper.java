package com.b2bprocure.system.order.mapper;

import com.b2bprocure.system.order.dto.PaymentSummaryResponse;
import com.b2bprocure.system.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PaymentSummaryMapper {

    /**
     * Map a Payment entity to a safe PaymentSummaryResponse.
     *
     * Only exposes summary fields (paymentMethod, paymentStatus, amount, paidAt).
     * Sensitive provider-specific fields ({@code providerTransactionId}, {@code appTransId},
     * {@code paymentCode}) are intentionally NOT mapped.
     *
     * Enum fields are flattened to their {@code String} name to keep the public
     * contract string-based and decoupled from internal enum types.
     */
    @Mapping(target = "paymentMethod", source = "paymentMethod", qualifiedByName = "enumName")
    @Mapping(target = "paymentStatus", source = "status", qualifiedByName = "enumName")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "paidAt", source = "paidAt")
    PaymentSummaryResponse toResponse(Payment payment);

    @Named("enumName")
    default String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

}
