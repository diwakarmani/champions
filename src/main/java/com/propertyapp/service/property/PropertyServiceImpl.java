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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('SELLER', 'REALTOR', 'SUPER_ADMIN')")
    public PropertyDTO createProperty(PropertyCreateRequest request) {

        log.info("Creating new property: {}", request.getTitle());

        Long ownerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        User owner = userRepository.findByIdAndDeletedAtIsNull(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));

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
    @CacheEvict(value = "properties", key = "#id")
    public PropertyDTO updateProperty(Long id, PropertyUpdateRequest request) {

        log.info("Updating property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        boolean isOwner = property.getOwner().getId().equals(currentUserId);
        boolean isAdmin = SecurityUtils.hasRole("SUPER_ADMIN");

        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You don't have permission to update this property");
        }

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
    @Transactional(readOnly = true)
    @Cacheable(value = "properties", key = "#id")
    public PropertyDTO getPropertyById(Long id) {
        log.info("Fetching property by id: {}", id);
        
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        
        // Increment view count asynchronously
        incrementViewCount(id);
        
        return propertyMapper.toDTO(property);
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
    @CacheEvict(value = "properties", key = "#id")
    public void deleteProperty(Long id) {
        log.info("Deleting property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        boolean isAdmin = SecurityUtils.hasRole("SUPER_ADMIN");
        boolean isRealtor = SecurityUtils.hasRole("REALTOR");

        if (isRealtor && !isAdmin) {
            throw new UnauthorizedException("Realtors must submit a deletion request for admin approval.");
        }

        if (!isAdmin) {
            boolean isOwner = property.getOwner().getId().equals(currentUserId);
            if (!isOwner) {
                throw new UnauthorizedException("You can only delete your own properties.");
            }
        }

        property.markAsDeleted(currentUserId);
        propertyRepository.save(property);
        log.info("Property deleted (userId={}): {}", currentUserId, id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "properties", key = "#id")
    public PropertyDTO requestDeletion(Long id) {
        log.info("Deletion requested for property: {}", id);

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        boolean isOwner = property.getOwner().getId().equals(currentUserId);
        if (!isOwner) {
            throw new UnauthorizedException("You can only request deletion of your own properties.");
        }

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
    @CacheEvict(value = "properties", key = "#id")
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
    @CacheEvict(value = "properties", key = "#id")
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
    @CacheEvict(value = "properties", key = "#id")
    public PropertyDTO publishProperty(Long id) {
        log.info("Publishing property: {}", id);
        
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        
        // Check ownership
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        
        if (!property.getOwner().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You don't have permission to publish this property");
        }
        
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

        property.setStatus("PENDING_APPROVAL");
        log.info("Property submitted for admin approval: {}", id);

        property = propertyRepository.save(property);
        return propertyMapper.toDTO(property);
    }
    
    @Override
    @Transactional
    @CacheEvict(value = "properties", key = "#id")
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
        
        if (!property.getOwner().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You don't have permission to add images to this property");
        }
        
        PropertyImage image = propertyMapper.toImageEntity(imageDTO);
        image.setProperty(property);
        
        // If this is the first image, make it primary
        if (property.getImages().isEmpty()) {
            image.setPrimary(true);
        }
        
        image = propertyImageRepository.save(image);
        
        log.info("Image added successfully to property: {}", propertyId);
        return propertyMapper.toImageDTO(image);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PropertyImageDTO> getPropertyImages(Long propertyId) {
        log.info("Fetching images for property: {}", propertyId);
        
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
        
        if (!property.getOwner().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You don't have permission to delete images from this property");
        }
        
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
        
        if (!property.getOwner().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You don't have permission to modify this property");
        }
        
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
    @CacheEvict(value = "properties", key = "#id")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PropertyDTO approveProperty(Long id) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
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
    @CacheEvict(value = "properties", key = "#id")
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
    @CacheEvict(value = "properties", key = "#id")
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
    @CacheEvict(value = "properties", key = "#id")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'REALTOR')")
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
        propertyRepository.findByIdAndDeletedAtIsNull(id)
                .ifPresent(property -> {
                    property.incrementViewCount();
                    propertyRepository.save(property);
                });
    }

    @Override
    @Transactional
    public ContactRevealResponse revealContact(Long propertyId, Long userId) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

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