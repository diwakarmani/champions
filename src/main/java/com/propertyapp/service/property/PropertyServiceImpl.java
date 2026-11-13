package com.propertyapp.service.property;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.*;
import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.property.*;
import com.propertyapp.entity.property.PropertyContactEvent;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.DuplicateResourceException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.mapper.PropertyMapper;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.property.*;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.repository.property.PropertyContactEventRepository;
import com.propertyapp.service.notification.NotificationService;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import com.propertyapp.entity.property.ListingReassignment;
import com.propertyapp.repository.property.ListingReassignmentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final PropertySubTypeRepository propertySubTypeRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final UserRepository userRepository;
    private final PropertyMapper propertyMapper;
    private final LocalityRepository localityRepository;
private final PropertyContactEventRepository contactEventRepository;
    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;
    private final ListingReassignmentRepository listingReassignmentRepository;
    private final com.propertyapp.storage.ImageRefs imageRefs;

    /** Client's "configured approval process" (ADR-0005). */
    @Value("${app.listing.approval-required:true}")
    private boolean listingApprovalRequired;

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('REALTOR', 'SUPER_ADMIN')")
    public PropertyDTO createProperty(PropertyCreateRequest request) {

        log.info("Creating new property: {}", request.getTitle());

        User owner = resolveListingAgent(request.getAgentId());
        Long ownerId = owner.getId();

        PropertyType propertyType = propertyTypeRepository.findById(request.getPropertyTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("PropertyType", "id", request.getPropertyTypeId()));

        PropertySubType propertySubType = null;

        if (request.getPropertySubTypeId() != null) {

            propertySubType = propertySubTypeRepository
                    .findById(request.getPropertySubTypeId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "PropertySubType",
                                    "id",
                                    request.getPropertySubTypeId()
                            )
                    );

            if (!propertySubType.getPropertyType().getId()
                    .equals(propertyType.getId())) {

                throw new BadRequestException(
                        "Selected PropertySubType does not belong to PropertyType"
                );
            }
        }

        // Defense-in-depth against double-submit: the client already guards against a
        // double-tap, but two independent near-simultaneous requests (e.g. a client retry
        // outside this guard) can still both pass validation and insert. Reject an exact
        // repeat of the same owner+title within a short window instead of creating a second row.
        long recentDuplicates = propertyRepository.countByOwnerIdAndTitleAndCreatedAtAfterAndDeletedAtIsNull(
                ownerId, request.getTitle(), LocalDateTime.now().minusSeconds(10));
        if (recentDuplicates > 0) {
            throw new DuplicateResourceException(
                    "A listing with this title was already submitted moments ago. Check My Listings before submitting again.");
        }

        Property property = propertyMapper.toEntity(request);

        property.setOwner(owner);
        property.setPropertyType(propertyType);
        property.setPropertySubType(propertySubType);

        property.setStatus("DRAFT");
        property.setViewCount(0);
        property.setInquiryCount(0);

        if (request.getLocalityId() != null) {
            Locality locality = localityRepository.findById(request.getLocalityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Locality", "id", request.getLocalityId()));

            if (!locality.getIsActive()) {
                throw new BadRequestException("Selected locality is not active");
            }

            property.setLocalityRef(locality);

            BigDecimal lat = request.getLatitude() != null ? request.getLatitude() : locality.getLatitude();
            BigDecimal lng = request.getLongitude() != null ? request.getLongitude() : locality.getLongitude();
            property.setLatitude(lat);
            property.setLongitude(lng);

            if (lat != null && lng != null) {
                property.setLocation(createPoint(lat.doubleValue(), lng.doubleValue()));
            }

            property.setCity(locality.getCity().getName());
            property.setState(locality.getCity().getState().getName());
            property.setCountry(locality.getCity().getState().getCountry().getName());
            property.setLocality(locality.getName());
        } else {
            if (request.getLatitude() != null && request.getLongitude() != null) {
                property.setLatitude(request.getLatitude());
                property.setLongitude(request.getLongitude());
                property.setLocation(createPoint(
                        request.getLatitude().doubleValue(),
                        request.getLongitude().doubleValue()
                ));
            }
        }

        if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
            Set<PropertyAmenity> amenities =
                    propertyAmenityRepository.findByIdIn(request.getAmenityIds());
            property.setAmenities(amenities);
        }

        property = propertyRepository.save(property);

        log.info("Property created successfully with ID: {}", property.getId());

        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    public PropertyDTO updateProperty(Long id, PropertyUpdateRequest request) {

        log.info("Updating property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "update this property");

        if (request.getTitle() != null) property.setTitle(request.getTitle());
        if (request.getDescription() != null) property.setDescription(request.getDescription());
        if (request.getPrice() != null) property.setPrice(request.getPrice());
        if (request.getDepositAmount() != null) property.setDepositAmount(request.getDepositAmount());
        if (request.getMaintenanceCharge() != null) property.setMaintenanceCharge(request.getMaintenanceCharge());
        if (request.getListingType() != null) property.setListingType(request.getListingType());
        if (request.getBedrooms() != null) property.setBedrooms(request.getBedrooms());
        if (request.getBathrooms() != null) property.setBathrooms(request.getBathrooms());
        if (request.getCarpetArea() != null) property.setCarpetArea(request.getCarpetArea());
        if (request.getBuiltUpArea() != null) property.setBuiltUpArea(request.getBuiltUpArea());
        if (request.getFurnishedStatus() != null) property.setFurnishedStatus(request.getFurnishedStatus());
        if (request.getAvailableFrom() != null) property.setAvailableFrom(request.getAvailableFrom());
        if (request.getAddressLine1() != null) property.setAddressLine1(request.getAddressLine1());
        if (request.getPostalCode() != null) property.setPostalCode(request.getPostalCode());
        if (request.getOwnershipType() != null) property.setOwnershipType(request.getOwnershipType());
        if (request.getPossessionStatus() != null) property.setPossessionStatus(request.getPossessionStatus());
        if (request.getKitchenType() != null) property.setKitchenType(request.getKitchenType());
        if (request.getWaterSupply() != null) property.setWaterSupply(request.getWaterSupply());

        if (request.getLocalityId() != null) {

            Locality locality = localityRepository.findById(request.getLocalityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Locality", "id", request.getLocalityId()));

            property.setLocalityRef(locality);

            property.setLatitude(locality.getLatitude());
            property.setLongitude(locality.getLongitude());

            Point point = createPoint(
                    locality.getLatitude().doubleValue(),
                    locality.getLongitude().doubleValue()
            );

            property.setLocation(point);

            property.setCity(locality.getCity().getName());
            property.setState(locality.getCity().getState().getName());
            property.setCountry(locality.getCity().getState().getCountry().getName());
            property.setLocality(locality.getName());
        }

        if (request.getAmenityIds() != null) {
            Set<PropertyAmenity> amenities =
                    propertyAmenityRepository.findByIdIn(request.getAmenityIds());
            property.setAmenities(amenities);
        }

        property = propertyRepository.save(property);

        log.info("Property updated successfully: {}", id);

        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional // not read-only: it records the view
    public PropertyDTO getPropertyById(Long id) {
        log.info("Fetching property by id: {}", id);

        Property property = findViewableProperty(id);

        if (PropertyVisibility.isPubliclyVisible(property)) {
            incrementViewCount(id);
        }

        return propertyMapper.toDTO(property);
    }

    /** The agent a new listing belongs to (ADR-0005): the caller, or the agent an admin names. */
    private User resolveListingAgent(Long requestedAgentId) {
        Long callerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        if (requestedAgentId == null || requestedAgentId.equals(callerId)) {
            User caller = userRepository.findByIdAndDeletedAtIsNull(callerId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
            if (caller.isAgentSuspended()) {
                throw new BadRequestException("Your agent account is suspended, so you cannot create listings");
            }
            if (!PropertyVisibility.isActiveAgent(caller)) {
                throw new BadRequestException("A listing must belong to an agent: choose an agent for this listing");
            }
            return caller;
        }

        if (!SecurityUtils.hasRole("SUPER_ADMIN")) {
            throw new UnauthorizedException("Only an admin can create a listing for another agent");
        }
        return findActiveAgent(requestedAgentId);
    }

    private User findActiveAgent(Long agentId) {
        User agent = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", agentId));
        if (!PropertyVisibility.isActiveAgent(agent)) {
            throw new BadRequestException("The selected user is not an active agent");
        }
        return agent;
    }

    /** Listings are managed by an admin, or by their owner while that owner is an active agent. */
    private void requireListingManager(Property property, Long currentUserId, String action) {
        boolean isOwningAgent = property.getOwner().getId().equals(currentUserId)
                && PropertyVisibility.isActiveAgent(property.getOwner());
        if (!isOwningAgent && !SecurityUtils.hasRole("SUPER_ADMIN")) {
            throw new UnauthorizedException("You don't have permission to " + action);
        }
    }

    private void requireActiveAgentOwner(Property property) {
        if (!PropertyVisibility.isActiveAgent(property.getOwner())) {
            throw new BadRequestException(
                    "This listing has no active agent. Reassign it to an agent before it can go live.");
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO reassignListing(Long id, Long agentId) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        User newAgent = findActiveAgent(agentId);
        User previousOwner = property.getOwner();

        if (previousOwner.getId().equals(newAgent.getId())) {
            throw new BadRequestException("This listing already belongs to that agent");
        }
        Long adminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        property.setOwner(newAgent);
        property = propertyRepository.save(property);

        listingReassignmentRepository.save(ListingReassignment.builder()
                .propertyId(property.getId())
                .fromUserId(previousOwner.getId())
                .toUserId(newAgent.getId())
                .reassignedBy(adminId)
                .build());

        notificationService.send(
                newAgent.getId(),
                NotificationType.LISTING_REASSIGNED,
                "A listing was assigned to you",
                "\"" + property.getTitle() + "\" is now yours to manage, including its enquiries.",
                "PROPERTY",
                property.getId());

        log.info("Listing {} reassigned from user {} to agent {} by admin {}",
                id, previousOwner.getId(), newAgent.getId(), adminId);
        return propertyMapper.toDTO(property);
    }

    private Property findViewableProperty(Long id) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        Long viewerId = SecurityUtils.getCurrentUserId().orElse(null);
        if (!PropertyVisibility.canView(property, viewerId, SecurityUtils.hasRole("SUPER_ADMIN"))) {
            throw new ResourceNotFoundException("Property", "id", id);
        }
        return property;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PropertyDTO> searchProperties(PropertySearchRequest request, Pageable pageable) {
        log.info("Searching properties with filters: {}", request);

        Specification<Property> spec = PropertySpecification.withFilters(
                request.getCity(),
                request.getState(),
                request.getListingType(),
                request.getPropertyTypeId(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getMinBedrooms(),
                request.getMaxBedrooms(),
                request.getFurnishedStatus(),
                "ACTIVE", // Only show active properties in public search
                request.getLocalities()
        );

        Page<Property> properties = propertyRepository.findAll(spec, pageable);
        Page<PropertyDTO> propertyDTOs = properties.map(propertyMapper::toDTO);

        return PageResponse.of(propertyDTOs);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PropertyDTO> getMyListings(Pageable pageable) {
        log.info("Fetching user's property listings");

        Long ownerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        Page<Property> properties = propertyRepository.findByOwnerIdAndDeletedAtIsNull(ownerId, pageable);
        Page<PropertyDTO> propertyDTOs = properties.map(propertyMapper::toDTO);

        return PageResponse.of(propertyDTOs);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<PropertyDTO> getAllProperties(Pageable pageable) {
        Page<Property> properties = propertyRepository.findByDeletedAtIsNull(pageable);
        return PageResponse.of(properties.map(propertyMapper::toDTO));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<PropertyDTO> getPropertiesByStatus(String status, Pageable pageable) {
        log.info("Fetching properties by status: {}", status);

        Page<Property> properties = propertyRepository.findByStatusAndDeletedAtIsNull(status, pageable);
        Page<PropertyDTO> propertyDTOs = properties.map(propertyMapper::toDTO);

        return PageResponse.of(propertyDTOs);
    }

    @Override
    @Transactional
    public void deleteProperty(Long id) {
        log.info("Deleting property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        // Agents ask for deletion and an admin decides; only an admin deletes directly.
        if (!SecurityUtils.hasRole("SUPER_ADMIN")) {
            throw new UnauthorizedException("Please submit a deletion request for admin approval.");
        }

        property.markAsDeleted(currentUserId);
        propertyRepository.save(property);
        log.info("Property deleted (userId={}): {}", currentUserId, id);
    }

    @Override
    @Transactional
    public PropertyDTO requestDeletion(Long id) {
        log.info("Deletion requested for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "request deletion of this property");

        if ("DELETION_REQUESTED".equals(property.getStatus())) {
            throw new BadRequestException("A deletion request is already pending for this property.");
        }

        if ("INACTIVE".equals(property.getStatus())) {
            throw new BadRequestException("This property is already inactive.");
        }

        property.setStatus("DELETION_REQUESTED");
        property.setRejectionReason(null);
        property = propertyRepository.save(property);
        log.info("Deletion request submitted for property: {}", id);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO approveDeletion(Long id) {
        log.info("Admin approving deletion for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        if (!"DELETION_REQUESTED".equals(property.getStatus())) {
            throw new BadRequestException("Property does not have a pending deletion request.");
        }

        property.setStatus("INACTIVE");
        property.setRejectionReason(null);
        property = propertyRepository.save(property);
        log.info("Deletion approved — property marked INACTIVE: {}", id);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO rejectDeletion(Long id, String reason) {
        log.info("Admin rejecting deletion for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        if (!"DELETION_REQUESTED".equals(property.getStatus())) {
            throw new BadRequestException("Property does not have a pending deletion request.");
        }

        property.setStatus("ACTIVE");
        property.setRejectionReason(reason);
        property = propertyRepository.save(property);
        log.info("Deletion rejected — property restored to ACTIVE: {}", id);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    public PropertyDTO publishProperty(Long id) {
        log.info("Publishing property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        // Check ownership
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "manage this property");

        // Validate required fields
        if (property.getTitle() == null || property.getPrice() == null) {
            throw new BadRequestException("Property must have title and price to be published");
        }

        if ("PENDING_APPROVAL".equals(property.getStatus())) {
            throw new BadRequestException("Property is already pending admin approval");
        }
        if ("ACTIVE".equals(property.getStatus())) {
            throw new BadRequestException("Property is already active");
        }

        requireActiveAgentOwner(property);

        if (listingApprovalRequired) {
            property.setStatus("PENDING_APPROVAL");
            log.info("Property submitted for admin approval: {}", id);
        } else {
            property.setStatus("ACTIVE");
            property.setRejectionReason(null);
            if (property.getPublishedAt() == null) {
                property.setPublishedAt(LocalDateTime.now());
            }
            log.info("Property published without approval (app.listing.approval-required=false): {}", id);
        }

        property = propertyRepository.save(property);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO updatePropertyStatus(Long id, String status) {
        log.info("Updating property status: {} to {}", id, status);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        // Validate status
        List<String> validStatuses = List.of("DRAFT", "PENDING_APPROVAL", "ACTIVE", "SOLD", "RENTED", "INACTIVE");
        if (!validStatuses.contains(status)) {
            throw new BadRequestException("Invalid status: " + status);
        }

        if ("ACTIVE".equals(status)) {
            requireActiveAgentOwner(property);
        }

        property.setStatus(status);

        if ("ACTIVE".equals(status) && property.getPublishedAt() == null) {
            property.setPublishedAt(LocalDateTime.now());
        }

        property = propertyRepository.save(property);

        log.info("Property status updated successfully: {}", id);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    public PropertyImageDTO addPropertyImage(Long propertyId, PropertyImageDTO imageDTO) {
        log.info("Adding image to property: {}", propertyId);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        // Check ownership
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "manage this property");

        PropertyImage image = propertyMapper.toImageEntity(imageDTO);
        image.setProperty(property);
        // ADR-0016: keep the storage key, not the host; refuse photos on hosts we do not serve.
        image.setImageUrl(imageRefs.toStored(imageDTO.getImageUrl()));
        String card = imageRefs.cardFor(image.getImageUrl());
        image.setThumbnailUrl(card != null ? card : imageRefs.toStored(imageDTO.getThumbnailUrl()));

        // The first image is the cover. Later ones never are on add (set-primary changes it), so a
        // client cannot create two covers by sending isPrimary.
        image.setPrimary(property.getImages().isEmpty());

        image = propertyImageRepository.save(image);

        log.info("Image added successfully to property: {}", propertyId);
        return propertyMapper.toImageDTO(image);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyImageDTO> getPropertyImages(Long propertyId) {
        log.info("Fetching images for property: {}", propertyId);

        findViewableProperty(propertyId);

        List<PropertyImage> images = propertyImageRepository.findByPropertyIdOrderByDisplayOrder(propertyId);
        return images.stream()
                .map(propertyMapper::toImageDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePropertyImage(Long propertyId, Long imageId) {
        log.info("Deleting image {} from property {}", imageId, propertyId);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        // Check ownership
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "manage this property");

        PropertyImage image = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyImage", "id", imageId));

        if (!image.getProperty().getId().equals(propertyId)) {
            throw new BadRequestException("Image does not belong to this property");
        }

        propertyImageRepository.delete(image);

        log.info("Image deleted successfully");
    }

    @Override
    @Transactional
    public void setPrimaryImage(Long propertyId, Long imageId) {
        log.info("Setting primary image {} for property {}", imageId, propertyId);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        // Check ownership
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        requireListingManager(property, currentUserId, "manage this property");

        PropertyImage newPrimaryImage = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyImage", "id", imageId));

        if (!newPrimaryImage.getProperty().getId().equals(propertyId)) {
            throw new BadRequestException("Image does not belong to this property");
        }

        // Unset current primary image
        propertyImageRepository.findByPropertyIdAndIsPrimaryTrue(propertyId)
                .ifPresent(currentPrimary -> {
                    currentPrimary.setPrimary(false);
                    propertyImageRepository.save(currentPrimary);
                });

        // Set new primary image
        newPrimaryImage.setPrimary(true);
        propertyImageRepository.save(newPrimaryImage);

        log.info("Primary image set successfully");
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO approveProperty(Long id) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        requireActiveAgentOwner(property);
        property.setStatus("ACTIVE");
        property.setRejectionReason(null);
        if (property.getPublishedAt() == null) {
            property.setPublishedAt(java.time.LocalDateTime.now());
        }
        property = propertyRepository.save(property);
        log.info("Property approved and set ACTIVE: {}", id);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO rejectProperty(Long id, String reason) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        property.setStatus("REJECTED");
        property.setRejectionReason(reason);
        property = propertyRepository.save(property);
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO toggleFeatured(Long id) {
        log.info("Toggling featured status for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        property.setFeatured(!property.isFeatured());
        property = propertyRepository.save(property);

        log.info("Featured status toggled to: {}", property.isFeatured());
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO toggleVerified(Long id) {
        log.info("Toggling verified status for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        property.setVerified(!property.isVerified());
        property = propertyRepository.save(property);

        log.info("Verified status toggled to: {}", property.isVerified());
        return propertyMapper.toDTO(property);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getFeaturedProperties(int limit) {
        log.info("Fetching featured properties, limit: {}", limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<Property> properties = propertyRepository.findFeaturedProperties(pageable);

        return properties.stream()
                .map(propertyMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        // Atomic, and leaves updated_at alone: a page view is not an edit of the listing.
        propertyRepository.incrementViewCount(id);
    }

    @Override
    @Transactional
    public ContactRevealResponse revealContact(Long propertyId, Long userId) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        PropertyVisibility.requireContactable(property);

        User owner = property.getOwner();
        if (owner.getId().equals(userId)) {
            throw new BadRequestException("You cannot reveal contact details on your own listing");
        }
        boolean alreadyContacted = contactEventRepository.existsByPropertyIdAndContactedById(propertyId, userId);

        if (!alreadyContacted) {
            User contactingUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            // Record the contact reveal event (for analytics / dedup)
            contactEventRepository.save(PropertyContactEvent.builder()
                    .property(property)
                    .contactedBy(contactingUser)
                    .build());
            property.incrementContactCount();
            propertyRepository.save(property);

            // Auto-create an inquiry so the contacting user can rate the owner later.
            // Skip if they already sent a formal inquiry for this property.
            if (!inquiryRepository.existsByProperty_IdAndInquirer_Id(propertyId, userId)) {
                Inquiry autoInquiry = Inquiry.builder()
                        .property(property)
                        .inquirer(contactingUser)
                        .name((contactingUser.getFirstName() + " " + contactingUser.getLastName()).trim())
                        .email(contactingUser.getEmail())
                        .phone(contactingUser.getPhone())
                        .message("I revealed the contact details and reached out directly.")
                        .status(InquiryStatus.NEW)
                        .source("CONTACT_REVEAL")
                        .build();
                inquiryRepository.save(autoInquiry);
                property.incrementInquiryCount();
                propertyRepository.save(property);
            }

            String contactingName = (contactingUser.getFirstName() + " " + contactingUser.getLastName()).trim();
            notificationService.send(
                    owner.getId(),
                    NotificationType.CONTACT_REVEALED,
                    "Someone wants to contact you!",
                    contactingName + " revealed your contact details for \"" + property.getTitle() + "\".",
                    "PROPERTY",
                    property.getId()
            );
        }

        return ContactRevealResponse.builder()
                .phone(owner.getPhone())
                .email(owner.getEmail())
                .ownerName((owner.getFirstName() + " " + owner.getLastName()).trim())
                .alreadyContacted(alreadyContacted)
                .build();
    }

    private Point createPoint(Double latitude, Double longitude) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        return geometryFactory.createPoint(new Coordinate(longitude, latitude));
    }
}
