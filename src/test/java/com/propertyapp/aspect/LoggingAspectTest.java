package com.propertyapp.aspect;

import com.propertyapp.dto.auth.LoginWithPasswordRequest;
import com.propertyapp.enums.ClientType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Controller arguments must never reach the log with their contents. */
class LoggingAspectTest {

    @Test
    void secretsAndContactDetailsAreNotLogged_idsAndFlagsAre() {
        LoginWithPasswordRequest login = new LoginWithPasswordRequest();
        login.setIdentifier("person@example.com");
        login.setPassword("Sup3r-Secret!");

        String logged = LoggingAspect.describe(new Object[] {login, "guest@example.com", 42L, true, ClientType.MOBILE, null});

        assertThat(logged)
                .doesNotContain("Sup3r-Secret!")
                .doesNotContain("person@example.com")
                .doesNotContain("guest@example.com")
                .isEqualTo("[LoginWithPasswordRequest, String, 42, true, MOBILE, null]");
    }
}
