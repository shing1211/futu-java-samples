package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetOrderList;
import com.futu.openapi.pb.TrdUnlockTrade;
import com.futu.openapi.pb.TrdModifyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 34: Cancel All Orders (get_order_list + cancel_order + batch cancel)
 *
 * Demonstrates:
 *   - orderListQuery: view current submitted orders before cancel
 *   - cancelOrder: cancel individual orders
 *   - Proper logging of all order fields
 *
 * Mirrors: examples/34_cancel_all/main.py from futu-python-samples
 */
public class Example34_CancelAll implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example34_CancelAll.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_MARKET_HK = 1;

    // OrderStatus: Submitted=5
    private static final int STATUS_SUBMITTED = 5;

    private long accId = 0;

    // Latches
    private final CountDownLatch accListLatch = new CountDownLatch(1);
    private final CountDownLatch orderListLatch = new CountDownLatch(1);
    private final CountDownLatch cancelOrderLatch = new CountDownLatch(1);

    // State
    private List<Long> pendingOrderIds = new ArrayList<>();

    public static void main(String[] args) {
        logger.info("=== Cancel All Orders Demo ===");
        FTAPI.init();
        Example34_CancelAll demo = new Example34_CancelAll();
        demo.start();
    }

    public void start() {
        trd.setClientInfo("javaclient-trd", 1);
        trd.setConnSpi(this);
        trd.setTrdSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            trd.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = trd.initConnect(
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

        // ── Unlock trade ──────────────────────────────────────────────
        String tradePwd = Config.FUTU_TRADE_PASSWORD;
        if (tradePwd != null && !tradePwd.isEmpty()) {
            logger.info("\n--- unlockTrade ---");
            String pwdMd5 = md5(tradePwd);
            var unlockC2s = TrdUnlockTrade.C2S.newBuilder()
                .setUnlock(true)
                .setPwdMD5(pwdMd5)
                .build();
            int ret = trd.unlockTrade(TrdUnlockTrade.Request.newBuilder().setC2S(unlockC2s).build());
            logger.info("unlockTrade ret={}", ret);
            sleep(300);
        } else {
            logger.info("FUTU_TRADE_PASSWORD not set — skipping unlock.");
        }

        // ── Get account list ──────────────────────────────────────────
        logger.info("\n--- getAccList ---");
        var accC2s = TrdGetAccList.C2S.newBuilder()
            .setNeedGeneralSecAccount(false)
            .build();
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accC2s).build());
        logger.info("getAccList ret={}", ret);

        trd.close();
        logger.info("Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc={}", errCode, desc);
        if (errCode == 0) connected = true;
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
        connected = false;
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetAccList(FTAPI_Conn client, int retCode, TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getAccListCount();
            for (int i = 0; i < count; i++) {
                var acc = s2c.getAccList(i);
                logger.info("    acc[{}] accId={} env={}", i, acc.getAccID(), acc.getTrdEnv());
                if (i == 0) accId = acc.getAccID();
            }
            if (accId > 0) querySubmittedOrders();
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
        accListLatch.countDown();
    }

    private void querySubmittedOrders() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // ── orderListQuery: SUBMITTED ───────────────────────────────────
        logger.info("\n=== Current SUBMITTED orders ===");
        var olC2s = TrdGetOrderList.C2S.newBuilder()
            .setHeader(header)
            .addFilterStatusList(STATUS_SUBMITTED)
            .setRefreshCache(false)
            .build();
        int ret = trd.getOrderList(TrdGetOrderList.Request.newBuilder().setC2S(olC2s).build());
        logger.info("orderListQuery ret={}", ret);
    }

    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int retCode, TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed");
    }

    @Override
    public void onReply_GetOrderList(FTAPI_Conn client, int retCode, TrdGetOrderList.Response rsp) {
        logger.info("  [Trd] onReply_GetOrderList: retCode={}", retCode);
        pendingOrderIds.clear();
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOrderListCount();
            logger.info("    Submitted orders ({}):", count);
            for (int i = 0; i < Math.min(count, 20); i++) {
                var o = s2c.getOrderList(i);
                logger.info("      order[{}] id={} code={} status={} side={} qty={} price={:.2f}",
                    i, o.getOrderID(), o.getCode(), orderStatusLabel(o.getOrderStatus()),
                    trdSideLabel(o.getTrdSide()), o.getQty(), o.getPrice());
                logger.info("        createTime={} updateTime={} fillQty={}",
                    o.getCreateTime(), o.getUpdateTime(), o.getFillQty());
                pendingOrderIds.add(o.getOrderID());
            }
            if (count > 20) logger.info("      ... ({} more)", count - 20);

            // Cancel each order individually
            if (!pendingOrderIds.isEmpty()) {
                logger.info("\n=== Cancelling {} orders individually ===", pendingOrderIds.size());
                for (Long orderId : pendingOrderIds) {
                    cancelOrder(orderId);
                }
            }
        } else {
            logger.warn("    getOrderList failed retCode={}", retCode);
        }
        orderListLatch.countDown();
    }

    private void cancelOrder(long orderId) {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        var c2s = TrdModifyOrder.C2S.newBuilder()
            .setHeader(header)
            .setOrderID(orderId)
            .setModifyOrderOp(2)  // 2 = cancel
            .build();
        int ret = trd.modifyOrder(TrdModifyOrder.Request.newBuilder().setC2S(c2s).build());
        logger.info("cancelOrder orderId={} ret={}", orderId, ret);
    }

    @Override
    public void onReply_ModifyOrder(FTAPI_Conn client, int retCode, TrdModifyOrder.Response rsp) {
        logger.info("  [Trd] onReply_ModifyOrder: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    cancelled orderID={}", s2c.getOrderID());
        } else {
            logger.warn("    cancelOrder failed retCode={}", retCode);
        }
        cancelOrderLatch.countDown();
    }

    private static String orderStatusLabel(int status) {
        return switch (status) {
            case 0 -> "UNKNOWN";
            case 1 -> "SUBMITTING";
            case 2 -> "FILLED_ALL";
            case 3 -> "PARTIAL_FILLED";
            case 4 -> "CANCELLED";
            case 5 -> "SUBMITTED";
            case 6 -> "PARTIAL_CANCEL";
            case 7 -> "DELETED";
            case 8 -> "NORMAL";
            case 9 -> "NOT_CONFIRMED";
            default -> "STATUS(" + status + ")";
        };
    }

    private static String trdSideLabel(int side) {
        return switch (side) {
            case 1 -> "BUY";
            case 2 -> "SELL";
            default -> "SIDE(" + side + ")";
        };
    }

    private static String md5(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
