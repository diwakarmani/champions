package com.propertyapp.service.property;


import com.propertyapp.dto.property.DiscoveryResponseDTO;
import com.propertyapp.dto.property.HomeDiscoveryResponse;
import com.propertyapp.dto.property.PropertyCardDTO;
import com.propertyapp.enums.DiscoveryCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DiscoveryService {

    DiscoveryResponseDTO getHomeDiscovery(
            Long userId,
            String city,
            Double lat,
            Double lng
    );

    void logInteraction(Long userId, Long propertyId, String type);

     HomeDiscoveryResponse getHomeDiscoveryUpdated(
            Long userId,
            String city,
            Double lat,
            Double lng
    );

     Page<PropertyCardDTO> viewMore(
            Long userId,
            DiscoveryCategory category,
            Double lat,
            Double lng,
            Pageable pageable
    );
}
