package com.propertyapp.service.rating;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.rating.CreateRatingRequest;
import com.propertyapp.dto.rating.RatingDTO;

import java.util.Optional;

public interface RealtorRatingService {

    /** Submit or update a rating. Throws if caller is not eligible (no CONTACTED inquiry). */
    RatingDTO submitRating(Long realtorId, Long raterId, CreateRatingRequest request);

    /** Returns the caller's existing rating for this property, or empty if eligible but not yet rated. Throws BusinessException (422) if not eligible. */
    Optional<RatingDTO> getMyRating(Long realtorId, Long raterId, Long propertyId);

    /** Public paginated listing of all ratings for a realtor. */
    PageResponse<RatingDTO> getRatings(Long realtorId, int page, int size);
}
