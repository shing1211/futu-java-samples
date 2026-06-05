package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotStockScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.futu.sdk.examples.Helpers.sleep;
import static com.futu.sdk.examples.Helpers.CONNECTION_TIMEOUT_MS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 66: Multi-Criteria Stock Screener (SDK 10.7+)
 *
 * Demonstrates:
 *   - getStockScreen: the new powerful screener added in futu-api 10.7
 *   - ScreenQuery / RetrieveQuery / Sort filter pipeline
 *   - Pagination via pageFrom + pageCount
 *   - OneOf-based field selection (basic / simple / cumulative / financial / indicator)
 *
 * The QotStockScreen API is significantly richer than the legacy stockFilter:
 *   - Filters are typed (SimpleField, PropertySimple, PropertyFinancial, etc.)
 *   - Results carry back the specific properties you asked to retrieve
 *   - Sort accepts the same property types as filters
 *
 * This example shows the boilerplate (connect, build request, call, log response).
 * For real filters, build the appropriate QueryProperty* message and attach to
 * a ScreenQuery. See the proto definitions in futu-api for the full schema.
 *
 * Requires OpenD 10.7+ and SDK 10.7.6708+.
 */
public class Example66_StockScreen implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example66_StockScreen.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Stock Screen Demo (SDK 10.7+) ===");
        FTAPI.init();
        new Example66_StockScreen().start();
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
            // Minimal request — no filterList, no sortList.
            // Server returns the first page of stocks with no filtering,
            // useful for verifying the API call works end-to-end.
            queryLatch = new CountDownLatch(1);
            var c2s = QotStockScreen.C2S.newBuilder()
                .setPageFrom(0)
                .setPageCount(20)
                .build();
            int ret = qot.getStockScreen(QotStockScreen.Request.newBuilder().setC2S(c2s).build());
            logger.info("getStockScreen ret={}", ret);
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
    public void onReply_StockScreen(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    QotStockScreen.Response rsp) {
        logger.info("  [Qot] onReply_GetStockScreen: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    lastPage={} allCount={} dataListCount={}",
                s2c.getLastPage(), s2c.getAllCount(), s2c.getDataListCount());
            for (int i = 0; i < Math.min(s2c.getDataListCount(), 10); i++) {
                var item = s2c.getDataList(i);
                logger.info("      [{}] stockId={} results={}",
                    i, item.getStockId(), item.getResultsCount());
            }
            if (s2c.getDataListCount() > 10) {
                logger.info("      ... ({} more)", s2c.getDataListCount() - 10);
            }
        } else {
            logger.warn("    getStockScreen failed retCode={}", retCode);
        }
        queryLatch.countDown();
    }
}
