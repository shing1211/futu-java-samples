package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetShareholdersOverview;
import com.futu.openapi.pb.QotGetShareholdersHoldingChanges;
import com.futu.openapi.pb.QotGetShareholdersInstitutional;
import com.futu.openapi.pb.QotGetInsiderHolderList;
import com.futu.openapi.pb.QotGetInsiderTradeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 61: Shareholders, Institutional & Insider Data
 *
 * Demonstrates:
 *   - getShareholdersOverview: major holder breakdown, holding periods
 *   - getShareholdersHoldingChanges: top holders' stake changes
 *   - getShareholdersInstitutional: institutional holders
 *   - getInsiderHolderList: insider holdings
 *   - getInsiderTradeList: insider buy/sell transactions
 *
 * Mirrors: examples/102_shareholders_insiders/ from futu-python-samples
 */
public class Example61_ShareholdersInsiders implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example61_ShareholdersInsiders.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Shareholders & Insider Data Demo ===");
        FTAPI.init();
        new Example61_ShareholdersInsiders().start();
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

            logger.info("\n--- getShareholdersOverview: HK.00700 ---");
            var c2s = QotGetShareholdersOverview.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getShareholdersOverview(QotGetShareholdersOverview.Request.newBuilder().setC2S(c2s).build());
            logger.info("getShareholdersOverview ret={}", ret);
            sleep(300);

            logger.info("\n--- getShareholdersHoldingChanges: HK.00700 ---");
            var c2s2 = QotGetShareholdersHoldingChanges.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getShareholdersHoldingChanges(QotGetShareholdersHoldingChanges.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getShareholdersHoldingChanges ret={}", ret);
            sleep(300);

            logger.info("\n--- getShareholdersInstitutional: HK.00700 ---");
            var c2s3 = QotGetShareholdersInstitutional.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getShareholdersInstitutional(QotGetShareholdersInstitutional.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getShareholdersInstitutional ret={}", ret);
            sleep(300);

            logger.info("\n--- getInsiderHolderList: HK.00700 ---");
            var c2s4 = QotGetInsiderHolderList.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getInsiderHolderList(QotGetInsiderHolderList.Request.newBuilder().setC2S(c2s4).build());
            logger.info("getInsiderHolderList ret={}", ret);
            sleep(300);

            logger.info("\n--- getInsiderTradeList: HK.00700 ---");
            var c2s5 = QotGetInsiderTradeList.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getInsiderTradeList(QotGetInsiderTradeList.Request.newBuilder().setC2S(c2s5).build());
            logger.info("getInsiderTradeList ret={}", ret);
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
    public void onReply_GetShareholdersOverview(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                  QotGetShareholdersOverview.Response rsp) {
        logger.info("  [Qot] onReply_GetShareholdersOverview: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getShareholdersOverview failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetShareholdersHoldingChanges(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                         QotGetShareholdersHoldingChanges.Response rsp) {
        logger.info("  [Qot] onReply_GetShareholdersHoldingChanges: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getShareholdersHoldingChanges failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetShareholdersInstitutional(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                       QotGetShareholdersInstitutional.Response rsp) {
        logger.info("  [Qot] onReply_GetShareholdersInstitutional: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getShareholdersInstitutional failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetInsiderHolderList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                QotGetInsiderHolderList.Response rsp) {
        logger.info("  [Qot] onReply_GetInsiderHolderList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getInsiderHolderList failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetInsiderTradeList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                               QotGetInsiderTradeList.Response rsp) {
        logger.info("  [Qot] onReply_GetInsiderTradeList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getInsiderTradeList failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}