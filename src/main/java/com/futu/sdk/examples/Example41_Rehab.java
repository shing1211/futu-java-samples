package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotRequestRehab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 41: Rehabilitation Data (get_rehab — dividend/split adjustment)
 *
 * Demonstrates:
 *   - getRehab: dividend, split, and other corporate action adjustment records
 *   - Used for adjusting historical prices (forward/backward adjust)
 *   - All returned fields logged per record
 *
 * Note: get_rehab is already covered in Example31_Misc alongside other APIs.
 * This dedicated example focuses purely on the rehab data pipeline.
 *
 * Mirrors: examples/41_rehab/main.py from futu-python-samples
 */
public class Example41_Rehab implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example41_Rehab.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== Rehab (Corporate Actions) Demo ===");
        FTAPI.init();
        Example41_Rehab demo = new Example41_Rehab();
        demo.start();
    }

    public void start() {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = qot.initConnect(
            Config.FUTU_OPEND_HOST,
            Config.FUTU_OPEND_PORT,
            Config.FUTU_RSA_ENABLED
        );
        if (!ok) { logger.error("initConnect failed!"); return; }
        logger.info("Connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        int waited = 0;
        while (!connected && waited < 8000) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        // ── getRehab: HK.00700 (Tencent) ─────────────────────────────────
        logger.info("\n=== getRehab: HK.00700 ===");
        var secHk = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var rehabHk = QotRequestRehab.C2S.newBuilder()
            .setSecurity(secHk)
            .build();
        int ret = qot.requestRehab(QotRequestRehab.Request.newBuilder().setC2S(rehabHk).build());
        logger.info("requestRehab(HK.00700) ret={}", ret);
        sleep(300);

        // ── getRehab: US.AAPL (Apple) ───────────────────────────────────
        logger.info("\n=== getRehab: US.AAPL ===");
        var secUs = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build();
        var rehabUs = QotRequestRehab.C2S.newBuilder()
            .setSecurity(secUs)
            .build();
        ret = qot.requestRehab(QotRequestRehab.Request.newBuilder().setC2S(rehabUs).build());
        logger.info("requestRehab(US.AAPL) ret={}", ret);

        qot.close();
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
    // FTSPI_Qot
    // -------------------------------------------------------------------------

    @Override
    public void onReply_RequestRehab(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      QotRequestRehab.Response rsp) {
        logger.info("  [Qot] onReply_RequestRehab: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getRehabListCount();
            if (count == 0) {
                logger.info("    No rehab records returned");
            } else {
                logger.info("    Rehab records ({}):", count);
                for (int i = 0; i < Math.min(count, 20); i++) {
                    var r = s2c.getRehabList(i);
                    logger.info("      [{}] time={} dividend={} splitBase={}/{} fwdFactorA={} bwdFactorA={}",
                        i, r.getTime(), r.getDividend(), r.getSplitBase(), r.getSplitErt(),
                        r.getFwdFactorA(), r.getBwdFactorA());
                    logger.info("        bonusBase={} bonusErt={} addBase={} allotBase={} allotErt={}",
                        r.getBonusBase(), r.getBonusErt(), r.getAddBase(), r.getAllotBase(), r.getAllotErt());
                    logger.info("        forwardFactor={} backwardFactor={}",
                        r.getFwdFactorA(), r.getBwdFactorA());
                }
                if (count > 20) logger.info("      ... ({} more)", count - 20);
            }
        } else {
            logger.warn("    requestRehab failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}