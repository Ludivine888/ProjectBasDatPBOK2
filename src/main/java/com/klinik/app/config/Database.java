package com.klinik.app.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {

    private static String host = "localhost";
    private static String port = "3306";
    private static String dbName = "db_klinik2";
    private static String user = "root";
    private static String pass = "";
    private static String params = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=UTF-8";

    private static boolean initialized = false;

    static {
        loadConfiguration();
    }

    private static synchronized void loadConfiguration() {
        if (initialized) return;
        try (InputStream in = Database.class.getResourceAsStream("/database.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                host = props.getProperty("db.host", host).trim();
                port = props.getProperty("db.port", port).trim();
                dbName = props.getProperty("db.name", dbName).trim();
                user = props.getProperty("db.user", user).trim();
                pass = props.getProperty("db.password", pass).trim();
                params = props.getProperty("db.params", params).trim();
            }
        } catch (Exception e) {
            System.err.println("[Database] Menggunakan konfigurasi default (db_klinik2): " + e.getMessage());
        }
        initialized = true;
    }

    public static String getJdbcUrl(String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?" + params;
    }

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(getJdbcUrl(dbName), user, pass);
        } catch (SQLException e) {
            // Jika db_klinik2 gagal karena database tidak ditemukan (SQLState 42000), coba fallback ke db_klinik
            if ("42000".equals(e.getSQLState()) && "db_klinik2".equals(dbName)) {
                try {
                    Connection conn = DriverManager.getConnection(getJdbcUrl("db_klinik"), user, pass);
                    dbName = "db_klinik"; // switch fallback
                    return conn;
                } catch (SQLException ignored) {
                    // lempar exception asli
                }
            }
            throw e;
        }
    }

    public static Connection getConnectionWithoutDb() throws SQLException {
        String urlNoDb = "jdbc:mysql://" + host + ":" + port + "/?" + params;
        return DriverManager.getConnection(urlNoDb, user, pass);
    }

    public static String getActiveDbName() {
        return dbName;
    }

    public static void setDbName(String name) {
        dbName = name;
    }

    /**
     * Memeriksa koneksi database dan mengembalikan pesan status diagnostik yang informatif.
     */
    public static String checkConnectionStatus() {
        try (Connection conn = getConnection()) {
            return "OK: Terhubung ke database [" + dbName + "] di " + host + ":" + port;
        } catch (SQLException e) {
            String state = e.getSQLState();
            int errorCode = e.getErrorCode();

            if (errorCode == 1049 || "42000".equals(state)) {
                return "Database [" + dbName + "] belum dibuat di MySQL. Silakan impor file db_klinik2.sql ke MySQL/Laragon.";
            } else if (errorCode == 1045 || "28000".equals(state)) {
                return "Gagal login ke MySQL: Username atau password salah (User: " + user + ").";
            } else if ("08S01".equals(state) || "08001".equals(state) || e.getMessage().toLowerCase().contains("communications link failure")) {
                return "Koneksi ke MySQL gagal di " + host + ":" + port + ". Pastikan server MySQL (Laragon/XAMPP) sudah aktif!";
            } else {
                return "Gagal terhubung ke database: " + e.getMessage();
            }
        }
    }
}
