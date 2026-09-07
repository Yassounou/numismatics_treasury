package dev.yassou.numismaticstreasury.server.auction;

public enum ListingType {
    FIXED,
    AUCTION;

    public static ListingType parse(String value) {
        try {
            return valueOf(value == null ? "" : value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FIXED;
        }
    }
}
