package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetRT;
import com.futu.openapi.pb.QotGetTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 08: Real-time Ticker & RT Data
 *
 * Demonstrates:
 *   - getTicker: tick-by-tick trade records (last N ticks)
 *   - getRT: intraday minute-level OHLCV data
 *
 * Mirrors: examples/08_rt_ticker/main.py from futu-python-samples
 */
public class Example08_RtTicker implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example08_RtTicker.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== RT Ticker & RT Data Demo ===");
        FTAPI.init();
        Example08_RtTicker demo = new Example08_RtTicker();
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

        String code = "00700";
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode(code).build();

        // ── getTicker: last 50 ticks ────────────────────────────────────
        logger.info("\n=== getTicker: HK.00700 (last 50 ticks) ===");
        var tickerC2s = QotGetTicker.C2S.newBuilder()
            .setSecurity(sec)
            .setMaxRetNum(50)
            .build();
        int ret = qot.getTicker(QotGetTicker.Request.newBuilder().setC2S(tickerC2s).build());
        logger.info("getTicker ret={}", ret);
        sleep(300);

        // ── getRT: full intraday minute bars ─────────────────────────────
        logger.info("\n=== getRT: HK.00700 (full intraday) ===");
        var rtC2s = QotGetRT.C2S.newBuilder()
            .setSecurity(sec)
            .build();
        ret = qot.getRT(QotGetRT.Request.newBuilder().setC2S(rtC2s).build());
        logger.info("getRT ret={}", ret);
        sleep(300);

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
    public void onReply_GetTicker(com.futu.openapi.FTAPI_Conn client, int retCode,
                                  QotGetTicker.Response rsp) {
        logger.info("  [Qot] onReply_GetTicker: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    {} ({} ticks):", sec.getCode(), s2c.getTickerListCount());
            for (int i = 0; i < s2c.getTickerListCount(); i++) {
                var t = s2c.getTickerList(i);
                String dirStr = switch (t.getDir()) {
                    case 1 -> "BUY";
                    case 2 -> "SELL";
                    default -> "NONE";
                };
                logger.info("      tick[{}] time={} price={:.2f} vol={} turnover={:.2f} dir={}",
                    i, t.getTime(), t.getPrice(), t.getVolume(), t.getTurnover(), dirStr);
            }
        } else {
            logger.warn("    getTicker failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetRT(com.futu.openapi.FTAPI_Conn client, int retCode,
                              QotGetRT.Response rsp) {
        logger.info("  [Qot] onReply_GetRT: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            int count = s2c.getRtListCount();
            logger.info("    {} ({} minute bars):", sec.getCode(), count);
            for (int i = 0; i < count; i++) {
                var ts = s2c.getRtList(i);
                logger.info("      rt[{}] time={} minute={} price={:.2f} vol={} turnover={:.2f}",
                    i, ts.getTime(), ts.getMinute(), ts.getPrice(), ts.getVolume(), ts.getTurnover());
            }
        } else {
            logger.warn("    getRT failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}