package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetKL;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetFunds;
import com.futu.openapi.pb.TrdGetPositionList;
import com.futu.openapi.pb.TrdPlaceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 05: Quote + Trade (BUY order)
 *
 * Demonstrates:
 *   - Two contexts: quote (Qot) + trade (Trd)
 *   - getSecuritySnapshot: current price + lot_size
 *   - accinfo_query: buying power check
 *   - place_order: buy order on SIMULATE environment
 *   - Proper lot sizing (floor to lot multiples)
 *
 * Mirrors: examples/05_quote_trade/main.py from futu-python-samples
 *
 * Note: This uses SIMULATE trading environment (no real orders).
 */
public class Example05_QuoteTrade implements FTSPI_Qot, FTSPI_Conn, FTSPI_Trd {

    private static final Logger logger = LoggerFactory.getLogger(Example05_QuoteTrade.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean qotConnected = false;
    private volatile boolean trdConnected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // TrdEnv: Simulate=1, Real=2
    private static final int TRD_ENV_SIMULATE = 1;

    // TrdSide: Buy=1, Sell=2
    private static final int TRD_SIDE_BUY = 1;
    private static final int TRD_SIDE_SELL = 2;

    // OrderType: Normal=0
    private static final int ORDER_TYPE_NORMAL = 0;

    public static void main(String[] args) {
        logger.info("=== Quote + Trade Demo ===");
        FTAPI.init();
        Example05_QuoteTrade demo = new Example05_QuoteTrade();
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

        // ── Get account list (find first HK simulated account) ─────────
        logger.info("Fetching account list...");
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().build());
        if (ret != 0) { logger.error("getAccList failed ret={}", ret); return; }
        sleep(300);
        if (accList.isEmpty()) { logger.error("No trading accounts found!"); return; }

        var acc = accList.get(0);
        logger.info("Using account: accId={} market={} env={}", acc.accId, acc.trdMarket, acc.trdEnv);

        // ── Build TrdHeader (used in all trd requests) ──────────────────
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(acc.accId)
            .setTrdMarket(acc.trdMarket)
            .build();

        // ── Get market snapshot for HK.00700 ───────────────────────────
        logger.info("\n--- getSecuritySnapshot: HK.00700 ---");
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addSecurityList(sec)
            .build();
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
        logger.info("getSecuritySnapshot ret={}", ret);
        sleep(300);

        // ── Check account funds ─────────────────────────────────────────
        logger.info("\n--- accinfo_query (SIMULATE) ---");
        var fundsC2s = TrdGetFunds.C2S.newBuilder()
            .setHeader(header)
            .build();
        ret = trd.getFunds(TrdGetFunds.Request.newBuilder().setC2S(fundsC2s).build());
        logger.info("getFunds ret={}", ret);
        sleep(300);

        // ── Check existing positions ───────────────────────────────────
        logger.info("\n--- position_list_query (SIMULATE) ---");
        var posC2s = TrdGetPositionList.C2S.newBuilder()
            .setHeader(header)
            .build();
        ret = trd.getPositionList(TrdGetPositionList.Request.newBuilder().setC2S(posC2s).build());
        logger.info("getPositionList ret={}", ret);
        sleep(300);

        // ── Place a BUY order (SIMULATE) ──────────────────────────────
        // Note: SIMULATE env does not require unlock. REAL env requires unlockTrade.
        if (lastSnapshotPrice > 0 && lotSize > 0) {
            logger.info("\n--- place_order: BUY HK.00700 at {:.2f} (simulate) ---", lastSnapshotPrice);
            double buyingPower = lastFundsPower;

            // Calculate qty: floor(buying_power / price) rounded down to nearest lot
            long qty = (long) (buyingPower / lastSnapshotPrice);
            qty = (qty / lotSize) * lotSize;

            if (qty >= lotSize) {
                var orderC2s = TrdPlaceOrder.C2S.newBuilder()
                    .setHeader(header)
                    .setTrdSide(TRD_SIDE_BUY)
                    .setOrderType(ORDER_TYPE_NORMAL)
                    .setCode("00700")
                    .setPrice(lastSnapshotPrice)
                    .setQty(qty)
                    .setSecMarket(MARKET_HK)
                    .build();

                ret = trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
                logger.info("placeOrder ret={}", ret);
            } else {
                logger.info("Insufficient buying power. power={:.2f} price={:.2f} → qty={} < lot_size={}",
                    buyingPower, lastSnapshotPrice, qty, lotSize);
            }
        }

        sleep(500);

        qot.close();
        trd.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private record AccInfo(long accId, int trdMarket, int trdEnv) {}
    private java.util.List<AccInfo> accList = new java.util.ArrayList<>();
    private double lastSnapshotPrice = 0;
    private int lotSize = 0;
    private double lastFundsPower = 0;

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(com.futu.openapi.FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) {
            if (client == qot) qotConnected = true;
            if (client == trd) trdConnected = true;
        }
    }

