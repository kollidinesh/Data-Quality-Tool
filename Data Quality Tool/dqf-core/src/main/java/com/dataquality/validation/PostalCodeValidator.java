package com.dataquality.validation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

/**
 * FINAL – CORRECTED PostalCodeValidator WITH COUNTRY NAME NORMALIZATION
 */
public class PostalCodeValidator {

    // -------------------- STRICT COUNTRY POSTAL REGEX MAP --------------------
    private static final Map<String, String> POSTAL_PATTERNS = new HashMap<>();

    // -------------------- COUNTRY NAME + ISO NORMALIZATION MAP --------------------
    private static final Map<String, String> COUNTRY_NORMALIZATION = new HashMap<>();

    static {

        // ---------------- NORMALIZATION: ISO + aliases + full names ----------------

        add("UNITED STATES", "US"); add("USA", "US"); add("US", "US");
        add("U.S.", "US");

        add("UNITED KINGDOM", "UNITED KINGDOM");
        add("UK", "UNITED KINGDOM");
        add("U.K.", "UNITED KINGDOM");
        add("GB", "UNITED KINGDOM");
        add("GBR", "UNITED KINGDOM");
        add("GREAT BRITAIN", "UNITED KINGDOM");
        add("BRITAIN", "UNITED KINGDOM");

        add("NETHERLANDS", "NETHERLANDS");
        add("HOLLAND", "NETHERLANDS");
        add("NL", "NETHERLANDS");
        add("NLD", "NETHERLANDS");

        add("INDIA", "INDIA");
        add("IN", "INDIA");
        add("IND", "INDIA");

        add("SWEDEN", "SWEDEN");
        add("SE", "SWEDEN");
        add("SWE", "SWEDEN");

        add("POLAND", "POLAND");
        add("PL", "POLAND");
        add("POL", "POLAND");

        add("CANADA", "CANADA");
        add("CA", "CANADA");
        add("CAN", "CANADA");

        add("RUSSIA", "RUSSIAN FEDERATION");
        add("RU", "RUSSIAN FEDERATION");
        add("RUS", "RUSSIAN FEDERATION");

        add("SOUTH KOREA", "KOREA, REPUBLIC OF");
        add("KOREA, REPUBLIC OF", "KOREA, REPUBLIC OF");
        add("KR", "KOREA, REPUBLIC OF");
        add("KOR", "KOREA, REPUBLIC OF");

        add("NORTH KOREA", "KOREA, DEMOCRATIC PEOPLE'S REPUBLIC OF");
        add("KP", "KOREA, DEMOCRATIC PEOPLE'S REPUBLIC OF");
        add("PRK", "KOREA, DEMOCRATIC PEOPLE'S REPUBLIC OF");

        add("JAPAN", "JAPAN");
        add("JP", "JAPAN");
        add("JPN", "JAPAN");

        add("CHINA", "CHINA");
        add("CN", "CHINA");
        add("CHN", "CHINA");

        // ------------------------- Your original POSTAL_PATTERNS -------------------------
        // 3-digit countries
        for (String c : Arrays.asList("FAROE ISLANDS", "ICELAND", "LESOTHO"))
            POSTAL_PATTERNS.put(c, "^[0-9]{3}$");

        // Vietnam, Taiwan
        POSTAL_PATTERNS.put("VIETNAM", "^[0-9]{5,6}$");
        POSTAL_PATTERNS.put("TAIWAN", "^[0-9]{3,6}$");

        // 4-digit countries
        for (String c : Arrays.asList(
                "AUSTRALIA","AUSTRIA","DENMARK","HUNGARY","LIECHTENSTEIN",
                "LUXEMBOURG","NEW ZEALAND","NORWAY","PHILIPPINES","SOUTH AFRICA",
                "SWITZERLAND","TUNISIA","VENEZUELA"))
            POSTAL_PATTERNS.put(c, "^[0-9]{4}$");

        // 5-digit countries
        for (String c : Arrays.asList(
                "ALGERIA","FINLAND","FRANCE","GERMANY","GREECE","INDONESIA","IRAN","ITALY",
                "KUWAIT","LITHUANIA","MALAYSIA","MEXICO","MONACO","MONTENEGRO","SAUDI ARABIA",
                "SERBIA, THE REPUBLIC OF","SPAIN","THAILAND","TURKEY","YUGOSLAVIA"))
            POSTAL_PATTERNS.put(c, "^[0-9]{5}$");

        // 6-digit countries
        for (String c : Arrays.asList("CHINA","INDIA","KAZAKHSTAN","RUSSIAN FEDERATION","SINGAPORE"))
            POSTAL_PATTERNS.put(c, "^[0-9]{6}$");

        // 7-digit country
        POSTAL_PATTERNS.put("ISRAEL", "^[0-9]{7}$");

        // Mixed formats (your original)
        POSTAL_PATTERNS.put("BRAZIL", "^[0-9A-Za-z\\-\\s]{0,9}$");
        POSTAL_PATTERNS.put("BELGIUM", "^[0-9]{0,4}$");
        POSTAL_PATTERNS.put("CYPRUS", "^[0-9]{0,4}$");
        POSTAL_PATTERNS.put("COSTA RICA", "^[0-9\\-\\s]{0,5}$");
        POSTAL_PATTERNS.put("CROATIA", "^[0-9]{0,5}$");
        POSTAL_PATTERNS.put("SLOVENIA", "^[0-9]{0,5}$");
        POSTAL_PATTERNS.put("SLOVAKIA", "^[0-9A-Za-z\\-\\s]{0,8}$");
        POSTAL_PATTERNS.put("CZECH REPUBLIC", "^[0-9]{0,6}$");
        POSTAL_PATTERNS.put("NEPAL", "^[0-9]{0,6}$");
        POSTAL_PATTERNS.put("UKRAINE", "^[0-9]{0,6}$");
        POSTAL_PATTERNS.put("CANADA", "^[0-9A-Za-z\\-\\s]{0,7}$");
        POSTAL_PATTERNS.put("KOREA, DEMOCRATIC PEOPLE'S REPUBLIC OF", "^[0-9A-Za-z\\-\\s]{0,7}$");
        POSTAL_PATTERNS.put("ROMANIA", "^[0-9]{0,7}$");
        POSTAL_PATTERNS.put("KOREA, REPUBLIC OF", "^[0-9A-Za-z\\-\\s]{0,7}$");
        POSTAL_PATTERNS.put("CHILE", "^[0-9A-Za-z\\-\\s]{0,8}$");
        POSTAL_PATTERNS.put("PORTUGAL", "^[0-9A-Za-z\\-\\s]{0,8}$");

        POSTAL_PATTERNS.put("JAPAN", "^[0-9\\-\\s]{0,8}$");
        POSTAL_PATTERNS.put("UNITED KINGDOM", "^[0-9A-Za-z\\-\\s]{0,9}$"); 
        POSTAL_PATTERNS.put("SWEDEN", "^[0-9]{3}[- ]?[0-9]{2}$");
        POSTAL_PATTERNS.put("NETHERLANDS", "^[0-9]{4}[- ]?[A-Za-z]{2}$");
        POSTAL_PATTERNS.put("POLAND", "^[0-9]{2}-[0-9]{3}$");

        POSTAL_PATTERNS.put("IN", "^[1-9][0-9]{5}$");
        POSTAL_PATTERNS.put("US", "^[0-9]{5}(-[0-9]{4})?$");
        POSTAL_PATTERNS.put("NL", "^[0-9]{4}[- ]?[A-Za-z]{2}$");
        POSTAL_PATTERNS.put("SE", "^[0-9]{3}[- ]?[0-9]{2}$");
        POSTAL_PATTERNS.put("PL", "^[0-9]{2}-[0-9]{3}$");

    }

