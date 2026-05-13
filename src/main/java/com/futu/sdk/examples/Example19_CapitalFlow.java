package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCapitalDistribution;
import com.futu.openapi.pb.QotGetCapitalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 19: Capital Flow & Distribution
 *
 * Demonstrates:
 *   - getCapitalFlow: intraday and daily capital flow (buy/sell pressure)
 *   - getCapitalDistribution: large/medium/small order distribution
 *   - PeriodType: Intraday=0, Day=1, Week=2, Month=3, Year=4, QTD=5, YTD=6
 *
 * Mirrors: examples/19_capital_flow/main.py from futu-python-samples
 */
public class Example19_CapitalFlow implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example19_CapitalFlow.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // PeriodType: Intraday=0, Day=1, Week=2, Month=3, Year=4, QTD=5, YTD=6
    private static final int PERIOD_INTRADAY = 0;
    private static final int PERIOD_DAY = 1;

    public static void main(String[] args) {
        logger.info("=== Capital Flow & Distribution Demo ===");
        FTAPI.init();
        Example19_CapitalFlow demo = new Example19_CapitalFlow();
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

        // ── getCapitalFlow: INTRADAY ───────────────────────────────────
        logger.info("\n=== getCapitalFlow: HK.00700 (INTRADAY) ===");
        var flowIntra = QotGetCapitalFlow.C2S.newBuilder()
            .setSecurity(sec)
            .setPeriodType(PERIOD_INTRADAY)
            .build();
        int ret = qot.getCapitalFlow(QotGetCapitalFlow.Request.newBuilder().setC2S(flowIntra).build());
        logger.info("getCapitalFlow(INTRADAY) ret={}", ret);
        sleep(300);

        // ── getCapitalFlow: DAY ────────────────────────────────────────
        logger.info("\n=== getCapitalFlow: HK.00700 (DAY) ===");
        var flowDay = QotGetCapitalFlow.C2S.newBuilder()
            .setSecurity(sec)
            .setPeriodType(PERIOD_DAY)
            .build();
        ret = qot.getCapitalFlow(QotGetCapitalFlow.Request.newBuilder().setC2S(flowDay).build());
        logger.info("getCapitalFlow(DAY) ret={}", ret);
        sleep(300);

        // ── getCapitalDistribution ─────────────────────────────────────
        logger.info("\n=== getCapitalDistribution: HK.00700 ===");
        var distC2s = QotGetCapitalDistribution.C2S.newBuilder()
            .setSecurity(sec)
            .build();
        ret = qot.getCapitalDistribution(QotGetCapitalDistribution.Request.newBuilder().setC2S(distC2s).build());
        logger.info("getCapitalDistribution ret={}", ret);

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
    public void onReply_GetCapitalFlow(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetCapitalFlow.Response rsp) {
        logger.info("  [Qot] onReply_GetCapitalFlow: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getFlowItemListCount();
            logger.info("    Flow items ({} bars): lastValidTime={}", count, s2c.getLastValidTime());
            for (int i = Math.max(0, count - 5); i < count; i++) {
                var item = s2c.getFlowItemList(i);
                logger.info("      [{}] inFlow={:.2f} main={:.2f} super={:.2f} big={:.2f} mid={:.2f} sml={:.2f}",
                    item.getTime(), item.getInFlow(), item.getMainInFlow(),
                    item.getSuperInFlow(), item.getBigInFlow(),
                    item.getMidInFlow(), item.getSmlInFlow());
            }
        } else {
            logger.warn("    getCapitalFlow failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCapitalDistribution(com.futu.openapi.FTAPI_Conn client, int retCode,
                                               QotGetCapitalDistribution.Response rsp) {
        logger.info("  [Qot] onReply_GetCapitalDistribution: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    Update time: {}", s2c.getUpdateTime());
            logger.info("    IN  — super={:.2f} big={:.2f} mid={:.2f} small={:.2f}",
                s2c.getCapitalInSuper(), s2c.getCapitalInBig(),
                s2c.getCapitalInMid(), s2c.getCapitalInSmall());
            logger.info("    OUT — super={:.2f} big={:.2f} mid={:.2f} small={:.2f}",
                s2c.getCapitalOutSuper(), s2c.getCapitalOutBig(),
                s2c.getCapitalOutMid(), s2c.getCapitalOutSmall());
            double netSuper = s2c.getCapitalInSuper() - s2c.getCapitalOutSuper();
            double netBig = s2c.getCapitalInBig() - s2c.getCapitalOutBig();
            double netMid = s2c.getCapitalInMid() - s2c.getCapitalOutMid();
            double netSml = s2c.getCapitalInSmall() - s2c.getCapitalOutSmall();
            logger.info("    NET — super={:.2f} big={:.2f} mid={:.2f} small={:.2f}",
                netSuper, netBig, netMid, netSml);
        } else {
            logger.warn("    getCapitalDistribution failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}