package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetBasicQot;
import com.futu.openapi.pb.QotSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 16: Real-time Stock Quote (get_stock_quote)
 *
 * Demonstrates:
 *   - subscribe: subscribe to QUOTE (SubType_Basic) subtype
 *   - getBasicQot: fetch real-time quote fields for multiple stocks
 *   - BasicQot fields: curPrice, openPrice, highPrice, lowPrice,
 *     lastClosePrice, volume, turnover, turnoverRate, amplitude, etc.
 *   - Cross-market: HK, US, SH, SZ stocks
 *
 * Mirrors: examples/16_stock_quote/main.py from futu-python-samples
 */
public class Example16_StockQuote implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example16_StockQuote.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;
    private static final int MARKET_SH = 4;
    private static final int MARKET_SZ = 5;

    public static void main(String[] args) {
        logger.info("=== Real-time Stock Quote Demo ===");
        FTAPI.init();
        Example16_StockQuote demo = new Example16_StockQuote();
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

        // ── Build security list: HK.00700, HK.HSImain, US.AAPL, SH.600000, SZ.000001 ──
        var sec1 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var sec2 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build();
        var sec3 = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build();
        var sec4 = QotCommon.Security.newBuilder().setMarket(MARKET_SH).setCode("600000").build();
        var sec5 = QotCommon.Security.newBuilder().setMarket(MARKET_SZ).setCode("000001").build();

        // ── Subscribe to QUOTE (Basic) ─────────────────────────────────────
        logger.info("\n--- Subscribe: QUOTE for 5 stocks ---");
        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec1).addSecurityList(sec2)
            .addSecurityList(sec3).addSecurityList(sec4).addSecurityList(sec5)
            .addSubTypeList(QotCommon.SubType.SubType_Basic.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(false)
            .setIsFirstPush(false)
            .build();
        int ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);
        sleep(500);

        // ── getBasicQot: fetch quotes for all 5 stocks ────────────────────
        logger.info("\n=== getBasicQot: 5 stocks ===");
        var quoteC2s = QotGetBasicQot.C2S.newBuilder()
            .addSecurityList(sec1).addSecurityList(sec2)
            .addSecurityList(sec3).addSecurityList(sec4).addSecurityList(sec5)
            .build();
        ret = qot.getBasicQot(QotGetBasicQot.Request.newBuilder().setC2S(quoteC2s).build());
        logger.info("getBasicQot ret={}", ret);

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
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode != 0) logger.warn("    subscribe failed retCode={}", retCode);
    }

    @Override
    public void onReply_GetBasicQot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   QotGetBasicQot.Response rsp) {
        logger.info("  [Qot] onReply_GetBasicQot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getBasicQotListCount();
            logger.info("    Retrieved {} quotes:", count);
            for (int i = 0; i < count; i++) {
                var q = s2c.getBasicQotList(i);
                var sec = q.getSecurity();
                logger.info("\n    === {} ({}) ===", sec.getCode(), q.getName());
                logger.info("      curPrice={:.2f} open={:.2f} high={:.2f} low={:.2f} lastClose={:.2f}",
                    q.getCurPrice(), q.getOpenPrice(), q.getHighPrice(),
                    q.getLowPrice(), q.getLastClosePrice());
                logger.info("      volume={} turnover={:.2f} turnoverRate={:.2f}% amplitude={:.2f}%",
                    q.getVolume(), q.getTurnover(), q.getTurnoverRate(), q.getAmplitude());
                logger.info("      updateTime={} suspended={}", q.getUpdateTime(), q.getIsSuspended());
                if (q.hasPreMarket()) {
                    var pm = q.getPreMarket();
                    logger.info("      preMarket: price={:.2f} vol={} chg={:.2f}%",
                        pm.getPrice(), pm.getVolume(), pm.getChangeRate());
                }
                if (q.hasAfterMarket()) {
                    var am = q.getAfterMarket();
                    logger.info("      afterMarket: price={:.2f} vol={} chg={:.2f}%",
                        am.getPrice(), am.getVolume(), am.getChangeRate());
                }
            }
        } else {
            logger.warn("    getBasicQot failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}