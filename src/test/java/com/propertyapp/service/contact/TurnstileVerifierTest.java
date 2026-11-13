package com.propertyapp.service.contact;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileVerifierTest {

    @Test
    void offMeansEveryRequestPassesWithoutACall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(false, "", builder.build());
        assertThat(verifier.verify(null, "1.2.3.4")).isTrue();
        server.verify();
    }

    @Test
    void onItAsksCloudflareAndBelievesOnlyAClearYes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(true, "s3cret", builder.build());

        server.expect(requestTo(TurnstileVerifier.VERIFY_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("secret", "s3cret", "response", "tok")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(TurnstileVerifier.VERIFY_URL))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(TurnstileVerifier.VERIFY_URL)).andRespond(withServerError());

        assertThat(verifier.verify("tok", "1.2.3.4")).isTrue();
        assertThat(verifier.verify("bad", "1.2.3.4")).isFalse();
        assertThat(verifier.verify("tok", "1.2.3.4")).isFalse();
        server.verify();

        assertThat(verifier.verify("", "1.2.3.4")).isFalse(); // no call made for a missing token
        assertThat(new TurnstileVerifier(true, "", builder.build()).verify("tok", "1.2.3.4")).isFalse();
    }
}
