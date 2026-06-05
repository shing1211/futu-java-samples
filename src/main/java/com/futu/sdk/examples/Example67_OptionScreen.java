package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotOptionScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.futu.sdk.examples.Helpers.sleep;
import static com.futu.sdk.examples.Helpers.CONNECTION_TIMEOUT_MS;
import static com.futu.sdk.examples.Helpers.f2;
import static com.futu.sdk.examples.Helpers.f4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 67: Option Screener (SDK 10.7+)
 *
 * Demonstrates:
 *   - getOptionScreen: the new option screener added in futu-api 10.7
 *   - marketCategoryList filter (HK option categories)
 *   - Pagination via pageFrom + pageCount
 *
 * The QotOptionScreen API lets you screen options by their underlying
 * (implied vol, open interest, Greeks, etc.) and by option-level properties
 * (strike range, expiry, type).
 *
 * For a real filter, build a QotOptionScreen.FilterGroup and add it to
 * filterList. The FilterGroup wraps a typed filter (one of:
 * impliedVol, premium, delta, gamma, vega, theta, openInterest, volume,
 * etc.) plus its min/max boundary.
 *
 * Requires OpenD 10.7+ and SDK 10.7.6708+.
 */
public class Example67_OptionScreen implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example67_OptionScreen.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Option Screen Demo (SDK 10.7+) ===");
        FTAPI.init();
        new Example67_OptionScreen().start();
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
            // Minimal request — no filterList, page 1 of 20.
            queryLatch = new CountDownLatch(1);
            var c2s = QotOptionScreen.C2S.newBuilder()
                .setPageFrom(0)
                .setPageCount(20)
                .build();
            int ret = qot.getOptionScreen(QotOptionScreen.Request.newBuilder().setC2S(c2s).build());
            logger.info("getOptionScreen ret={}", ret);
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
    public void onReply_OptionScreen(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotOptionScreen.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionScreen: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    lastPage={} allCount={} dataListCount={}",
                s2c.getLastPage(), s2c.getAllCount(), s2c.getDataListCount());
            for (int i = 0; i < Math.min(s2c.getDataListCount(), 10); i++) {
                var item = s2c.getDataList(i);
                String code = item.hasSecurity() ? item.getSecurity().getCode() : "?";
                logger.info("      [{}] {} strike={} price={} vol={} oi={} {}d",
                    i, code, f2(item.getStrikePrice()), f4(item.getPrice()),
                    item.getVolume(), item.getOpenInterest(), item.getLeftDay());
            }
            if (s2c.getDataListCount() > 10) {
                logger.info("      ... ({} more)", s2c.getDataListCount() - 10);
            }
        } else {
            logger.warn("    getOptionScreen failed retCode={}", retCode);
        }
        queryLatch.countDown();
    }
}
