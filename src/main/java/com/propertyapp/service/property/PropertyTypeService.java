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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyTypeService {
    
    private final PropertyTypeRepository propertyTypeRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final PropertyMapper propertyMapper;

    private final PropertyTypeRepository typeRepo;
    private final PropertySubTypeRepository subTypeRepo;
    private final PropertyAmenityRepository amenityRepo;

    // ================= TYPE =================

    public PropertyTypeDTO createType(PropertyTypeDTO dto) {

        if (typeRepo.existsByName(dto.getName()))
            throw new RuntimeException("Property type already exists");

        PropertyType type = PropertyType.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .displayOrder(dto.getDisplayOrder())
                .isActive(true)
                .build();

        return map(typeRepo.save(type));
    }

    public PropertyTypeDTO updateType(Long id, PropertyTypeDTO dto) {

        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));

        type.setName(dto.getName());
        type.setDescription(dto.getDescription());
        type.setDisplayOrder(dto.getDisplayOrder());

        return map(type);
    }

    public void deactivateType(Long id) {

        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));

        type.setActive(false);
    }

    public PropertyTypeDTO updateDisplayOrder(Long id, Integer order) {

        PropertyType type = typeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Type not found"));

        type.setDisplayOrder(order);

        return map(type);
    }

    // ================= SUB TYPE =================

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

    public PropertySubTypeDTO createSubType(PropertySubTypeCreateRequest request) {

        PropertyType propertyType = propertyTypeRepository.findById(request.getPropertyTypeId())
                .orElseThrow(() -> new RuntimeException("Property type not found"));

        if (subTypeRepo.existsByNameAndPropertyType(
                request.getName(), propertyType)) {
            throw new RuntimeException("SubType already exists");
        }

        Integer maxOrder = subTypeRepo
                .findMaxDisplayOrderByPropertyType(propertyType);

        PropertySubType subType = PropertySubType.builder()
                .propertyType(propertyType)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder((maxOrder == null ? 0 : maxOrder) + 1)
                .isActive(true)
                .build();

        return propertyMapper.toSubTypeDTO(
                subTypeRepo.save(subType)
        );
    }

    public PropertySubTypeDTO updateSubType(
            Long id,
            PropertySubTypeUpdateRequest request) {

        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));

        subType.setName(request.getName());
        subType.setDescription(request.getDescription());

        if (request.getDisplayOrder() != null) {
            subType.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsActive() != null) {
            subType.setActive(request.getIsActive());
        }

        return propertyMapper.toSubTypeDTO(subType);
    }

    public PropertySubTypeDTO updateSubTypeOrder(Long id, Integer displayOrder) {

        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));

        subType.setDisplayOrder(displayOrder);

        return propertyMapper.toSubTypeDTO(subType);
    }

    public void deactivateSubType(Long id) {

        PropertySubType subType = subTypeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("SubType not found"));

        subType.setActive(false);
    }

    public PropertyAmenityDTO createAmenity(
            PropertyAmenityCreateRequest request) {

        if (propertyAmenityRepository.existsByName(request.getName())) {
            throw new RuntimeException("Amenity already exists");
        }

        Integer maxOrder = propertyAmenityRepository
                .findMaxDisplayOrderByCategory(request.getCategory());

        PropertyAmenity amenity = PropertyAmenity.builder()
                .name(request.getName())
                .iconClass(request.getIconClass())
                .category(request.getCategory())
                .displayOrder((maxOrder == null ? 0 : maxOrder) + 1)
                .isActive(true)
                .build();

        return propertyMapper.toAmenityDTO(
                propertyAmenityRepository.save(amenity)
        );
    }


    // ================= AMENITY =================

    public PropertyAmenityDTO createAmenity(PropertyAmenityDTO dto) {

        if (amenityRepo.existsByName(dto.getName()))
            throw new RuntimeException("Amenity already exists");

        PropertyAmenity amenity = PropertyAmenity.builder()
                .name(dto.getName())
                .iconClass(dto.getIconClass())
                .category(dto.getCategory())
                .displayOrder(dto.getDisplayOrder())
                .isActive(true)
                .build();

        return map(amenityRepo.save(amenity));
    }

    // ================= GETTERS =================

//    public List<PropertyTypeDTO> getAllPropertyTypes() {
//        return typeRepo.findByIsActiveTrueOrderByDisplayOrderAsc()
//                .stream().map(this::map).toList();
//    }
//
//    public List<PropertyAmenityDTO> getAllAmenities() {
//        return amenityRepo.findByIsActiveTrueOrderByDisplayOrderAsc()
//                .stream().map(this::map).toList();
//    }

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

    @Transactional(readOnly = true)
    @Cacheable(value = "propertyTypes")
    public List<PropertyTypeDTO> getAllPropertyTypes() {
        log.info("Fetching all property types");
        
        List<PropertyType> propertyTypes = propertyTypeRepository.findByIsActiveTrueOrderByDisplayOrder();
        
        return propertyTypes.stream()
                .map(propertyMapper::toTypeDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "propertyTypes", key = "'amenities'")
    public List<PropertyAmenityDTO> getAllAmenities() {
        log.info("Fetching all property amenities");
        
        List<PropertyAmenity> amenities = propertyAmenityRepository.findByIsActiveTrueOrderByDisplayOrder();
        
        return amenities.stream()
                .map(propertyMapper::toAmenityDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<PropertyAmenityDTO> getAmenitiesByCategory(String category) {
        log.info("Fetching amenities by category: {}", category);
        
        List<PropertyAmenity> amenities = propertyAmenityRepository.findByCategoryOrderByDisplayOrder(category);
        
        return amenities.stream()
                .map(propertyMapper::toAmenityDTO)
                .collect(Collectors.toList());
    }
}