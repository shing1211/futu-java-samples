package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetPlateSecurity;
import com.futu.openapi.pb.QotGetPlateSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 13: Plate List & Stocks in Plate
 *
 * Demonstrates:
 *   - getPlateSet: all industry/sector plates for a market
 *   - getPlateSecurity: stocks belonging to a specific plate
 *   - PlateSetType: ALL, INDUSTRY, CONCEPT, etc.
 *
 * Mirrors: examples/13_plate/main.py from futu-python-samples
 */
public class Example13_Plate implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example13_Plate.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;

    // PlateSetType: All=0, Industry=1, Region=2, Concept=3, Other=4
    private static final int PLATE_SET_ALL = 0;
    private static final int PLATE_SET_INDUSTRY = 1;
    private static final int PLATE_SET_CONCEPT = 3;

    public static void main(String[] args) {
        logger.info("=== Plate (Sector) List Demo ===");
        FTAPI.init();
        Example13_Plate demo = new Example13_Plate();
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

        // ── getPlateSet: CONCEPT plates for HK ─────────────────────────
        logger.info("\n=== getPlateSet: HK (CONCEPT) ===");
        var c2sCon = QotGetPlateSet.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setPlateSetType(PLATE_SET_CONCEPT)
            .build();
        int ret = qot.getPlateSet(QotGetPlateSet.Request.newBuilder().setC2S(c2sCon).build());
        logger.info("getPlateSet(CONCEPT) ret={}", ret);

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
    public void onReply_GetPlateSet(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    QotGetPlateSet.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSet: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getPlateInfoListCount();
            logger.info("    Total plates: {}", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                var p = s2c.getPlateInfoList(i);
                logger.info("      plate[{}] code={} name={} type={}",
                    i, p.getPlate().getCode(), p.getName(), p.getPlateType());
            }
            if (count > 0) {
                // Pick one plate and query its stocks
                var firstPlate = s2c.getPlateInfoList(0);
                logger.info("\n    Fetching stocks for plate: {} ({})",
                    firstPlate.getPlate().getCode(), firstPlate.getName());
                var psC2s = QotGetPlateSecurity.C2S.newBuilder()
                    .setPlate(firstPlate.getPlate())
                    .build();
                int ret = qot.getPlateSecurity(QotGetPlateSecurity.Request.newBuilder().setC2S(psC2s).build());
                logger.info("getPlateSecurity ret={}", ret);
            }
        } else {
            logger.warn("    getPlateSet failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetPlateSecurity(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotGetPlateSecurity.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSecurity: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Stocks in plate ({}):", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                var s = s2c.getStaticInfoList(i);
                if (!s.hasBasic()) continue;
                logger.info("      [{}] code={} name={}", i,
                    s.getBasic().getSecurity().getCode(), s.getBasic().getName());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
        } else {
            logger.warn("    getPlateSecurity failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}