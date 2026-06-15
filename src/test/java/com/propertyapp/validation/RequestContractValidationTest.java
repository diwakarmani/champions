package com.propertyapp.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.propertyapp.dto.property.PropertyCreateRequest;
import com.propertyapp.dto.user.UserUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContractValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void propertyCreateRequiresLocalityBeforeRepositoryAccess() {
        PropertyCreateRequest request = PropertyCreateRequest.builder()
                .title("Title")
                .description("Description")
                .propertyTypeId(1L)
                .listingType("SALE")
                .price(java.math.BigDecimal.ONE)
                .addressLine1("Address")
                .city("City")
                .state("State")
                .country("Country")
                .build();

        assertThat(validator.validate(request))
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("localityId");
                    assertThat(v.getMessage()).isEqualTo("Locality is required");
                });
    }

    @Test
    void userUpdateDeserializesIsoDateButRejectsInvalidDateShape() throws Exception {
        UserUpdateRequest request = objectMapper.readValue(
                "{\"dateOfBirth\":\"2002-12-19\"}", UserUpdateRequest.class);
        assertThat(request.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(2002, 12, 19));

        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"dateOfBirth\":\"not-a-date\"}", UserUpdateRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }
}
