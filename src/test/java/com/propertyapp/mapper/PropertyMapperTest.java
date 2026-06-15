package com.propertyapp.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyMapperTest {

    private final PropertyMapper mapper = Mappers.getMapper(PropertyMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapsAndSerializesAdminFlagsUsingTheMobileContract() throws Exception {
        User owner = User.builder()
                .firstName("Demo")
                .lastName("Seller")
                .email("seller@example.com")
                .passwordHash("unused")
                .build();
        Property property = Property.builder()
                .title("Mapped property")
                .propertyType(PropertyType.builder().name("Apartment").build())
                .owner(owner)
                .listingType("SALE")
                .price(BigDecimal.TEN)
                .addressLine1("1 Test Street")
                .city("Los Angeles")
                .state("California")
                .country("United States")
                .isFeatured(true)
                .isVerified(true)
                .isPremium(true)
                .build();

        PropertyDTO dto = mapper.toDTO(property);
        assertThat(dto.isFeatured()).isTrue();
        assertThat(dto.isVerified()).isTrue();
        assertThat(dto.isPremium()).isTrue();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));
        assertThat(json.path("isFeatured").asBoolean()).isTrue();
        assertThat(json.path("isVerified").asBoolean()).isTrue();
        assertThat(json.path("isPremium").asBoolean()).isTrue();
        assertThat(json.has("featured")).isFalse();
        assertThat(json.has("verified")).isFalse();
        assertThat(json.has("premium")).isFalse();
    }
}