    @Override
    public void onDisconnect(com.futu.openapi.FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode, TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        accList.clear();
        if (retCode == 0 && rsp.hasS2C()) {
            for (var acc : rsp.getS2C().getAccListList()) {
                int market = acc.getTrdMarketAuthListCount() > 0 ? acc.getTrdMarketAuthList(0) : 0;
                logger.info("    accId={} trdEnv={} firstMarket={}", acc.getAccID(), acc.getTrdEnv(), market);
                accList.add(new AccInfo(acc.getAccID(), market, acc.getTrdEnv()));
            }
        }
    }

    @Override
    public void onReply_GetFunds(com.futu.openapi.FTAPI_Conn client, int retCode, TrdGetFunds.Response rsp) {
        logger.info("  [Trd] onReply_GetFunds: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var funds = rsp.getS2C().getFunds();
            logger.info("    power={} cash={} total_assets={} market_val={}",
                funds.getPower(), funds.getCash(), funds.getTotalAssets(), funds.getMarketVal());
            lastFundsPower = funds.getPower();
        }
    }

    @Override
    public void onReply_GetPositionList(com.futu.openapi.FTAPI_Conn client, int retCode, TrdGetPositionList.Response rsp) {
        logger.info("  [Trd] onReply_GetPositionList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var list = rsp.getS2C().getPositionListList();
            if (list.isEmpty()) {
                logger.info("    No positions");
            } else {
                for (var pos : list) {
                    logger.info("    {} {} qty={} price={:.2f} cost={:.2f}",
                        pos.getCode(), pos.getName(), (long) pos.getQty(),
                        pos.getPrice(), pos.getCostPrice());
                }
            }
        }
    }

    @Override
    public void onReply_PlaceOrder(com.futu.openapi.FTAPI_Conn client, int retCode, TrdPlaceOrder.Response rsp) {
        logger.info("  [Trd] onReply_PlaceOrder: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    orderID={} orderIDEx={}", s2c.getOrderID(), s2c.getOrderIDEx());
        } else {
            logger.warn("    placeOrder failed retCode={}", retCode);
        }
    }

    // -------------------------------------------------------------------------
    // FTSPI_Qot
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetSecuritySnapshot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var list = rsp.getS2C().getSnapshotListList();
            if (!list.isEmpty()) {
                var snap = list.get(0);
                var basic = snap.getBasic();
                var exData = snap.hasEquityExData() ? snap.getEquityExData() : null;
                lastSnapshotPrice = basic.getCurPrice();
                lotSize = basic.getLotSize();
                double changeRate = (exData != null && basic.getLastClosePrice() > 0)
                    ? (basic.getCurPrice() - basic.getLastClosePrice()) / basic.getLastClosePrice() * 100
                    : 0;
                logger.info("    {} name={} last_price={:.2f} lot_size={} change={:.2f}% open={:.2f} high={:.2f} low={:.2f}",
                    basic.getSecurity().getCode(), basic.getName(), basic.getCurPrice(), lotSize,
                    changeRate, basic.getOpenPrice(), basic.getHighPrice(), basic.getLowPrice());
            }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}