package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyImageDTO {
    
    private Long id;
    
    /** Web and mobile render these: only plain http(s) addresses, and no longer than the column. */
    @NotBlank(message = "Image URL is required")
    @Size(max = 255, message = "Image URL must be at most 255 characters")
    @Pattern(regexp = "^https?://\\S+$", message = "Image URL must be an http(s) address")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    private String imageUrl;

    @Size(max = 255, message = "Thumbnail URL must be at most 255 characters")
    @Pattern(regexp = "^https?://\\S+$", message = "Thumbnail URL must be an http(s) address")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    private String thumbnailUrl;
    private String caption;
    private Integer displayOrder;
    @JsonProperty("isPrimary")
    private boolean isPrimary;

    // Explicit accessors: Lombok's isPrimary()/setPrimary() made Jackson see a second property,
    // "primary", and the API schema listed one or both depending on test order (S10).
    @JsonProperty("isPrimary")
    public boolean isPrimary() {
        return isPrimary;
    }

    @JsonProperty("isPrimary")
    public void setPrimary(boolean primary) {
        this.isPrimary = primary;
    }
}