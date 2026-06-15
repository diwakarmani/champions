package com.propertyapp.service.realtor;

import com.propertyapp.dto.property.ConnectRealtorRequest;
import com.propertyapp.dto.property.ConnectRealtorResponse;
import com.propertyapp.dto.property.RealtorProfileDTO;

public interface PublicRealtorService {
    RealtorProfileDTO getProfile(Long realtorId);

    ConnectRealtorResponse connect(Long realtorId, Long userId, ConnectRealtorRequest request);
}
