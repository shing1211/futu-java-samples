package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 18: Reference Stock List
 *
 * Demonstrates:
 *   - getReference: get related warrant/bull-bear reference stocks
 *   - SecurityReferenceType: WARRANT=1, BULL_BEAR=2
 *   - SecurityStaticInfo → SecurityStaticBasic → getSecurity()/getName()/getListTime()
 *
 * Mirrors: examples/18_referencestock/main.py from futu-python-samples
 */
public class Example18_ReferenceStock implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example18_ReferenceStock.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    // SecurityReferenceType: Warrant=1, BullBear=2
    private static final int REF_TYPE_WARRANT = 1;
    private static final int REF_TYPE_BULL_BEAR = 2;

    public static void main(String[] args) {
        logger.info("=== Reference Stock List Demo ===");
        FTAPI.init();
        Example18_ReferenceStock demo = new Example18_ReferenceStock();
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

        QotCommon.Security[] securities = {
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build(),
        };

        for (QotCommon.Security sec : securities) {
            logger.info("\n=== Processing {} ===", sec.getCode());

            // WARRANT
            logger.info("\n  --- getReference: {} [WARRANT] ---", sec.getCode());
            var refWarrant = QotGetReference.C2S.newBuilder()
                .setSecurity(sec)
                .setReferenceType(REF_TYPE_WARRANT)
                .build();
            int ret = qot.getReference(QotGetReference.Request.newBuilder().setC2S(refWarrant).build());
            logger.info("getReference(WARRANT) ret={}", ret);
            sleep(300);

            // BULL_BEAR
            logger.info("\n  --- getReference: {} [BULL_BEAR] ---", sec.getCode());
            var refBull = QotGetReference.C2S.newBuilder()
                .setSecurity(sec)
                .setReferenceType(REF_TYPE_BULL_BEAR)
                .build();
            ret = qot.getReference(QotGetReference.Request.newBuilder().setC2S(refBull).build());
            logger.info("getReference(BULL_BEAR) ret={}", ret);
            sleep(300);
        }

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
    public void onReply_GetReference(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotGetReference.Response rsp) {
        logger.info("  [Qot] onReply_GetReference: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            if (count == 0) {
                logger.info("    No reference stocks found");
            } else {
                logger.info("    Reference stocks ({}):", count);
                for (int i = 0; i < Math.min(count, 10); i++) {
                    var s = s2c.getStaticInfoList(i);
                    if (!s.hasBasic()) continue;
                    var basic = s.getBasic();
                    logger.info("      ref[{}] code={} name={} listTime={}",
                        i, basic.getSecurity().getCode(), basic.getName(), basic.getListTime());
                }
                if (count > 10) logger.info("      ... ({} more)", count - 10);
            }
        } else {
            logger.warn("    getReference failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}