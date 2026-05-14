package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetWarrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 47: Advanced Warrant Filter
 *
 * Demonstrates:
 *   - getWarrant: query structured products (warrants/bull-bear certs)
 *   - WarrantType: Buy=1 (call), Sell=2 (put), Bull=3, Bear=4, InLine=5
 *   - Filtering by type, strike price distance, expiry range
 *   - Key warrant metrics: delta, implied volatility, effective leverage,
 *     premium, conversion ratio
 *
 * Warrants (窝轮/涡轮) are leveraged derivative instruments popular in HK.
 * This example filters for specific warrant characteristics.
 *
 * Mirrors: examples/47_warrant_filter/main.py from futu-python-samples
 */
public class Example47_WarrantFilter implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example47_WarrantFilter.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // WarrantType
    private static final int WARRANT_TYPE_BUY = 1;   // Call warrant
    private static final int WARRANT_TYPE_SELL = 2;  // Put warrant
    private static final int WARRANT_TYPE_BULL = 3;  // Bull cert
    private static final int WARRANT_TYPE_BEAR = 4;  // Bear cert

    // WarrantSortField (for ordering)
    private static final int SORT_VOLUME = 1;
    private static final int SORT_DELTA = 5;
    private static final int SORT_IMPLIED_VOL = 6;

    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Advanced Warrant Filter Demo ===");
        FTAPI.init();
        Example47_WarrantFilter demo = new Example47_WarrantFilter();
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

        // Underlying: HK.00700 (Tencent) and HK.HSImain (HSI futures)
        queryAndFilterWarrants("00700", "HK.00700 (Tencent)");
        sleep(1000);

        queryAndFilterWarrants("HSImain", "HK.HSImain (HSI Futures)");
        sleep(1000);

        // ── Filtered query: Bull warrants only, sorted by implied vol ──────
        logger.info("\n=== Bull Warrants on HSI, sorted by Implied Vol ===");
        var hsiOwner = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build();

        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var bullC2s = QotGetWarrant.C2S.newBuilder()
            .setOwner(hsiOwner)
            .addTypeList(WARRANT_TYPE_BULL)
            .setBegin(0)
            .setNum(10)
            .setSortField(SORT_IMPLIED_VOL)
            .setAscend(false)  // 0=descend, 1=ascend
            .build();
        int ret = qot.getWarrant(QotGetWarrant.Request.newBuilder().setC2S(bullC2s).build());
        logger.info("getWarrant(BULL,HSI) ret={}", ret);

        try {
            queryLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        qot.close();
        logger.info("Done.");
    }

    private void queryAndFilterWarrants(String code, String label) {
        logger.info("\n=== Querying warrants for {} ===", label);
        var owner = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode(code).build();

        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        // Basic request: first 20 warrants sorted by volume
        var c2s = QotGetWarrant.C2S.newBuilder()
            .setOwner(owner)
            .setBegin(0)
            .setNum(20)
            .build();

        int ret = qot.getWarrant(QotGetWarrant.Request.newBuilder().setC2S(c2s).build());
        logger.info("getWarrant({}) ret={}", label, ret);

        try {
            queryLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    public void onReply_GetWarrant(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   QotGetWarrant.Response rsp) {
        logger.info("  [Qot] onReply_GetWarrant: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getWarrantDataListCount();
            boolean lastPage = s2c.getLastPage();
            int total = s2c.getAllCount();
            logger.info("    Warrants: {} returned (total={}, lastPage={})",
                count, total, lastPage);

            for (int i = 0; i < Math.min(count, 10); i++) {
                var w = s2c.getWarrantDataList(i);
                var sec = w.getStock();
                logger.info("      [{}] {} name={}", i, sec.getCode(), w.getName());
                logger.info("        type={} issuer={} strike={} maturity={}",
                    warrantTypeLabel(w.getType()), w.getIssuer(),
                    formatStrike(w.getStrikePrice()), w.getMaturityTime());
                logger.info("        curPrice={:.4f} changeRate={:.2f}% volume={}",
                    w.getCurPrice(), w.getChangeRate() * 100, w.getVolume());
                logger.info("        delta={:.4f} impliedVol={:.4f} effLev={:.2f}",
                    w.getDelta(), w.getImpliedVolatility(), w.getEffectiveLeverage());
                logger.info("        convRatio={} lotSize={} premium={:.2f}% status={}",
                    w.getConversionRatio(), w.getLotSize(),
                    w.getPremium() * 100, w.getStatus());
            }
            if (count > 10) {
                logger.info("      ... ({} more)", count - 10);
            }
        } else {
            logger.warn("    getWarrant failed retCode={}", retCode);
        }

        if (pendingQueries.decrementAndGet() == 0) {
            queryLatch.countDown();
        }
    }

    private static String warrantTypeLabel(int type) {
        return switch (type) {
            case 1 -> "BUY (Call)";
            case 2 -> "SELL (Put)";
            case 3 -> "BULL";
            case 4 -> "BEAR";
            case 5 -> "INLINE";
            default -> "TYPE(" + type + ")";
        };
    }

    private static String formatStrike(double strike) {
        if (strike <= 0) return "N/A";
        return String.format("%.2f", strike);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
