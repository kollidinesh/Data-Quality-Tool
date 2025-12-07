package com.dataquality.main;

import com.dataquality.config.ConfigReader;
import com.dataquality.db.DBConnection;
import com.dataquality.report.ExcelReportGenerator;
import com.dataquality.report.ExcelReportGenerator.ValidationResult;
import com.dataquality.validation.AddressValidator;
import com.dataquality.validation.NameValidator;
import com.dataquality.validation.PostalCodeValidator;
import com.dataquality.validation.RegionValidator;
import com.dataquality.common.CoreLogStream;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DataQualityTool {

    // Keep track of latest generated report for UI download controller
    private static String latestReportPath = null;

    public static synchronized void setLatestReportPath(String path) {
        latestReportPath = path;
    }

    public static synchronized String getLatestReportPath() {
        return latestReportPath;
    }

    public static void main(String[] args) {
        int upsertCount = 0;
        try {
        	
            // Load configuration from userfile.xlsx
            ConfigReader cfg = ConfigReader.load();
            CoreLogStream.push(" Configuration loaded."); // LOG 1: Configuration loaded.

            // Initialize DB connection helper
            DBConnection.init(cfg.getUrl(), cfg.getUser(), cfg.getPassword());
            CoreLogStream.push(" DBConnection initialized."); // LOG 2: DBConnection initialized.

            // Build SELECT using configured columns
            String table = cfg.getTableName();
            String idCol = cfg.getIdColumn();
            String nameCol = cfg.getCustomerNameColumn();
            String addrCol = cfg.getAddressLine1Column();
            String cityCol = cfg.getCityColumn();
            String regionCol = cfg.getRegionCodeColumn();
            String countryCol = cfg.getCountryColumn();
            String postalCol = cfg.getPostalColumn();
            String dunsCol = cfg.getDunsColumn();
            int limit = cfg.getLimit(20);
            
            // NOTE: The table name 'data_quality_check' is used in the upsert method below, 
            // consistent with the provided simplified file's logic.
            
            String query = String.format(
                    "SELECT %s, %s, %s, %s, %s, %s, %s, %s FROM %s LIMIT %d",
                    idCol, nameCol, addrCol, cityCol, regionCol, countryCol, postalCol, dunsCol, table, limit
            );
            CoreLogStream.push("Executing query: " + query); // LOG 3: Executing query: ...

            List<ValidationResult> results = new ArrayList<>();

            // CRITICAL: DB connection in try-with-resources block ensures cleanup 
            // and correct error handling if the connection fails here.
            try (Connection conn = DBConnection.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(query)) {

            	CoreLogStream.push("DB Query executed, processing rows..."); // LOG 4: DB Query executed, processing rows...
            	
                while (rs.next()) {
                    int id = safeInt(rs, idCol); // MDM ID
                    String name = safeStr(rs, nameCol);
                    String rawAddress = safeStr(rs, addrCol); // Raw address from DB
                    String city = safeStr(rs, cityCol);
                    String region = safeStr(rs, regionCol);
                    String country = safeStr(rs, countryCol);
                    String postal = safeStr(rs, postalCol);
                    String duns = safeStr(rs, dunsCol);
                    
                    // --- FULL VALIDATION LOGIC INTEGRATED HERE ---
                    
                    // 1. Name validation
                    String nameReason = NameValidator.getValidationFailureReason(name);
                    String nameStatus = (nameReason == null) ? "Valid" : "Invalid";

                    // 2. Region validation FIRST (Uses DB connection)
                    String regionReason = RegionValidator.getValidationFailureReason(conn, country, region);
                    String regionStatus = (regionReason == null) ? "Valid" : "Invalid";
                    
                    // 3. Build finalAddress using smart rule (only append region if it's valid)
                    String regionToAppend = regionStatus.equals("Valid") ? region : null;
                    String finalAddress = buildFinalAddressSmart(rawAddress, regionToAppend);
                    
                    // 4. Address validation (Uses the finalized address)
                    String addrReason;
                    String addrStatus;
                    if (finalAddress == null || finalAddress.trim().isEmpty()) {
                        addrReason = "Address cannot be empty";
                        addrStatus = "Invalid";
                    } else {
                        addrReason = AddressValidator.getValidationFailureReason(finalAddress, city, region);
                        addrStatus = (addrReason == null) ? "Valid" : "Invalid";
                    }

                    // 5. Postal validation
                    String postalReason = PostalCodeValidator.getValidationFailureReason(conn, country, region, postal);
                    String postalStatus = (postalReason == null) ? "Valid" : "Invalid";

                    // 6. Record-level validation (overall)
                    String recordValidation = ("Valid".equalsIgnoreCase(nameStatus) &&
                                               "Valid".equalsIgnoreCase(addrStatus) &&
                                               "Valid".equalsIgnoreCase(regionStatus) &&
                                               "Valid".equalsIgnoreCase(postalStatus)) ? "Valid" : "Invalid";
                    
                    // 7. Remarks aggregation
                    List<String> reasonList = new ArrayList<>();
                    if (nameReason != null) reasonList.add("Name: " + nameReason);
                    if (addrReason != null) reasonList.add("Address: " + addrReason);
                    if (regionReason != null) reasonList.add("Region: " + regionReason);
                    if (postalReason != null) reasonList.add("Postal: " + postalReason);
                    String remarks = String.join(" | ", reasonList);

                    // --- VALIDATION LOGIC END ---

                    ValidationResult vr = new ValidationResult(
                            id, name, finalAddress, city, region, country, // Use finalAddress for report/DB
                            postal, duns, nameStatus, addrStatus, postalStatus,
                            regionStatus, recordValidation, remarks
                    );
                    results.add(vr);

                    // Upsert to data_quality_check table
                    try {
                        if (upsertRecordIntoDB(conn, vr)) upsertCount++;
                    } catch (Exception e) {
                    	CoreLogStream.push("Failed to upsert record mdmid=" + id + ": " + e.getMessage());
                    }
                }
            } // Connection, Statement, ResultSet closed

            // --- SUCCESS LOGS (Execute ONLY if all above steps completed successfully) ---
            
            // Generate report 
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String outName = "db_validation_validated_" + timestamp + ".xlsx"; 
            String outputPath = System.getProperty("user.dir") +"/ValidationReport.xlsx";

            ExcelReportGenerator.generateExcelReport(results, outputPath);
            setLatestReportPath(outputPath);
            
            CoreLogStream.push("Excel Report Generated: " + new File(outputPath).getName());
            CoreLogStream.push("Total records upserted: " + upsertCount);
            CoreLogStream.push("DB Mode Completed."); // FINAL SUCCESS LOG: Enables download button

        } catch (Exception e) {
            // --- FAILURE LOGS (Execute only on exception) ---
            setLatestReportPath(null);
            CoreLogStream.push("DB Mode Failed: " + e.getMessage()); // EXPLICIT FAILURE LOG
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------
    // Utility Methods (Merged from full version for robust logic)
    // -------------------------------------------------------------

    /**
     * Builds the final address string, cleaning trailing punctuation and 
     * appending the valid region code with a separator.
     */
    private static String buildFinalAddressSmart(String address, String region) {
        if (address == null) address = "";
        address = address.trim();
 
        if (region == null || region.trim().isEmpty()) {
            return address;
        }
 
        String r = region.trim();
        // Clean trailing punctuation from address
        while (!address.isEmpty()) {
            char last = address.charAt(address.length() - 1);
            if (last == ',' || last == '.' || last == ';' || last == ':' || last == '-' || last == '/' || last == '|' ) {
                address = address.substring(0, address.length() - 1).trim();
            } else break;
        }
 
        // Use a comma separator if the address is not empty
        if (address.isEmpty()) {
            return r;
        } else {
            return address + ", " + r;
        }
    }

    /**
     * Upsert record into the data_quality_check table using ON CONFLICT (mdmid).
     * Uses the database table name 'data_quality_check' as found in the simplified file.
     */
    private static boolean upsertRecordIntoDB(Connection conn, ValidationResult r) {
        String sql = "INSERT INTO data_quality_check " + 
                "(mdmid, customername, addressline_1, city, regioncode, countrycode, postalcode, dunsnumber, recordvalidated, remarks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (mdmid) DO UPDATE SET " +
                "customername = EXCLUDED.customername, " +
                "addressline_1 = EXCLUDED.addressline_1, " +
                "city = EXCLUDED.city, " +
                "regioncode = EXCLUDED.regioncode, " +
                "countrycode = EXCLUDED.countrycode, " +
                "postalcode = EXCLUDED.postalcode, " +
                "dunsnumber = EXCLUDED.dunsnumber, " +
                "recordvalidated = EXCLUDED.recordvalidated, " +
                "remarks = EXCLUDED.remarks";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.MDMID);
            ps.setString(2, r.CustomerName);
            ps.setString(3, r.AddressLine1); 
            ps.setString(4, r.city);
            ps.setString(5, r.region);
            ps.setString(6, r.country);
            ps.setString(7, r.postal);
            ps.setString(8, r.dunsnumber);
            ps.setString(9, r.recordValidation);
            ps.setString(10, r.remarks);

            ps.executeUpdate();
            return true;
        } catch (Exception e) {
        	CoreLogStream.push("Upsert error for mdmid=" + r.MDMID + ": " + e.getMessage());
            return false;
        }
    }

    private static int safeInt(ResultSet rs, String col) {
        try { return rs.getInt(col);
        } catch (Exception e) { return 0; }
    }

    private static String safeStr(ResultSet rs, String col) {
        try {
            String v = rs.getString(col);
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            return "";
        }
    }
}