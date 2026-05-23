package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCorporateActionsDividends;
import com.futu.openapi.pb.QotGetCorporateActionsBuybacks;
import com.futu.openapi.pb.QotGetCorporateActionsStockSplits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 62: Corporate Actions
 *
 * Demonstrates:
 *   - getCorporateActionsDividends: dividend history per share
 *   - getCorporateActionsBuybacks: share buyback programs
 *   - getCorporateActionsStockSplits: stock splits
 *
 * Mirrors: examples/103_corporate_actions/ from futu-python-samples
 */
public class Example62_CorporateActions implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example62_CorporateActions.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Corporate Actions Demo ===");
        FTAPI.init();
        new Example62_CorporateActions().start();
    }

    public void start() {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);
        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }
        boolean ok = qot.initConnect(Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RSA_ENABLED);
        if (!ok) { logger.error("initConnect failed"); return; }
        logger.info("Connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        int waited = 0;
        while (!connected && waited < 8000) { sleep(50); waited += 50; }
        if (!connected) { logger.error("Connection timed out"); return; }

        try {
            var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

            logger.info("\n--- getCorporateActionsDividends: HK.00700 ---");
            var c2s = QotGetCorporateActionsDividends.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getCorporateActionsDividends(QotGetCorporateActionsDividends.Request.newBuilder().setC2S(c2s).build());
            logger.info("getCorporateActionsDividends ret={}", ret);
            sleep(300);

            logger.info("\n--- getCorporateActionsBuybacks: HK.00700 ---");
            var c2s2 = QotGetCorporateActionsBuybacks.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getCorporateActionsBuybacks(QotGetCorporateActionsBuybacks.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getCorporateActionsBuybacks ret={}", ret);
            sleep(300);

            logger.info("\n--- getCorporateActionsStockSplits: HK.00700 ---");
            var c2s3 = QotGetCorporateActionsStockSplits.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getCorporateActionsStockSplits(QotGetCorporateActionsStockSplits.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getCorporateActionsStockSplits ret={}", ret);
            sleep(300);

        } finally {
            qot.close();
        }
        logger.info("Done.");
    }

    @Override public void onInitConnect(com.futu.openapi.FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) connected = true;
    }
    @Override public void onDisconnect(com.futu.openapi.FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
        connected = false;
    }

    @Override
    public void onReply_GetCorporateActionsDividends(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                       QotGetCorporateActionsDividends.Response rsp) {
        logger.info("  [Qot] onReply_GetCorporateActionsDividends: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCorporateActionsDividends failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCorporateActionsBuybacks(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                       QotGetCorporateActionsBuybacks.Response rsp) {
        logger.info("  [Qot] onReply_GetCorporateActionsBuybacks: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCorporateActionsBuybacks failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCorporateActionsStockSplits(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                           QotGetCorporateActionsStockSplits.Response rsp) {
        logger.info("  [Qot] onReply_GetCorporateActionsStockSplits: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCorporateActionsStockSplits failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}