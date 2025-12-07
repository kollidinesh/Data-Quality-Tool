package com.dataquality.db;
 
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
 
public class DBConnection {
    private static String URL = "";
    private static String USER = "";
    private static String PASSWORD = "";
 
    public static void init(String url, String user, String password) {
        URL = (url == null) ? "" : url.trim();
        USER = (user == null) ? "" : user.trim();
        PASSWORD = (password == null) ? "" : password.trim();
 
        System.out.println("DBConnection.init()");
        System.out.println("  URL = " + (URL.isEmpty() ? "<empty>" : URL));
        System.out.println("  USER = " + (USER.isEmpty() ? "<empty>" : USER));
    }
 
    public static Connection getConnection() throws SQLException {
        if (URL == null || URL.isEmpty()) {
            throw new SQLException("Database URL not configured. Check Excel config (userfile.xlsx).");
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
 