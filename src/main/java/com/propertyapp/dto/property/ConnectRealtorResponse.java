package com.propertyapp.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRealtorResponse {
    private boolean success;
    private Long interactionId;
    private Long realtorId;
    private Long propertyId;
    private long totalUserInteractions;
}
