package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetResearchAnalystConsensus;
import com.futu.openapi.pb.QotGetResearchRatingSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 59: Research Ratings & Analyst Consensus
 *
 * Demonstrates:
 *   - getResearchAnalystConsensus: target price, rating breakdown
 *   - getResearchRatingSummary: analyst/institutional rating summary
 *
 * Mirrors: examples/100_research_ratings/ from futu-python-samples
 */
public class Example59_ResearchRatings implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example59_ResearchRatings.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== Research Ratings & Analyst Consensus ===");
        FTAPI.init();
        new Example59_ResearchRatings().start();
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
            var secUs = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build();

            logger.info("\n--- getResearchAnalystConsensus: HK.00700 ---");
            var c2s = QotGetResearchAnalystConsensus.C2S.newBuilder().setSecurity(secHk).build();
            int ret = qot.getResearchAnalystConsensus(QotGetResearchAnalystConsensus.Request.newBuilder().setC2S(c2s).build());
            logger.info("getResearchAnalystConsensus ret={}", ret);
            sleep(500);

            logger.info("\n--- getResearchRatingSummary: HK.00700 ---");
            var c2s2 = QotGetResearchRatingSummary.C2S.newBuilder().setSecurity(secHk).build();
            ret = qot.getResearchRatingSummary(QotGetResearchRatingSummary.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getResearchRatingSummary ret={}", ret);
            sleep(500);

            logger.info("\n--- getResearchAnalystConsensus: US.AAPL ---");
            var c2s3 = QotGetResearchAnalystConsensus.C2S.newBuilder().setSecurity(secUs).build();
            ret = qot.getResearchAnalystConsensus(QotGetResearchAnalystConsensus.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getResearchAnalystConsensus ret={}", ret);
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
    public void onReply_GetResearchAnalystConsensus(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                      QotGetResearchAnalystConsensus.Response rsp) {
        logger.info("  [Qot] onReply_GetResearchAnalystConsensus: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getResearchAnalystConsensus failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetResearchRatingSummary(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                   QotGetResearchRatingSummary.Response rsp) {
        logger.info("  [Qot] onReply_GetResearchRatingSummary: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getResearchRatingSummary failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}