    // Helper for normalization
    private static void add(String key, String val) {
        COUNTRY_NORMALIZATION.put(key.toUpperCase(), val.toUpperCase());
    }

    // ---------------------- PUBLIC VALIDATION METHOD ----------------------
    public static String getValidationFailureReason(Connection conn,
                                                    String countryCode,
                                                    String regionCode,
                                                    String postalCode) {

        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "Country code cannot be empty";
        }

        // *************** NORMALIZATION ADDED HERE ***************
        String country = normalizeCountry(countryCode);
        // ********************************************************

        String region = regionCode == null ? "" : regionCode.trim().toUpperCase();
        String postal = postalCode == null ? "" : postalCode.trim();

        // CASE A: empty postal → DB lookup
        if (postal.isEmpty()) {
            Boolean mandatory = fetchPostalMandatoryFlag(conn, country, region);

            if (mandatory == null) return null;
            if (mandatory)
                return "Postal code is mandatory for " + country +
                        (region.isEmpty() ? "" : " - region " + region);

            return null;
        }

        // CASE B: strict regex
        String regex = POSTAL_PATTERNS.get(country);

        if (regex != null) {
            if (!Pattern.matches(regex, postal)) {
                return "Invalid postal code for " + country +
                        ". Expected format like: " + example(regex);
            }
            return null;
        }

        // CASE C: fallback
        if (!postal.matches("\\d+")) {
            return "Postal code must be numeric for " + country;
        }
        if (postal.length() > 10) {
            return "Postal code too long for " + country + " (max 10 digits)";
        }

        return null;
    }

    // COUNTRY NORMALIZATION
    private static String normalizeCountry(String input) {
        String key = input.trim().toUpperCase();
        return COUNTRY_NORMALIZATION.getOrDefault(key, key);
    }

    // ------------------ DB LOOKUP FOR EMPTY POSTAL CODES ------------------
    private static Boolean fetchPostalMandatoryFlag(Connection conn, String country, String region) {
        String sql =
                "SELECT requirespostalcode FROM public.country_region_postal_validation " +
                "WHERE UPPER(alpha2code)=? AND UPPER(ebxregioncode__regioncode)=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, country);
            ps.setString(2, region);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String flag = rs.getString("requirespostalcode");
                return "1".equals(flag);
            }
        } catch (Exception ignored) {}

        return null;
    }

    // -------------------------- EXAMPLE BUILDER --------------------------
    private static String example(String regex) {

        if (regex.equals("^[0-9]{6}$") || regex.equals("^[1-9][0-9]{5}$"))
            return "560001 (6 digits)";
        if (regex.equals("^[0-9]{5}$"))
            return "12345";
        if (regex.equals("^[0-9]{4}$"))
            return "1234";
        if (regex.equals("^[0-9]{3}$"))
            return "123";
        if (regex.contains("{5,6}"))
            return "12345 or 123456";
        if (regex.contains("[A-Za-z]") && regex.contains("{2}"))
            return "1234 AB";
        if (regex.contains("(-[0-9]{4})"))
            return "12345 or 12345-6789";

        return "Valid format";
    }
}
 