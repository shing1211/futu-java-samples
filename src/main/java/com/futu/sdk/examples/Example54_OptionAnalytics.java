package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOptionVolatility;
import com.futu.openapi.pb.QotGetOptionExerciseProbability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 54: Options Analytics — Implied Volatility & Exercise Probability
 *
 * Demonstrates:
 *   - getOptionVolatility: IV/HV ratio, volatility premium, analysis
 *   - getOptionExerciseProbability: probability of expiring in-the-money
 *
 * Mirrors: examples/58_options_greeks/ and examples/106_option_analytics/ from futu-python-samples
 */
public class Example54_OptionAnalytics implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example54_OptionAnalytics.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Options Analytics (IV + Exercise Probability) ===");
        FTAPI.init();
        new Example54_OptionAnalytics().start();
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

            logger.info("\n--- getOptionVolatility: HK.00700 ---");
            var c2s = QotGetOptionVolatility.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getOptionVolatility(QotGetOptionVolatility.Request.newBuilder().setC2S(c2s).build());
            logger.info("getOptionVolatility ret={}", ret);
            sleep(500);

            logger.info("\n--- getOptionExerciseProbability: HK.00700 ---");
            var c2s2 = QotGetOptionExerciseProbability.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getOptionExerciseProbability(QotGetOptionExerciseProbability.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getOptionExerciseProbability ret={}", ret);
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
    public void onReply_GetOptionVolatility(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetOptionVolatility.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionVolatility: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getOptionVolatility failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetOptionExerciseProbability(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                       QotGetOptionExerciseProbability.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionExerciseProbability: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getOptionExerciseProbability failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}