package com.dataquality.web;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
public class DownloadController {

    @GetMapping("/download/report")
    public ResponseEntity<?> downloadReport() {

        String path = ReportTracker.getLastReportPath();
        if (path == null) {
            return ResponseEntity.badRequest().body("No report available. Please run validation first.");
        }

        File file = new File(path);
        if (!file.exists()) {
            return ResponseEntity.badRequest().body("Report file does not exist.");
        }

        FileSystemResource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ValidationReport.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}