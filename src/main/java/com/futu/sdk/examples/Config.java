package com.futu.sdk.examples;

import io.github.cdimascio.dotenv.Dotenv;

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

    // Security firm (e.g., "FUTU HK", "MOOMOO US", "MOOMOO SG")
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
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static boolean getBoolEnv(String key, boolean defaultValue) {
        String value = DOTENV.get(key);
        if (value != null && !value.isBlank()) {
            return "true".equalsIgnoreCase(value.trim());
        }
        return defaultValue;
    }

    public static String[] getHosts() {
        String hosts = DOTENV.get("FUTU_OPEND_HOSTS", "");
        if (hosts != null && !hosts.trim().isEmpty()) {
            return hosts.split(",");
        }
        return new String[]{ FUTU_OPEND_HOST + ":" + FUTU_OPEND_PORT + ":true" };
    }
}