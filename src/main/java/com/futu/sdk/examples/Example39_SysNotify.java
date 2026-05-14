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
 * Example 39: System Notification Push (SysNotify via FTSPI_Conn)
 *
 * Demonstrates:
 *   - SysNotify push via FTSPI_Conn onNotify callback
 *   - System notifications include connection events, market events, risk alerts
 *   - Full notification field logging
 *
 * Mirrors: examples/39_push_sysnotify/main.py from futu-python-samples
 */
public class Example39_SysNotify implements FTSPI_Conn, FTSPI_Trd {

    private static final Logger logger = LoggerFactory.getLogger(Example39_SysNotify.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    public static void main(String[] args) {
        logger.info("=== System Notification Push Demo ===");
        FTAPI.init();
        Example39_SysNotify demo = new Example39_SysNotify();
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

        // Unlock trade to enable notification pushes
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
        } else {
            logger.info("FUTU_TRADE_PASSWORD not set — skipping unlock.");
        }

        // Listen for system notifications for 15 seconds
        logger.info("\nListening for system notifications (15s)...");
        sleep(15000);

        trd.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn — connection + system notification callbacks
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

    /**
     * onNotify is the SysNotify push handler — called for every system notification.
     * Notification type values (from Futu proto):
     *   1 = System heartbeat
     *   2 = Market state change
     *   3 = Trade connect state
     *   4 = Order update (push)
     *   5 = Deal update (push)
     *   6 = API usage quota warning
     *   7 = Market close notification
     *   8 = Trade error
     */
    public void onNotify(com.futu.openapi.FTAPI_Conn client, long errCode, String desc,
                         int notifyType, byte[] reqGuid) {
        logger.info("  [Conn] onNotify: errCode={} desc={} notifyType={} reqGuid={}",
            errCode, desc, notifyType, reqGuid == null ? "null" : new String(reqGuid));

        // Log every known notification sub-type label
        String typeLabel = switch (notifyType) {
            case 1 -> "Heartbeat";
            case 2 -> "MarketState";
            case 3 -> "TrdConnectState";
            case 4 -> "OrderUpdatePush";
            case 5 -> "DealUpdatePush";
            case 6 -> "QuotaWarning";
            case 7 -> "MarketClose";
            case 8 -> "TrdError";
            default -> "NotifyType(" + notifyType + ")";
        };
        logger.info("    notifyTypeLabel={}", typeLabel);
        if (errCode != 0) {
            logger.error("    notification error: errCode={} desc={}", errCode, desc);
        }
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd — trade reply callbacks (satisfy interface contract)
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
        // Not called in this demo — satisfy FTSPI_Trd interface
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