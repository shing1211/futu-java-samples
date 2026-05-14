package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import com.futu.openapi.pb.QotRequestHistoryKL;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetPositionList;
import com.futu.openapi.pb.TrdPlaceOrder;
import com.futu.openapi.pb.TrdUnlockTrade;
import com.futu.openapi.pb.TrdGetFunds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 04: MACD Quantitative Trading Strategy
 *
 * Demonstrates:
 *   - request_history_kline: fetch historical K-line data
 *   - MACD calculation from close prices
 *   - getSecuritySnapshot: fetch current price + lot_size
 *   - position_list_query: check current holdings
 *   - accinfo_query: check buying power
 *   - place_order: buy/sell with proper lot sizing
 *   - Proper logging of all fields and signals
 *
 * Entry signal: MACD line crosses above signal line (golden cross)
 * Exit signal:  MACD line crosses below signal line (death cross)
 *
 * Mirrors: examples/04_macd_strategy/main.py from futu-python-samples
 */
public class Example04_MacdStrategy implements FTSPI_Qot, FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example04_MacdStrategy.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean qotConnected = false;
    private volatile boolean trdConnected = false;

    // MACD parameters
    private static final int SHORT_PERIOD = 12;
    private static final int LONG_PERIOD = 26;
    private static final int SMOOTH_PERIOD = 9;
    private static final int OBSERVATION = 100;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;

    // TrdSide: Buy=1, Sell=2
    private static final int TRD_SIDE_BUY = 1;
    private static final int TRD_SIDE_SELL = 2;

    // OrderType: Normal=1
    private static final int ORDER_TYPE_NORMAL = 1;

    // KLType: Day=9
    private static final int KL_TYPE_DAY = 9;

    // RehabType: Forward=1 (qfq)
    private static final int REHAB_FORWARD = 1;

    // State
    private record AccInfo(long accId, int trdMarket, int trdEnv) {}
    private java.util.List<AccInfo> accList = new java.util.ArrayList<>();
    private final CountDownLatch accListLatch = new CountDownLatch(1);

    private double lastSnapshotPrice = 0;
    private int lotSize = 0;
    private double lastFundsPower = 0;
    private int currentPositionQty = 0;
    private boolean hasPosition = false;

    // Latches for async responses
    private final CountDownLatch historyKLReqLatch = new CountDownLatch(1);
    private final CountDownLatch snapshotLatch = new CountDownLatch(1);
    private final CountDownLatch positionLatch = new CountDownLatch(1);
    private final CountDownLatch fundsLatch = new CountDownLatch(1);
    private final CountDownLatch placeOrderLatch = new CountDownLatch(1);

    // History K-line data
    private volatile java.util.List<Double> closePrices = new java.util.ArrayList<>();
    private volatile String historyKLRet = "";

    public static void main(String[] args) {
        logger.info("=== MACD Strategy Demo ===");
        logger.info("Stock: HK.00700 | short={} long={} smooth={} observation={}",
            SHORT_PERIOD, LONG_PERIOD, SMOOTH_PERIOD, OBSERVATION);
        FTAPI.init();
        Example04_MacdStrategy demo = new Example04_MacdStrategy();
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

        // ── SIMULATE env does not require unlock ───────────────────────
        logger.info("Trade unlocked (SIMULATE mode — no password needed)");

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

        String stockCode = "HK.00700";
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

        // ── Fetch historical K-line ───────────────────────────────────
        LocalDate endDate = LocalDate.now();
        LocalDate beginDate = endDate.minusDays(OBSERVATION);
        logger.info("\n=== Fetching history kline: {} to {} ===", beginDate, endDate);

        var histC2s = QotRequestHistoryKL.C2S.newBuilder()
            .setSecurity(sec)
            .setKlType(KL_TYPE_DAY)
            .setRehabType(REHAB_FORWARD)
            .setBeginTime(beginDate.toString())
            .setEndTime(endDate.toString())
            .setMaxAckKLNum(200)
            .build();
        ret = qot.requestHistoryKL(QotRequestHistoryKL.Request.newBuilder().setC2S(histC2s).build());
        logger.info("requestHistoryKL ret={}", ret);

        try { historyKLReqLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        logger.info("History KL response ret={}", historyKLRet);

        // ── Compute MACD ──────────────────────────────────────────────
        if (closePrices.size() < LONG_PERIOD + SMOOTH_PERIOD) {
            logger.error("Not enough kline data ({} bars) for MACD (need {})",
                closePrices.size(), LONG_PERIOD + SMOOTH_PERIOD);
            qot.close(); trd.close();
            return;
        }

        double[] closeArr = new double[closePrices.size()];
        for (int i = 0; i < closePrices.size(); i++) closeArr[i] = closePrices.get(i);

        // Calculate EMAs
        double[] emaFast = calculateEMA(closeArr, SHORT_PERIOD);
        double[] emaSlow = calculateEMA(closeArr, LONG_PERIOD);

        int lastIdx = emaFast.length - 1;
        double latestMacd = emaFast[lastIdx] - emaSlow[lastIdx];
        double prevMacd = emaFast[lastIdx - 1] - emaSlow[lastIdx - 1];

        // MACD signal line
        double[] macdLine = new double[emaFast.length];
        for (int i = 0; i < macdLine.length; i++) macdLine[i] = emaFast[i] - emaSlow[i];
        double[] signal = calculateEMA(macdLine, SMOOTH_PERIOD);

        double latestSignal = signal[signal.length - 1];
        double prevSignal = signal[signal.length - 2];
        double latestHist = latestMacd - latestSignal;

        logger.info("MACD: macd={:.4f} signal={:.4f} hist={:.4f}", latestMacd, latestSignal, latestHist);
        logger.info("Prev: macd={:.4f} signal={:.4f}", prevMacd, prevSignal);

        // ── Check positions ───────────────────────────────────────────
        logger.info("\n--- Portfolio Check ---");
        var posC2s = TrdGetPositionList.C2S.newBuilder()
            .setHeader(header)
            .build();
        ret = trd.getPositionList(TrdGetPositionList.Request.newBuilder().setC2S(posC2s).build());
        logger.info("getPositionList ret={}", ret);
        sleep(500);

        // ── Determine signal ───────────────────────────────────────────
        boolean sellSignal = latestMacd < latestSignal && prevMacd > prevSignal;
        boolean buySignal = latestMacd > latestSignal && prevMacd < prevSignal;

        if (sellSignal) {
            logger.info("\n>>> SELL SIGNAL (death cross) — MACD crossed below signal");

            if (hasPosition && currentPositionQty > 0) {
                // Get current price
                var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
                    .addSecurityList(sec)
                    .build();
                ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
                logger.info("getSecuritySnapshot ret={}", ret);
                sleep(500);

                if (lastSnapshotPrice > 0) {
                    logger.info("  Current price: {:.2f} | lot_size: {}", lastSnapshotPrice, lotSize);

                    var orderC2s = TrdPlaceOrder.C2S.newBuilder()
                        .setHeader(header)
                        .setTrdSide(TRD_SIDE_SELL)
                        .setOrderType(ORDER_TYPE_NORMAL)
                        .setCode("00700")
                        .setPrice(lastSnapshotPrice)
                        .setQty(currentPositionQty)
                        .setSecMarket(MARKET_HK)
                        .build();

                    ret = trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
                    logger.info("placeOrder SELL ret={}", ret);
                    sleep(500);
                }
            } else {
                logger.info("  No position to sell");
            }
        } else if (buySignal) {
            logger.info("\n>>> BUY SIGNAL (golden cross) — MACD crossed above signal");

            // Get account funds
            logger.info("Checking buying power...");
            var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
                .addSecurityList(sec)
                .build();
            ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
            logger.info("getSecuritySnapshot ret={}", ret);
            sleep(500);

            if (lastSnapshotPrice > 0 && lotSize > 0) {
                double buyingPower = lastFundsPower;
                logger.info("  Buying power: {:.2f}", buyingPower);
                logger.info("  Current price: {:.2f} | lot_size: {}", lastSnapshotPrice, lotSize);

                long qty = (long) (buyingPower / lastSnapshotPrice);
                qty = (qty / lotSize) * lotSize;
                logger.info("  Calculated qty: {} (buying_power={:.2f} / price={:.2f})", qty, buyingPower, lastSnapshotPrice);

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
                    logger.info("placeOrder BUY ret={}", ret);
                } else {
                    logger.info("  qty < lot_size, skipping buy");
                }
            }
        } else {
            logger.info("\n>>> No signal (MACD={:.4f}, Signal={:.4f}) — holding{}",
                latestMacd, latestSignal,
                hasPosition ? " " + currentPositionQty + " shares" : "");
        }

        sleep(500);
        qot.close();
        trd.close();
        logger.info("Done.");
    }

    // EMA calculation helper
    private double[] calculateEMA(double[] data, int period) {
        double[] ema = new double[data.length];
        double multiplier = 2.0 / (period + 1);
        ema[0] = data[0];
        for (int i = 1; i < data.length; i++) {
            ema[i] = (data[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }
        return ema;
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
                lastSnapshotPrice = basic.getCurPrice();
                lotSize = basic.getLotSize();
                logger.info("    {} name={} last_price={:.2f} lot_size={}",
                    basic.getSecurity().getCode(), basic.getName(), basic.getCurPrice(), lotSize);
            }
        }
        snapshotLatch.countDown();
    }

    @Override
    public void onReply_RequestHistoryKL(FTAPI_Conn client, int retCode, QotRequestHistoryKL.Response rsp) {
        logger.info("  [Qot] onReply_RequestHistoryKL: retCode={}", retCode);
        historyKLRet = "retCode=" + retCode;
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    {}: {} bars (nextReqKey present={})",
                s2c.getSecurity().getCode(), s2c.getKlListCount(), s2c.hasNextReqKey());

            closePrices.clear();
            for (var bar : s2c.getKlListList()) {
                closePrices.add(bar.getClosePrice());
                logger.info("      {} close={:.2f}", bar.getTime(), bar.getClosePrice());
            }
        } else {
            logger.warn("    requestHistoryKL failed retCode={}", retCode);
        }
        historyKLReqLatch.countDown();
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
    public void onReply_GetPositionList(FTAPI_Conn client, int retCode, TrdGetPositionList.Response rsp) {
        logger.info("  [Trd] onReply_GetPositionList: retCode={}", retCode);
        hasPosition = false;
        currentPositionQty = 0;
        if (retCode == 0 && rsp.hasS2C()) {
            var list = rsp.getS2C().getPositionListList();
            if (list.isEmpty()) {
                logger.info("    No positions");
            } else {
                logger.info("    Positions ({} rows):", list.size());
                for (var pos : list) {
                    logger.info("      {} {} qty={} price={:.2f} cost={:.2f}",
                        pos.getCode(), pos.getName(), (long) pos.getQty(),
                        pos.getPrice(), pos.getCostPrice());
                    if ("00700".equals(pos.getCode())) {
                        currentPositionQty = (int) pos.getQty();
                        hasPosition = currentPositionQty > 0;
                    }
                }
            }
        }
        positionLatch.countDown();
    }

    @Override
    public void onReply_GetFunds(FTAPI_Conn client, int retCode, TrdGetFunds.Response rsp) {
        logger.info("  [Trd] onReply_GetFunds: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var funds = rsp.getS2C().getFunds();
            logger.info("    power={} cash={} total_assets={} market_val={}",
                funds.getPower(), funds.getCash(), funds.getTotalAssets(), funds.getMarketVal());
            lastFundsPower = funds.getPower();
        }
        fundsLatch.countDown();
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
        placeOrderLatch.countDown();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
