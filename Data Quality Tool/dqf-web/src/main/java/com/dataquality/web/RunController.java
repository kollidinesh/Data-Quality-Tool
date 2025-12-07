//package com.dataquality.web;
//
//import com.dataquality.common.CoreLogStream;
//import com.dataquality.main.DataQualityTool;
//import com.dataquality.main.DataQualityExcelTool;
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.nio.file.*;
//import java.io.IOException;
//import java.util.Comparator;
//
//@RestController
//public class RunController {
//
//    // -------------------------------------------------------
//    // RUN DB MODE
//    // -------------------------------------------------------
//    @GetMapping("/run/db-mode")
//    public ResponseEntity<String> runDbMode() {
//
//        try {
//            CoreLogStream.push("--------------------------------------------------");
//            CoreLogStream.push("Starting DB Mode Validation...");
//            CoreLogStream.push("--------------------------------------------------");
//
//            // RUN YOUR MAIN CLASS (VOID)
//            DataQualityTool.main(new String[]{});
//
//            // Get generated report
//            String reportPath = findLatestReportFile();
//            ReportTracker.setLastReportPath(reportPath);
//
//            CoreLogStream.push("--------------------------------------------------");
//            CoreLogStream.push(" DB Mode Completed Successfully.");
//            CoreLogStream.push(" Report ready for download.");
//            CoreLogStream.push("--------------------------------------------------");
//
//            return ResponseEntity.ok("DB Mode executed successfully.");
//
//        } catch (Exception e) {
//            CoreLogStream.push("DB Mode failed: " + e.getMessage());
//
//            ReportTracker.setLastReportPath(null);
//
//            return ResponseEntity.status(500).body("DB Mode Failed: " + e.getMessage());
//        }
//    }
//
//    // -------------------------------------------------------
//    // RUN EXCEL MODE (UPLOAD FILE)
//    // -------------------------------------------------------
//    @PostMapping("/run/excel-mode")
//    public ResponseEntity<String> runExcelMode(@RequestParam("file") MultipartFile file) {
//
//        try {
//
//            CoreLogStream.push("--------------------------------------------------");
//            CoreLogStream.push(" Excel file received: " + file.getOriginalFilename());
//            CoreLogStream.push("Starting Excel Mode Validation...");
//            CoreLogStream.push("--------------------------------------------------");
//
//            // Save incoming file to temp
//            Path temp = Files.createTempFile("dqf_upload_", ".xlsx");
//            Files.write(temp, file.getBytes());
//
//            CoreLogStream.push("Temporary upload saved at: " + temp);
//
//            // Run Excel Mode
//            DataQualityExcelTool.main(new String[]{ temp.toString() });
//
//            // Find report
//            String reportPath = findLatestReportFile();
//            ReportTracker.setLastReportPath(reportPath);
//
//            CoreLogStream.push("--------------------------------------------------");
//            CoreLogStream.push("Excel Mode Completed Successfully.");
//            CoreLogStream.push("Report ready for download.");
//            CoreLogStream.push("--------------------------------------------------");
//
//            return ResponseEntity.ok("Excel Mode executed successfully.");
//
//        } catch (Exception e) {
//            CoreLogStream.push("Excel Mode failed: " + e.getMessage());
//
//            ReportTracker.setLastReportPath(null);
//
//            return ResponseEntity.status(500).body("Excel Mode Failed: " + e.getMessage());
//        }
//    }
//
//    // -------------------------------------------------------
//    // FIND LATEST GENERATED ValidationReport.xlsx
//    // -------------------------------------------------------
//    private String findLatestReportFile() throws Exception {
//
//        Path projectRoot = Paths.get(System.getProperty("user.dir"));
//
//        // 1) CHECK PROJECT ROOT
//        try {
//            Path latest = Files.list(projectRoot)
//                    .filter(p -> p.getFileName().toString().startsWith("ValidationReport"))
//                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
//                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
//                    .orElse(null);
//
//            if (latest != null) return latest.toString();
//        } catch (IOException ignored) {}
//
//        // 2) CHECK /dqf-web/target (Spring Boot runs from here)
//        Path targetDir = projectRoot.resolve("dqf-web").resolve("target");
//        try {
//            Path latest = Files.list(targetDir)
//                    .filter(p -> p.getFileName().toString().startsWith("ValidationReport"))
//                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
//                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
//                    .orElse(null);
//
//            if (latest != null) return latest.toString();
//        } catch (IOException ignored) {}
//
//        throw new Exception("ValidationReport.xlsx not found!");
//    }
//}

