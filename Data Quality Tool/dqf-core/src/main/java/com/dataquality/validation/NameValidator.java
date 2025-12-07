package com.dataquality.validation;
 
import java.util.*;
 
public class NameValidator {
 
    private static final int SEQUENCE = 4;
 
    private static final Set<String> COMMON_NAMES = new HashSet<>(Arrays.asList(
        "TEST", "TESTING", "DEMO", "SAMPLE", "EXAMPLE", "DUMMY", "PLACEHOLDER",
        "TEMP", "TEMPORARY", "UNKNOWN", "NA", "N/A", "NONE", "NOTAVAILABLE",
        "ADMIN", "USER", "USERNAME", "DEFAULT", "SYSTEM","IDLE"
    ));
 
    public static Set<String> getCommonNames() {
        return COMMON_NAMES;
    }
 
    // ✔ Allow all language letters + diacritics + space
    // ❌ Disallow numbers and special characters
    private static final String ALLOWED_PATTERN = "^[\\p{L}\\p{M} ]+$";
 
    private static boolean containsInvalidCharacters(String name) {
        return !name.matches(ALLOWED_PATTERN);
    }
 
    private static boolean isContinuousSequence(String name) {
        String upper = name.toUpperCase();
 
        for (int i = 0; i <= upper.length() - SEQUENCE; i++) {
            char first = upper.charAt(i);
            boolean consecutive = true;
 
            for (int j = 1; j < SEQUENCE; j++) {
                if (upper.charAt(i + j) != (char)(first + j)) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) return true;
        }
 
        String[] rows = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        for (String row : rows) {
            for (int i = 0; i <= row.length() - SEQUENCE; i++) {
                String seq = row.substring(i, i + SEQUENCE);
                if (upper.contains(seq)) return true;
            }
        }
 
        return false;
    }
    
    // Check if the name contains any common name as a substring (case-insensitive)
    private static boolean containsCommonNameSubstring(String name) {
        String upperName = name.toUpperCase();
        for (String commonName : COMMON_NAMES) {
            if (upperName.contains(commonName)) {
                return true;
            }
        }
        return false;
    }
 
    public static String getValidationFailureReason(String name) {
 
        if (name == null)
            return "Name cannot be null or empty";
 
        // Step 1: Trim surrounding whitespace
        String outerTrimmed = name.trim();
        
        if (outerTrimmed.isEmpty())
            return "Name cannot be null or empty";
 
        // Step 2: Check for original leading/trailing spaces (existing rule)
        if (!name.equals(outerTrimmed))
            return "Name cannot have leading or trailing spaces";
 
        String trimmed = outerTrimmed;
        int length = trimmed.length();
 
        // Step 3: Check length constraints
        if (length < 5)
            return "Name too short (min 5 characters)";
        if (length > 200)
            return "Name too long (max 200 characters)";
 
        // Step 4: Check for invalid characters (multilingual letters only)
        if (containsInvalidCharacters(trimmed))
            return "Name contains special characters";
 
        // Step 5: Check for common placeholder names (exact match, case-insensitive)
        if (COMMON_NAMES.contains(trimmed.toUpperCase()))
            return "Name contains common placeholder names (exact match)";
        
        // Step 6: Check for common placeholder names (as substring, case-insensitive)
        if (containsCommonNameSubstring(trimmed))
            return "Name contains common placeholder names (as substring)";
 
        // Step 7: Check for sequential patterns
        if (isContinuousSequence(trimmed))
            return "Name contains sequential patterns";
 
        return null;
    }
 
    public static boolean validateName(String name) {
        return getValidationFailureReason(name) == null;
    }
}
 