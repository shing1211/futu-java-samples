package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetValuationDetail;
import com.futu.openapi.pb.QotGetValuationPlateStockList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 64: Valuation Detail & Plate-based Valuation
 *
 * Demonstrates:
 *   - getValuationDetail: full valuation metrics (PE, PB, PCF, PS, etc.)
 *   - getValuationPlateStockList: stocks in a valuation plate
 *
 * Mirrors: examples/105_valuation_screener/ from futu-python-samples
 */
public class Example64_ValuationScreener implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example64_ValuationScreener.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;
    private static final int MARKET_SH = 4;

    public static void main(String[] args) {
        logger.info("=== Valuation Detail & Plate Valuation Demo ===");
        FTAPI.init();
        new Example64_ValuationScreener().start();
    }

    public void start() {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);
        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }
        boolean ok = qot.initConnect(Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RESA_ENABLED);
        if (!ok) { logger.error("initConnect failed"); return; }
        logger.info("Connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        int waited = 0;
        while (!connected && waited < 8000) { sleep(50); waited += 50; }
        if (!connected) { logger.error("Connection timed out"); return; }

        try {
            var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

            logger.info("\n--- getValuationDetail: HK.00700 ---");
            var c2s = QotGetValuationDetail.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getValuationDetail(QotGetValuationDetail.Request.newBuilder().setC2S(c2s).build());
            logger.info("getValuationDetail ret={}", ret);
            sleep(500);

            logger.info("\n--- getValuationPlateStockList: SH market, low PE plate ---");
            var c2s2 = QotGetValuationPlateStockList.C2S.newBuilder()
                .setPlateType(1)
                .build();
            ret = qot.getValuationPlateStockList(QotGetValuationPlateStockList.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getValuationPlateStockList ret={}", ret);
            sleep(500);

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
    public void onReply_GetValuationDetail(com.futu.openapi.FTAPI_Conn client, int retCode,
                                              QotGetValuationDetail.Response rsp) {
        logger.info("  [Qot] onReply_GetValuationDetail: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getValuationDetail failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetValuationPlateStockList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                      QotGetValuationPlateStockList.Response rsp) {
        logger.info("  [Qot] onReply_GetValuationPlateStockList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getValuationPlateStockList failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}