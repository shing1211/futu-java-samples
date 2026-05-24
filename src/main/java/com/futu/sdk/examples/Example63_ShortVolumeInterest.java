package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetDailyShortVolume;
import com.futu.openapi.pb.QotGetShortInterest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.futu.sdk.examples.Helpers.*;

/**
 * Example 63: Short Volume & Interest
 *
 * Demonstrates:
 *   - getDailyShortVolume: daily short selling volume (HK)
 *   - getShortInterest: short interest positions (HK + US)
 *
 * Mirrors: examples/104_short_volume_interest/ from futu-python-samples
 */
public class Example63_ShortVolumeInterest implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example63_ShortVolumeInterest.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== Short Volume & Interest Demo ===");
        FTAPI.init();
        new Example63_ShortVolumeInterest().start();
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

            logger.info("\n--- getDailyShortVolume: HK.00700 ---");
            var c2s = QotGetDailyShortVolume.C2S.newBuilder().setSecurity(secHk).build();
            int ret = qot.getDailyShortVolume(QotGetDailyShortVolume.Request.newBuilder().setC2S(c2s).build());
            logger.info("getDailyShortVolume ret={}", ret);
            sleep(300);

            logger.info("\n--- getShortInterest: HK.00700 ---");
            var c2s2 = QotGetShortInterest.C2S.newBuilder().setSecurity(secHk).build();
            ret = qot.getShortInterest(QotGetShortInterest.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getShortInterest ret={}", ret);
            sleep(300);

            logger.info("\n--- getShortInterest: US.AAPL ---");
            var c2s3 = QotGetShortInterest.C2S.newBuilder().setSecurity(secUs).build();
            ret = qot.getShortInterest(QotGetShortInterest.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getShortInterest ret={}", ret);
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
    public void onReply_GetDailyShortVolume(com.futu.openapi.FTAPI_Conn client, int retCode,
                                              QotGetDailyShortVolume.Response rsp) {
        logger.info("  [Qot] onReply_GetDailyShortVolume: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getDailyShortVolume failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetShortInterest(com.futu.openapi.FTAPI_Conn client, int retCode,
                                            QotGetShortInterest.Response rsp) {
        logger.info("  [Qot] onReply_GetShortInterest: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getShortInterest failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}