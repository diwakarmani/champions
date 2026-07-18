package com.propertyapp.service.locality;

import com.propertyapp.entity.locality.Country;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.repository.locality.CityRepository;
import com.propertyapp.repository.locality.CountryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.locality.StateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the location-bootstrap "Import Country" bug: an exact ["name"="%s"] Overpass match
 * failed for any input not identical to OSM's primary "name" tag (e.g. the admin screen's own
 * placeholder suggests "USA", but OSM tags the US relation's "name" as "United States"), and a
 * missing null-check on the Overpass response threw a raw NPE instead of a clean 400.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    @Mock CountryRepository countryRepo;
    @Mock StateRepository stateRepo;
    @Mock CityRepository cityRepo;
    @Mock LocalityRepository localityRepo;
    @Mock RestClient restClient;

    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private LocationServiceImpl service;

    @BeforeEach
    void setUp() {
        // RestClient's fluent builder returns a chain of narrow interfaces
        // (RequestBodyUriSpec -> RequestBodySpec -> ResponseSpec). RETURNS_DEEP_STUBS does not
        // reliably traverse this chain past the varargs `header(String, String...)` call, so
        // each level is mocked explicitly instead.
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        // body(Object) is overloaded with body(StreamingHttpOutputMessage.Body); an untyped
        // any() lets javac silently resolve to the wrong overload, so pin the type explicitly.
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        // Constructor order mirrors LocationServiceImpl's field declarations (Lombok
        // @RequiredArgsConstructor); the last two params duplicate cityRepo/stateRepo by type
        // (pre-existing in production code, not something introduced by this test).
        service = new LocationServiceImpl(countryRepo, stateRepo, cityRepo, localityRepo, restClient, cityRepo, stateRepo);
    }

    private void stubOverpassResponse(Map<String, Object> response) {
        when(responseSpec.body(Map.class)).thenReturn(response);
    }

    @Test
    void importCountryMatchesOnAlternateNameTagNotJustThePrimaryNameTag() {
        Map<String, Object> element = Map.of(
                "id", 148838,
                "center", Map.of("lat", "39.83", "lon", "-98.58")
        );
        stubOverpassResponse(Map.of("elements", List.of(element)));
        when(countryRepo.existsByOsmId(148838L)).thenReturn(false);
        when(countryRepo.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        // "USA" would fail an exact ["name"="USA"] match against OSM's "United States" tag —
        // the fix unions name/name:en/int_name so this now resolves via one of the alternates.
        service.importCountry("USA");

        verify(countryRepo).save(argThat(c -> c != null && Long.valueOf(148838L).equals(c.getOsmId())));
    }

    @Test
    void importCountryEscapesDoubleQuotesInNameBeforeBuildingTheQuery() {
        Map<String, Object> element = Map.of(
                "id", 999,
                "center", Map.of("lat", "1.0", "lon", "2.0")
        );
        stubOverpassResponse(Map.of("elements", List.of(element)));
        when(countryRepo.existsByOsmId(999L)).thenReturn(false);
        when(countryRepo.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        // An unescaped `"` in the name would break out of the ["name"="..."] string literal and
        // produce malformed Overpass QL. Escaping it must keep the query well-formed.
        service.importCountry("Foo\"Bar");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        String sentQuery = URLDecoder.decode(((String) bodyCaptor.getValue()).substring("data=".length()), StandardCharsets.UTF_8);

        assertThat(sentQuery).contains("[\"name\"=\"Foo\\\"Bar\"]");
        assertThat(sentQuery).doesNotContain("[\"name\"=\"Foo\"Bar\"]");
    }

    @Test
    void importCountryThrowsACleanBadRequestWhenProviderReturnsNoBody() {
        stubOverpassResponse(null);

        assertThatThrownBy(() -> service.importCountry("Nowhereland"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Country not found");
    }

    @Test
    void importCountryThrowsACleanBadRequestWhenProviderResponseHasNoElementsKey() {
        stubOverpassResponse(Map.of("unexpected", "shape"));

        assertThatThrownBy(() -> service.importCountry("Nowhereland"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void importStatesThrowsACleanBadRequestWhenProviderReturnsNoElementsKey() {
        Country country = Country.builder().name("United States").osmId(148838L).build();
        when(countryRepo.findById(1L)).thenReturn(Optional.of(country));
        stubOverpassResponse(Map.of("unexpected", "shape"));

        assertThatThrownBy(() -> service.importStates(1L))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void importStatesThrowsACleanBadRequestWhenProviderReturnsNullBody() {
        Country country = Country.builder().name("United States").osmId(148838L).build();
        when(countryRepo.findById(1L)).thenReturn(Optional.of(country));
        stubOverpassResponse(null);

        assertThatThrownBy(() -> service.importStates(1L))
            .isInstanceOf(BadRequestException.class);
    }
}
