package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOwnerPlate;
import com.futu.openapi.pb.QotGetReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 17: Owner Plate & Reference Stock
 *
 * Demonstrates:
 *   - getOwnerPlate: get industry/concept plates a stock belongs to
 *   - getReference: warrant/bull-bear reference stocks
 *   - SecurityReferenceType: WARRANT=1, BULL_BEAR=2
 *
 * Mirrors: examples/17_owner_plate/main.py from futu-python-samples
 */
public class Example17_OwnerPlate implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example17_OwnerPlate.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // SecurityReferenceType: Warrant=1, BullBear=2
    private static final int REF_TYPE_WARRANT = 1;
    private static final int REF_TYPE_BULL_BEAR = 2;

    public static void main(String[] args) {
        logger.info("=== Owner Plate & Reference Stock Demo ===");
        FTAPI.init();
        Example17_OwnerPlate demo = new Example17_OwnerPlate();
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

        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

        // ── getOwnerPlate ─────────────────────────────────────────────
        logger.info("\n=== getOwnerPlate: HK.00700 ===");
        var ownerC2s = QotGetOwnerPlate.C2S.newBuilder()
            .addSecurityList(sec)
            .build();
        int ret = qot.getOwnerPlate(QotGetOwnerPlate.Request.newBuilder().setC2S(ownerC2s).build());
        logger.info("getOwnerPlate ret={}", ret);
        sleep(300);

        // ── getReference: WARRANT ──────────────────────────────────────
        logger.info("\n=== getReference: HK.00700 [WARRANT] ===");
        var refWarrantC2s = QotGetReference.C2S.newBuilder()
            .setSecurity(sec)
            .setReferenceType(REF_TYPE_WARRANT)
            .build();
        ret = qot.getReference(QotGetReference.Request.newBuilder().setC2S(refWarrantC2s).build());
        logger.info("getReference(WARRANT) ret={}", ret);
        sleep(300);

        // ── getReference: BULL_BEAR ────────────────────────────────────
        logger.info("\n=== getReference: HK.00700 [BULL_BEAR] ===");
        var refBullC2s = QotGetReference.C2S.newBuilder()
            .setSecurity(sec)
            .setReferenceType(REF_TYPE_BULL_BEAR)
            .build();
        ret = qot.getReference(QotGetReference.Request.newBuilder().setC2S(refBullC2s).build());
        logger.info("getReference(BULL_BEAR) ret={}", ret);

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
    public void onReply_GetOwnerPlate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      QotGetOwnerPlate.Response rsp) {
        logger.info("  [Qot] onReply_GetOwnerPlate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOwnerPlateListCount();
            logger.info("    Plates ({}):", count);
            for (int i = 0; i < count; i++) {
                var item = s2c.getOwnerPlateList(i);
                var sec = item.getSecurity();
                int plateCount = item.getPlateInfoListCount();
                logger.info("      security={} name={} plateCount={}", sec.getCode(), item.getName(), plateCount);
                for (int j = 0; j < plateCount; j++) {
                    var p = item.getPlateInfoList(j);
                    logger.info("        plate[{}] code={} name={} type={}",
                        j, p.getPlate().getCode(), p.getName(), p.getPlateType());
                }
            }
        } else {
            logger.warn("    getOwnerPlate failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetReference(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    QotGetReference.Response rsp) {
        logger.info("  [Qot] onReply_GetReference: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Reference stocks ({}):", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                var s = s2c.getStaticInfoList(i);
                if (!s.hasBasic()) continue;
                var basic = s.getBasic();
                logger.info("      ref[{}] code={} name={} listTime={}",
                    i, basic.getSecurity().getCode(), basic.getName(), basic.getListTime());
            }
            if (count > 10) logger.info("      ... ({} more)", count - 10);
        } else {
            logger.warn("    getReference failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}