package com.dataquality.web;

import com.dataquality.common.CoreLogStream;
import com.dataquality.main.DataQualityTool;
import com.dataquality.main.DataQualityExcelTool;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.io.IOException;
import java.util.Comparator;

@RestController
public class RunController {

    // -------------------------------------------------------
    // RUN DB MODE
    // -------------------------------------------------------
    @GetMapping("/run/db-mode")
    public ResponseEntity<String> runDbMode() {

        try {
            CoreLogStream.push("--------------------------------------------------");
            CoreLogStream.push("Starting DB Mode Validation...");
            CoreLogStream.push("--------------------------------------------------");

            // RUN DB MAIN CLASS
            DataQualityTool.main(new String[]{});

            // FIND REPORT
            String reportPath = findLatestReportFile();
            ReportTracker.setLastReportPath(reportPath);

            CoreLogStream.push("--------------------------------------------------");
            CoreLogStream.push("DB Mode Completed Successfully.");
            CoreLogStream.push("Report ready for download.");
            CoreLogStream.push("--------------------------------------------------");

            return ResponseEntity.ok("DB Mode executed successfully.");

        } catch (Exception e) {
//            CoreLogStream.push("DB Mode Failed: " + e.getMessage());

            ReportTracker.setLastReportPath(null); // disable download

            return ResponseEntity.status(500)
                    .body("DB Mode Failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // RUN EXCEL MODE
    // -------------------------------------------------------
    @PostMapping("/run/excel-mode")
    public ResponseEntity<String> runExcelMode(@RequestParam("file") MultipartFile file) {

        try {
            CoreLogStream.push("--------------------------------------------------");
            CoreLogStream.push("Excel file received: " + file.getOriginalFilename());
            CoreLogStream.push("Starting Excel Mode Validation...");
            CoreLogStream.push("--------------------------------------------------");

            // Save uploaded file
            Path temp = Files.createTempFile("dqf_upload_", ".xlsx");
            Files.write(temp, file.getBytes());
            CoreLogStream.push("Uploaded file stored at: " + temp);

            // RUN EXCEL MAIN CLASS (ONLY ONCE)
            DataQualityExcelTool.main(new String[]{ temp.toString() });

            // FIND REPORT
            String reportPath = findLatestReportFile();
            ReportTracker.setLastReportPath(reportPath);

            CoreLogStream.push("--------------------------------------------------");
            CoreLogStream.push("Excel Mode Completed Successfully.");
            CoreLogStream.push("Report ready for download.");
            CoreLogStream.push("--------------------------------------------------");

            return ResponseEntity.ok("Excel Mode executed successfully.");

        } catch (Exception e) {

        	CoreLogStream.push("Excel Mode Failed: " + e.getMessage());
            CoreLogStream.push("No report generated due to error.");

            ReportTracker.setLastReportPath(null);  // disable download button

            return ResponseEntity.status(500)
                    .body("Please Maintain Your Excel Column Names with Exact Match");
        }
    }

    // -------------------------------------------------------
    // FIND LATEST ValidationReport*.xlsx in project or target
    // -------------------------------------------------------
    private String findLatestReportFile() throws Exception {

        Path root = Paths.get(System.getProperty("user.dir"));

        // 1) Project Root
        try {
            Path p = Files.list(root)
                    .filter(f -> f.getFileName().toString().startsWith("ValidationReport"))
                    .filter(f -> f.toString().endsWith(".xlsx"))
                    .max(Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .orElse(null);
            if (p != null) return p.toString();
        } catch (Exception ignored) {}

        // 2) dqf-web/target
        Path target = root.resolve("dqf-web").resolve("target");
        try {
            Path p = Files.list(target)
                    .filter(f -> f.getFileName().toString().startsWith("ValidationReport"))
                    .filter(f -> f.toString().endsWith(".xlsx"))
                    .max(Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .orElse(null);
            if (p != null) return p.toString();
        } catch (Exception ignored) {}

        throw new Exception("ValidationReport.xlsx not found!");
    }
}