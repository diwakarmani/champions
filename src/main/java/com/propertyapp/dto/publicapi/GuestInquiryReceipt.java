package com.propertyapp.dto.publicapi;

import lombok.Value;

/** The same answer for a stored, duplicate or honeypot inquiry, so bots learn nothing (ADR-0007). */
@Value
public class GuestInquiryReceipt {

    boolean received;

    public static GuestInquiryReceipt accepted() {
        return new GuestInquiryReceipt(true);
    }
}
