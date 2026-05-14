package com.futu.sdk.examples;

import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateOrderBook;
import com.futu.openapi.pb.QotUpdateBasicQot;
import com.futu.openapi.pb.QotUpdateTicker;
import com.futu.openapi.pb.QotUpdateBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 02: Real-time Quote Push
 *
 * Demonstrates subscribing to and receiving real-time push updates:
 *   - Stock quotes (cur price, bid/ask, volume)
 *   - Order book (bid/ask depth)
 *   - Ticker (tick-by-tick trades)
 *   - Broker queue (buy/sell wall)
 *
 * Mirrors: examples/02_quote_push/main.py from futu-python-samples
 */
public class Example02_QuotePush implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example02_QuotePush.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private volatile boolean subscribed = false;

    // SubTypes in QotCommon.SubType: None=0, Basic=1, Broker=2, OrderBook=3, Ticker=4
    private static final int SUB_TYPE_NONE = 0;
    private static final int SUB_TYPE_BASIC = 1;
    private static final int SUB_TYPE_BROKER = 2;
    private static final int SUB_TYPE_ORDER_BOOK = 3;
    private static final int SUB_TYPE_TICKER = 4;

    public static void main(String[] args) {
        logger.info("=== Real-time Quote Push Demo ===");
        com.futu.openapi.FTAPI.init();
        Example02_QuotePush demo = new Example02_QuotePush();
        demo.start();
    }

    public void start() {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);

        // Set RSA private key before connecting (required for RSA auth)
        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
            logger.info("RSA key set");
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

        // Wait for connection ready
        int waited = 0;
        while (!connected && waited < 10000) {
            sleep(50);
            waited += 50;
        }

        if (!connected) {
            logger.error("Connection timed out. Status={}", qot.getConnStatus());
            return;
        }

        // Build subscription list
        // Market: HK_Security=1, HK_Future=7, US_Security=2, CNSH=4, CNSZ=5
        QotCommon.Security[] securities = {
            QotCommon.Security.newBuilder().setMarket(1).setCode("00700").build(),   // Tencent
            QotCommon.Security.newBuilder().setMarket(1).setCode("HSImain").build(), // HSI futures
        };

        int[] subtypes = {SUB_TYPE_BASIC, SUB_TYPE_ORDER_BOOK, SUB_TYPE_TICKER, SUB_TYPE_BROKER};

        // Subscribe
        logger.info("\n--- Subscribing to {} securities with {} subtype(s) ---",
            securities.length, subtypes.length);
        for (QotCommon.Security sec : securities) {
            logger.info("  {} market={}", sec.getCode(), sec.getMarket());
        }

        QotSub.C2S.Builder c2sBuilder = QotSub.C2S.newBuilder();
        for (QotCommon.Security sec : securities) {
            c2sBuilder.addSecurityList(sec);
        }
        for (int subtype : subtypes) {
            c2sBuilder.addSubTypeList(subtype);
        }
        c2sBuilder.setIsSubOrUnSub(true);   // subscribe (not unsubscribe)
        c2sBuilder.setIsRegOrUnRegPush(true);  // register push handlers
        c2sBuilder.setIsFirstPush(true);    // receive current snapshot immediately

        QotSub.Request req = QotSub.Request.newBuilder()
            .setC2S(c2sBuilder.build())
            .build();

        int ret = qot.sub(req);
        logger.info("subscribe ret={}", ret);

        // Wait for push updates
        logger.info("\nReceiving pushes for 15 seconds...");
        sleep(15000);

        // Unsubscribe
        logger.info("\n--- Unsubscribing ---");
        QotSub.C2S.Builder unsubBuilder = QotSub.C2S.newBuilder();
        for (QotCommon.Security sec : securities) {
            unsubBuilder.addSecurityList(sec);
        }
        for (int subtype : subtypes) {
            unsubBuilder.addSubTypeList(subtype);
        }
        unsubBuilder.setIsSubOrUnSub(false);  // unsubscribe
        unsubBuilder.setIsRegOrUnRegPush(false);

        QotSub.Request unsubReq = QotSub.Request.newBuilder()
            .setC2S(unsubBuilder.build())
            .build();
        ret = qot.sub(unsubReq);
        logger.info("unsubscribe ret={}", ret);

        sleep(500);
        qot.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn callbacks
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
    // FTSPI_Qot callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            subscribed = true;
            var s2c = rsp.getS2C();
            // S2C fields vary by server response; just log retCode
            logger.info("    sub confirmed, retCode={}", retCode);
        } else {
            logger.warn("    sub failed retCode={}", retCode);
        }
    }

    // ---- Push handlers ----

    @Override
    public void onPush_UpdateBasicQuote(com.futu.openapi.FTAPI_Conn client, QotUpdateBasicQot.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        for (var qot : s2c.getBasicQotListList()) {
            String code = qot.getSecurity().getCode();
            double curPrice = qot.getCurPrice();
            double lastClose = qot.getLastClosePrice();
            long vol = qot.getVolume();
            double chgPct = (curPrice > 0 && lastClose > 0) ? (curPrice - lastClose) / lastClose * 100 : 0;
            logger.info("[Quote] {} @ {} ({:+.2f}%) vol={}", code, curPrice, chgPct, vol);
        }
    }

    @Override
    public void onPush_UpdateOrderBook(com.futu.openapi.FTAPI_Conn client, QotUpdateOrderBook.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        String code = s2c.getSecurity().getCode();
        var bids = s2c.getOrderBookBidListList();
        var asks = s2c.getOrderBookAskListList();
        double bestBid = bids.isEmpty() ? 0 : bids.get(0).getPrice();
        double bestAsk = asks.isEmpty() ? 0 : asks.get(0).getPrice();
        logger.info("[OrderBook] {} | {} bid levels, {} ask levels | best_bid={:.2f} best_ask={:.2f}",
            code, bids.size(), asks.size(), bestBid, bestAsk);
    }

    @Override
    public void onPush_UpdateTicker(com.futu.openapi.FTAPI_Conn client, QotUpdateTicker.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        String code = s2c.getSecurity().getCode();
        for (var ticker : s2c.getTickerListList()) {
            long ts = (long) ticker.getTimestamp();
            double price = ticker.getPrice();
            long volume = ticker.getVolume();
            double turnover = ticker.getTurnover();
            int dir = ticker.getDir();  // 0=none, 1=buy, 2=sell
            String dirStr = dir == 1 ? "BUY" : dir == 2 ? "SELL" : "NONE";
            logger.info("[Ticker] {} price={} vol={} turnover={:.2f} dir={}",
                code, price, volume, turnover, dirStr);
        }
    }

    @Override
    public void onPush_UpdateBroker(com.futu.openapi.FTAPI_Conn client, QotUpdateBroker.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        String code = s2c.getSecurity().getCode();
        var bidBrokers = s2c.getBrokerBidListList();
        var askBrokers = s2c.getBrokerAskListList();
        logger.info("[Broker] {} | {} bid brokers, {} ask brokers",
            code, bidBrokers.size(), askBrokers.size());
        // Show top 3 bid/ask broker positions
        for (int i = 0; i < Math.min(3, bidBrokers.size()); i++) {
            var b = bidBrokers.get(i);
            logger.info("  bid[{}] pos={}", i, b.getPos());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}