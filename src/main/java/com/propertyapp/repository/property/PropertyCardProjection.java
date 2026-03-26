package com.propertyapp.repository.property;

import java.math.BigDecimal;

public interface PropertyCardProjection {

    Long getId();
    String getTitle();
    String getListingType();
    BigDecimal getPrice();
    String getCity();
    String getLocality();
    Integer getBedrooms();
    String getFurnishedStatus();
    String getPrimaryImageUrl();
    Boolean getVerified();
    Boolean getPremium();
    Double getDistanceInKm();
}