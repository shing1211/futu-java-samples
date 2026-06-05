package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotWarrantScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.futu.sdk.examples.Helpers.sleep;
import static com.futu.sdk.examples.Helpers.CONNECTION_TIMEOUT_MS;
import static com.futu.sdk.examples.Helpers.f2;
import static com.futu.sdk.examples.Helpers.f4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 68: Warrant Screener (SDK 10.7+)
 *
 * Demonstrates:
 *   - getWarrantScreen: the new warrant screener added in futu-api 10.7
 *   - marketType + isDelay flags
 *   - onlyCount short-circuit (returns count without full data)
 *   - Pagination via pageFrom + pageCount
 *
 * The QotWarrantScreen API is richer than the legacy getWarrant — it
 * supports structured filter groups (ScreenGroup) for warrant-specific
 * properties (issuer, leverage range, implied vol range, maturity range,
 * strike moneyness, call/put type, etc.) plus full sort and pagination.
 *
 * For a real filter, build QotWarrantScreen.ScreenGroup entries and add
 * to filterList. Each ScreenGroup wraps a typed filter
 * (sortField, sortType, issuer, price, strike, premium, leverage, etc.).
 *
 * Requires OpenD 10.7+ and SDK 10.7.6708+.
 */
public class Example68_WarrantScreen implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example68_WarrantScreen.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Warrant Screen Demo (SDK 10.7+) ===");
        FTAPI.init();
        new Example68_WarrantScreen().start();
    }

    public void start() {
        qot.setClientInfo("javaclient-qot", 1);
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

        int waited = 0;
        while (!connected && waited < CONNECTION_TIMEOUT_MS) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        try {
            // Minimal request — HK market (1), no delay, no filterList, page 1 of 20.
            // setOnlyCount(true) returns allCount without the data list — useful for UI.
            queryLatch = new CountDownLatch(1);
            var c2s = QotWarrantScreen.C2S.newBuilder()
                .setMarketType(1)
                .setIsDelay(false)
                .setOnlyCount(false)
                .setPageFrom(0)
                .setPageCount(20)
                .build();
            int ret = qot.getWarrantScreen(QotWarrantScreen.Request.newBuilder().setC2S(c2s).build());
            logger.info("getWarrantScreen ret={}", ret);
            try {
                queryLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            qot.close();
        }
        logger.info("Done.");
    }

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

    @Override
    public void onReply_WarrantScreen(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      QotWarrantScreen.Response rsp) {
        logger.info("  [Qot] onReply_GetWarrantScreen: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    lastPage={} allCount={} warrantsCount={}",
                s2c.getLastPage(), s2c.getAllCount(), s2c.getWarrantsCount());
            for (int i = 0; i < Math.min(s2c.getWarrantsCount(), 10); i++) {
                var w = s2c.getWarrants(i);
                logger.info("      [{}] stockId={} type={} strike={} price={} iv={}",
                    i, w.getStockId(), w.getWarrantType(), f2(w.getStrikePrice()),
                    f4(w.getCurrentPrice()), f4(w.getImpliedVolatility()));
            }
            if (s2c.getWarrantsCount() > 10) {
                logger.info("      ... ({} more)", s2c.getWarrantsCount() - 10);
            }
        } else {
            logger.warn("    getWarrantScreen failed retCode={}", retCode);
        }
        queryLatch.countDown();
    }
}
