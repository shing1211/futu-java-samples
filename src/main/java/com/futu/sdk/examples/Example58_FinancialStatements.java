package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetFinancialsStatements;
import com.futu.openapi.pb.QotGetFinancialsRevenueBreakdown;
import com.futu.openapi.pb.QotGetFinancialsEarningsPriceMove;
import com.futu.openapi.pb.QotGetFinancialsEarningsPriceHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.futu.sdk.examples.Helpers.*;

/**
 * Example 58: Financial Statements
 *
 * Demonstrates:
 *   - getFinancialsStatements: P&L, balance sheet, cash flow statements
 *   - getFinancialsRevenueBreakdown: revenue breakdown by segment
 *   - getFinancialsEarningsPriceMove: earnings calendar + price reaction
 *   - getFinancialsEarningsPriceHistory: historical earnings vs price movement
 *
 * Mirrors: examples/99_financial_statements/ from futu-python-samples
 */
public class Example58_FinancialStatements implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example58_FinancialStatements.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Financial Statements Demo ===");
        FTAPI.init();
        new Example58_FinancialStatements().start();
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

            logger.info("\n--- getFinancialsStatements: HK.00700 ---");
            var c2s = QotGetFinancialsStatements.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getFinancialsStatements(QotGetFinancialsStatements.Request.newBuilder().setC2S(c2s).build());
            logger.info("getFinancialsStatements ret={}", ret);
            sleep(500);

            logger.info("\n--- getFinancialsRevenueBreakdown: HK.00700 ---");
            var c2s2 = QotGetFinancialsRevenueBreakdown.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getFinancialsRevenueBreakdown(QotGetFinancialsRevenueBreakdown.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getFinancialsRevenueBreakdown ret={}", ret);
            sleep(500);

            logger.info("\n--- getFinancialsEarningsPriceMove: HK.00700 ---");
            var c2s3 = QotGetFinancialsEarningsPriceMove.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getFinancialsEarningsPriceMove(QotGetFinancialsEarningsPriceMove.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getFinancialsEarningsPriceMove ret={}", ret);
            sleep(500);

            logger.info("\n--- getFinancialsEarningsPriceHistory: HK.00700 ---");
            var c2s4 = QotGetFinancialsEarningsPriceHistory.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getFinancialsEarningsPriceHistory(QotGetFinancialsEarningsPriceHistory.Request.newBuilder().setC2S(c2s4).build());
            logger.info("getFinancialsEarningsPriceHistory ret={}", ret);
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
    public void onReply_GetFinancialsStatements(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                  QotGetFinancialsStatements.Response rsp) {
        logger.info("  [Qot] onReply_GetFinancialsStatements: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getFinancialsStatements failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetFinancialsRevenueBreakdown(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                        QotGetFinancialsRevenueBreakdown.Response rsp) {
        logger.info("  [Qot] onReply_GetFinancialsRevenueBreakdown: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getFinancialsRevenueBreakdown failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetFinancialsEarningsPriceMove(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                        QotGetFinancialsEarningsPriceMove.Response rsp) {
        logger.info("  [Qot] onReply_GetFinancialsEarningsPriceMove: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getFinancialsEarningsPriceMove failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetFinancialsEarningsPriceHistory(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                            QotGetFinancialsEarningsPriceHistory.Response rsp) {
        logger.info("  [Qot] onReply_GetFinancialsEarningsPriceHistory: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getFinancialsEarningsPriceHistory failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}