package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetMarketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 22: Market State (get_market_state)
 *
 * Demonstrates:
 *   - getMarketState: check trading session status for multiple stocks
 *   - Market states: 1=HK Pre, 2=HK AM, 3=HK PM, 4=Closed, etc.
 *   - Cross-market: HK, US, SH, SZ stocks
 *
 * Mirrors: examples/22_market_state/main.py from futu-python-samples
 */
public class Example22_MarketState implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example22_MarketState.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;
    private static final int MARKET_SH = 4;
    private static final int MARKET_SZ = 5;

    public static void main(String[] args) {
        logger.info("=== Market State (Trading Session) Demo ===");
        FTAPI.init();
        Example22_MarketState demo = new Example22_MarketState();
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

        // Build security list: HK.00700, US.AAPL, SH.600000, SZ.000001
        var sec1 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var sec2 = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build();
        var sec3 = QotCommon.Security.newBuilder().setMarket(MARKET_SH).setCode("600000").build();
        var sec4 = QotCommon.Security.newBuilder().setMarket(MARKET_SZ).setCode("000001").build();

        // ── getMarketState ─────────────────────────────────────────────
        logger.info("\n=== getMarketState: 4 stocks ===");
        var msC2s = QotGetMarketState.C2S.newBuilder()
            .addSecurityList(sec1).addSecurityList(sec2)
            .addSecurityList(sec3).addSecurityList(sec4)
            .build();
        int ret = qot.getMarketState(QotGetMarketState.Request.newBuilder().setC2S(msC2s).build());
        logger.info("getMarketState ret={}", ret);

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
    public void onReply_GetMarketState(com.futu.openapi.FTAPI_Conn client, int retCode,
                                        QotGetMarketState.Response rsp) {
        logger.info("  [Qot] onReply_GetMarketState: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getMarketInfoListCount();
            logger.info("    Market states ({} records):", count);
            for (int i = 0; i < count; i++) {
                var info = s2c.getMarketInfoList(i);
                logger.info("      {} ({}) state={} ({})",
                    info.getSecurity().getCode(), info.getName(),
                    info.getMarketState(), marketStateLabel(info.getMarketState()));
            }
        } else {
            logger.warn("    getMarketState failed retCode={}", retCode);
        }
    }

    private static String marketStateLabel(int state) {
        return switch (state) {
            case 0 -> "MORNING";         // HK/A-share morning
            case 1 -> "AFTERNOON";       // HK/A-share afternoon, US hours
            case 2 -> "FUTURE_DAY_OPEN"; // HK/SG/JP futures day market open
            case 3 -> "FUTURE_OPEN";     // US futures open
            case 4 -> "FUTURE_BREAK_OVER"; // US futures after break
            case 5 -> "CLOSED";          // Market closed
            case 6 -> "PRE_MARKET";      // US pre-market
            case 7 -> "AFTER_HOURS";     // US after-hours
            case 8 -> "REST";            // Rest period
            default -> "UNKNOWN(" + state + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}