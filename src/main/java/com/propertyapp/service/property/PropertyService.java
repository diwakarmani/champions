package com.propertyapp.service.property;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PropertyService {
    
    PropertyDTO createProperty(PropertyCreateRequest request);
    
    PropertyDTO updateProperty(Long id, PropertyUpdateRequest request);
    
    PropertyDTO getPropertyById(Long id);
    
    PageResponse<PropertyDTO> searchProperties(PropertySearchRequest request, Pageable pageable);
    
    PageResponse<PropertyDTO> getMyListings(Pageable pageable);
    
    PageResponse<PropertyDTO> getPropertiesByStatus(String status, Pageable pageable);
    
    void deleteProperty(Long id);
    
    PropertyDTO publishProperty(Long id);
    
    PropertyDTO updatePropertyStatus(Long id, String status);
    
    PropertyImageDTO addPropertyImage(Long propertyId, PropertyImageDTO imageDTO);
    
    List<PropertyImageDTO> getPropertyImages(Long propertyId);
    
    void deletePropertyImage(Long propertyId, Long imageId);
    
    void setPrimaryImage(Long propertyId, Long imageId);
    
    PropertyDTO toggleFeatured(Long id);
    
    PropertyDTO toggleVerified(Long id);
    
    List<PropertyDTO> getFeaturedProperties(int limit);
    
    void incrementViewCount(Long id);
}