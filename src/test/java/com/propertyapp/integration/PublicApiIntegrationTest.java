package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public read API: what a visitor may see, and nothing more (ADR-0006). */
class PublicApiIntegrationTest extends AbstractPostgisIntegrationTest {

    /** Adding a field to a public DTO must be a deliberate change to this list. */
    static final Set<String> PROPERTY_FIELDS = Set.of("id", "slug", "path", "title", "description", "listingType",
            "status", "contactable", "price", "depositAmount", "maintenanceCharge", "propertyTypeName",
            "propertySubTypeName", "addressLine1", "addressLine2", "locality", "city", "state", "postalCode",
            "country", "latitude", "longitude", "bedrooms", "bathrooms", "balconies", "floorNumber", "totalFloors",
            "carpetArea", "builtUpArea", "plotArea", "facingDirection", "furnishedStatus", "parkingCovered",
            "parkingOpen", "ageOfProperty", "availableFrom", "possessionStatus", "verified", "featured",
            "primaryImageUrl", "images", "amenities", "agent", "publishedAt", "updatedAt");
    static final Set<String> AGENT_REF_FIELDS = Set.of("id", "name", "slug", "path", "photoUrl", "brokerageName");
    static final Set<String> AGENT_PROFILE_FIELDS = Set.of("id", "name", "slug", "path", "photoUrl", "bio",
            "brokerageName", "licenseNumber", "licenseState", "serviceAreas", "yearsExperience", "ratingAverage",
            "ratingCount", "activeListingCount", "memberSince");
    static final Set<String> CARD_FIELDS = Set.of("id", "slug", "path", "title", "listingType", "status", "price",
            "propertyTypeName", "locality", "city", "state", "bedrooms", "bathrooms", "builtUpArea",
            "primaryImageUrl", "updatedAt");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    Fixtures f;
    User agent;

    @BeforeEach
    void setUp() {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        agent = f.user("REALTOR", "+1512" + (1_000_000 + (int) (Math.random() * 8_999_999)));
        jdbc.update("UPDATE users SET brokerage_name = 'Lone Star Realty', license_number = 'TX-12345', "
                + "license_state = 'TX', bio = 'Austin agent' WHERE id = ?", agent.getId());
    }

