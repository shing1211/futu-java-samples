package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdSubAccPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 30: Account & User Info
 *
 * Demonstrates:
 *   - getAccList: list all trading accounts linked to the user
 *   - TrdAcc fields: getAccId/getTrdEnv/getTrdMarketAuthList/getAccType/getSecurityFirm/getSimAccType
 *   - subAccPush: subscribe to account data push (orders, fills)
 *
 * Note: Requires unlocked trading context (password or saved session).
 * This example focuses on account discovery; trading operations require
 * unlock_trade to be called first.
 *
 * Mirrors: examples/30_user_info/main.py from futu-python-samples
 */
public class Example30_UserInfo implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example30_UserInfo.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;
    private long firstAccId = 0;  // save for subAccPush

    public static void main(String[] args) {
        logger.info("=== Account & User Info Demo ===");
        FTAPI.init();
        Example30_UserInfo demo = new Example30_UserInfo();
        demo.start();
    }

    public void start() {
        trd.setClientInfo("javaclient_trd", 1);
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
        while (!connected && waited < 8000) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        // ── getAccList ─────────────────────────────────────────────────
        logger.info("\n=== getAccList ===");
        var accC2s = TrdGetAccList.C2S.newBuilder()
            .setNeedGeneralSecAccount(false)
            .build();
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accC2s).build());
        logger.info("getAccList ret={}", ret);
        sleep(300);

        // ── subAccPush: subscribe to first account data ───────────────
        if (firstAccId > 0) {
            logger.info("\n=== subAccPush (accID={}) ===", firstAccId);
            var subC2s = TrdSubAccPush.C2S.newBuilder()
                .addAccIDList(firstAccId)
                .build();
            ret = trd.subAccPush(TrdSubAccPush.Request.newBuilder().setC2S(subC2s).build());
            logger.info("subAccPush ret={}", ret);
        } else {
            logger.info("\n  No accID available for subAccPush (need unlock)");
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
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getAccListCount();
            logger.info("    Accounts ({}):", count);
            for (int i = 0; i < count; i++) {
                var acc = s2c.getAccList(i);
                int env = acc.getTrdEnv();
                String envLabel = switch (env) {
                    case 0 -> "SIMULATE";
                    case 1 -> "REAL";
                    default -> "ENV(" + env + ")";
                };
                logger.info("      acc[{}] accId={} env={} accType={} simAccType={}",
                    i, acc.getAccID(), envLabel, acc.getAccType(), acc.getSimAccType());
                int marketCount = acc.getTrdMarketAuthListCount();
                var markets = new String[marketCount];
                for (int j = 0; j < marketCount; j++) {
                    markets[j] = trdMarketLabel(acc.getTrdMarketAuthList(j));
                }
                logger.info("        trdMarketAuth=[{}] securityFirm={} cardNum={} uniCardNum={}",
                    String.join(",", markets), acc.getSecurityFirm(), acc.getCardNum(), acc.getUniCardNum());
                if (i == 0) firstAccId = acc.getAccID();
            }
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_SubAccPush(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdSubAccPush.Response rsp) {
        logger.info("  [Trd] onReply_SubAccPush: retCode={}", retCode);
        if (retCode != 0) logger.warn("    subAccPush failed retCode={}", retCode);
    }

    @Override
    public void onPush_UpdateOrder(com.futu.openapi.FTAPI_Conn client, com.futu.openapi.pb.TrdUpdateOrder.Response rsp) {
        logger.info("  [Trd] onPush_UpdateOrder");
        // Real-time order update push
    }

    @Override
    public void onPush_UpdateOrderFill(com.futu.openapi.FTAPI_Conn client, com.futu.openapi.pb.TrdUpdateOrderFill.Response rsp) {
        logger.info("  [Trd] onPush_UpdateOrderFill");
        // Real-time fill/execution push
    }

    private static String trdMarketLabel(int market) {
        return switch (market) {
            case 1 -> "HK";
            case 2 -> "US";
            case 4 -> "SH";
            case 5 -> "SZ";
            case 6 -> "SG";
            case 7 -> "JP";
            default -> "MKT(" + market + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}