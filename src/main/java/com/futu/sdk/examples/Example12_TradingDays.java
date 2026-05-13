package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotRequestTradeDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 12: Trading Days Calendar
 *
 * Demonstrates:
 *   - requestTradeDate: get trading days for a market in a date range
 *   - Market types: HK, US, SH, SZ
 *
 * Mirrors: examples/12_trading_days/main.py from futu-python-samples
 */
public class Example12_TradingDays implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example12_TradingDays.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;
    private static final int MARKET_SH = 4;
    private static final int MARKET_SZ = 5;

    public static void main(String[] args) {
        logger.info("=== Trading Days Calendar Demo ===");
        FTAPI.init();
        Example12_TradingDays demo = new Example12_TradingDays();
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

        // ── Query trading days for each market ──────────────────────────
        int[][] markets = {
            {MARKET_HK, 1},  // index 0: HK placeholder
            {MARKET_US, 0},
            {MARKET_SH, 0},
            {MARKET_SZ, 0},
        };
        String[] labels = {"HK", "US", "SH", "SZ"};
        int[] marketIds = {MARKET_HK, MARKET_US, MARKET_SH, MARKET_SZ};

        for (int i = 0; i < marketIds.length; i++) {
            logger.info("\n=== requestTradeDate: {} (2026-04 to 2026-05) ===", labels[i]);
            var c2s = QotRequestTradeDate.C2S.newBuilder()
                .setMarket(marketIds[i])
                .setBeginTime("2026-04-01")
                .setEndTime("2026-05-31")
                .build();

            int ret = qot.requestTradeDate(QotRequestTradeDate.Request.newBuilder().setC2S(c2s).build());
            logger.info("requestTradeDate ret={}", ret);
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
    public void onReply_RequestTradeDate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                         QotRequestTradeDate.Response rsp) {
        logger.info("  [Qot] onReply_RequestTradeDate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getTradeDateListCount();
            logger.info("    Trading days ({}):", count);
            // Show first 5 and last 5
            int shown = Math.min(count, 5);
            for (int i = 0; i < shown; i++) {
                logger.info("      trade_date[{}] = {}", i, s2c.getTradeDateList(i).getTime());
            }
            if (count > 10) {
                logger.info("      ... ({} more)", count - 10);
                for (int i = count - 5; i < count; i++) {
                    logger.info("      trade_date[{}] = {}", i, s2c.getTradeDateList(i).getTime());
                }
            }
        } else {
            logger.warn("    requestTradeDate failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}