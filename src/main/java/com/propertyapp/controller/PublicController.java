package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.publicapi.GuestContactRequest;
import com.propertyapp.dto.publicapi.GuestInquiryReceipt;
import com.propertyapp.dto.publicapi.GuestInquiryRequest;
import com.propertyapp.dto.publicapi.PublicAgentProfileDTO;
import com.propertyapp.dto.publicapi.PublicContactDTO;
import com.propertyapp.dto.publicapi.PublicListingCardDTO;
import com.propertyapp.dto.publicapi.PublicMarketDTO;
import com.propertyapp.dto.publicapi.PublicPropertyDTO;
import com.propertyapp.dto.publicapi.SitemapEntryDTO;
import com.propertyapp.security.ratelimit.ClientIpResolver;
import com.propertyapp.service.contact.GuestContactService;
import com.propertyapp.service.publicapi.PublicListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything a visitor without an account may do (ADR-0006, ADR-0007). Read-only except contact.
 * Hidden listings and agents answer 404.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public", description = "No sign-in needed: listings, agents, sitemap feeds and guest contact")
public class PublicController {

    private final PublicListingService listingService;
    private final GuestContactService contactService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/properties")
    @Operation(summary = "Search ACTIVE listings by listing type, state, city, price range and minimum bedrooms; sort is newest, price_asc or price_desc")
    public ResponseEntity<ApiResponse<PageResponse<PublicListingCardDTO>>> search(
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Integer minBeds,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                listingService.search(listingType, state, city, minPrice, maxPrice, minBeds, sort, page, size)));
    }

    @GetMapping("/markets")
    @Operation(summary = "Cities with public listings, per listing type, with counts and last-modified times")
    public ResponseEntity<ApiResponse<List<PublicMarketDTO>>> markets() {
        return ResponseEntity.ok(ApiResponse.success(listingService.markets()));
    }

    @GetMapping("/properties/{id}")
    @Operation(summary = "A listing anyone may see; 410 with city and type if it was public and is now taken down; 404 if hidden")
    public ResponseEntity<ApiResponse<PublicPropertyDTO>> property(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(listingService.getProperty(id)));
    }

    @GetMapping("/agents/{id}")
    @Operation(summary = "An active agent's public profile; 404 if not an active agent")
    public ResponseEntity<ApiResponse<PublicAgentProfileDTO>> agent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(listingService.getAgent(id)));
    }

    @GetMapping("/agents/{id}/listings")
    @Operation(summary = "An active agent's ACTIVE listings, newest first")
    public ResponseEntity<ApiResponse<PageResponse<PublicListingCardDTO>>> agentListings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success(listingService.getAgentListings(id, page, size)));
    }

    @GetMapping("/sitemap/properties")
    @Operation(summary = "Sitemap feed: ACTIVE listings with paths and last-modified times")
    public ResponseEntity<ApiResponse<PageResponse<SitemapEntryDTO>>> sitemapProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok(ApiResponse.success(listingService.sitemapProperties(page, size)));
    }

    @GetMapping("/sitemap/agents")
    @Operation(summary = "Sitemap feed: active agents with paths and last-modified times")
    public ResponseEntity<ApiResponse<PageResponse<SitemapEntryDTO>>> sitemapAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok(ApiResponse.success(listingService.sitemapAgents(page, size)));
    }

    @PostMapping("/properties/{id}/inquiries")
    @Operation(summary = "Guest inquiry. Always 202 when accepted, including duplicates; 429 when over the limit")
    public ResponseEntity<ApiResponse<GuestInquiryReceipt>> inquire(
            @PathVariable Long id,
            @Valid @RequestBody GuestInquiryRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Enquiry received",
                contactService.inquire(id, request, clientIpResolver.resolve(http))));
    }

    @PostMapping("/properties/{id}/contact")
    @Operation(summary = "Reveal the agent's phone and email for an ACTIVE listing; recorded and rate-limited")
    public ResponseEntity<ApiResponse<PublicContactDTO>> contact(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) GuestContactRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                contactService.revealContact(id, request, clientIpResolver.resolve(http))));
    }
}
