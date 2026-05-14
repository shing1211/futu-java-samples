package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetOrderList;
import com.futu.openapi.pb.TrdGetOrderFee;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Example 38: Order Fee Query (order_fee_query)
 *
 * Demonstrates:
 *   - orderFeeQuery: fees for specific order IDs
 *   - First fetches historical orders, then queries fees
 *   - All returned fields logged
 *
 * Mirrors: examples/38_order_fee/main.py from futu-python-samples
 */
public class Example38_OrderFee implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example38_OrderFee.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_MARKET_HK = 1;

    // OrderStatus: FilledAll=2
    private static final int STATUS_FILLED_ALL = 2;

    private long accId = 0;

    // Latches
    private final CountDownLatch accListLatch = new CountDownLatch(1);
    private final CountDownLatch orderListLatch = new CountDownLatch(1);
    private final CountDownLatch orderFeeLatch = new CountDownLatch(1);

    // State
    private List<Long> orderIds = new ArrayList<>();

    public static void main(String[] args) {
        logger.info("=== Order Fee Query Demo ===");
        FTAPI.init();
        Example38_OrderFee demo = new Example38_OrderFee();
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
            if (accId > 0) queryHistoricalOrders();
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
        accListLatch.countDown();
    }

    private void queryHistoricalOrders() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // ── Find historical filled orders ─────────────────────────────
        logger.info("\n=== Finding historical orders (FILLED_ALL) ===");
        var holC2s = TrdGetOrderList.C2S.newBuilder()
            .setHeader(header)
            .addFilterStatusList(STATUS_FILLED_ALL)
            .setFilterConditions(TrdCommon.TrdFilterConditions.newBuilder()
                .setBeginTime("2025-01-01")
                .setEndTime("2026-05-12")
                .build())
            .setRefreshCache(false)
            .build();
        int ret = trd.getOrderList(TrdGetOrderList.Request.newBuilder().setC2S(holC2s).build());
        logger.info("historyOrderListQuery ret={}", ret);
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
        orderIds.clear();
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOrderListCount();
            logger.info("    Found {} filled orders", count);
            int displayCount = Math.min(count, 5);
            for (int i = 0; i < displayCount; i++) {
                var o = s2c.getOrderList(i);
                logger.info("      [{}] order_id={} code={} side={} qty={} price={:.2f} create_time={}",
                    i, o.getOrderID(), o.getCode(), trdSideLabel(o.getTrdSide()),
                    o.getQty(), o.getPrice(), o.getCreateTime());
                orderIds.add(o.getOrderID());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);

            // Query fees for collected order IDs
            if (!orderIds.isEmpty()) {
                queryOrderFees();
            }
        } else {
            logger.warn("    getOrderList failed retCode={}", retCode);
        }
        orderListLatch.countDown();
    }

    private void queryOrderFees() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        logger.info("\n=== orderFeeQuery: {} order IDs ===", orderIds.size());
        var orderIdExList = orderIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        var c2s = TrdGetOrderFee.C2S.newBuilder()
            .setHeader(header)
            .addAllOrderIdExList(orderIdExList)
            .build();
        int ret = trd.getOrderFee(TrdGetOrderFee.Request.newBuilder().setC2S(c2s).build());
        logger.info("getOrderFee ret={}", ret);
    }

    @Override
    public void onReply_GetOrderFee(FTAPI_Conn client, int retCode, TrdGetOrderFee.Response rsp) {
        logger.info("  [Trd] onReply_GetOrderFee: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOrderFeeListCount();
            logger.info("    Fee data ({} orders):", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                var fee = s2c.getOrderFeeList(i);
                logger.info("      [{}] order_id_ex={} fee_amount={:.2f}",
                    i, fee.getOrderIDEx(), fee.getFeeAmount());
                int itemCount = fee.getFeeListCount();
                for (int j = 0; j < itemCount; j++) {
                    var item = fee.getFeeList(j);
                    logger.info("        item[{}] title={} value={:.2f}",
                        j, item.getTitle(), item.getValue());
                }
            }
            if (count > 10) logger.info("      ... ({} more)", count - 10);
            logger.info("    raw s2c: {}", s2c);
        } else {
            logger.warn("    getOrderFee failed retCode={}", retCode);
        }
        orderFeeLatch.countDown();
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
