package com.dataquality.validation;
 
import java.sql.*;
import java.util.*;
 
/**
 * RegionValidator
 *
 * - Supports alpha2 (2-char) and alpha3 (3-char) country codes.
 * - Fetches multiple lookup rows per country from
 *   table: country_region_postal_validation
 * - Validates requiresregion and region existence.
 *
 * Usage:
 *   String reason = RegionValidator.getValidationFailureReason(conn, countryCode, regionCode);
 *   boolean valid = RegionValidator.validateRegion(conn, countryCode, regionCode);
 */
public class RegionValidator {
 
    public static class RegionLookupRow {
        public final String alpha2;
        public final String alpha3;
        public final String regionCode;
        public final boolean requiresRegion;
 
        public RegionLookupRow(String alpha2, String alpha3, String regionCode, boolean requiresRegion) {
            this.alpha2 = alpha2;
            this.alpha3 = alpha3;
            this.regionCode = regionCode;
            this.requiresRegion = requiresRegion;
        }
    }
 
    /**
     * Fetch lookup rows for a country code (alpha2 or alpha3).
     */
    public static List<RegionLookupRow> fetchLookupRows(Connection conn, String countryCode) throws SQLException {
        List<RegionLookupRow> rows = new ArrayList<>();
 
        if (countryCode == null) return rows;
        String key = countryCode.trim().toUpperCase();
 
        if (!(key.length() == 2 || key.length() == 3)) {
            return rows;
        }
 
        String sql;
        if (key.length() == 2) {
            sql = "SELECT alpha2code, alpha3code, ebxregioncode__regioncode, requiresregion " +
                  "FROM country_region_postal_validation WHERE UPPER(alpha2code) = ?";
        } else {
            sql = "SELECT alpha2code, alpha3code, ebxregioncode__regioncode, requiresregion " +
                  "FROM country_region_postal_validation WHERE UPPER(alpha3code) = ?";
        }
 
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a2 = rs.getString("alpha2code");
                    String a3 = rs.getString("alpha3code");
                    String region = rs.getString("ebxregioncode__regioncode");
                    String req = rs.getString("requiresregion");
                    boolean requiresRegion = "1".equals(req) || "Y".equalsIgnoreCase(req) || "T".equalsIgnoreCase(req);
                    rows.add(new RegionLookupRow(a2, a3, region == null ? "" : region.trim().toUpperCase(), requiresRegion));
                }
            }
        }
 
        return rows;
    }
 
    /**
     * Returns a human-friendly failure reason or null if valid.
     */
    public static String getValidationFailureReason(Connection conn, String countryCode, String regionCode) {
        try {
            if (countryCode == null || countryCode.trim().isEmpty()) {
                return "Country code cannot be null or empty";
            }
            String ctry = countryCode.trim().toUpperCase();
            String region = regionCode == null ? "" : regionCode.trim().toUpperCase();
 
            if (ctry.length() != 2 && ctry.length() != 3) {
                return "Invalid country code format '" + ctry + "' (must be 2 or 3 characters)";
            }
 
            List<RegionLookupRow> rows = fetchLookupRows(conn, ctry);
            if (rows.isEmpty()) {
                return "Invalid country code '" + ctry + "' (not found in reference table)";
            }
 
            // Determine requiresRegion: if any row requires region, treat as required.
            boolean requiresRegion = false;
            for (RegionLookupRow r : rows) {
                if (r.requiresRegion) { requiresRegion = true; break; }
            }
 
            // If region is required but not provided
            if (requiresRegion && region.isEmpty()) {
                return "Region is mandatory for country '" + ctry + "'";
            }
 
            // If region provided, it must match at least one lookup row's regionCode (non-empty)
            if (!region.isEmpty()) {
                boolean match = false;
                for (RegionLookupRow r : rows) {
                    String lookupRegion = r.regionCode == null ? "" : r.regionCode.trim().toUpperCase();
                    if (!lookupRegion.isEmpty() && lookupRegion.equalsIgnoreCase(region)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    return "Invalid region '" + region + "' for country '" + ctry + "'";
                }
            }
 
            // All checks passed
            return null;
 
        } catch (SQLException ex) {
            // On DB error, return a readable failure reason (do not leak internals)
            return "Region lookup failure for country '" + countryCode + "'";
        } catch (Exception ex) {
            return "Unexpected region validation error";
        }
    }
 
    public static boolean validateRegion(Connection conn, String countryCode, String regionCode) {
        return getValidationFailureReason(conn, countryCode, regionCode) == null;
    }
}
 
