package com.propertyapp.service.property;

import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;

import java.util.Set;

/**
 * Who may see a listing, and whether it can still be contacted (ADR-0006).
 * Hidden listings answer 404 rather than 403 so their existence is not revealed.
 */
public final class PropertyVisibility {

    private static final Set<String> PUBLIC_STATUSES = Set.of("ACTIVE", "SOLD", "RENTED");

    private PropertyVisibility() {
    }

    public static boolean isPublic(String status) {
        return status != null && PUBLIC_STATUSES.contains(status);
    }

    /**
     * May this user represent listings? Mirrors the SQL view active_listing_agents and the
     * public-query filters in PropertyRepository and PropertySpecification (ADR-0005).
     */
    public static boolean isActiveAgent(User user) {
        return user != null
                && user.isActive()
                && !user.isDeleted()
                && !user.isAgentSuspended()
                && user.getRoles().stream().anyMatch(role -> "REALTOR".equals(role.getName()));
    }

    /** Publicly visible: an open status, and an active agent answerable for it. */
    public static boolean isPubliclyVisible(Property property) {
        return isPublic(property.getStatus()) && isActiveAgent(property.getOwner());
    }

    public static boolean canView(Property property, Long viewerId, boolean viewerIsAdmin) {
        return isPubliclyVisible(property)
                || viewerIsAdmin
                || (viewerId != null && viewerId.equals(property.getOwner().getId()));
    }

    /** Only ACTIVE listings accept new contact; closed ones explain why, hidden ones do not exist. */
    public static void requireContactable(Property property) {
        if (!isPubliclyVisible(property)) {
            throw new ResourceNotFoundException("Property", "id", property.getId());
        }
        if (!"ACTIVE".equals(property.getStatus())) {
            throw new BadRequestException("This listing is closed and no longer accepting enquiries");
        }
    }
}
