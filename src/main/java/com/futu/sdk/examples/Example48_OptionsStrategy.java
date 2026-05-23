package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOptionChain;
import com.futu.openapi.pb.QotGetOptionExpirationDate;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example 48: Options Strategy Builder
 *
 * Demonstrates:
 *   - getOptionExpirationDate: fetch all expiry dates for an index/stock option
 *   - getOptionChain: fetch full chain for a given expiry
 *   - getSecuritySnapshot: get current underlying price
 *   - getSecuritySnapshot: get real-time option price & Greeks (delta, gamma, theta, vega)
 *   - Construct multi-leg strategies: Bull Call Spread, Bear Put Spread, etc.
 *   - Calculate net debit/credit and break-even for each strategy
 *
 * Strategies implemented:
 *   - Bull Call Spread: long call at lower strike + short call at higher strike
 *   - Bear Put Spread: long put at higher strike + short put at lower strike
 *
 * Mirrors: examples/48_options_strategy/main.py from futu-python-samples
 */
public class Example48_OptionsStrategy implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example48_OptionsStrategy.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: US=2
    private static final int MARKET_US = 2;

    // OptionType: CALL=1, PUT=2
    private static final int OPT_CALL = 1;
    private static final int OPT_PUT = 2;

    // IndexOptionType: NORMAL=1, WEEKLY=2, MONTHLY=3
    private static final int IDX_OPT_NORMAL = 1;

    // State
    private volatile double underlyingPrice = 0;
    private final List<OptionLeg> callLegs = new ArrayList<>();
    private final List<OptionLeg> putLegs = new ArrayList<>();
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private CountDownLatch queryLatch;

    // Option leg record
    private record OptionLeg(String code, int type /*1=CALL 2=PUT*/, double strike,
                             double price, double delta, double gamma,
                             double theta, double vega) {}

    public static void main(String[] args) {
        logger.info("=== Options Strategy Builder Demo ===");
        FTAPI.init();
        Example48_OptionsStrategy demo = new Example48_OptionsStrategy();
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

        try {
        // Underlying: US.NDX (Nasdaq 100 Index options)
        var owner = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NDX").build();

        // ── Step 1: Get underlying snapshot price ──────────────────────────
        logger.info("\n=== Step 1: Get underlying price for US.NDX ===");
        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addSecurityList(owner)
            .build();
        int ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
        logger.info("getSecuritySnapshot ret={}", ret);

        try { queryLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        logger.info("  Underlying NDX price: {:.2f}", underlyingPrice);

        // ── Step 2: Get expiration dates ───────────────────────────────────
        logger.info("\n=== Step 2: Get option expiration dates for US.NDX ===");
        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var expC2s = QotGetOptionExpirationDate.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(IDX_OPT_NORMAL)
            .build();
        ret = qot.getOptionExpirationDate(QotGetOptionExpirationDate.Request.newBuilder().setC2S(expC2s).build());
        logger.info("getOptionExpirationDate ret={}", ret);

        try { queryLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ── Step 3: Get CALL option chain ─────────────────────────────────
        logger.info("\n=== Step 3: Get CALL option chain (near-term expiry) ===");
        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var callChainC2s = QotGetOptionChain.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(IDX_OPT_NORMAL)
            .setType(OPT_CALL)
            .setBeginTime("2026-05-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getOptionChain(QotGetOptionChain.Request.newBuilder().setC2S(callChainC2s).build());
        logger.info("getOptionChain(CALL) ret={}", ret);

        try { queryLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ── Step 4: Get PUT option chain ───────────────────────────────────
        logger.info("\n=== Step 4: Get PUT option chain (near-term expiry) ===");
        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var putChainC2s = QotGetOptionChain.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(IDX_OPT_NORMAL)
            .setType(OPT_PUT)
            .setBeginTime("2026-05-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getOptionChain(QotGetOptionChain.Request.newBuilder().setC2S(putChainC2s).build());
        logger.info("getOptionChain(PUT) ret={}", ret);

        try { queryLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ── Step 5: Get option snapshots for real price & Greeks ──────────
        logger.info("\n=== Step 5: Get option snapshots for real price & Greeks ===");
        List<QotCommon.Security> optSecs = new ArrayList<>();
        synchronized (callLegs) {
            for (var leg : callLegs) {
                optSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode(leg.code).build());
            }
        }
        synchronized (putLegs) {
            for (var leg : putLegs) {
                optSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode(leg.code).build());
            }
        }

        if (!optSecs.isEmpty()) {
            pendingQueries.set(1);
            queryLatch = new CountDownLatch(1);

            var optSnapC2s = QotGetSecuritySnapshot.C2S.newBuilder()
                .addAllSecurityList(optSecs)
                .build();
            ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(optSnapC2s).build());
            logger.info("getSecuritySnapshot(options) ret={}", ret);

            try { queryLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        } else {
            logger.warn("  No option legs to query snapshots for");
        }

        // ── Step 6: Build and display strategies ───────────────────────────
        logger.info("\n=== Step 6: Strategy Construction ===");
        if (underlyingPrice > 0) {
            buildBullCallSpread();
            buildBearPutSpread();
        } else {
            logger.warn("  Skipping strategy build (no underlying price)");
        }

        } finally {
            qot.close();
        }
        logger.info("Done.");
    }

    /**
     * Replace an OptionLeg in the appropriate list with updated price/Greeks.
     */
    private void updateLegData(String code, double price, double delta, double gamma, double theta, double vega) {
        synchronized (callLegs) {
            for (int i = 0; i < callLegs.size(); i++) {
                var leg = callLegs.get(i);
                if (leg.code.equals(code)) {
                    callLegs.set(i, new OptionLeg(code, leg.type, leg.strike, price, delta, gamma, theta, vega));
                    return;
                }
            }
        }
        synchronized (putLegs) {
            for (int i = 0; i < putLegs.size(); i++) {
                var leg = putLegs.get(i);
                if (leg.code.equals(code)) {
                    putLegs.set(i, new OptionLeg(code, leg.type, leg.strike, price, delta, gamma, theta, vega));
                    return;
                }
            }
        }
    }

    private void buildBullCallSpread() {
        logger.info("\n  === Bull Call Spread ===");
        logger.info("  Strategy: Long Call (lower strike) + Short Call (higher strike)");
        logger.info("  Net Debit: (long call premium) - (short call premium)");
        logger.info("  Break-even: lower strike + net debit");

        if (callLegs.size() < 2) {
            logger.info("  Not enough call options to build spread (need 2, got {})", callLegs.size());
            return;
        }

        // Sort by strike ascending
        callLegs.sort((a, b) -> Double.compare(a.strike, b.strike));
        OptionLeg longCall = callLegs.get(0);
        OptionLeg shortCall = callLegs.get(1);

        double netDebit = longCall.price - shortCall.price;
        double breakEven = longCall.strike + netDebit;
        double maxProfit = shortCall.strike - longCall.strike - netDebit;
        double maxLoss = netDebit;

        // Net Greeks
        double netDelta = longCall.delta - shortCall.delta;
        double netGamma = longCall.gamma - shortCall.gamma;
        double netTheta = longCall.theta - shortCall.theta;
        double netVega = longCall.vega - shortCall.vega;

        logger.info("  Long Call:  {} strike={:.2f} price={:.4f} delta={:.4f}",
            longCall.code, longCall.strike, longCall.price, longCall.delta);
        logger.info("  Short Call: {} strike={:.2f} price={:.4f} delta={:.4f}",
            shortCall.code, shortCall.strike, shortCall.price, shortCall.delta);
        logger.info("  ─────────────────────────────");
        logger.info("  Net Debit:     {:.4f}", netDebit);
        logger.info("  Break-even:    {:.2f}", breakEven);
        logger.info("  Max Profit:   {:.4f} (if price > {:.2f} at expiry)",
            maxProfit, shortCall.strike);
        logger.info("  Max Loss:     {:.4f} (if price < {:.2f} at expiry)",
            maxLoss, longCall.strike);
        logger.info("  Net Greeks:  delta={:.4f} gamma={:.6f} theta={:.4f} vega={:.4f}",
            netDelta, netGamma, netTheta, netVega);
    }

    private void buildBearPutSpread() {
        logger.info("\n  === Bear Put Spread ===");
        logger.info("  Strategy: Long Put (higher strike) + Short Put (lower strike)");
        logger.info("  Net Debit: (long put premium) - (short put premium)");
        logger.info("  Break-even: higher strike - net debit");

        if (putLegs.size() < 2) {
            logger.info("  Not enough put options to build spread (need 2, got {})", putLegs.size());
            return;
        }

        // Sort by strike descending
        putLegs.sort((a, b) -> Double.compare(b.strike, a.strike));
        OptionLeg longPut = putLegs.get(0);
        OptionLeg shortPut = putLegs.get(1);

        double netDebit = longPut.price - shortPut.price;
        double breakEven = longPut.strike - netDebit;
        double maxProfit = longPut.strike - shortPut.strike - netDebit;
        double maxLoss = netDebit;

        // Net Greeks
        double netDelta = longPut.delta - shortPut.delta;
        double netGamma = longPut.gamma - shortPut.gamma;
        double netTheta = longPut.theta - shortPut.theta;
        double netVega = longPut.vega - shortPut.vega;

        logger.info("  Long Put:  {} strike={:.2f} price={:.4f} delta={:.4f}",
            longPut.code, longPut.strike, longPut.price, longPut.delta);
        logger.info("  Short Put: {} strike={:.2f} price={:.4f} delta={:.4f}",
            shortPut.code, shortPut.strike, shortPut.price, shortPut.delta);
        logger.info("  ─────────────────────────────");
        logger.info("  Net Debit:     {:.4f}", netDebit);
        logger.info("  Break-even:    {:.2f}", breakEven);
        logger.info("  Max Profit:   {:.4f} (if price < {:.2f} at expiry)",
            maxProfit, shortPut.strike);
        logger.info("  Max Loss:     {:.4f} (if price > {:.2f} at expiry)",
            maxLoss, longPut.strike);
        logger.info("  Net Greeks:  delta={:.4f} gamma={:.6f} theta={:.4f} vega={:.4f}",
            netDelta, netGamma, netTheta, netVega);
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
    public void onReply_GetSecuritySnapshot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var list = rsp.getS2C().getSnapshotListList();
            for (var snap : list) {
                var basic = snap.getBasic();
                if (snap.hasOptionExData()) {
                    var opt = snap.getOptionExData();
                    String code = basic.getSecurity().getCode();
                    double price = basic.getCurPrice();
                    double delta = opt.getDelta();
                    double gamma = opt.getGamma();
                    double theta = opt.getTheta();
                    double vega = opt.getVega();
                    logger.info("    Option: {} price={:.4f} delta={:.4f} gamma={:.6f} theta={:.4f} vega={:.4f}",
                        code, price, delta, gamma, theta, vega);
                    updateLegData(code, price, delta, gamma, theta, vega);
                } else {
                    underlyingPrice = basic.getCurPrice();
                    logger.info("    Underlying: {} price={:.2f}", basic.getSecurity().getCode(), underlyingPrice);
                }
            }
        } else {
            logger.warn("    getSecuritySnapshot failed retCode={}", retCode);
        }
        if (pendingQueries.decrementAndGet() == 0) queryLatch.countDown();
    }

    @Override
    public void onReply_GetOptionExpirationDate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                QotGetOptionExpirationDate.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionExpirationDate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getDateListCount();
            logger.info("    Expiration dates ({}):", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                logger.info("      exp[{}] = {}", i, s2c.getDateList(i));
            }
        } else {
            logger.warn("    getOptionExpirationDate failed retCode={}", retCode);
        }
        if (pendingQueries.decrementAndGet() == 0) queryLatch.countDown();
    }

    @Override
    public void onReply_GetOptionChain(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetOptionChain.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionChain: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int chainCount = s2c.getOptionChainCount();
            logger.info("    Option chains ({}):", chainCount);

            for (int i = 0; i < Math.min(chainCount, 3); i++) {
                var chain = s2c.getOptionChain(i);
                // getStrikeTime returns a String (protobuf int64 encoding)
                double strike = parseStrikeTime(chain.getStrikeTime());
                logger.info("      Expiry chain[{}]: strikeTime={} optionCount={}",
                    i, chain.getStrikeTime(), chain.getOptionCount());

                for (int j = 0; j < chain.getOptionCount(); j++) {
                    var item = chain.getOption(j);
                    if (item.hasCall()) {
                        var call = item.getCall();
                        if (call.hasBasic()) {
                            String code = call.getBasic().getSecurity().getCode();
                            // Price and Greeks will be fetched via getSecuritySnapshot in Step 5
                            OptionLeg leg = new OptionLeg(code, OPT_CALL, strike, 0.0, 0.0, 0.0, 0.0, 0.0);
                            synchronized (callLegs) {
                                callLegs.add(leg);
                            }
                            logger.info("        CALL: {} strike={}", code, strike);
                        }
                    }
                    if (item.hasPut()) {
                        var put = item.getPut();
                        if (put.hasBasic()) {
                            String code = put.getBasic().getSecurity().getCode();
                            OptionLeg leg = new OptionLeg(code, OPT_PUT, strike, 0.0, 0.0, 0.0, 0.0, 0.0);
                            synchronized (putLegs) {
                                putLegs.add(leg);
                            }
                            logger.info("        PUT:  {} strike={}", code, strike);
                        }
                    }
                }
            }
        } else {
            logger.warn("    getOptionChain failed retCode={}", retCode);
        }
        if (pendingQueries.decrementAndGet() == 0) queryLatch.countDown();
    }

    /**
     * Parse strike time string to double.
     * getStrikeTime() returns String due to protobuf int64 encoding in javalite.
     * The value represents the strike price in the chain.
     */
    private static double parseStrikeTime(String strikeTimeStr) {
        try {
            return Double.parseDouble(strikeTimeStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
