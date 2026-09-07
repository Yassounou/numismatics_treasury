package dev.yassou.numismaticstreasury.block.entity;

public enum ShopMode {
    SELL_TO_PLAYER,
    BUY_FROM_PLAYER;

    public static ShopMode parse(String value) {
        try {
            return valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            return SELL_TO_PLAYER;
        }
    }
}
