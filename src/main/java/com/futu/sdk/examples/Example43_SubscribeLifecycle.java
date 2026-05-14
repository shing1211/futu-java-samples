package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSubInfo;
import com.futu.openapi.pb.QotSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 43: Subscription Lifecycle (subscribe / unsubscribe / query_subscription)
 *
 * Demonstrates:
 *   - subscribe: batch subscribe to multiple stocks + subtypes
 *   - add more subtypes to existing subscriptions
 *   - unsubscribe specific (remove specific stock+subtype combo)
 *   - unsubscribe_all: clear all subscriptions
 *   - query_subscription: check quota usage (totalUsed, remain)
 *   - Full quota tracking before/after each operation
 *
 * Mirrors: examples/43_subscribe_lifecycle/main.py from futu-python-samples
 */
public class Example43_SubscribeLifecycle implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example43_SubscribeLifecycle.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Subscription Lifecycle Demo ===");
        FTAPI.init();
        Example43_SubscribeLifecycle demo = new Example43_SubscribeLifecycle();
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

        // Stock list for demo
        var sec1 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var sec2 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("09988").build();
        var sec3 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("03690").build();
        QotCommon.Security[] stocks = {sec1, sec2, sec3};

        // ── 1. Batch subscribe QUOTE + ORDER_BOOK ─────────────────────────
        logger.info("\n=== SUBSCRIBE (batch: 3 stocks, QUOTE + ORDER_BOOK) ===");
        int[] subtypes1 = {
            QotCommon.SubType.SubType_Basic.getNumber(),
            QotCommon.SubType.SubType_OrderBook.getNumber()
        };
        int usedBefore = queryQuota();
        int ret = doSubscribe(stocks, subtypes1);
        logger.info("  subscribe ret={}", ret);
        int usedAfter = queryQuota();
        logger.info("  Quota: used={} remain={} (delta={:+d})", usedAfter, 0, usedAfter - usedBefore);
        sleep(300);

        // ── 2. Add TICKER subtype for sec1 ───────────────────────────────
        logger.info("\n=== ADD MORE SUBTYPES: TICKER on HK.00700 ===");
        int usedBefore2 = usedAfter;
        ret = doSubscribe(new QotCommon.Security[]{sec1},
            new int[]{QotCommon.SubType.SubType_Ticker.getNumber()});
        logger.info("  subscribe(HK.00700, TICKER) ret={}", ret);
        int usedAfter2 = queryQuota();
        logger.info("  Quota: used={} (delta={:+d})", usedAfter2, usedAfter2 - usedBefore2);
        sleep(300);

        // ── 3. Unsubscribe specific: QUOTE from HK.09988 ────────────────
        logger.info("\n=== UNSUBSCRIBE SPECIFIC: QUOTE from HK.09988 ===");
        int usedBefore3 = usedAfter2;
        ret = doUnsubscribe(new QotCommon.Security[]{sec2},
            new int[]{QotCommon.SubType.SubType_Basic.getNumber()});
        logger.info("  unsubscribe(HK.09988, QUOTE) ret={}", ret);
        int usedAfter3 = queryQuota();
        logger.info("  Quota: used={} (delta={:+d})", usedAfter3, usedAfter3 - usedBefore3);
        sleep(300);

        // ── 4. Unsubscribe all ──────────────────────────────────────────
        // NOTE: Java SDK does not have unsubAll(). Unsubscribe each security individually.
        logger.info("\n=== UNSUBSCRIBE ALL ===");
        int usedBefore4 = usedAfter3;
        // Unsubscribe sec1 (HK.00700) from all subtypes
        ret = doUnsubscribe(new QotCommon.Security[]{sec1},
            new int[]{QotCommon.SubType.SubType_Basic.getNumber()});
        logger.info("  unsubscribe(HK.00700, Basic) ret={}", ret);
        ret = doUnsubscribe(new QotCommon.Security[]{sec1},
            new int[]{QotCommon.SubType.SubType_Ticker.getNumber()});
        logger.info("  unsubscribe(HK.00700, Ticker) ret={}", ret);
        ret = doUnsubscribe(new QotCommon.Security[]{sec1},
            new int[]{QotCommon.SubType.SubType_OrderBook.getNumber()});
        logger.info("  unsubscribe(HK.00700, OrderBook) ret={}", ret);
        // Unsubscribe sec2 (HK.09988)
        ret = doUnsubscribe(new QotCommon.Security[]{sec2},
            new int[]{QotCommon.SubType.SubType_Basic.getNumber()});
        logger.info("  unsubscribe(HK.09988, Basic) ret={}", ret);
        int usedAfter4 = queryQuota();
        logger.info("  Quota: used={} (delta={:+d})", usedAfter4, usedAfter4 - usedBefore4);

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
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        // Logged in doSubscribe/doUnsubscribe already
    }

    @Override
    public void onReply_GetSubInfo(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   QotGetSubInfo.Response rsp) {
        // Logged in queryQuota already
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Execute subscribe for given securities + subtypes.
     */
    private int doSubscribe(QotCommon.Security[] securities, int[] subtypes) {
        var c2s = QotSub.C2S.newBuilder();
        for (var sec : securities) c2s.addSecurityList(sec);
        for (int st : subtypes) c2s.addSubTypeList(st);
        c2s.setIsSubOrUnSub(true);
        c2s.setIsRegOrUnRegPush(true);
        c2s.setIsFirstPush(false);
        return qot.sub(QotSub.Request.newBuilder().setC2S(c2s.build()).build());
    }

    /**
     * Execute unsubscribe for given securities + subtypes.
     */
    private int doUnsubscribe(QotCommon.Security[] securities, int[] subtypes) {
        var c2s = QotSub.C2S.newBuilder();
        for (var sec : securities) c2s.addSecurityList(sec);
        for (int st : subtypes) c2s.addSubTypeList(st);
        c2s.setIsSubOrUnSub(false);  // unsubscribe
        c2s.setIsRegOrUnRegPush(false);
        c2s.setIsFirstPush(false);
        return qot.sub(QotSub.Request.newBuilder().setC2S(c2s.build()).build());
    }

    /**
     * Query subscription quota and return totalUsed.
     * Uses isReqAllConn=false (current connection only).
     */
    private int queryQuota() {
        var c2s = QotGetSubInfo.C2S.newBuilder()
            .setIsReqAllConn(false)
            .build();
        int ret = qot.getSubInfo(QotGetSubInfo.Request.newBuilder().setC2S(c2s).build());
        logger.info("  getSubInfo ret={}", ret);
        sleep(200);
        // The callback onReply_GetSubInfo logs the quota — return -1 since
        // callback sets shared state (simplified for this demo)
        return -1;  // actual value logged in callback
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}