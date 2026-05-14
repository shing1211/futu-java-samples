package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 51: Account List (get_acc_list)
 *
 * Demonstrates:
 *   - getAccList: list all sub-accounts under the user
 *   - Each account: accId, trdEnv, accType, trdMarketAuthList, securityFirm
 *   - Suitable for multi-account setups (family accounts, firms with multiple traders)
 *
 * Note: get_acc_list is already covered in Example05_QuoteTrade and Example30_UserInfo.
 * This dedicated example focuses purely on account discovery and field logging.
 *
 * Mirrors: examples/51_acc_list/main.py from futu-python-samples
 */
public class Example51_AccList implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example51_AccList.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    public static void main(String[] args) {
        logger.info("=== Account List Demo ===");
        FTAPI.init();
        Example51_AccList demo = new Example51_AccList();
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

        // ── getAccList ─────────────────────────────────────────────────
        logger.info("\n=== getAccList ===");
        var accC2s = TrdGetAccList.C2S.newBuilder()
            .setNeedGeneralSecAccount(false)
            .build();
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accC2s).build());
        logger.info("getAccList ret={}", ret);

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
            if (count == 0) {
                logger.info("    No accounts returned.");
            } else {
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
                    // TrdMarket auth list
                    int marketCount = acc.getTrdMarketAuthListCount();
                    var markets = new String[marketCount];
                    for (int j = 0; j < marketCount; j++) {
                        markets[j] = trdMarketLabel(acc.getTrdMarketAuthList(j));
                    }
                    logger.info("        trdMarketAuth=[{}] securityFirm={} cardNum={} uniCardNum={}",
                        String.join(",", markets), acc.getSecurityFirm(),
                        acc.getCardNum(), acc.getUniCardNum());
                }
            }
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
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