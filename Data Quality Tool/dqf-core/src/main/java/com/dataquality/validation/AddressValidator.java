package com.dataquality.validation;
 
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
 
public class AddressValidator {
 
    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 100;
    private static final String INVALID_CHARS_PATTERN = "[!?%]";
    private static final int SEQUENCE_LENGTH = 4;
 
    private static final Set<String> COMMON_NAMES = new HashSet<>(Arrays.asList(
            "TEST", "TESTING", "DEMO", "SAMPLE", "EXAMPLE", "DUMMY", "PLACEHOLDER",
            "TEMP", "TEMPORARY", "UNKNOWN", "NA", "N/A", "NONE", "NOTAVAILABLE",
            "ADMIN", "USER", "USERNAME", "DEFAULT", "SYSTEM"
    ));
 
    /**
     * FINAL updated address validator method (3 parameters)
     * This is the ONLY valid signature your tool expects.
     */
    
 // Overload to support DB Tool (backward compatibility)
    public static String getValidationFailureReason(String addressLine1) {
        return getValidationFailureReason(addressLine1, "", "");
    }
     
    public static String getValidationFailureReason(String addressLine1, String city, String region) {
 
        if (addressLine1 == null || addressLine1.trim().isEmpty()) {
            return "Address cannot be empty";
        }
 
        String trimmed = addressLine1.trim().toUpperCase();
        int length = trimmed.length();
 
        if (length < MIN_LENGTH)
            return "Address too short (minimum 10 characters)";
        if (length > MAX_LENGTH)
            return "Address too long (maximum 100 characters)";
 
        if (trimmed.matches(".*" + INVALID_CHARS_PATTERN + ".*"))
            return "Address contains invalid characters (!, ?, % are not allowed)";
 
        // Check for placeholder words
        String[] words = trimmed.split("\\s+");
        for (String word : words) {
            if (COMMON_NAMES.contains(word)) {
                return "Address contains placeholder word: " + word;
            }
        }
 
        // Check for sequences (ABC, QWER etc.)
        if (isContinuousSequence(trimmed))
            return "Address contains sequential character patterns";
 
        return null; // VALID
    }
 
    // Sequence detector
    private static boolean isContinuousSequence(String value) {
        String upper = value.toUpperCase();
 
        // Alphabetic sequences
        for (int i = 0; i <= upper.length() - SEQUENCE_LENGTH; i++) {
            char first = upper.charAt(i);
            boolean consecutive = true;
            for (int j = 1; j < SEQUENCE_LENGTH; j++) {
                if (upper.charAt(i + j) != (char) (first + j)) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) return true;
        }
 
        // Keyboard sequences
        String[] keyboardRows = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        for (String row : keyboardRows) {
            for (int i = 0; i <= row.length() - SEQUENCE_LENGTH; i++) {
                String seq = row.substring(i, i + SEQUENCE_LENGTH);
                if (upper.contains(seq)) return true;
            }
        }
 
        return false;
    }
}
 
 


