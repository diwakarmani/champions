package com.propertyapp.exception;

import com.propertyapp.dto.publicapi.GoneListingDTO;
import lombok.Getter;

/** A listing that was once public and is now delisted or deleted: the public API answers 410. */
@Getter
public class ListingGoneException extends RuntimeException {

    private final transient GoneListingDTO listing;

    public ListingGoneException(GoneListingDTO listing) {
        super("This listing is no longer available");
        this.listing = listing;
    }
}
