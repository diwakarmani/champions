package com.propertyapp.service.property;

import com.propertyapp.dto.property.DiscoveryResponseDTO;
import com.propertyapp.dto.property.HomeDiscoveryResponse;
import com.propertyapp.dto.property.PropertyCardDTO;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.enums.DiscoveryCategory;
import com.propertyapp.mapper.PropertyMapper;
import com.propertyapp.repository.property.DiscoveryRepository;
import com.propertyapp.repository.property.PropertyCardProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DiscoveryServiceImpl implements DiscoveryService {

    private final JdbcTemplate jdbcTemplate;
    private final DiscoveryRepository discoveryRepository;
    private final PropertyMapper propertyMapper;

    @Override
    @Transactional(readOnly = true)
    public DiscoveryResponseDTO getHomeDiscovery(
            Long userId,
            String city,
            Double lat,
            Double lng
    ) {

        List<PropertyDTO> nearest = List.of();

        if (lat != null && lng != null) {
            nearest = discoveryRepository.findNearest(lat, lng, 10)
                    .stream().map(propertyMapper::toDTO).toList();
        }

        List<PropertyDTO> popular =
                discoveryRepository
                        .getCachedProperties(0L, "POPULAR", city, 10)
                        .stream().map(propertyMapper::toDTO).toList();

        List<PropertyDTO> recommended = List.of();

        if (userId != null) {
            recommended = discoveryRepository
                    .getCachedProperties(userId, "RECOMMENDED", null, 10)
                    .stream().map(propertyMapper::toDTO).toList();
        }

        if (recommended.isEmpty()) {
            recommended = popular;
        }

        return DiscoveryResponseDTO.builder()
                .popular(popular)
                .recommended(recommended)
                .nearest(nearest)
                .build();
    }

    @Override
    @Async
    public void logInteraction(Long userId, Long propertyId, String type) {

        try {
            jdbcTemplate.update("""
                    INSERT INTO user_property_interactions
                    (user_id, property_id, interaction_type)
                    VALUES (?, ?, ?)
                    """,
                    userId,
                    propertyId,
                    type
            );
        } catch (Exception e) {
            log.error("Failed to save interaction", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HomeDiscoveryResponse getHomeDiscoveryUpdated(
            Long userId,
            String city,
            Double lat,
            Double lng
    ) {

        int limit = 10;

        List<PropertyCardDTO> popular =
                discoveryRepository
                        .getHomeCards(0L, "POPULAR", city, limit)
                        .stream()
                        .map(this::mapCard)
                        .toList();

        List<PropertyCardDTO> recommended = List.of();

        if (userId != null) {
            recommended =
                    discoveryRepository
                            .getHomeCards(userId, "RECOMMENDED", null, limit)
                            .stream()
                            .map(this::mapCard)
                            .toList();
        }

        if (recommended.isEmpty()) {
            recommended = popular;
        }

        List<PropertyCardDTO> nearest = List.of();

        if (lat != null && lng != null) {
            nearest =
                    discoveryRepository
                            .getNearestCards(lat, lng, limit)
                            .stream()
                            .map(this::mapCard)
                            .toList();
        }

        return HomeDiscoveryResponse.builder()
                .popular(popular)
                .recommended(recommended)
                .nearest(nearest)
                .build();
    }

    private PropertyCardDTO mapCard(PropertyCardProjection p) {
        return PropertyCardDTO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .listingType(p.getListingType())
                .price(p.getPrice())
                .city(p.getCity())
                .locality(p.getLocality())
                .bedrooms(p.getBedrooms())
                .furnishedStatus(p.getFurnishedStatus())
                .primaryImageUrl(p.getPrimaryImageUrl())
                .verified(Boolean.TRUE.equals(p.getVerified()))
                .premium(Boolean.TRUE.equals(p.getPremium()))
                .distanceInKm(p.getDistanceInKm())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public Page<PropertyCardDTO> viewMore(
            Long userId,
            DiscoveryCategory category,
            Double lat,
            Double lng,
            Pageable pageable
    ) {

        if (category == DiscoveryCategory.NEAREST) {

            List<PropertyCardDTO> list =
                    discoveryRepository
                            .getNearestCards(lat, lng, pageable.getPageSize())
                            .stream()
                            .map(this::mapCard)
                            .toList();

            return new PageImpl<>(list, pageable, list.size());
        }

        Page<PropertyCardProjection> page =
                discoveryRepository.getViewMore(
                        userId != null ? userId : 0L,
                        category.name(),
                        pageable
                );

        return page.map(this::mapCard);
    }
}
