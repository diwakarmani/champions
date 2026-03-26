package com.propertyapp.mapper;

import com.propertyapp.dto.property.*;
import com.propertyapp.entity.property.*;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyMapper {
    
    // Property mappings
    @Mapping(target = "propertyTypeId", source = "propertyType.id")
    @Mapping(target = "propertyTypeName", source = "propertyType.name")
    @Mapping(target = "propertySubTypeId", source = "propertySubType.id")
    @Mapping(target = "propertySubTypeName", source = "propertySubType.name")
    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", expression = "java(property.getOwner().getFirstName() + \" \" + property.getOwner().getLastName())")
    @Mapping(target = "ownerEmail", source = "owner.email")
    @Mapping(target = "ownerPhone", source = "owner.phone")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "primaryImageUrl", expression = "java(getPrimaryImageUrl(property))")
    @Mapping(target = "amenities", source = "amenities")
    PropertyDTO toDTO(Property property);
    
    List<PropertyDTO> toDTOList(List<Property> properties);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "propertyType", ignore = true)
    @Mapping(target = "propertySubType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "amenities", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "inquiryCount", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "isVerified", ignore = true)
    @Mapping(target = "isFeatured", ignore = true)
    @Mapping(target = "isPremium", ignore = true)
    Property toEntity(PropertyCreateRequest request);
    
    // PropertyImage mappings
    @Mapping(target = "property", ignore = true)
    PropertyImage toImageEntity(PropertyImageDTO dto);
    
    PropertyImageDTO toImageDTO(PropertyImage entity);
    
    List<PropertyImageDTO> toImageDTOList(List<PropertyImage> entities);
    
    // PropertyType mappings
    @Mapping(target = "subTypes", source = "subTypes")
    PropertyTypeDTO toTypeDTO(PropertyType entity);
    
    List<PropertyTypeDTO> toTypeDTOList(List<PropertyType> entities);
    
    PropertySubTypeDTO toSubTypeDTO(PropertySubType entity);
    
    // PropertyAmenity mappings
    PropertyAmenityDTO toAmenityDTO(PropertyAmenity entity);
    
    List<PropertyAmenityDTO> toAmenityDTOList(List<PropertyAmenity> entities);
    
    Set<PropertyAmenityDTO> toAmenityDTOSet(Set<PropertyAmenity> entities);
    
    // Helper method to get primary image URL
    default String getPrimaryImageUrl(Property property) {
        if (property.getImages() == null || property.getImages().isEmpty()) {
            return null;
        }

        return property.getImages().stream()
                .filter(PropertyImage::isPrimary)
                .findFirst()
                .map(PropertyImage::getImageUrl)
                .orElse(property.getImages().iterator().next().getImageUrl());
    }

}