package com.socgen.riskweb.dao;

public enum AppQueries {
    QRY_ACTIONCODE_TRUNCATE("TRUNCATE TABLE \"MAESNUMIPL\""),
    QRY_PRIMARYROLE_TRUNCATE("TRUNCATE TABLE %s"),
    QRY_SAVE_PRIMARYROLE("INSERT INTO %s (\"entityId\", \"code\", \"subbookingId\") VALUES (?, ?, ?)"),
    QRY_SAVE_ACTIONCODE("INSERT INTO \"WK_MAESTRO_PRIMROLE_DBE\" (\"bdrId\", \"businessEntity\", \"subbookingId\") VALUES (?, ?, ?)");

    private final String value;

    AppQueries(String enumValue) {
        this.value = enumValue;
    }

    public String value() {
        return this.value;
    }

    public String value(String tableName) {
        return String.format(this.value, tableName);
    }
}
