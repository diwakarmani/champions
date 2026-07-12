package com.propertyapp.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertySubType;
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

    // Bug 12: PropertyType/PropertySubType/PropertyAmenity all declare their entity field as
    // `isActive` (Lombok's boolean-getter convention resolves the JavaBean property name to
    // "active"), so the mapper needs an explicit source="active" mapping — otherwise MapStruct's
    // implicit name-matching silently fails and every LIST endpoint returns isActive:null
    // regardless of the true persisted value.
    @Test
    void mapsActiveTypeCorrectlyToIsActiveTrue() {
        PropertyType type = PropertyType.builder().name("Villa").isActive(true).build();
        assertThat(mapper.toTypeDTO(type).getIsActive()).isTrue();
    }

    @Test
    void mapsInactiveTypeCorrectlyToIsActiveFalse() {
        PropertyType type = PropertyType.builder().name("Villa").isActive(false).build();
        assertThat(mapper.toTypeDTO(type).getIsActive()).isFalse();
    }

    @Test
    void mapsActiveAndInactiveSubTypeCorrectly() {
        PropertyType parent = PropertyType.builder().name("Apartment").isActive(true).build();
        PropertySubType active = PropertySubType.builder().propertyType(parent).name("1 BHK").isActive(true).build();
        PropertySubType inactive = PropertySubType.builder().propertyType(parent).name("2 BHK").isActive(false).build();

        assertThat(mapper.toSubTypeDTO(active).getIsActive()).isTrue();
        assertThat(mapper.toSubTypeDTO(inactive).getIsActive()).isFalse();
    }

    @Test
    void mapsActiveAndInactiveAmenityCorrectly() {
        PropertyAmenity active = PropertyAmenity.builder().name("Pool").isActive(true).build();
        PropertyAmenity inactive = PropertyAmenity.builder().name("Gym").isActive(false).build();

        assertThat(mapper.toAmenityDTO(active).getIsActive()).isTrue();
        assertThat(mapper.toAmenityDTO(inactive).getIsActive()).isFalse();
    }
}
