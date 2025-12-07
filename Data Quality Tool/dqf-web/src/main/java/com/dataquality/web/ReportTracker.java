package com.dataquality.web;

public class ReportTracker {

    private static String lastReportPath = null;

    public static void setLastReportPath(String path) {
        lastReportPath = path;
    }

    public static String getLastReportPath() {
        return lastReportPath;
    }
}