package com.propertyapp.dto.publicapi;

import com.propertyapp.entity.user.User;

final class PublicNames {

    private PublicNames() {
    }

    static String of(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }
}
