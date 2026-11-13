package com.propertyapp.storage;

import java.util.regex.Pattern;

/** The shape of keys this app writes: {@code images/<uuid>.jpg} and its card size {@code images/<uuid>_card.jpg}. */
public final class StorageKeys {

    public static final Pattern KEY = Pattern.compile("^images/[0-9a-f-]{36}(_card)?\\.(jpg|png)$");

    private StorageKeys() {
    }

    public static boolean isKey(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    /** The card-size key that goes with a full-size key, or null when the value is not one of ours. */
    public static String cardKeyFor(String key) {
        if (!isKey(key) || key.contains("_card.")) return null;
        int dot = key.lastIndexOf('.');
        return key.substring(0, dot) + "_card" + key.substring(dot);
    }
}
