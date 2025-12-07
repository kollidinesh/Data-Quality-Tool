package com.dataquality.config;
 
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
 
public class ConfigReader {
 
    private final Map<String, String> config = new HashMap<>();
 
    private ConfigReader() {}
 
    public static ConfigReader load() throws IOException {
 
        String[] possiblePaths = {
                System.getProperty("user.dir") + File.separator + "userfile.xlsx",             // running DB mode / Excel mode
                System.getProperty("user.dir") + File.separator + ".." + File.separator + "userfile.xlsx", // running from dqf-web
                new File("userfile.xlsx").getAbsolutePath(),                                  // fallback
        };
 
        File file = null;
 
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists()) {
                file = f;
                break;
            }
        }
 
        if (file == null)
            throw new IOException("userfile.xlsx NOT FOUND in any known location!");
 
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
 
            Sheet sheet = wb.getSheetAt(0);
            ConfigReader r = new ConfigReader();
 
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
 
                Cell key = row.getCell(0);
                Cell val = row.getCell(1);
 
                if (key == null) continue;
 
                String k = cellToString(key).trim();
                if (k.isEmpty()) continue;
 
                String v = (val == null) ? "" : cellToString(val).trim();
                r.config.put(k, v);
            }
 
            return r;
        }
    }
 
    private static String cellToString(Cell c) {
        if (c == null) return "";
        switch (c.getCellType()) {
            case STRING:  return c.getStringCellValue();
            case NUMERIC:
                long l = (long)c.getNumericCellValue();
                double d = c.getNumericCellValue();
                return (d == l) ? String.valueOf(l) : String.valueOf(d);
            case BOOLEAN: return String.valueOf(c.getBooleanCellValue());
            default:      return "";
        }
    }
 
    // Config fields
    public String get(String key) { return config.getOrDefault(key, ""); }
 
    public String getUrl()               { return get("URL"); }
    public String getUser()              { return get("USER"); }
    public String getPassword()          { return get("PASSWORD"); }
    public String getTableName()         { return get("Table_Name"); }
    public String getIdColumn()          { return get("Id"); }
    public String getCustomerNameColumn(){ return get("Customer_name"); }
    public String getAddressLine1Column(){ return get("Address_locality_line_1"); }
    public String getCityColumn()        { return get("Address_city"); }
    public String getRegionCodeColumn()  { return get("Region_code"); }
    public String getCountryColumn()     { return get("Address_country_code"); }
    public String getPostalColumn()      { return get("Address_postal_code"); }
    public String getDunsColumn()        { return get("Duns_Number"); }
 
    public int getLimit(int fallback) {
        try { return Integer.parseInt(get("Limit")); }
        catch (Exception e) { return fallback; }
    }
 
    public String getCustomExcelPath() {
        return get("Custom_excel_path");
    }
}
 
 