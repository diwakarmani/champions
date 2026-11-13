package com.propertyapp.service.publicapi;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.publicapi.GoneListingDTO;
import com.propertyapp.dto.publicapi.PublicAgentProfileDTO;
import com.propertyapp.dto.publicapi.PublicAgentRef;
import com.propertyapp.dto.publicapi.PublicListingCardDTO;
import com.propertyapp.dto.publicapi.PublicMarketDTO;
import com.propertyapp.dto.publicapi.PublicPropertyDTO;
import com.propertyapp.dto.publicapi.SitemapEntryDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ListingGoneException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.mapper.PropertyMapper;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.realtor.RealtorRatingRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.service.property.PropertyVisibility;
import com.propertyapp.util.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only public data for the web and guest mobile (ADR-0006). Hidden things answer 404. */
@Service
@RequiredArgsConstructor
public class PublicListingService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_SITEMAP_PAGE_SIZE = 5000;

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final RealtorRatingRepository ratingRepository;
    private final PropertyMapper propertyMapper;

    /** Public sort keys (ADR-0006 rule 5): anything else is a 400. */
    static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("id")),
            "price_asc", Sort.by(Sort.Order.asc("price").nullsLast(), Sort.Order.desc("id")),
            "price_desc", Sort.by(Sort.Order.desc("price").nullsLast(), Sort.Order.desc("id")));
    static final Set<String> LISTING_TYPES = Set.of("SALE", "RENT");
    /** Taken down after being public: these answer 410 (ADR-0009 Amendment 2). */
    static final Set<String> GONE_STATUSES = Set.of("INACTIVE", "DELETION_REQUESTED");

    /**
     * 200 for a public listing; 410 for one that was published and has since been taken down (it was
     * public, so saying so reveals nothing); 404 for everything else, including a suspended agent's
     * listings, which may come back.
     */
    @Transactional(readOnly = true)
    public PublicPropertyDTO getProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        if (property.getDeletedAt() == null && PropertyVisibility.isPubliclyVisible(property)) {
            return PublicPropertyDTO.from(property, propertyMapper.toDTO(property));
        }
        if (isGone(property)) {
            throw new ListingGoneException(GoneListingDTO.builder()
                    .id(property.getId())
                    .listingType(property.getListingType())
                    .city(property.getCity())
                    .state(property.getState())
                    .build());
        }
        throw new ResourceNotFoundException("Property", "id", id);
    }

    static boolean isGone(Property property) {
        return property.getPublishedAt() != null
                && (property.getDeletedAt() != null || GONE_STATUSES.contains(property.getStatus()));
    }

    /** ACTIVE listings of active agents, optionally in one city and of one type. */
    @Transactional(readOnly = true)
    public PageResponse<PublicListingCardDTO> search(String listingType, String state, String city, String sort,
                                                     int page, int size) {
        return search(listingType, state, city, null, null, null, sort, page, size);
    }

    /** As above, also narrowed by price range and minimum bedrooms (S12: city page filters). */
    @Transactional(readOnly = true)
    public PageResponse<PublicListingCardDTO> search(String listingType, String state, String city,
                                                     Long minPrice, Long maxPrice, Integer minBeds,
                                                     String sort, int page, int size) {
        if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0) || (minBeds != null && minBeds < 0)) {
            throw new BadRequestException("Price and bedrooms cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BadRequestException("Minimum price is above the maximum");
        }
        Sort order = SORTS.get(sort == null ? "newest" : sort);
        if (order == null) {
            throw new BadRequestException("Unknown sort. Use one of: " + String.join(", ", SORTS.keySet().stream().sorted().toList()));
        }
        String type = blankToNull(listingType);
        if (type != null && !LISTING_TYPES.contains(type)) {
            throw new BadRequestException("Unknown listing type. Use SALE or RENT");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), order);
        return PageResponse.of(propertyRepository.searchPublic(type, blankToNull(state), blankToNull(city),
                        minPrice == null ? null : java.math.BigDecimal.valueOf(minPrice),
                        maxPrice == null ? null : java.math.BigDecimal.valueOf(maxPrice), minBeds, pageable)
                .map(property -> PublicListingCardDTO.from(propertyMapper.toDTO(property), propertyMapper.getCardImageUrl(property))));
    }

    /** Cities with at least one public listing, per listing type. */
    @Transactional(readOnly = true)
    public List<PublicMarketDTO> markets() {
        return propertyRepository.findPublicMarkets().stream()
                .map(row -> PublicMarketDTO.builder()
                        .state(row.getState())
                        .stateSlug(Slugs.of("state", row.getState()))
                        .city(row.getCity())
                        .citySlug(Slugs.of("city", row.getCity()))
                        .listingType(row.getListingType())
                        .listings(row.getListings())
                        .lastModified(row.getLastModified())
                        .build())
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public PublicAgentProfileDTO getAgent(Long id) {
        User agent = requireActiveAgent(id);
        PublicAgentRef ref = PublicAgentRef.from(agent);
        Double average = ratingRepository.findAverageRatingByRealtorId(id)
                .map(raw -> BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .orElse(null);
        return PublicAgentProfileDTO.builder()
                .id(agent.getId())
                .name(ref.getName())
                .slug(ref.getSlug())
                .path(ref.getPath())
                .photoUrl(agent.getProfileImageUrl())
                .bio(agent.getBio())
                .brokerageName(agent.getBrokerageName())
                .licenseNumber(agent.isLicensePublic() ? agent.getLicenseNumber() : null)
                .licenseState(agent.isLicensePublic() ? agent.getLicenseState() : null)
                .serviceAreas(agent.getServiceAreas())
                .yearsExperience(agent.getYearsExperience())
                .ratingAverage(average)
                .ratingCount(ratingRepository.countByRealtor_Id(id))
                .activeListingCount(propertyRepository.countByOwnerAndStatus(id, "ACTIVE"))
                .memberSince(agent.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicListingCardDTO> getAgentListings(Long id, int page, int size) {
        requireActiveAgent(id);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
        return PageResponse.of(propertyRepository.findActiveByOwner(id, pageable)
                .map(property -> PublicListingCardDTO.from(propertyMapper.toDTO(property), propertyMapper.getCardImageUrl(property))));
    }

    @Transactional(readOnly = true)
    public PageResponse<SitemapEntryDTO> sitemapProperties(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SITEMAP_PAGE_SIZE),
                Sort.by("id"));
        return PageResponse.of(propertyRepository.findSitemapRows(pageable).map(row -> SitemapEntryDTO.builder()
                .id(row.getId())
                .path(Slugs.propertyPath(PublicPropertyDTO.slugOf(row.getTitle(), row.getCity(), row.getState()),
                        row.getId()))
                .lastModified(row.getUpdatedAt())
                .build()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SitemapEntryDTO> sitemapAgents(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SITEMAP_PAGE_SIZE),
                Sort.by("id"));
        return PageResponse.of(userRepository.findActiveAgents(pageable).map(agent -> SitemapEntryDTO.builder()
                .id(agent.getId())
                .path(PublicAgentRef.from(agent).getPath())
                .lastModified(agent.getUpdatedAt())
                .build()));
    }

    private User requireActiveAgent(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .filter(PropertyVisibility::isActiveAgent)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
    }
}
