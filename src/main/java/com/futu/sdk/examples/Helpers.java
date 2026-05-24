package com.futu.sdk.examples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Helpers {

    // ── Market Constants (from QotCommon.QotMarket) ──────────────
    public static final int MARKET_HK = 1;
    public static final int MARKET_US = 11;
    public static final int MARKET_SH = 4;
    public static final int MARKET_SZ = 5;
    public static final int MARKET_HK_FUTURE = 7;

    // ── Trading Constants ────────────────────────────────────────
    public static final int TRD_ENV_SIMULATE = 1;
    public static final int TRD_ENV_REAL = 2;
    public static final int TRD_MARKET_HK = 1;
    public static final int TRD_MARKET_US = 2;

    // ── Connection Timeout ───────────────────────────────────────
    public static final long CONNECTION_TIMEOUT_MS = 8000;

    // ── Sleep ───────────────────────────────────────────────────
    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── MD5 (with UTF-8 charset) ───────────────────────────────
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    // ── Number Formatting ───────────────────────────────────────
    public static String f0(double v) { return String.format("%.0f", v); }
    public static String f2(double v) { return String.format("%.2f", v); }
    public static String f3(double v) { return String.format("%.3f", v); }
    public static String f4(double v) { return String.format("%.4f", v); }
    public static String f6(double v) { return String.format("%.6f", v); }
    public static String pct(double v) { return String.format("%.2f%%", v); }
    public static String signed2(double v) { return String.format("%+.2f", v); }
    public static String signed4(double v) { return String.format("%+.4f", v); }

    // ── Order Status Labels ─────────────────────────────────────
    public static String orderStatusLabel(int status) {
        return switch (status) {
            case 0 -> "SUBMITTING";
            case 1 -> "SUBMITTED";
            case 2 -> "SUBMIT_FAILED";
            case 3 -> "QUEUED";
            case 4 -> "PART_FILLED";
            case 5 -> "FILLED";
            case 6 -> "CANCELLED";
            case 7 -> "CANCELLING";
            case 8 -> "SUBMITTED_PARTIAL";
            case 9 -> "CANCEL_PARTIAL";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    // ── Trading Side Labels ─────────────────────────────────────
    public static String trdSideLabel(int side) {
        return switch (side) {
            case 1 -> "BUY";
            case 2 -> "SELL";
            default -> "SIDE(" + side + ")";
        };
    }

    // ── Turnover Formatting ─────────────────────────────────────
    public static String formatTurnover(double t) {
        if (t >= 1_000_000_000) return String.format("%.2fB", t / 1_000_000_000);
        if (t >= 1_000_000) return String.format("%.2fM", t / 1_000_000);
        if (t >= 1_000) return String.format("%.2fK", t / 1_000);
        return String.format("%.2f", t);
    }

    private Helpers() {}
}
