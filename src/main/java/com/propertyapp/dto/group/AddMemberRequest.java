package com.propertyapp.dto.group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private BigDecimal commissionPercent;

    private Integer monthlyTarget;
}
