package com.propertyapp.service.property;

import com.propertyapp.dto.property.*;
import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertySubType;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.mapper.PropertyMapper;
import com.propertyapp.repository.property.PropertyAmenityRepository;
import com.propertyapp.repository.property.PropertySubTypeRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyTypeService {

    private final PropertyTypeRepository typeRepo;
    private final PropertySubTypeRepository subTypeRepo;
    private final PropertyAmenityRepository amenityRepo;
    private final PropertyMapper propertyMapper;

    // ================= TYPE =================

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "propertyTypes", allEntries = true)
    })
    public PropertyTypeDTO createType(PropertyTypeDTO dto) {
        if (typeRepo.existsByName(dto.getName()))
            throw new RuntimeException("Property type already exists");

        PropertyType type = PropertyType.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .displayOrder(dto.getDisplayOrder())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return map(typeRepo.save(type));
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "propertyTypes", allEntries = true)
    })
    public PropertyTypeDTO updateType(Long id, PropertyTypeDTO dto) {
        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));

        type.setName(dto.getName());
        type.setDescription(dto.getDescription());
        if (dto.getDisplayOrder() != null) type.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getIsActive() != null) type.setActive(dto.getIsActive());

        return map(typeRepo.save(type));
    }

    @Transactional
    @CacheEvict(value = "propertyTypes", allEntries = true)
    public PropertyTypeDTO updateDisplayOrder(Long id, Integer order) {
        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));
        type.setDisplayOrder(order);
        return map(typeRepo.save(type));
    }

    @Transactional
    public void deactivateType(Long id) {
        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));
        type.setActive(false);
        typeRepo.save(type);
    }

    // ================= SUB TYPE =================

    @Transactional
    @CacheEvict(value = "propertyTypes", allEntries = true)
    public PropertySubTypeDTO createSubType(PropertySubTypeDTO dto) {
        PropertyType type = typeRepo.findById(dto.getPropertyTypeId())
                .orElseThrow(() -> new RuntimeException("Type not found"));

        if (subTypeRepo.existsByNameAndPropertyType(dto.getName(), type))
            throw new RuntimeException("SubType already exists");

        PropertySubType subType = PropertySubType.builder()
                .propertyType(type)
                .name(dto.getName())
                .description(dto.getDescription())
                .displayOrder(dto.getDisplayOrder())
                .isActive(true)
                .build();

        return map(subTypeRepo.save(subType));
    }

    @Transactional
    @CacheEvict(value = "propertyTypes", allEntries = true)
    public PropertySubTypeDTO createSubType(PropertySubTypeCreateRequest request) {
        PropertyType propertyType = typeRepo.findById(request.getPropertyTypeId())
                .orElseThrow(() -> new RuntimeException("Property type not found"));

        if (subTypeRepo.existsByNameAndPropertyType(request.getName(), propertyType))
            throw new RuntimeException("SubType already exists");

        Integer maxOrder = subTypeRepo.findMaxDisplayOrderByPropertyType(propertyType);

        PropertySubType subType = PropertySubType.builder()
                .propertyType(propertyType)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder((maxOrder == null ? 0 : maxOrder) + 1)
                .isActive(true)
                .build();

        return propertyMapper.toSubTypeDTO(subTypeRepo.save(subType));
    }

    @Transactional
    @CacheEvict(value = "propertyTypes", allEntries = true)
    public PropertySubTypeDTO updateSubType(Long id, PropertySubTypeUpdateRequest request) {
        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));

        subType.setName(request.getName());
        subType.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) subType.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) subType.setActive(request.getIsActive());

        return propertyMapper.toSubTypeDTO(subTypeRepo.save(subType));
    }

    @Transactional
    @CacheEvict(value = "propertyTypes", allEntries = true)
    public PropertySubTypeDTO updateSubTypeOrder(Long id, Integer displayOrder) {
        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));
        subType.setDisplayOrder(displayOrder);
        return propertyMapper.toSubTypeDTO(subTypeRepo.save(subType));
    }

    @Transactional
    public void deactivateSubType(Long id) {
        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));
        subType.setActive(false);
        subTypeRepo.save(subType);
    }

    // ================= AMENITY =================

    @Transactional
    @CacheEvict(value = "propertyTypes", key = "'amenities'")
    public PropertyAmenityDTO createAmenity(PropertyAmenityDTO dto) {
        if (amenityRepo.existsByName(dto.getName()))
            throw new RuntimeException("Amenity already exists");

        Integer maxOrder = amenityRepo.findMaxDisplayOrderByCategory(dto.getCategory());

        PropertyAmenity amenity = PropertyAmenity.builder()
                .name(dto.getName())
                .iconClass(dto.getIconClass())
                .category(dto.getCategory())
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : (maxOrder == null ? 0 : maxOrder) + 1)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return map(amenityRepo.save(amenity));
    }

    @Transactional
    @CacheEvict(value = "propertyTypes", key = "'amenities'")
    public PropertyAmenityDTO createAmenity(PropertyAmenityCreateRequest request) {
        if (amenityRepo.existsByName(request.getName()))
            throw new RuntimeException("Amenity already exists");

        Integer maxOrder = amenityRepo.findMaxDisplayOrderByCategory(request.getCategory());

        PropertyAmenity amenity = PropertyAmenity.builder()
                .name(request.getName())
                .iconClass(request.getIconClass())
                .category(request.getCategory())
                .displayOrder((maxOrder == null ? 0 : maxOrder) + 1)
                .isActive(true)
                .build();

        return propertyMapper.toAmenityDTO(amenityRepo.save(amenity));
    }

    // ================= GETTERS =================

    @Transactional(readOnly = true)
    @Cacheable(value = "propertyTypes")
    public List<PropertyTypeDTO> getAllPropertyTypes() {
        log.info("Fetching all property types");
        return typeRepo.findByIsActiveTrueOrderByDisplayOrder()
                .stream()
                .map(propertyMapper::toTypeDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "propertyTypes", key = "'amenities'")
    public List<PropertyAmenityDTO> getAllAmenities() {
        log.info("Fetching all property amenities");
        return amenityRepo.findByIsActiveTrueOrderByDisplayOrder()
                .stream()
                .map(propertyMapper::toAmenityDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PropertyAmenityDTO> getAmenitiesByCategory(String category) {
        return amenityRepo.findByCategoryOrderByDisplayOrder(category)
                .stream()
                .map(propertyMapper::toAmenityDTO)
                .collect(Collectors.toList());
    }

    // ================= MAPPERS =================

    private PropertyTypeDTO map(PropertyType type) {
        return PropertyTypeDTO.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .displayOrder(type.getDisplayOrder())
                .isActive(type.isActive())
                .build();
    }

    private PropertySubTypeDTO map(PropertySubType subType) {
        return PropertySubTypeDTO.builder()
                .id(subType.getId())
                .propertyTypeId(subType.getPropertyType().getId())
                .name(subType.getName())
                .description(subType.getDescription())
                .displayOrder(subType.getDisplayOrder())
                .isActive(subType.isActive())
                .build();
    }

    private PropertyAmenityDTO map(PropertyAmenity amenity) {
        return PropertyAmenityDTO.builder()
                .id(amenity.getId())
                .name(amenity.getName())
                .iconClass(amenity.getIconClass())
                .category(amenity.getCategory())
                .displayOrder(amenity.getDisplayOrder())
                .isActive(amenity.isActive())
                .build();
    }
}
