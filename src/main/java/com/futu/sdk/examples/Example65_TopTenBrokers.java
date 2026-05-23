package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetTopTenBuySellBrokers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 65: Top Ten Buy/Sell Brokers
 *
 * Demonstrates:
 *   - getTopTenBuySellBrokers: top brokers by volume (buy/sell side)
 *
 * Shows the broker queue concentration — who is actively buying vs selling.
 */
public class Example65_TopTenBrokers implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example65_TopTenBrokers.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Top Ten Buy/Sell Brokers ===");
        FTAPI.init();
        new Example65_TopTenBrokers().start();
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
            var secHk = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
            var secUs = QotCommon.Security.newBuilder().setMarket(2).setCode("AAPL").build();

            logger.info("\n--- getTopTenBuySellBrokers: HK.00700 ---");
            var c2s = QotGetTopTenBuySellBrokers.C2S.newBuilder()
                .setSecurity(secHk)
                .build();
            int ret = qot.getTopTenBuySellBrokers(QotGetTopTenBuySellBrokers.Request.newBuilder().setC2S(c2s).build());
            logger.info("getTopTenBuySellBrokers ret={}", ret);
            sleep(500);

            logger.info("\n--- getTopTenBuySellBrokers: US.AAPL ---");
            var c2s2 = QotGetTopTenBuySellBrokers.C2S.newBuilder()
                .setSecurity(secUs)
                .build();
            ret = qot.getTopTenBuySellBrokers(QotGetTopTenBuySellBrokers.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getTopTenBuySellBrokers ret={}", ret);
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
    public void onReply_GetTopTenBuySellBrokers(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                  QotGetTopTenBuySellBrokers.Response rsp) {
        logger.info("  [Qot] onReply_GetTopTenBuySellBrokers: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getTopTenBuySellBrokers failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}