package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyImageDTO {
    
    private Long id;
    
    @NotBlank(message = "Image URL is required")
    private String imageUrl;
    
    private String thumbnailUrl;
    private String caption;
    private Integer displayOrder;
    @JsonProperty("isPrimary")
    private boolean isPrimary;
}