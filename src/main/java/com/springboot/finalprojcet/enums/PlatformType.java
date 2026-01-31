package com.springboot.finalprojcet.enums;

public enum PlatformType {
    Tidal("Tidal"),
    YouTube_Music("YouTube Music"),
    Apple_Music("Apple Music");

    private final String value;

    PlatformType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
