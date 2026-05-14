package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetPlateSecurity;
import com.futu.openapi.pb.QotGetPlateSet;
import com.futu.openapi.pb.QotGetOwnerPlate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example 46: Plate-Based Stock Filtering
 *
 * Demonstrates:
 *   - getOwnerPlate: find all plates (sectors/concepts) a stock belongs to
 *   - getPlateSet: list all plates in a market
 *   - getPlateSecurity: get all stocks in a given plate
 *   - Two-stage workflow: stock -> plates -> all stocks in each plate
 *   - Using QotCommon.Security throughout for all security references
 *
 * Mirrors: examples/46_plate_stock_filter/main.py from futu-python-samples
 */
public class Example46_PlateStockFilter implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example46_PlateStockFilter.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market IDs
    private static final int MARKET_HK = 1;
    private static final int MARKET_SH = 4;
    private static final int MARKET_SZ = 5;

    // PlateSetType: All=0, Industry=1, Region=2, Concept=3
    private static final int PLATE_SET_INDUSTRY = 1;
    private static final int PLATE_SET_CONCEPT = 3;

    // Results tracking
    private final List<String> stockPlates = new ArrayList<>();
    private final List<String> plateStocks = new ArrayList<>();
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Plate-Based Stock Filter Demo ===");
        FTAPI.init();
        Example46_PlateStockFilter demo = new Example46_PlateStockFilter();
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

        // ── Stage 1: Get plates for a specific stock ─────────────────────
        // HK.00700 (Tencent) - find all plates it belongs to
        logger.info("\n=== Stage 1: Get plates for HK.00700 (Tencent) ===");
        var tencentSec = QotCommon.Security.newBuilder()
            .setMarket(MARKET_HK)
            .setCode("00700")
            .build();

        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var ownerC2s = QotGetOwnerPlate.C2S.newBuilder()
            .addSecurityList(tencentSec)
            .build();
        int ret = qot.getOwnerPlate(QotGetOwnerPlate.Request.newBuilder().setC2S(ownerC2s).build());
        logger.info("getOwnerPlate ret={}", ret);

        try {
            queryLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Log plates found
        logger.info("\n  Plates for HK.00700 ({} plates):", stockPlates.size());
        for (String p : stockPlates) {
            logger.info("    - {}", p);
        }

        // ── Stage 2: Get stocks in a specific plate ─────────────────────
        // Use the first plate found if any, otherwise use a known HK concept plate
        if (!stockPlates.isEmpty()) {
            String firstPlateCode = stockPlates.get(0);
            logger.info("\n=== Stage 2: Get stocks in plate [{}] ===", firstPlateCode);

            pendingQueries.set(1);
            queryLatch = new CountDownLatch(1);

            // Parse market from plate code format
            int plateMarket = MARKET_HK;
            if (firstPlateCode.contains("SH")) plateMarket = MARKET_SH;
            else if (firstPlateCode.contains("SZ")) plateMarket = MARKET_SZ;

            var plateSec = QotCommon.Security.newBuilder()
                .setMarket(plateMarket)
                .setCode(firstPlateCode)
                .build();

            var psC2s = QotGetPlateSecurity.C2S.newBuilder()
                .setPlate(plateSec)
                .build();
            ret = qot.getPlateSecurity(QotGetPlateSecurity.Request.newBuilder().setC2S(psC2s).build());
            logger.info("getPlateSecurity ret={}", ret);

            try {
                queryLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("\n  Stocks in plate [{}] ({} stocks):", firstPlateCode, plateStocks.size());
            for (String s : plateStocks) {
                logger.info("    - {}", s);
            }
        }

        // ── Stage 3: List all concept plates for HK ──────────────────────
        logger.info("\n=== Stage 3: List HK CONCEPT plates ===");
        pendingQueries.set(1);
        queryLatch = new CountDownLatch(1);

        var plateSetC2s = QotGetPlateSet.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setPlateSetType(PLATE_SET_CONCEPT)
            .build();
        ret = qot.getPlateSet(QotGetPlateSet.Request.newBuilder().setC2S(plateSetC2s).build());
        logger.info("getPlateSet ret={}", ret);

        try {
            queryLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
    public void onReply_GetOwnerPlate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetOwnerPlate.Response rsp) {
        logger.info("  [Qot] onReply_GetOwnerPlate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOwnerPlateListCount();
            logger.info("    Owner plates ({}):", count);
            synchronized (stockPlates) {
                for (var p : s2c.getOwnerPlateListList()) {
                    String code = p.getSecurity().getCode();
                    String name = p.getName();
                    logger.info("      {} [{}]", code, name);
                    stockPlates.add(code);
                }
            }
        } else {
            logger.warn("    getOwnerPlate failed retCode={}", retCode);
        }

        if (pendingQueries.decrementAndGet() == 0) {
            queryLatch.countDown();
        }
    }

    @Override
    public void onReply_GetPlateSecurity(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotGetPlateSecurity.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSecurity: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Stocks in plate ({}):", count);
            synchronized (plateStocks) {
                for (int i = 0; i < Math.min(count, 20); i++) {
                    var s = s2c.getStaticInfoList(i);
                    if (!s.hasBasic()) continue;
                    String code = s.getBasic().getSecurity().getCode();
                    String name = s.getBasic().getName();
                    logger.info("      [{}] {}", code, name);
                    plateStocks.add(code + " " + name);
                }
                if (count > 20) {
                    logger.info("      ... ({} more)", count - 20);
                }
            }
        } else {
            logger.warn("    getPlateSecurity failed retCode={}", retCode);
        }

        if (pendingQueries.decrementAndGet() == 0) {
            queryLatch.countDown();
        }
    }

    @Override
    public void onReply_GetPlateSet(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotGetPlateSet.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSet: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getPlateInfoListCount();
            logger.info("    Total HK concept plates: {}", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                var p = s2c.getPlateInfoList(i);
                logger.info("      plate[{}] {} [{}] type={}",
                    i, p.getPlate().getCode(), p.getName(), p.getPlateType());
            }
            if (count > 10) {
                logger.info("      ... ({} more plates)", count - 10);
            }
        } else {
            logger.warn("    getPlateSet failed retCode={}", retCode);
        }

        if (pendingQueries.decrementAndGet() == 0) {
            queryLatch.countDown();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
