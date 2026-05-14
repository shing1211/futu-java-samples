package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOrderBook;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdPlaceOrder;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 06: Stock Sell (simple_sell / smart_sell)
 *
 * Demonstrates:
 *   - simple_sell: place sell order at fixed price
 *   - smart_sell: read order book, sell at best bid price
 *   - getSecuritySnapshot: fetch lot_size for position rounding
 *   - getOrderBook: read current bid queue
 *   - place_order: sell with proper lot sizing
 *   - Proper logging throughout
 *
 * Mirrors: examples/06_stock_sell/main.py from futu-python-samples
 */
public class Example06_StockSell implements FTSPI_Qot, FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example06_StockSell.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean qotConnected = false;
    private volatile boolean trdConnected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;

    // TrdSide: Sell=2
    private static final int TRD_SIDE_SELL = 2;

    // OrderType: Normal=1
    private static final int ORDER_TYPE_NORMAL = 1;

    // State
    private record AccInfo(long accId, int trdMarket, int trdEnv) {}
    private java.util.List<AccInfo> accList = new java.util.ArrayList<>();
    private final CountDownLatch accListLatch = new CountDownLatch(1);

    private int lotSize = 0;
    private double bestBidPrice = 0;

    public static void main(String[] args) {
        logger.info("=== Stock Sell Demo ===");
        FTAPI.init();
        Example06_StockSell demo = new Example06_StockSell();
        demo.start();
    }

    public void start() {
        // ── Setup quote context ───────────────────────────────────────
        qot.setClientInfo("javaclient-qot", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = qot.initConnect(Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RSA_ENABLED);
        if (!ok) { logger.error("qot initConnect failed!"); return; }
        logger.info("Qot connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        // ── Setup trade context ───────────────────────────────────────
        trd.setClientInfo("javaclient-trd", 2);
        trd.setConnSpi(this);
        trd.setTrdSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            trd.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        ok = trd.initConnect(Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RSA_ENABLED);
        if (!ok) { logger.error("trd initConnect failed!"); return; }
        logger.info("Trd connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        int waited = 0;
        while ((!qotConnected || !trdConnected) && waited < 10000) {
            sleep(50);
            waited += 50;
        }
        if (!qotConnected || !trdConnected) {
            logger.error("Connection timeout. qot={} trd={}", qotConnected, trdConnected);
            return;
        }

        // ── Get account list ──────────────────────────────────────────
        logger.info("Fetching account list...");
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().build());
        if (ret != 0) { logger.error("getAccList failed ret={}", ret); return; }
        try { accListLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (accList.isEmpty()) { logger.error("No trading accounts found!"); return; }

        var acc = accList.get(0);
        logger.info("Using account: accId={} market={} env={}", acc.accId, acc.trdMarket, acc.trdEnv);

        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(acc.accId)
            .setTrdMarket(acc.trdMarket)
            .build();

        // ── Unlock trade ──────────────────────────────────────────────
        String tradePwd = Config.FUTU_TRADE_PASSWORD;
        if (tradePwd != null && !tradePwd.isEmpty()) {
            logger.info("\n--- unlockTrade ---");
            String pwdMd5 = md5(tradePwd);
            var unlockC2s = TrdUnlockTrade.C2S.newBuilder()
                .setUnlock(true)
                .setPwdMD5(pwdMd5)
                .build();
            ret = trd.unlockTrade(TrdUnlockTrade.Request.newBuilder().setC2S(unlockC2s).build());
            logger.info("unlockTrade ret={}", ret);
            sleep(300);
        }

        String code = "00700";
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode(code).build();

        // ── Order book data will be fetched directly via getOrderBook ──
        logger.info("Order book for HK.{} will be fetched on demand.", code);

        // ── simple_sell at fixed price ─────────────────────────────────
        logger.info("\n--- simple_sell at fixed price 280.0 ---");
        double fixedPrice = 280.0;
        int volume = 100;

        // Get lot_size
        var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addSecurityList(sec)
            .build();
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
        logger.info("getSecuritySnapshot ret={}", ret);
        sleep(500);

        if (lotSize <= 0) {
            logger.error("Invalid lot_size={} for HK.{}", lotSize, code);
            qot.close(); trd.close();
            return;
        }

        long qty = (long) (volume / lotSize) * lotSize;
        if (qty == 0) {
            logger.error("Volume {} is less than one lot ({}) — cannot sell", volume, lotSize);
        } else {
            logger.info("  adjusted qty to {} (rounded to lot={})", qty, lotSize);

            var orderC2s = TrdPlaceOrder.C2S.newBuilder()
                .setHeader(header)
                .setTrdSide(TRD_SIDE_SELL)
                .setOrderType(ORDER_TYPE_NORMAL)
                .setCode(code)
                .setPrice(fixedPrice)
                .setQty(qty)
                .setSecMarket(MARKET_HK)
                .build();

            ret = trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
            logger.info("simple_sell placeOrder ret={}", ret);
            sleep(500);
        }

        // ── smart_sell at best bid ─────────────────────────────────────
        logger.info("\n--- smart_sell at best bid ---");

        // Get lot_size again
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
        logger.info("getSecuritySnapshot ret={}", ret);
        sleep(500);

        qty = (long) (volume / lotSize) * lotSize;
        if (qty == 0) {
            logger.error("Volume {} is less than one lot ({}) — cannot sell", volume, lotSize);
        } else {
            logger.info("  adjusted qty={} (lot={})", qty, lotSize);

            // Get order book
            var obC2s = QotGetOrderBook.C2S.newBuilder()
                .setSecurity(sec)
                .setNum(1)
                .build();
            ret = qot.getOrderBook(QotGetOrderBook.Request.newBuilder().setC2S(obC2s).build());
            logger.info("getOrderBook ret={}", ret);
            sleep(500);

            if (bestBidPrice > 0) {
                logger.info("  best bid price={:.2f}", bestBidPrice);

                var orderC2s = TrdPlaceOrder.C2S.newBuilder()
                    .setHeader(header)
                    .setTrdSide(TRD_SIDE_SELL)
                    .setOrderType(ORDER_TYPE_NORMAL)
                    .setCode(code)
                    .setPrice(bestBidPrice)
                    .setQty(qty)
                    .setSecMarket(MARKET_HK)
                    .build();

                ret = trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
                logger.info("smart_sell placeOrder ret={}", ret);
            }
        }

        qot.close();
        trd.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) {
            if (client == qot) qotConnected = true;
            if (client == trd) trdConnected = true;
        }
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
    }

    // -------------------------------------------------------------------------
    // FTSPI_Qot
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetSecuritySnapshot(FTAPI_Conn client, int retCode, QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var list = rsp.getS2C().getSnapshotListList();
            if (!list.isEmpty()) {
                var snap = list.get(0);
                var basic = snap.getBasic();
                lotSize = basic.getLotSize();
                logger.info("    {} lot_size={}", basic.getSecurity().getCode(), lotSize);
            }
        }
    }

    @Override
    public void onReply_GetOrderBook(FTAPI_Conn client, int retCode, QotGetOrderBook.Response rsp) {
        logger.info("  [Qot] onReply_GetOrderBook: retCode={}", retCode);
        bestBidPrice = 0;
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var bidList = s2c.getOrderBookBidListList();
            int bidCount = bidList.size();
            if (bidCount > 0) {
                var bid = bidList.get(0);
                bestBidPrice = bid.getPrice();
                logger.info("    best bid: price={:.2f} vol={} ordCnt={}",
                    bestBidPrice, bid.getVolume(), bid.getOrederCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetAccList(FTAPI_Conn client, int retCode, TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        accList.clear();
        if (retCode == 0 && rsp.hasS2C()) {
            for (var acc : rsp.getS2C().getAccListList()) {
                int market = acc.getTrdMarketAuthListCount() > 0 ? acc.getTrdMarketAuthList(0) : 0;
                logger.info("    accId={} trdEnv={} firstMarket={}", acc.getAccID(), acc.getTrdEnv(), market);
                accList.add(new AccInfo(acc.getAccID(), market, acc.getTrdEnv()));
            }
        }
        accListLatch.countDown();
    }

    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int retCode, TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed");
    }

    @Override
    public void onReply_PlaceOrder(FTAPI_Conn client, int retCode, TrdPlaceOrder.Response rsp) {
        logger.info("  [Trd] onReply_PlaceOrder: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    orderID={} orderIDEx={}", s2c.getOrderID(), s2c.getOrderIDEx());
        } else {
            logger.warn("    placeOrder failed retCode={}", retCode);
        }
    }

    private static String md5(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
