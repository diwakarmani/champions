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

    /**
     * Bug 34 — localityId was @NotNull which broke CreateListingScreen (no locality picker).
     * It is now optional: omitting it succeeds bean validation; the service falls back to the
     * free-text city/state/country fields supplied in the request body.
     */
    @Test
    void propertyCreateAllowsNullLocalityIdBeanValidationPasses() {
        PropertyCreateRequest withoutLocality = PropertyCreateRequest.builder()
                .title("Title")
                .description("Description")
                .propertyTypeId(1L)
                .listingType("SALE")
                .price(java.math.BigDecimal.ONE)
                .addressLine1("Address")
                .city("City")
                .state("State")
                .country("Country")
                // localityId intentionally omitted
                .build();

        // Bean validation must not produce a localityId violation
        assertThat(validator.validate(withoutLocality))
                .noneMatch(v -> v.getPropertyPath().toString().equals("localityId"));
    }

    @Test
    void propertyCreateStillRequiresCoreFieldsWhenLocalityIsAbsent() {
        PropertyCreateRequest empty = PropertyCreateRequest.builder().build();
        var violations = validator.validate(empty);

        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("title"));
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("propertyTypeId"));
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("price"));
        // localityId must NOT appear in violations (it is now optional)
        assertThat(violations).noneMatch(v ->
                v.getPropertyPath().toString().equals("localityId"));
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
