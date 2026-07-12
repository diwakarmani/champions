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
    @Mapping(target = "ownerEmail", ignore = true)
    @Mapping(target = "ownerPhoneMasked", expression = "java(maskPhone(property.getOwner().getPhone()))")
    @Mapping(target = "ownerIsRealtor", expression = "java(property.getOwner().getRoles().stream().anyMatch(r -> \"REALTOR\".equals(r.getName())))")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "primaryImageUrl", expression = "java(getPrimaryImageUrl(property))")
    @Mapping(target = "amenities", source = "amenities")
    @Mapping(target = "isVerified", source = "verified")
    @Mapping(target = "isFeatured", source = "featured")
    @Mapping(target = "isPremium", source = "premium")
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
    // isActive needs an explicit mapping: the entity's Lombok-generated getter is isActive(),
    // which JavaBean introspection (and MapStruct's implicit matching) resolves to property
    // name "active" — it silently fails to match the DTO's "isActive" property otherwise
    // (unmappedTargetPolicy = IGNORE swallows the mismatch instead of failing the build).
    @Mapping(target = "subTypes", source = "subTypes")
    @Mapping(target = "isActive", source = "active")
    PropertyTypeDTO toTypeDTO(PropertyType entity);

    List<PropertyTypeDTO> toTypeDTOList(List<PropertyType> entities);

    @Mapping(target = "isActive", source = "active")
    PropertySubTypeDTO toSubTypeDTO(PropertySubType entity);

    // PropertyAmenity mappings
    @Mapping(target = "isActive", source = "active")
    PropertyAmenityDTO toAmenityDTO(PropertyAmenity entity);
    
    List<PropertyAmenityDTO> toAmenityDTOList(List<PropertyAmenity> entities);
    
    Set<PropertyAmenityDTO> toAmenityDTOSet(Set<PropertyAmenity> entities);
    
    @Mapping(target = "propertyTypeName",    source = "propertyType.name")
    @Mapping(target = "propertySubTypeName", source = "propertySubType.name")
    @Mapping(target = "primaryImageUrl",     expression = "java(getPrimaryImageUrl(property))")
    @Mapping(target = "isVerified",          source = "verified")
    @Mapping(target = "isFeatured",          source = "featured")
    @Mapping(target = "isPremium",           source = "premium")
    @Mapping(target = "amenities",           source = "amenities")
    PropertyCompareDTO toCompareDTO(Property property);

    // Mask all but first 2 significant digits and last 2 digits of a phone number
    @Named("maskPhone")
    default String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (cleaned.length() <= 4) return "XXXX";
        int prefixEnd = cleaned.startsWith("+") ? Math.min(4, cleaned.length() - 2) : Math.min(2, cleaned.length() - 2);
        String prefix = cleaned.substring(0, prefixEnd);
        String suffix = cleaned.substring(cleaned.length() - 2);
        int maskLen = cleaned.length() - prefixEnd - 2;
        String masked = prefix + "X".repeat(Math.max(4, maskLen)) + suffix;
        return masked;
    }

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
