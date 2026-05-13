package com.futu.sdk.examples;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.*;

/**
 * Global configuration loaded from .env file.
 * Mirrors the connect.py pattern from futu-python-samples.
 */
public final class Config {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    // OpenD connection settings
    public static final String FUTU_OPEND_HOST = getenv("FUTU_OPEND_HOST", "127.0.0.1");
    public static final int FUTU_OPEND_PORT = getIntEnv("FUTU_OPEND_PORT", 11111);

    // RSA encryption
    public static final boolean FUTU_RSA_ENABLED = getBoolEnv("FUTU_RSA_ENABLED", false);
    public static final String FUTU_RSA_KEY_PATH = getenv("FUTU_RSA_KEY_PATH", "/etc/futu/keys/private_key.pem");

    // Pre-loaded RSA key content (PKCS#1 format, ready for SDK)
    // The Java SDK requires PKCS#1 (-----BEGIN RSA PRIVATE KEY-----) not PKCS#8.
    // If the key file is PKCS#8, we auto-convert once at load time.
    public static final String RSA_KEY_CONTENT = loadRSAKey(FUTU_RSA_KEY_PATH);

    // Security firm
    public static final String FUTU_SECURITY_FIRM = getenv("FUTU_SECURITY_FIRM", "FUTU HK");

    // Trading password
    public static final String FUTU_TRADE_PASSWORD = getenv("FUTU_TRADE_PASSWORD", "");

    // Environment
    public static final String FUTU_ENV = getenv("FUTU_ENV", "dev");

    // Prevent instantiation
    private Config() {}

    private static String getenv(String key, String defaultValue) {
        String value = DOTENV.get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static int getIntEnv(String key, int defaultValue) {
        String value = DOTENV.get(key);
        if (value != null && !value.isBlank()) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static boolean getBoolEnv(String key, boolean defaultValue) {
        String value = DOTENV.get(key);
        return (value != null && !value.isBlank()) ? "true".equalsIgnoreCase(value.trim()) : defaultValue;
    }

    public static String[] getHosts() {
        String hosts = DOTENV.get("FUTU_OPEND_HOSTS", "");
        if (hosts != null && !hosts.trim().isEmpty()) {
            return hosts.split(",");
        }
        return new String[]{ FUTU_OPEND_HOST + ":" + FUTU_OPEND_PORT + ":true" };
    }

    /**
     * Loads RSA key and ensures PKCS#1 format for the Java SDK.
     * Auto-converts PKCS#8 (-----BEGIN PRIVATE KEY-----) to PKCS#1 (-----BEGIN RSA PRIVATE KEY-----).
     */
    private static String loadRSAKey(String path) {
        if (!FUTU_RSA_ENABLED) return "";
        try {
            String content = Files.readString(Paths.get(path)).trim();
            if (content.startsWith("-----BEGIN PRIVATE KEY-----")) {
                // Convert PKCS#8 -> PKCS#1 using OpenSSL
                ProcessBuilder pb = new ProcessBuilder("openssl", "rsa", "-in", path, "-traditional", "-out", "/tmp/futu_rsa_key.pem");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int ec = p.waitFor();
                if (ec != 0) throw new RuntimeException("openssl conversion failed: " + new String(p.getInputStream().readAllBytes()));
                content = Files.readString(Paths.get("/tmp/futu_rsa_key.pem")).trim();
            }
            return content;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA key from " + path + ": " + e.getMessage(), e);
        }
    }
}
