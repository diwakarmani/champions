package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactRevealResponse {
    private String phone;
    private String email;
    private String ownerName;
    private boolean alreadyContacted;
}
