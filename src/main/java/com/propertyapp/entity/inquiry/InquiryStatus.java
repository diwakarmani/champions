package com.propertyapp.entity.inquiry;

public enum InquiryStatus {
    NEW, CONTACTED, CLOSED;

    /** Returns true only for the single valid forward transition. CLOSED is terminal. */
    public boolean canTransitionTo(InquiryStatus next) {
        return switch (this) {
            case NEW       -> next == CONTACTED;
            case CONTACTED -> next == CLOSED;
            case CLOSED    -> false;
        };
    }
}
