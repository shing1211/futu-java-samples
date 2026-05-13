package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOrderBook;
import com.futu.openapi.pb.QotSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 10: Order Book (get_order_book)
 *
 * Demonstrates:
 *   - subscribe: subscribe to ORDER_BOOK subtype
 *   - getOrderBook: fetch N-level bid/ask depth
 *   - Order book structure: price, volume, order count per level
 *
 * Mirrors: examples/10_orderbook/main.py from futu-python-samples
 */
public class Example10_OrderBook implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example10_OrderBook.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Order Book (Depth) Demo ===");
        FTAPI.init();
        Example10_OrderBook demo = new Example10_OrderBook();
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

        // ── Subscribe to ORDER_BOOK ─────────────────────────────────────
        logger.info("\n--- Subscribe: HK.00700 ORDER_BOOK ---");
        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec)
            .addSubTypeList(QotCommon.SubType.SubType_OrderBook.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(false)
            .build();
        int ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);
        sleep(200);

        // ── getOrderBook: 10 levels ─────────────────────────────────────
        logger.info("\n=== getOrderBook: HK.00700 (10 levels) ===");
        var ob10 = QotGetOrderBook.C2S.newBuilder()
            .setSecurity(sec)
            .setNum(10)
            .build();
        ret = qot.getOrderBook(QotGetOrderBook.Request.newBuilder().setC2S(ob10).build());
        logger.info("getOrderBook ret={}", ret);
        sleep(300);

        // ── getOrderBook: 50 levels ─────────────────────────────────────
        logger.info("\n=== getOrderBook: HK.00700 (50 levels) ===");
        var ob50 = QotGetOrderBook.C2S.newBuilder()
            .setSecurity(sec)
            .setNum(50)
            .build();
        ret = qot.getOrderBook(QotGetOrderBook.Request.newBuilder().setC2S(ob50).build());
        logger.info("getOrderBook ret={}", ret);

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
    public void onReply_GetOrderBook(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotGetOrderBook.Response rsp) {
        logger.info("  [Qot] onReply_GetOrderBook: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            int bidCount = s2c.getOrderBookBidListCount();
            int askCount = s2c.getOrderBookAskListCount();
            logger.info("    {} (bid={} ask={}):", sec.getCode(), bidCount, askCount);
            for (int i = 0; i < Math.min(bidCount, 10); i++) {
                var b = s2c.getOrderBookBidList(i);
                logger.info("      bid[{}] price={:.2f} vol={} count={}",
                    i, b.getPrice(), b.getVolume(), b.getOrederCount());
            }
            for (int i = 0; i < Math.min(askCount, 10); i++) {
                var a = s2c.getOrderBookAskList(i);
                logger.info("      ask[{}] price={:.2f} vol={} count={}",
                    i, a.getPrice(), a.getVolume(), a.getOrederCount());
            }
            if (bidCount > 0 && askCount > 0) {
                var bestBid = s2c.getOrderBookBidList(0);
                var bestAsk = s2c.getOrderBookAskList(0);
                double spread = bestAsk.getPrice() - bestBid.getPrice();
                double spreadPct = spread / bestBid.getPrice() * 100;
                logger.info("    Best bid={:.2f} Best ask={:.2f} Spread={:.2f} ({:.2f}%)",
                    bestBid.getPrice(), bestAsk.getPrice(), spread, spreadPct);
            }
            // Total volume at 50 levels
            long totalBidVol = 0, totalAskVol = 0;
            for (int i = 0; i < bidCount; i++) totalBidVol += s2c.getOrderBookBidList(i).getVolume();
            for (int i = 0; i < askCount; i++) totalAskVol += s2c.getOrderBookAskList(i).getVolume();
            logger.info("    50 levels — total bid vol={} ask vol={} ratio={:.2f}",
                totalBidVol, totalAskVol, totalBidVol / (double) totalAskVol);
        } else {
            logger.warn("    getOrderBook failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}