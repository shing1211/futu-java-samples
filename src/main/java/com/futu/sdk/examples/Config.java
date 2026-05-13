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
    // FUTU_OPEND_HOSTS supports HA mode (comma-separated host:port:useRSA tuples, e.g. "host1:11111:true,host2:11111:false")
    // Falls back to single FUTU_OPEND_HOST if not set.
    public static final String FUTU_OPEND_HOST = getOpenDHost();
    public static final int FUTU_OPEND_PORT = getOpenDPort();

    private static String getOpenDHost() {
        String hosts = DOTENV.get("FUTU_OPEND_HOSTS", "");
        if (hosts != null && !hosts.trim().isEmpty()) {
            String[] parts = hosts.split(",");
            if (parts.length > 0) {
                String[] hostParts = parts[0].trim().split(":");
                return hostParts.length >= 1 ? hostParts[0] : "127.0.0.1";
            }
        }
        return getenv("FUTU_OPEND_HOST", "127.0.0.1");
    }

    private static int getOpenDPort() {
        String hosts = DOTENV.get("FUTU_OPEND_HOSTS", "");
        if (hosts != null && !hosts.trim().isEmpty()) {
            String[] parts = hosts.split(",");
            if (parts.length > 0) {
                String[] hostParts = parts[0].trim().split(":");
                if (hostParts.length >= 2) {
                    try { return Integer.parseInt(hostParts[1]); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return getIntEnv("FUTU_OPEND_PORT", 11111);
    }

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
