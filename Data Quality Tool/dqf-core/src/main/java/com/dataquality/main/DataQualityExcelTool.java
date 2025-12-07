package com.dataquality.main;

import com.dataquality.config.ConfigReader;
import com.dataquality.report.ExcelReportGenerator;
import com.dataquality.report.ExcelReportGenerator.ValidationResult;
import com.dataquality.validation.AddressValidator;
import com.dataquality.validation.NameValidator;
import com.dataquality.validation.PostalCodeValidator;
import com.dataquality.validation.RegionValidator;
import com.dataquality.common.CoreLogStream;
import com.dataquality.db.DBConnection;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

public class DataQualityExcelTool {

    // --- Core Constants for Fuzzy Matching (Restored from Full Version) ---
    private static final double NAME_THRESHOLD = 80.0;
    private static final double ADDR_THRESHOLD = 80.0;
    private static final double CITY_THRESHOLD = 70.0;
    private static final int CANDIDATE_LIMIT = 500;
    
    private static String latestReportPath = null;

    public static synchronized void setLatestReportPath(String path) {
        latestReportPath = path;
    }

    public static synchronized String getLatestReportPath() {
        return latestReportPath;
    }

    public static void main(String[] args) {
        String inputExcelPath = null;
        int insertCount = 0; // Tracks new DB inserts

        try { // <--- OUTER TRY BLOCK: Handles Config, Init, and Final Report Generation
          
            // Load config for DB creds and table/column mapping
            ConfigReader cfg = ConfigReader.load();
            CoreLogStream.push("Config loaded.");

            // Choose input source: arg path or Custom_excel_path from config
            if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
                inputExcelPath = args[0].trim();
                CoreLogStream.push("Using uploaded Excel: " + inputExcelPath);
            } else {
                String custom = cfg.getCustomExcelPath();
                if (custom != null && !custom.trim().isEmpty()) {
                    inputExcelPath = custom.trim();
                    CoreLogStream.push("Using custom Excel path from config: " + inputExcelPath);
                } else {
                    throw new RuntimeException("No input Excel provided (args or Custom_excel_path).");
                }
            }

            File inputFile = new File(inputExcelPath);
            if (!inputFile.exists()) {
                throw new RuntimeException("Input Excel not found: " + inputExcelPath);
            }

            // Initialize DB connection pool (actual connection made later in try-with-resources)
            DBConnection.init(cfg.getUrl(), cfg.getUser(), cfg.getPassword());
            CoreLogStream.push("DB Connection initialized for lookups.");

            List<ValidationResult> results = new ArrayList<>();
            Set<String> uniquenessSet = new HashSet<>();

            // Read Excel rows
            try (FileInputStream fis = new FileInputStream(inputFile); // <--- NESTED TRY 1: Excel Resources
                 Workbook wb = new XSSFWorkbook(fis)) {

                Sheet sheet = wb.getSheetAt(0);
                if (sheet == null) throw new RuntimeException("Input Excel has no sheets.");

                // Configuration for DB mapping (needed for upsert/fuzzy match)
                String table = cfg.getTableName();
                String idColumn = cfg.getIdColumn();
                String nameCol = cfg.getCustomerNameColumn();
                String addrCol = cfg.getAddressLine1Column();
                String cityCol = cfg.getCityColumn();
                String regionCol = cfg.getRegionCodeColumn();
                String countryCol = cfg.getCountryColumn();
                String postalCol = cfg.getPostalColumn();
                String dunsCol = cfg.getDunsColumn();

                // Validate header names (first row) - ensure exact expected columns
                Row header = sheet.getRow(0);
                if (header == null) {
                    throw new RuntimeException("Invalid Excel format: header row is missing.");
                }

                // --- Robust Column Indexing (From Full Version) ---
                int idxName = find(header, "Name1");
                int idxAddress = find(header, "Street/House");
                int idxCity = find(header, "City");
                int idxPostal = find(header, "Postal Code");
                int idxCountry = find(header, "Country");
                int idxRegion = find(header, "Region");
                int idxDuns = findOptional(header, "DUNS Number"); // Optional

                int last = sheet.getLastRowNum();
                CoreLogStream.push("Header validated. Rows to process: " + last);

                // --- CRITICAL: DB Connection in inner try block ---
                try (Connection conn = DBConnection.getConnection()) { // <--- NESTED TRY 2: DB Connection

                    for (int r = 1; r <= last; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        // Use robust cellToStr for safe reading
                        String name = cellToStr(row.getCell(idxName));
                        String rawAddress = cellToStr(row.getCell(idxAddress));
                        String city = cellToStr(row.getCell(idxCity));
                        String country = cellToStr(row.getCell(idxCountry));
                        String region = cellToStr(row.getCell(idxRegion));
                        String postal = cellToStr(row.getCell(idxPostal));
                        String excelDuns = idxDuns >= 0 ? cellToStr(row.getCell(idxDuns)) : "";

                        // 1. VALIDATIONS (using DB connection for lookups)
                        String nameReason = NameValidator.getValidationFailureReason(name);
                        String regionReason = RegionValidator.getValidationFailureReason(conn, country, region);
                        String postalReason = PostalCodeValidator.getValidationFailureReason(conn, country, region, postal);
                        
                        String nameStatus = (nameReason == null ? "Valid" : "Invalid");
                        String regionStatus = (regionReason == null ? "Valid" : "Invalid");
                        String postalStatus = (postalReason == null ? "Valid" : "Invalid");
                        
                        // Use smart logic to build the address (buildFinalAddressSmart)
                        String finalAddress = rawAddress == null ? "" : rawAddress.trim();
                        if ("Valid".equalsIgnoreCase(regionStatus)
                                && region != null && !region.trim().isEmpty()) {
                            finalAddress = buildFinalAddressSmart(finalAddress, region.trim()); 
                        }
                        
                        String addrReason;
                        String addrStatus;
                        if (rawAddress == null || rawAddress.trim().isEmpty()) {
                            addrReason = "Address cannot be empty";
                            addrStatus = "Invalid";
                        } else {
                            // Address validation uses the finalized address (raw address + region)
                            addrReason = AddressValidator.getValidationFailureReason(finalAddress, city, region);
                            addrStatus = (addrReason == null ? "Valid" : "Invalid");
                        }

                        String recordValidation =
                                ("Valid".equalsIgnoreCase(nameStatus)
                                && "Valid".equalsIgnoreCase(addrStatus)
                                && "Valid".equalsIgnoreCase(regionStatus)
                                && "Valid".equalsIgnoreCase(postalStatus))
                                        ? "Valid" : "Invalid";

                        // Build remarks (Combined validation failure reasons)
                        List<String> reasons = new ArrayList<>();
                        if (nameReason != null) reasons.add("Name: " + nameReason);
                        if (addrReason != null) reasons.add("Address: " + addrReason);
                        if (regionReason != null) reasons.add("Region: " + regionReason);
                        if (postalReason != null) reasons.add("Postal: " + postalReason);
                        String remarks = String.join(" | ", reasons).trim();

                        Integer matchedId = null;
                        String matchedDuns = excelDuns; 

                        // 2. FUZZY MATCHING & UPSERT LOGIC (From Full Version)
                        if ("Valid".equalsIgnoreCase(recordValidation)) {

                            Set<String> resolvedCountryCodes = resolveCountryCodes(conn, country);
                            String normPostal = normalizePostal(postal);

                            List<Candidate> candidates =
                                    fetchCandidates(conn, table, idColumn, dunsCol,
                                            nameCol, addrCol, cityCol, countryCol, postalCol,
                                            resolvedCountryCodes, normPostal, CANDIDATE_LIMIT);

                            double bestScore = -1.0;
                            Candidate bestCand = null;

                            String tName = safeUpper(name);
                            String tAddr = normalizeAndUpper(finalAddress);
                            String tCity = safeUpper(city);

                            // Score candidates
                            for (Candidate c : candidates) {
                                double nameSim = similarityPercent(tName, safeUpper(c.candName));
                                double addrSim = similarityPercent(tAddr, normalizeAndUpper(c.candAddress));
                                double citySim = similarityPercent(tCity, safeUpper(c.candCity));

                                if (nameSim >= NAME_THRESHOLD && addrSim >= ADDR_THRESHOLD && citySim >= CITY_THRESHOLD) {
                                    double combined = (nameSim * 0.45) + (addrSim * 0.45) + (citySim * 0.10);
                                    if (combined > bestScore) {
                                        bestScore = combined;
                                        bestCand = c;
                                    }
                                }
                            }

                            if (bestCand != null) {
                                matchedId = bestCand.id; 
                                matchedDuns = bestCand.duns == null || bestCand.duns.isEmpty() ? excelDuns : bestCand.duns; 
                                remarks = "Record exists (fuzzy match) | Score: " + String.format("%.2f", bestScore) + "%";
                            } else {
                                remarks = "Record doesn't exist (no fuzzy match)";
                            }
                        
                            // 3. Upsert into Database (using rawAddress for matching DB structure)
                            String upsertResult = upsertIntoExcelDataQuality(
                                    conn,
                                    rawAddress, name, city, region, country, postal,
                                    matchedDuns, matchedId, recordValidation, remarks
                            );
                            if ("INSERT".equals(upsertResult)) insertCount++;
                        }

                        // Uniqueness check for report generation
                        String businessKey =
                                (normalizeAndUpper(name) + "|" + normalizeAndUpper(finalAddress) + "|" +
                                        normalizeAndUpper(city) + "|" + normalizeAndUpper(region) + "|" +
                                        normalizeAndUpper(country) + "|" + normalizeAndUpper(postal));
                        if (!uniquenessSet.add(businessKey)) continue;

                        // Create ValidationResult for Excel Report
                        ValidationResult vr = new ValidationResult(
                                (matchedId == null ? 0 : matchedId),
                                name,
                                finalAddress, // Use finalAddress for report
                                city,
                                region,
                                country,
                                postal,
                                matchedDuns,
                                nameStatus,
                                addrStatus,
                                postalStatus,
                                regionStatus,
                                recordValidation,
                                remarks
                        );
                        results.add(vr);
                    }
                } // conn closed successfully
            } // workbook closed successfully

            // --- SUCCESS FLOW: ONLY RUNS IF NO EXCEPTION WAS THROWN ---
            String baseName = inputFile.getName();
            if (baseName.contains(".")) baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            String outputPath = System.getProperty("user.dir") + "/ValidationReport.xlsx";

            ExcelReportGenerator.generateExcelReport(results, outputPath);

            setLatestReportPath(outputPath);
            CoreLogStream.push("Excel Report Generated: " + new File(outputPath).getName());
            CoreLogStream.push("Excel Mode Completed.");

        } catch (Exception e) { 
            // --- FAILURE FLOW: FIX TO ENSURE CORRECT LOGS ---
            setLatestReportPath(null);
            // These log lines are what produce the correct failure sequence in your UI:
            CoreLogStream.push("Excel Mode Failed: " + e.getMessage()); 
            CoreLogStream.push("No report generated due to error.");
            throw new RuntimeException(e);
        }
    }
    
    // -------------------------------------------------------------
    // Utility and Business Logic Methods (From Full Version)
    // -------------------------------------------------------------

    private static String buildFinalAddressSmart(String address, String region) {
        if (address == null) return region == null ? "" : region;
        if (region == null || region.isEmpty()) return address;

        String a = address.trim();
        String r = region.trim();
        
        if (a.equalsIgnoreCase(r)
                || a.toUpperCase().endsWith((" " + r).toUpperCase())
                || a.toUpperCase().endsWith((", " + r).toUpperCase())) {
            return a;
        }

        return a + ", " + r;
    }

    private static class Candidate {
        final int id;
        final String duns;
        final String candName;
        final String candAddress;
        final String candCity;
        
        Candidate(int id, String duns, String candName, String candAddress, String candCity) {
            this.id = id;
            this.duns = duns;
            this.candName = candName;
            this.candAddress = candAddress;
            this.candCity = candCity;
        }
    }

    private static Set<String> resolveCountryCodes(Connection conn, String inputCountry) {
        Set<String> out = new LinkedHashSet<>();
        if (inputCountry == null || inputCountry.trim().isEmpty()) return out;

        String key = inputCountry.trim().toUpperCase();
        out.add(key);
        
        String sql = "SELECT alpha2code, alpha3code FROM country_region_postal_validation " +
                "WHERE UPPER(COALESCE(alpha2code,'')) = ? OR UPPER(COALESCE(alpha3code,'')) = ? LIMIT 1";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String a2 = rs.getString("alpha2code");
                    String a3 = rs.getString("alpha3code");
                    if (a2 != null && !a2.trim().isEmpty()) out.add(a2.trim().toUpperCase());
                    if (a3 != null && !a3.trim().isEmpty()) out.add(a3.trim().toUpperCase());
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static List<Candidate> fetchCandidates(
            Connection conn, String table, String idCol, String dunsCol,
            String nameCol, String addrCol, String cityCol,
            String countryCol, String postalCol,
            Set<String> countryVariants, String normPostal, int limit) {

        List<Candidate> list = new ArrayList<>();
        if (countryVariants == null || countryVariants.isEmpty() || normPostal == null) return list;

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < countryVariants.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }

        String sql =
                "SELECT " + idCol + ", " + dunsCol + ", " + nameCol + ", " + addrCol + ", " + cityCol +
                        " FROM " + table +
                        " WHERE UPPER(COALESCE(" + countryCol + ",'') ) IN (" + in + ")" +
                        " AND REPLACE(REPLACE(UPPER(COALESCE(" + postalCol + ",'') ),' ',''),'-','') = ?" +
                        " LIMIT " + limit;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String c : countryVariants) ps.setString(idx++, c.toUpperCase());
            ps.setString(idx, normPostal.toUpperCase());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Candidate(
                            rs.getInt(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5)
                    ));
                }
            }

        } catch (Exception ex) {
            System.err.println("Candidate fetch failed: " + ex.getMessage());
        }
        return list;
    }

    private static String upsertIntoExcelDataQuality(
            Connection conn,
            String rawAddress,
            String name, String city, String region,
            String country, String postal,
            String duns, Integer id,
            String recordStatus, String remarks) {

        try {
            // Check if a record with the same input key already exists
            String findSql =
                    "SELECT mdmid FROM excel_data_quality_check " +
                            "WHERE UPPER(COALESCE(name1,'')) = UPPER(?) " +
                            "AND UPPER(COALESCE(streetorhouse,'')) = UPPER(?) " +
                            "AND UPPER(COALESCE(city,'')) = UPPER(?) " +
                            "AND UPPER(COALESCE(region,'')) = UPPER(?) " +
                            "AND UPPER(COALESCE(country,'')) = UPPER(?) " +
                            "AND REPLACE(REPLACE(UPPER(COALESCE(postalcode,'')),' ',''),'-','') = " +
                            "    REPLACE(REPLACE(UPPER(?),' ',''),'-','')" +
                            " LIMIT 1";
            
            try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
                psFind.setString(1, safe(name));
                psFind.setString(2, safe(rawAddress));
                psFind.setString(3, safe(city));
                psFind.setString(4, safe(region));
                psFind.setString(5, safe(country));
                psFind.setString(6, safe(postal));
                
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        // Existing record found -> UPDATE it
                        int existingMDM = rs.getInt("mdmid");
                        String upd =
                                "UPDATE excel_data_quality_check SET name1=?, streetorhouse=?, city=?, region=?, " +
                                        "country=?, postalcode=?, dunsnumber=?, recordvalidated=?, remarks=?, mdmid=? " +
                                        "WHERE mdmid=?";
                        
                        try (PreparedStatement psUpd = conn.prepareStatement(upd)) {
                            psUpd.setString(1, name);
                            psUpd.setString(2, rawAddress);
                            psUpd.setString(3, city);
                            psUpd.setString(4, region);
                            psUpd.setString(5, country);
                            psUpd.setString(6, postal);
                            psUpd.setString(7, duns);
                            psUpd.setString(8, recordStatus);
                            psUpd.setString(9, remarks);
                            if (id != null) psUpd.setInt(10, id);
                            else psUpd.setNull(10, Types.INTEGER);
                            psUpd.setInt(11, existingMDM);
                            psUpd.executeUpdate();
                            return "UPDATE";
                        }
                    }
                }
            }

            // No existing record found -> INSERT new row
            String ins =
                    "INSERT INTO excel_data_quality_check " +
                            "(mdmid, name1, streetorhouse, city, region, country, postalcode, dunsnumber, recordvalidated, remarks) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement psIns = conn.prepareStatement(ins)) {
                if (id != null) psIns.setInt(1, id);
                else psIns.setNull(1, Types.INTEGER);
                psIns.setString(2, name);
                psIns.setString(3, rawAddress);
                psIns.setString(4, city);
                psIns.setString(5, region);
                psIns.setString(6, country);
                psIns.setString(7, postal);
                psIns.setString(8, duns);
                psIns.setString(9, recordStatus);
                psIns.setString(10, remarks);
                psIns.executeUpdate();

                return "INSERT";
            }

        } catch (Exception ex) {
            System.err.println("Excel upsert failed: " + ex.getMessage());
            return "NONE";
        }
       
    }

    private static String normalizePostal(String p) {
        if (p == null) return "";
        return p.replaceAll("[\\s\\-]+", "").trim().toUpperCase();
    }

    private static String normalizeAndUpper(String s) {
        if (s == null) return "";
        // Removes punctuation and multiple spaces, then uppercases
        return s.replaceAll("[\\s\\p{Punct}]+", " ").trim().toUpperCase();
    }

    private static String safeUpper(String s) { return s == null ? "" : s.trim().toUpperCase(); }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static double similarityPercent(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        if (s1.isEmpty() && s2.isEmpty()) return 100.0;

        int dist = levenshteinDistance(s1, s2);
        int max = Math.max(s1.length(), s2.length());

        if (max == 0) return 100.0;
        double sim = (1.0 - (double) dist / max) * 100.0;

        return Math.max(sim, 0.0);
    }

    private static int levenshteinDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
    
    // Excel Column Index Helpers (From Full Version, but adapted to use the robust cellToStr below)
    private static int find(Row header, String col) throws Exception {
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i);
            if (c != null && col.equalsIgnoreCase(cellToStr(c))) return i;
        }
        throw new Exception("Column not found in Excel: " + col);
    }

    private static int findOptional(Row header, String col) {
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i);
            if (c != null && col.equalsIgnoreCase(cellToStr(c))) return i;
        }
        return -1;
    }

    /**
     * Robust cell value reader, handling different types safely.
     */
    private static String cellToStr(Cell c) {
        if (c == null) return "";
        try {
            switch (c.getCellType()) {
                case STRING: 
                    return c.getStringCellValue().trim();
                case NUMERIC:
                    double dv = c.getNumericCellValue();
                    long lv = (long) dv;
                    // Return as integer string if no decimal part, otherwise as double string
                    return (dv == lv) ? String.valueOf(lv) : String.valueOf(dv);
                case BOOLEAN: 
                    return String.valueOf(c.getBooleanCellValue());
                case FORMULA:
                    // Attempt to get the cached value or formula string
                    return c.toString().trim();
                default: 
                    return "";
            }
        } catch (Exception ex) {
            // Fallback: use toString() if direct access fails
            return c.toString().trim();
        }
    }
}