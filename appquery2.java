// AppQueries.java - Separate enum file
package com.socgen.riskweb.enums; // or whatever package you use for enums

public enum AppQueries {
    // Existing queries
    QRY_ACTIONCODE_TRUNCATE(new StringBuffer("TRUNCATE TABLE \"MAESNUMIPL\"").toString()),
    QRY_PRIMARYROLE_TRUNCATE(new StringBuffer("TRUNCATE TABLE \"TMAESNUMIPL\"").toString()),
    QRY_SAVE_PRIMARYROLE(new StringBuffer("INSERT INTO \"TMAESNUMIPL\" (\"entityId\",\"code\",\"subbookingId\") VALUES (?,?,?)").toString()),
    QRY_SAVE_ACTIONCODE(new StringBuffer("INSERT INTO \"WK_MAESTRO_PRIMROLE_DBE\" (\"bdrId\",\"businessEntity\") VALUES (?,?)").toString()),
    
    // New queries for external registration
    QRY_EXTERNAL_TRUNCATE(new StringBuffer("TRUNCATE TABLE \"TMAESNUMIPL_EXTERNAL\"").toString()),
    QRY_SAVE_EXTERNAL(new StringBuffer("INSERT INTO \"TMAESNUMIPL_EXTERNAL\" (\"entityId\",\"code\",\"subbookingId\") VALUES (?,?,?)").toString());

    private final String value;

    AppQueries(String enumValue) {
        this.value = enumValue;
    }

    public String value() {
        return this.value;
    }
}
