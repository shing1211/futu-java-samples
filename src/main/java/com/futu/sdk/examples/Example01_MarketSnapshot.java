package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.GetGlobalState;
import com.futu.openapi.pb.QotGetBasicQot;
import com.futu.openapi.pb.QotCommon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 01: Market Snapshot (getMarketSnapshot / getBasicQot)
 *
 * Connects to OpenD, then fetches basic quote data for several securities
 * using the async callback-driven API.
 *
 * Mirrors: examples/01_snapshot/main.py from futu-python-samples
 */
public class Example01_MarketSnapshot implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example01_MarketSnapshot.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private volatile int globalStateRetCode = -1;
    private volatile GetGlobalState.Response globalStateRsp = null;

    // Sample securities: HK:00700, US:AAPL, HK:HSI
    private static final QotCommon.Security[] SECS = {
        QotCommon.Security.newBuilder()
            .setMarket(QotCommon.QotMarket.QotMarket_HK_Security.getNumber())
            .setCode("00700")
            .build(),
        QotCommon.Security.newBuilder()
            .setMarket(QotCommon.QotMarket.QotMarket_US_Security.getNumber())
            .setCode("AAPL")
            .build(),
        QotCommon.Security.newBuilder()
            .setMarket(QotCommon.QotMarket.QotMarket_HK_Security.getNumber())
            .setCode("HSI")
            .build(),
    };

    public static void main(String[] args) {
        logger.info("=== Market Snapshot Demo ===");
        FTAPI.init();
        Example01_MarketSnapshot demo = new Example01_MarketSnapshot();
        demo.start();
    }

    public void start() {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);

        // Set RSA private key before connecting (required for RSA auth)
        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = qot.initConnect(
            Config.FUTU_OPEND_HOST,
            Config.FUTU_OPEND_PORT,
            Config.FUTU_RSA_ENABLED
        );
        if (!ok) {
            logger.error("initConnect failed!");
            return;
        }
        logger.info("Connecting to {}:{} (RSA={})...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RSA_ENABLED);

        // Wait for connection ready (connected flag set in onInitConnect callback)
        int waited = 0;
        while (!connected && waited < 8000) {
            sleep(50);
            waited += 50;
        }

        if (!connected) {
            logger.error("Connection timed out. Status={}", qot.getConnStatus());
            return;
        }

        // ---- getGlobalState ----
        logger.info("\n--- getGlobalState ---");
        int ret = qot.getGlobalState(GetGlobalState.Request.getDefaultInstance());
        logger.info("getGlobalState ret={}", ret);

        // Wait for callback
        waited = 0;
        while (globalStateRetCode == -1 && waited < 5000) {
            sleep(30);
            waited += 30;
        }
        if (globalStateRsp != null && globalStateRsp.hasS2C()) {
            var s2c = globalStateRsp.getS2C();
            logger.info("  serverVer={} connStatus={} marketSvrTime={}",
                s2c.getServerVer(), s2c.getQotLogined()?"CONNECTED":"DISCONNECTED", s2c.getTime());
        }

        // ---- getBasicQot (market snapshot) ----
        logger.info("\n--- getBasicQot ---");
        for (QotCommon.Security sec : SECS) {
            QotGetBasicQot.C2S c2s = QotGetBasicQot.C2S.newBuilder()
                .addSecurityList(sec)
                .build();
            QotGetBasicQot.Request req = QotGetBasicQot.Request.newBuilder()
                .setC2S(c2s)
                .build();
            ret = qot.getBasicQot(req);
            logger.info("getBasicQot({}) ret={}", sec.getCode(), ret);
        }

        // Wait for callbacks to arrive
        sleep(2000);

        logger.info("\nDone. Close OpenD or check logs for push callbacks.");
        qot.close();
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(com.futu.openapi.FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) {
            connected = true;
        }
    }

    @Override
    public void onDisconnect(com.futu.openapi.FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
        connected = false;
    }

    // -------------------------------------------------------------------------
    // FTSPI_Qot callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetGlobalState(com.futu.openapi.FTAPI_Conn client, int retCode,
                                        GetGlobalState.Response rsp) {
        logger.info("  [Qot] onReply_GetGlobalState: retCode={}", retCode);
        globalStateRetCode = retCode;
        globalStateRsp = rsp;
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    serverVer={} connStatus={} marketSvrTime={}",
                s2c.getServerVer(), s2c.getQotLogined()?"CONNECTED":"DISCONNECTED", s2c.getTime());
        }
    }

    @Override
    public void onReply_GetBasicQot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotGetBasicQot.Response rsp) {
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            for (var basic : s2c.getBasicQotListList()) {
                String code = basic.getSecurity().getCode();
                double curPrice = basic.getCurPrice();
                
                double yesClose = basic.getLastClosePrice();
                double chgPct = (curPrice > 0 && yesClose > 0)
                    ? (curPrice - yesClose) / yesClose * 100 : 0;
                logger.info("  [Qot] {} @ {} ({:+.2f}%)", code, curPrice, chgPct);
            }
        } else {
            logger.warn("  [Qot] onReply_GetBasicQot retCode={}", retCode);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}