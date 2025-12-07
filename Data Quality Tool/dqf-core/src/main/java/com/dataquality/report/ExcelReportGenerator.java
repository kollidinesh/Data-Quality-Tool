package com.dataquality.report;

import org.apache.poi.ss.usermodel.*;

import org.apache.poi.xssf.usermodel.*;

import org.apache.poi.xddf.usermodel.chart.*;

import org.apache.poi.xddf.usermodel.*;

import org.apache.poi.ss.util.CellRangeAddress;

import java.io.FileOutputStream;

import java.util.*;

public class ExcelReportGenerator {

    public static class ValidationResult {

        public final int MDMID;

        public final String CustomerName;

        public final String AddressLine1;

        public final String city;

        public final String region;

        public final String country;

        public final String postal;

        public final String dunsnumber;

        public final String nameStatus;

        public final String addressStatus;

        public final String postalStatus;

        public final String regionStatus;

        public final String recordValidation;

        public final String remarks;

        public ValidationResult(

                int MDMID, String CustomerName, String AddressLine1,

                String city, String region, String country, String postal,

                String dunsnumber,

                String nameStatus, String addressStatus, String postalStatus,

                String regionStatus, String recordValidation, String remarks

        ) {

            this.MDMID = MDMID;

            this.CustomerName = CustomerName;

            this.AddressLine1 = AddressLine1;

            this.city = city;

            this.region = region;

            this.country = country;

            this.postal = postal;

            this.dunsnumber = dunsnumber;

            this.nameStatus = nameStatus;

            this.addressStatus = addressStatus;

            this.postalStatus = postalStatus;

            this.regionStatus = regionStatus;

            this.recordValidation = recordValidation;

            this.remarks = remarks;

        }

    }

    // ---------------- GENERATE REPORT --------------------

    public static void generateExcelReport(List<ValidationResult> rows, String filePath) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            XSSFSheet dataSheet = wb.createSheet("ValidationResults");

            XSSFFont boldFont = wb.createFont();

            boldFont.setBold(true);

            CellStyle boldStyle = wb.createCellStyle();

            boldStyle.setFont(boldFont);

            // --- NEW: Styles for Valid and Invalid cells (Light Green and Light Red) ---

            CellStyle validStyle = wb.createCellStyle();

            validStyle.setFillForegroundColor(IndexedColors. LIGHT_GREEN.getIndex()); 

            validStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle invalidStyle = wb.createCellStyle();

            invalidStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex()); 

            invalidStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ---------------------------------------------------------------------------

            String[] headers = {

                    "MDMID", "Customer Name", "Address Line 1", "City", "Region",

                    "Country", "Postal Code", "DUNS Number",

                    "Name Status", "Address Status", "Postal Status", "Region Status",

                    "Record Validation", "Remarks"

            };

            Row headerRow = dataSheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {

                Cell c = headerRow.createCell(i);

                c.setCellValue(headers[i]);

                c.setCellStyle(boldStyle);

            }

            // ----------- WRITE UNIQUE ROWS ONLY -------------

            int index = 1;

            for (ValidationResult r : rows) {

                Row row = dataSheet.createRow(index++);

                int col = 0;

                if (r.MDMID == 0) row.createCell(col++).setCellValue("");

                else row.createCell(col++).setCellValue(r.MDMID);

                row.createCell(col++).setCellValue(ns(r.CustomerName));

                row.createCell(col++).setCellValue(ns(r.AddressLine1));

                row.createCell(col++).setCellValue(ns(r.city));

                row.createCell(col++).setCellValue(ns(r.region));

                row.createCell(col++).setCellValue(ns(r.country));

                row.createCell(col++).setCellValue(ns(r.postal));

                row.createCell(col++).setCellValue(ns(r.dunsnumber));

                row.createCell(col++).setCellValue(ns(r.nameStatus));

                row.createCell(col++).setCellValue(ns(r.addressStatus));

                row.createCell(col++).setCellValue(ns(r.postalStatus));

                row.createCell(col++).setCellValue(ns(r.regionStatus));

                // --- NEW: Apply conditional style to the Record Validation cell ---

                Cell validationCell = row.createCell(col++);

                validationCell.setCellValue(ns(r.recordValidation));

                if ("Valid".equalsIgnoreCase(r.recordValidation)) {

                    validationCell.setCellStyle(validStyle);

                } else if ("Invalid".equalsIgnoreCase(r.recordValidation)) {

                    validationCell.setCellStyle(invalidStyle);

                }

                // ------------------------------------------------------------------

                row.createCell(col++).setCellValue(ns(r.remarks));

            }

            for (int i = 0; i < headers.length; i++)

                dataSheet.autoSizeColumn(i);

            // ----------------- SUMMARY -----------------

            XSSFSheet summary = wb.createSheet("Summary");

            int sRow = 0;

            Row t = summary.createRow(sRow++);

            Cell title = t.createCell(0);

            title.setCellValue("Summary Report");

            CellStyle titleStyle = wb.createCellStyle();

            XSSFFont tf = wb.createFont();

            tf.setBold(true);

            tf.setFontHeightInPoints((short) 12);

            titleStyle.setFont(tf);

            title.setCellStyle(titleStyle);

            summary.createRow(sRow++);

            int total = rows.size();

            int valid = (int) rows.stream().filter(r -> r.recordValidation.equals("Valid")).count();

            int invalid = total - valid;

            Row r1 = summary.createRow(sRow++);

            r1.createCell(0).setCellValue("Total Records Processed:");

            r1.createCell(1).setCellValue(total);

            Row r2 = summary.createRow(sRow++);

            r2.createCell(0).setCellValue("Total Valid Records:");

            r2.createCell(1).setCellValue(valid);

            Row r3 = summary.createRow(sRow++);

            r3.createCell(0).setCellValue("Total Invalid Records:");

            r3.createCell(1).setCellValue(invalid);

            sRow += 2;

            // ---------------- VALIDATION STATS --------------------

            String[] valTypes = {"Name", "Address", "Region", "Postal"};

            Map<String, Integer> pass = new LinkedHashMap<>();

            Map<String, Integer> fail = new LinkedHashMap<>();

            for (String v : valTypes) {

                pass.put(v, 0);

                fail.put(v, 0);

            }

            for (ValidationResult r : rows) {

                if (r.nameStatus.equals("Valid")) pass.put("Name", pass.get("Name") + 1);

                else fail.put("Name", fail.get("Name") + 1);

                if (r.addressStatus.equals("Valid")) pass.put("Address", pass.get("Address") + 1);

                else fail.put("Address", fail.get("Address") + 1);

                if (r.regionStatus.equals("Valid")) pass.put("Region", pass.get("Region") + 1);

                else fail.put("Region", fail.get("Region") + 1);

                if (r.postalStatus.equals("Valid")) pass.put("Postal", pass.get("Postal") + 1);

                else fail.put("Postal", fail.get("Postal") + 1);

            }

            Map<String, Double> pct = new LinkedHashMap<>();

            for (String v : valTypes) {

                pct.put(v, total == 0 ? 0 : (pass.get(v) * 100.0 / total));

            }

            // Header

            Row vrh = summary.createRow(sRow++);

            String[] statHead = {"Validation Type", "Passed", "Failed", "Pass%", "Fail%"};

            for (int i = 0; i < statHead.length; i++) {

                Cell c = vrh.createCell(i);

                c.setCellValue(statHead[i]);

                c.setCellStyle(boldStyle);

            }

            for (String v : valTypes) {

                Row rr = summary.createRow(sRow++);

                rr.createCell(0).setCellValue(v);

                rr.createCell(1).setCellValue(pass.get(v));

                rr.createCell(2).setCellValue(fail.get(v));

                rr.createCell(3).setCellValue(round(pct.get(v), 2));

                rr.createCell(4).setCellValue(round(100 - pct.get(v), 2));

            }

            int chartStart = sRow + 2;

            // ----------- BAR CHART (Pass % only) -------------------

            List<String> sorted = new ArrayList<>(Arrays.asList(valTypes));

            sorted.sort((a, b) -> Double.compare(pct.get(b), pct.get(a)));

            int chartDataStart = chartStart;

            Row ch = summary.createRow(chartDataStart++);

            ch.createCell(0).setCellValue("Validation");

            ch.createCell(1).setCellValue("Pass%");

            for (String v : sorted) {

                Row rr = summary.createRow(chartDataStart++);

                rr.createCell(0).setCellValue(v);

                rr.createCell(1).setCellValue(round(pct.get(v), 2));

            }

            XSSFDrawing draw = summary.createDrawingPatriarch();

            XSSFClientAnchor a = draw.createAnchor(0, 0, 0, 0, 0, chartDataStart + 1, 8, chartDataStart + 20);

            XSSFChart chart = draw.createChart(a);

            chart.setTitleText("Validation Pass% (Descending)");

            XDDFCategoryAxis x = chart.createCategoryAxis(AxisPosition.BOTTOM);

            XDDFValueAxis y = chart.createValueAxis(AxisPosition.LEFT);

            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(

                    summary,

                    new CellRangeAddress(chartStart + 1, chartDataStart - 1, 0, 0)

            );

            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(

                    summary,

                    new CellRangeAddress(chartStart + 1, chartDataStart - 1, 1, 1)

            );

            XDDFChartData data = chart.createData(ChartTypes.BAR, x, y);

            ((XDDFBarChartData) data).setBarDirection(BarDirection.COL);

            XDDFChartData.Series s = data.addSeries(cats, vals);

            s.setTitle("Pass%", null);

            XDDFSolidFillProperties fill = new XDDFSolidFillProperties(XDDFColor.from(new byte[]{0, (byte) 128, 0}));

            XDDFShapeProperties props = new XDDFShapeProperties();

            props.setFillProperties(fill);

            s.setShapeProperties(props);

            chart.plot(data);

            // ---- WRITE FILE ----

            try (FileOutputStream fos = new FileOutputStream(filePath)) {

                wb.write(fos);

            }

        }

    }

    private static String ns(String s) { return s == null ? "" : s; }

    private static double round(double v, int p) {

        double scale = Math.pow(10,p);

        return Math.round(v*scale)/scale;

    }

}
 

 

