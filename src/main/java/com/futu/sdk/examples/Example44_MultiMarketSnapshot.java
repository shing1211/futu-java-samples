package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example 44: Multi-Market Snapshot (Concurrent Queries)
 *
 * Demonstrates:
 *   - Concurrent getSecuritySnapshot queries across multiple markets (HK, US, SH, SZ)
 *   - Using CountDownLatch to coordinate parallel queries
 *   - Building Security lists with QotCommon.Security for each market
 *   - Aggregating results from all markets
 *
 * Mirrors: examples/44_multi_market_snapshot/main.py from futu-python-samples
 */
public class Example44_MultiMarketSnapshot implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example44_MultiMarketSnapshot.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market IDs: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;
    private static final int MARKET_SH = 4;
    private static final int MARKET_SZ = 5;

    // Snapshot results per market
    private record SnapshotResult(String market, String code, String name, double price, int lotSize) {}
    private final List<SnapshotResult> results = new ArrayList<>();
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Multi-Market Snapshot Demo ===");
        FTAPI.init();
        Example44_MultiMarketSnapshot demo = new Example44_MultiMarketSnapshot();
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

        // ── Build security lists for each market ─────────────────────────
        // HK: Tencent, HSImain
        List<QotCommon.Security> hkSecs = new ArrayList<>();
        hkSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build());
        hkSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build());

        // US: AAPL, NVDA
        List<QotCommon.Security> usSecs = new ArrayList<>();
        usSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("AAPL").build());
        usSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NVDA").build());

        // SH: 600000 (Shanghai Pudong Bank), 600519 (Kweichow Moutai)
        List<QotCommon.Security> shSecs = new ArrayList<>();
        shSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_SH).setCode("600000").build());
        shSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_SH).setCode("600519").build());

        // SZ: 000001 (Ping An Bank), 000858 (Wuliangye Yibin)
        List<QotCommon.Security> szSecs = new ArrayList<>();
        szSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_SZ).setCode("000001").build());
        szSecs.add(QotCommon.Security.newBuilder().setMarket(MARKET_SZ).setCode("000858").build());

        // ── Launch concurrent snapshot queries using CountDownLatch ───────
        logger.info("\n=== Launching concurrent snapshots across HK, US, SH, SZ ===");

        int numQueries = 4;
        pendingQueries.set(numQueries);
        queryLatch = new CountDownLatch(numQueries);

        // HK snapshot
        logger.info("\n--- HK Market Snapshot ---");
        var hkC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addAllSecurityList(hkSecs)
            .build();
        int ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(hkC2s).build());
        logger.info("getSecuritySnapshot(HK) ret={}", ret);

        // US snapshot
        logger.info("\n--- US Market Snapshot ---");
        var usC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addAllSecurityList(usSecs)
            .build();
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(usC2s).build());
        logger.info("getSecuritySnapshot(US) ret={}", ret);

        // SH snapshot
        logger.info("\n--- SH Market Snapshot ---");
        var shC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addAllSecurityList(shSecs)
            .build();
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(shC2s).build());
        logger.info("getSecuritySnapshot(SH) ret={}", ret);

        // SZ snapshot
        logger.info("\n--- SZ Market Snapshot ---");
        var szC2s = QotGetSecuritySnapshot.C2S.newBuilder()
            .addAllSecurityList(szSecs)
            .build();
        ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(szC2s).build());
        logger.info("getSecuritySnapshot(SZ) ret={}", ret);

        // Wait for all responses (up to 5 seconds)
        try {
            boolean completed = queryLatch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("Some queries timed out (pending={})", pendingQueries.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ── Summary ───────────────────────────────────────────────────────
        logger.info("\n=== Summary: {} stocks across 4 markets ===", results.size());
        for (SnapshotResult r : results) {
            logger.info("  [{}] {} {} price={:.2f} lot={}",
                r.market, r.code, r.name, r.price, r.lotSize);
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
    public void onReply_GetSecuritySnapshot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getSnapshotListCount();
            logger.info("    Snapshot count: {}", count);
            for (var snap : s2c.getSnapshotListList()) {
                var basic = snap.getBasic();
                var sec = basic.getSecurity();
                String marketLabel = marketLabel(sec.getMarket());
                double price = basic.getCurPrice();
                int lotSize = basic.getLotSize();
                String name = basic.getName();
                logger.info("    [{}] {} {} price={:.2f} lot={}",
                    marketLabel, sec.getCode(), name, price, lotSize);
                synchronized (results) {
                    results.add(new SnapshotResult(marketLabel, sec.getCode(), name, price, lotSize));
                }
            }
        } else {
            logger.warn("    getSecuritySnapshot failed retCode={}", retCode);
        }

        if (pendingQueries.decrementAndGet() == 0) {
            queryLatch.countDown();
        }
    }

    private static String marketLabel(int market) {
        return switch (market) {
            case 1 -> "HK";
            case 2 -> "US";
            case 4 -> "SH";
            case 5 -> "SZ";
            default -> "MKT(" + market + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
