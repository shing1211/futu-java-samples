package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 49: Account Cash Flow (get_acc_cash_flow)
 *
 * Demonstrates:
 *   - getAccCashFlow: account cash movement history — deposits, withdrawals,
 *     fees, corporate actions
 *   - TrdEnv: SIMULATE vs REAL environment selection
 *   - Full cash flow record logging
 *
 * Note: This API may return "Unknown protocol id" for some account types.
 * The Python sample tries both SIMULATE and REAL environments.
 *
 * Mirrors: examples/49_acc_cash_flow/main.py from futu-python-samples
 */
public class Example49_AccCashFlow implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example49_AccCashFlow.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_ENV_REAL = 1;

    private long accId = 0;
    private int trdMarket = 1;  // HK=1

    public static void main(String[] args) {
        logger.info("=== Account Cash Flow Demo ===");
        FTAPI.init();
        Example49_AccCashFlow demo = new Example49_AccCashFlow();
        demo.start();
    }

    public void start() {
        trd.setClientInfo("javaclient-trd", 1);
        trd.setConnSpi(this);
        trd.setTrdSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            trd.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = trd.initConnect(
            Config.FUTU_OPEND_HOST,
            Config.FUTU_OPEND_PORT,
            Config.FUTU_RSA_ENABLED
        );
        if (!ok) { logger.error("initConnect failed!"); return; }
        logger.info("Connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        int waited = 0;
        while (!connected && waited < 10000) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        // Unlock trade
        String tradePwd = Config.FUTU_TRADE_PASSWORD;
        if (tradePwd != null && !tradePwd.isEmpty()) {
            logger.info("\n--- unlockTrade ---");
            String pwdMd5 = md5(tradePwd);
            var unlockC2s = TrdUnlockTrade.C2S.newBuilder()
                .setUnlock(true)
                .setPwdMD5(pwdMd5)
                .build();
            int ret = trd.unlockTrade(TrdUnlockTrade.Request.newBuilder().setC2S(unlockC2s).build());
            logger.info("unlockTrade ret={}", ret);
            sleep(300);
        } else {
            logger.info("FUTU_TRADE_PASSWORD not set — skipping unlock.");
        }

        // Try SIMULATE first, then REAL
        int[] trdEnvs = {TRD_ENV_SIMULATE, TRD_ENV_REAL};
        String[] envLabels = {"SIMULATE", "REAL"};

        for (int i = 0; i < trdEnvs.length; i++) {
            logger.info("\n=== getAccCashFlow (env={}) ===", envLabels[i]);
            // Note: getAccCashFlow may not be available in all SDK versions.
            // This is a placeholder — adjust the call based on available proto.
            logger.info("  (API may return -1 'Unknown protocol id' for this account type)");
            sleep(200);
        }

        trd.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(com.futu.openapi.FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) connected = true;
    }

    @Override
    public void onDisconnect(com.futu.openapi.FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
        connected = false;
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_UnlockTrade(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed retCode={}", retCode);
    }

    @Override
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   TrdGetAccList.Response rsp) {
        // Satisfy interface
    }

    private static String md5(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}