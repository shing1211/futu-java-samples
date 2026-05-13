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
 * Example 15: Subscription Management (query_subscription)
 *
 * Demonstrates:
 *   - subscribe: subscribe to multiple stock+subtype combos
 *   - getSubInfo: list all active subscriptions (isReqAllConn=false)
 *   - getSubInfo (all conn): list across all connections (isReqAllConn=true)
 *   - unsubscribe: remove subscriptions (setIsSubOrUnSub=false)
 *
 * Mirrors: examples/15_sub_list/main.py from futu-python-samples
 */
public class Example15_SubList implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example15_SubList.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;
    private static final int MARKET_SH = 4;

    public static void main(String[] args) {
        logger.info("=== Subscription Management Demo ===");
        FTAPI.init();
        Example15_SubList demo = new Example15_SubList();
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

        // ── Subscribe to multiple stocks & subtypes ───────────────────
        logger.info("\n--- Subscribe: HK.00700 + HK.HSImain ---");
        var sec1 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var sec2 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build();

        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec1)
            .addSecurityList(sec2)
            .addSubTypeList(QotCommon.SubType.SubType_Basic.getNumber())
            .addSubTypeList(QotCommon.SubType.SubType_KL_Day.getNumber())
            .addSubTypeList(QotCommon.SubType.SubType_OrderBook.getNumber())
            .addSubTypeList(QotCommon.SubType.SubType_Broker.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(false)
            .build();
        int ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);
        sleep(300);

        // ── Query subscription (current connection) ────────────────────
        logger.info("\n=== getSubInfo (current conn only) ===");
        var subInfoC2s = QotGetSubInfo.C2S.newBuilder()
            .setIsReqAllConn(false)
            .build();
        ret = qot.getSubInfo(QotGetSubInfo.Request.newBuilder().setC2S(subInfoC2s).build());
        logger.info("getSubInfo ret={}", ret);
        sleep(300);

        // ── Query subscription (all connections) ──────────────────────
        logger.info("\n=== getSubInfo (all connections) ===");
        var subInfoAllC2s = QotGetSubInfo.C2S.newBuilder()
            .setIsReqAllConn(true)
            .build();
        ret = qot.getSubInfo(QotGetSubInfo.Request.newBuilder().setC2S(subInfoAllC2s).build());
        logger.info("getSubInfo(all) ret={}", ret);
        sleep(300);

        // ── Unsubscribe KL_Day from HK.00700 ─────────────────────────
        logger.info("\n--- Unsubscribe: KL_Day from HK.00700 ---");
        var unsubC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec1)
            .addSubTypeList(QotCommon.SubType.SubType_KL_Day.getNumber())
            .setIsSubOrUnSub(false)  // false = unsubscribe
            .setIsRegOrUnRegPush(false)
            .setIsFirstPush(false)
            .build();
        ret = qot.sub(QotSub.Request.newBuilder().setC2S(unsubC2s).build());
        logger.info("unsubscribe ret={}", ret);
        sleep(300);

        // ── Re-query ─────────────────────────────────────────────────
        logger.info("\n=== getSubInfo after unsubscribe ===");
        ret = qot.getSubInfo(QotGetSubInfo.Request.newBuilder().setC2S(subInfoC2s).build());
        logger.info("getSubInfo ret={}", ret);
        sleep(300);

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
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode != 0) logger.warn("    sub failed retCode={}", retCode);
    }

    @Override
    public void onReply_GetSubInfo(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   QotGetSubInfo.Response rsp) {
        logger.info("  [Qot] onReply_GetSubInfo: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int totalUsed = s2c.getTotalUsedQuota();
            int remain = s2c.getRemainQuota();
            int connCount = s2c.getConnSubInfoListCount();
            logger.info("    TotalUsedQuota={} RemainQuota={} Connections={}",
                totalUsed, remain, connCount);
            for (int i = 0; i < connCount; i++) {
                var connInfo = s2c.getConnSubInfoList(i);
                int usedQuota = connInfo.getUsedQuota();
                boolean isOwn = connInfo.getIsOwnConnData();
                int subCount = connInfo.getSubInfoListCount();
                logger.info("      conn[{}] usedQuota={} isOwn={} subTypes={}",
                    i, usedQuota, isOwn, subCount);
                for (int j = 0; j < subCount; j++) {
                    var sub = connInfo.getSubInfoList(j);
                    int subType = sub.getSubType();
                    int secCount = sub.getSecurityListCount();
                    String codes = "";
                    for (int k = 0; k < Math.min(secCount, 5); k++) {
                        codes += sub.getSecurityList(k).getCode() + " ";
                    }
                    if (secCount > 5) codes += "...+" + (secCount - 5);
                    logger.info("        subType={} securities=[{}]",
                        subTypeLabel(subType), codes.trim());
                }
            }
        } else {
            logger.warn("    getSubInfo failed retCode={}", retCode);
        }
    }

    private static String subTypeLabel(int subType) {
        return switch (subType) {
            case 0 -> "None";
            case 1 -> "Basic";
            case 2 -> "OrderBook";
            case 3 -> "Ticker";
            case 4 -> "RT";
            case 5 -> "KL_Day";
            case 6 -> "KL_1Min";
            case 7 -> "KL_5Min";
            case 8 -> "KL_15Min";
            case 9 -> "KL_30Min";
            case 10 -> "KL_60Min";
            case 11 -> "KL_Week";
            case 12 -> "KL_Month";
            case 13 -> "Broker";
            default -> "SubType(" + subType + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}