    @Test
    void aPublicListingShowsOnlyAllowlistedFieldsAndNeverTheAgentsContactDetails() throws Exception {
        long id = f.listing(agent, "ACTIVE");

        String body = mockMvc.perform(get("/api/public/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactable").value(true))
                .andExpect(jsonPath("$.data.agent.brokerageName").value("Lone Star Realty"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).at("/data");

        assertThat(keys(data)).isEqualTo(new TreeSet<>(PROPERTY_FIELDS));
        assertThat(keys(data.get("agent"))).isEqualTo(new TreeSet<>(AGENT_REF_FIELDS));
        assertThat(body).doesNotContain(agent.getEmail()).doesNotContain(agent.getPhone())
                .doesNotContain("viewCount").doesNotContain("rejectionReason").doesNotContain("ownerEmail");

        String slug = data.get("slug").asText();
        assertThat(slug).matches("[a-z0-9]+(-[a-z0-9]+)*").startsWith("sunny-cafe-home-").endsWith("-texas");
        assertThat(data.get("path").asText()).isEqualTo("/property/" + slug + "-" + id);
        assertThat(data.at("/agent/path").asText()).isEqualTo("/agents/test-realtor-" + agent.getId());
    }

    @Test
    void onlyOpenListingsOfActiveAgentsExist_everythingElseIs404() throws Exception {
        for (String hidden : List.of("DRAFT", "PENDING_APPROVAL", "REJECTED", "INACTIVE")) {
            long id = f.listing(agent, hidden);
            // Never published. A listing taken down after publishing is 410 (see the next test).
            jdbc.update("UPDATE properties SET published_at = NULL WHERE id = ?", id);
            mockMvc.perform(get("/api/public/properties/{id}", id)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/public/properties/{id}", f.listing(agent, "SOLD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactable").value(false));
        mockMvc.perform(get("/api/public/properties/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());

        long active = f.listing(agent, "ACTIVE");
        f.suspend(agent);
        mockMvc.perform(get("/api/public/properties/{id}", active)).andExpect(status().isNotFound());

        // A listing nobody answers for, such as a former seller's, is hidden too (ADR-0005).
        long ownedByBuyer = f.listing(f.user("BUYER"), "ACTIVE");
        mockMvc.perform(get("/api/public/properties/{id}", ownedByBuyer)).andExpect(status().isNotFound());
    }

    @Test
    void theAgentProfileShowsTheLicenseOnlyWhenTheAgentChoseTo() throws Exception {
        String body = mockMvc.perform(get("/api/public/agents/{id}", agent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.licenseNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.licenseState").value(nullValue()))
                .andExpect(jsonPath("$.data.brokerageName").value("Lone Star Realty"))
                .andReturn().getResponse().getContentAsString();
        assertThat(keys(objectMapper.readTree(body).at("/data"))).isEqualTo(new TreeSet<>(AGENT_PROFILE_FIELDS));
        assertThat(body).doesNotContain(agent.getEmail()).doesNotContain(agent.getPhone());

        jdbc.update("UPDATE users SET license_public = true WHERE id = ?", agent.getId());
        mockMvc.perform(get("/api/public/agents/{id}", agent.getId()))
                .andExpect(jsonPath("$.data.licenseNumber").value("TX-12345"))
                .andExpect(jsonPath("$.data.licenseState").value("TX"));
    }

    @Test
    void onlyActiveAgentsHaveAPublicProfileAndOnlyActiveListingsAreListed() throws Exception {
        long active = f.listing(agent, "ACTIVE");
        f.listing(agent, "SOLD");
        f.listing(agent, "DRAFT");

        String body = mockMvc.perform(get("/api/public/agents/{id}/listings", agent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(active))
                .andReturn().getResponse().getContentAsString();
        assertThat(keys(objectMapper.readTree(body).at("/data/content/0"))).isEqualTo(new TreeSet<>(CARD_FIELDS));
        mockMvc.perform(get("/api/public/agents/{id}", agent.getId()))
                .andExpect(jsonPath("$.data.activeListingCount").value(1));

        User buyer = f.user("BUYER");
        mockMvc.perform(get("/api/public/agents/{id}", buyer.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/agents/{id}/listings", buyer.getId())).andExpect(status().isNotFound());

        f.suspend(agent);
        mockMvc.perform(get("/api/public/agents/{id}", agent.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/agents/{id}/listings", agent.getId())).andExpect(status().isNotFound());
    }

    @Test
    void sitemapFeedsOfferOnlyActiveListingsAndActiveAgents() throws Exception {
        long active = f.listing(agent, "ACTIVE");
        long sold = f.listing(agent, "SOLD");
        long draft = f.listing(agent, "DRAFT");
        User suspended = f.user("REALTOR");
        long hidden = f.listing(suspended, "ACTIVE");
        f.suspend(suspended);

        List<Long> listingIds = sitemapIds("/api/public/sitemap/properties");
        assertThat(listingIds).contains(active).doesNotContain(sold, draft, hidden);
        String entry = mockMvc.perform(get("/api/public/sitemap/properties").param("size", "5000"))
                .andReturn().getResponse().getContentAsString();
        assertThat(entry).contains("-" + active + "\"");

        List<Long> agentIds = sitemapIds("/api/public/sitemap/agents");
        assertThat(agentIds).contains(agent.getId()).doesNotContain(suspended.getId());

        mockMvc.perform(get("/api/public/sitemap/properties").param("size", "999999"))
                .andExpect(jsonPath("$.data.pageSize").value(5000));
    }

    @Test
    void favoritesAndCompareOnlyEverShowListingsThePublicMaySee() throws Exception {
        String buyer = f.login(f.user("BUYER").getEmail());
        long a = f.listing(agent, "ACTIVE");
        long b = f.listing(agent, "ACTIVE");
        long draft = f.listing(agent, "DRAFT");

        mockMvc.perform(Fixtures.authed(post("/api/favorites/{id}", draft), buyer)).andExpect(status().isNotFound());
        mockMvc.perform(Fixtures.authed(post("/api/favorites/{id}", Long.MAX_VALUE), buyer))
                .andExpect(status().isNotFound());
        mockMvc.perform(Fixtures.authed(post("/api/favorites/{id}", a), buyer)).andExpect(status().isOk());
        mockMvc.perform(Fixtures.authed(post("/api/favorites/{id}", b), buyer)).andExpect(status().isOk());
        mockMvc.perform(Fixtures.authed(get("/api/favorites/compare").param("ids", a + "," + b), buyer))
                .andExpect(status().isOk());

        jdbc.update("UPDATE properties SET status = 'INACTIVE' WHERE id = ?", b);
        mockMvc.perform(Fixtures.authed(get("/api/favorites"), buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(a));
        mockMvc.perform(Fixtures.authed(get("/api/favorites/compare").param("ids", a + "," + b), buyer))
                .andExpect(status().isNotFound());
        // The ids call (S20) follows the same visibility rule.
        mockMvc.perform(Fixtures.authed(get("/api/favorites/ids"), buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(a));

        // It comes back when the listing does, and a suspended agent's listings drop out the same way.
        jdbc.update("UPDATE properties SET status = 'ACTIVE' WHERE id = ?", b);
        mockMvc.perform(Fixtures.authed(get("/api/favorites"), buyer)).andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(Fixtures.authed(get("/api/favorites/ids"), buyer))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value(b)); // newest first
        f.suspend(agent);
        mockMvc.perform(Fixtures.authed(get("/api/favorites"), buyer)).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(Fixtures.authed(get("/api/favorites/ids"), buyer)).andExpect(jsonPath("$.data.length()").value(0));
        // Saved homes are a buyer feature; an agent token is refused.
        mockMvc.perform(Fixtures.authed(get("/api/favorites/ids"), f.login(agent.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cardsUseTheCoverPhotosCardCopy_detailKeepsTheFullPhoto() throws Exception {
        long withCard = f.listing(agent, "ACTIVE");
        long noCard = f.listing(agent, "ACTIVE");
        String id = java.util.UUID.randomUUID().toString();
        String full = "images/" + id + ".jpg";
        String card = "images/" + id + "_card.jpg";
        jdbc.update("INSERT INTO property_images (property_id, image_url, thumbnail_url, display_order, is_primary, "
                + "created_at, updated_at) VALUES (?, ?, ?, 0, true, now(), now())", withCard, full, card);
        jdbc.update("INSERT INTO property_images (property_id, image_url, thumbnail_url, display_order, is_primary, "
                + "created_at, updated_at) VALUES (?, ?, NULL, 0, true, now(), now())", noCard, full);

        JsonNode cards = objectMapper.readTree(mockMvc.perform(get("/api/public/properties")
                        .param("listingType", "SALE").param("city", f.city))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/content");
        assertThat(imageOf(cards, withCard)).endsWith("/" + card);
        assertThat(imageOf(cards, noCard)).endsWith("/" + full);

        JsonNode feed = objectMapper.readTree(mockMvc.perform(get("/api/discovery/home/view-more")
                        .param("category", "POPULAR").param("city", f.city).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/content");
        assertThat(imageOf(feed, withCard)).endsWith("/" + card);
        assertThat(imageOf(feed, noCard)).endsWith("/" + full);

        mockMvc.perform(get("/api/public/properties/{id}", withCard))
                .andExpect(jsonPath("$.data.primaryImageUrl").value(org.hamcrest.Matchers.endsWith("/" + full)));
    }

    private static String imageOf(JsonNode list, long id) {
        for (JsonNode n : list) {
            if (n.path("id").asLong() == id) return n.path("primaryImageUrl").asText();
        }
        throw new AssertionError("listing " + id + " not in " + list);
    }

    @Test
    void searchReturnsOnlyPublicListingsOfOneCityAndType_sortedByAllowlistOnly() throws Exception {
        long cheap = f.listing(agent, "ACTIVE");
        long dear = f.listing(agent, "ACTIVE");
        jdbc.update("UPDATE properties SET price = 900000, published_at = now() WHERE id = ?", dear);
        jdbc.update("UPDATE properties SET price = 300000, published_at = now() - interval '1 day' WHERE id = ?", cheap);
        long rent = f.listing(agent, "ACTIVE");
        jdbc.update("UPDATE properties SET listing_type = 'RENT' WHERE id = ?", rent);
        f.listing(agent, "SOLD");
        f.listing(agent, "DRAFT");
        User suspended = f.user("REALTOR");
        f.listing(suspended, "ACTIVE");
        f.suspend(suspended);

        // City and state match without case; the web passes the names it got from /markets.
        String body = mockMvc.perform(get("/api/public/properties").param("listingType", "SALE")
                        .param("state", "TEXAS").param("city", f.city.toLowerCase()).param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(cheap))
                .andExpect(jsonPath("$.data.content[1].id").value(dear))
                .andReturn().getResponse().getContentAsString();
        assertThat(keys(objectMapper.readTree(body).at("/data/content/0"))).isEqualTo(new TreeSet<>(CARD_FIELDS));
        assertThat(body).doesNotContain(agent.getEmail()).doesNotContain(agent.getPhone());

        mockMvc.perform(get("/api/public/properties").param("listingType", "SALE").param("city", f.city))
                .andExpect(jsonPath("$.data.content[0].id").value(dear));
        mockMvc.perform(get("/api/public/properties").param("listingType", "RENT").param("city", f.city))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(rent));

        mockMvc.perform(get("/api/public/properties").param("sort", "owner.email")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties").param("listingType", "LEASE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties").param("size", "999"))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    @Test
    void marketsCountOnlyPublicListingsAndCarryComputedSlugs() throws Exception {
        f.listing(agent, "ACTIVE");
        long second = f.listing(agent, "ACTIVE");
        f.listing(agent, "DRAFT");
        User suspended = f.user("REALTOR");
        f.listing(suspended, "ACTIVE");
        f.suspend(suspended);
        jdbc.update("UPDATE properties SET updated_at = timestamp '2026-10-01 12:00:00' WHERE id = ?", second);

        String body = mockMvc.perform(get("/api/public/markets"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> mine = new ArrayList<>();
        objectMapper.readTree(body).at("/data").forEach(row -> {
            if (f.city.equals(row.get("city").asText())) mine.add(row);
        });
        assertThat(mine).hasSize(1);
        JsonNode market = mine.get(0);
        assertThat(keys(market)).containsExactlyInAnyOrder("state", "stateSlug", "city", "citySlug", "listingType",
                "listings", "lastModified");
        assertThat(market.get("listings").asLong()).isEqualTo(2);
        assertThat(market.get("listingType").asText()).isEqualTo("SALE");
        assertThat(market.get("stateSlug").asText()).isEqualTo("texas");
        assertThat(market.get("citySlug").asText()).isEqualTo(f.city.toLowerCase());
    }

    @Test
    void aListingTakenDownAfterPublishingIs410WithOnlyWhatSimilarHomesNeed() throws Exception {
        long delisted = f.listing(agent, "INACTIVE");
        long deleted = f.listing(agent, "ACTIVE");
        long neverPublished = f.listing(agent, "INACTIVE");
        jdbc.update("UPDATE properties SET published_at = NULL WHERE id = ?", neverPublished);
        jdbc.update("UPDATE properties SET published_at = now() - interval '30 days' WHERE id IN (?, ?)", delisted, deleted);
        jdbc.update("UPDATE properties SET deleted_at = now() WHERE id = ?", deleted);

        for (long gone : List.of(delisted, deleted)) {
            String body = mockMvc.perform(get("/api/public/properties/{id}", gone))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.id").value(gone))
                    .andExpect(jsonPath("$.data.city").value(f.city))
                    .andExpect(jsonPath("$.data.state").value("Texas"))
                    .andExpect(jsonPath("$.data.listingType").value("SALE"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(keys(objectMapper.readTree(body).at("/data")))
                    .containsExactlyInAnyOrder("id", "listingType", "city", "state");
            assertThat(body).doesNotContain(agent.getEmail()).doesNotContain("Sunny");
        }
        // Never public stays 404, and so does a published listing whose agent is suspended (may return).
        mockMvc.perform(get("/api/public/properties/{id}", neverPublished)).andExpect(status().isNotFound());
        long active = f.listing(agent, "ACTIVE");
        jdbc.update("UPDATE properties SET published_at = now() WHERE id = ?", active);
        f.suspend(agent);
        mockMvc.perform(get("/api/public/properties/{id}", active)).andExpect(status().isNotFound());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/public/sitemap/agents"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"));
    }

    @Test
    void corsAllowsOnlyListedOrigins() throws Exception {
        mockMvc.perform(options("/api/public/properties/1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mockMvc.perform(options("/api/public/properties/1")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(options("/api/public/properties/1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Evil-Header"))
                .andExpect(status().isForbidden());
    }

    private List<Long> sitemapIds(String url) throws Exception {
        String body = mockMvc.perform(get(url).param("size", "5000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(body).at("/data/content").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
