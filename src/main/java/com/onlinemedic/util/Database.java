package com.onlinemedic.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private static final String URL = setting("DATABASE_URL", env("H2_DATABASE_URL", "jdbc:h2:./data/online_medic;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
    private static final String USER = setting("DATABASE_USER", "sa");
    private static final String PASSWORD = setting("DATABASE_PASSWORD", "");
    private Database() { }

    public static Connection getConnection() throws SQLException {
        loadDriver();
        if (URL.startsWith("jdbc:h2:") && URL.contains("./data/")) {
            try { Files.createDirectories(Path.of("data")); } catch (IOException e) { throw new SQLException("Cannot create local database directory", e); }
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static void loadDriver() throws SQLException {
        String driver = URL.startsWith("jdbc:postgresql:") ? "org.postgresql.Driver"
                : URL.startsWith("jdbc:h2:") ? "org.h2.Driver" : null;
        if (driver == null) return;
        try {
            Class.forName(driver, true, Database.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver " + driver + " is missing from WEB-INF/lib.", e);
        }
    }

    public static boolean isPostgres() { return URL.startsWith("jdbc:postgresql:"); }
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String setting(String name,String fallback){String value=System.getProperty(name);return value==null||value.isBlank()?env(name,fallback):value;}
}
