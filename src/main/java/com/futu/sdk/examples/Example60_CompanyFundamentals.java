package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCompanyProfile;
import com.futu.openapi.pb.QotGetCompanyExecutives;
import com.futu.openapi.pb.QotGetCompanyOperationalEfficiency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.futu.sdk.examples.Helpers.*;

/**
 * Example 60: Company Fundamentals
 *
 * Demonstrates:
 *   - getCompanyProfile: company description, tags, industry
 *   - getCompanyExecutives: CEO, directors, key executives
 *   - getCompanyOperationalEfficiency: revenue/profit per employee metrics
 *
 * Mirrors: examples/101_company_fundamentals/ from futu-python-samples
 */
public class Example60_CompanyFundamentals implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example60_CompanyFundamentals.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Company Fundamentals Demo ===");
        FTAPI.init();
        new Example60_CompanyFundamentals().start();
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

            logger.info("\n--- getCompanyProfile: HK.00700 ---");
            var c2s = QotGetCompanyProfile.C2S.newBuilder().setSecurity(sec).build();
            int ret = qot.getCompanyProfile(QotGetCompanyProfile.Request.newBuilder().setC2S(c2s).build());
            logger.info("getCompanyProfile ret={}", ret);
            sleep(500);

            logger.info("\n--- getCompanyExecutives: HK.00700 ---");
            var c2s2 = QotGetCompanyExecutives.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getCompanyExecutives(QotGetCompanyExecutives.Request.newBuilder().setC2S(c2s2).build());
            logger.info("getCompanyExecutives ret={}", ret);
            sleep(500);

            logger.info("\n--- getCompanyOperationalEfficiency: HK.00700 ---");
            var c2s3 = QotGetCompanyOperationalEfficiency.C2S.newBuilder().setSecurity(sec).build();
            ret = qot.getCompanyOperationalEfficiency(QotGetCompanyOperationalEfficiency.Request.newBuilder().setC2S(c2s3).build());
            logger.info("getCompanyOperationalEfficiency ret={}", ret);
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
    public void onReply_GetCompanyProfile(com.futu.openapi.FTAPI_Conn client, int retCode,
                                           QotGetCompanyProfile.Response rsp) {
        logger.info("  [Qot] onReply_GetCompanyProfile: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCompanyProfile failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCompanyExecutives(com.futu.openapi.FTAPI_Conn client, int retCode,
                                               QotGetCompanyExecutives.Response rsp) {
        logger.info("  [Qot] onReply_GetCompanyExecutives: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCompanyExecutives failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCompanyOperationalEfficiency(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                        QotGetCompanyOperationalEfficiency.Response rsp) {
        logger.info("  [Qot] onReply_GetCompanyOperationalEfficiency: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    s2c={}", s2c);
        } else {
            logger.warn("    getCompanyOperationalEfficiency failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}