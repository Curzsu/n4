package com.n4monitor.annotation;

public enum BusinessType {
    OTHER(0),
    CREATE(1),
    UPDATE(2),
    DELETE(3),
    GRANT(4),
    EXPORT(5),
    IMPORT(6);

    private final int code;

    BusinessType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
