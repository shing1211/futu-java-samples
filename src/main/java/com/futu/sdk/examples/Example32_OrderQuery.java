package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetOrderFillList;
import com.futu.openapi.pb.TrdGetOrderList;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 32: Order & Deal Query
 *
 * Demonstrates:
 *   - unlockTrade: unlock trading with password (required before trading)
 *   - orderListQuery: live orders with status filter
 *   - historyOrderListQuery: historical orders with date range
 *   - dealListQuery: today's executed trades
 *   - historyDealListQuery: historical executed trades
 *
 * Note: Requires trading context to be unlocked.
 * Order fields: getOrderID/getCode/getName/getTrdSide/getOrderType/getOrderStatus/
 *   getQty/getPrice/getCreateTime/getUpdateTime/getDealQty/getCode
 * OrderFill fields: getFillID/getCode/getName/getTrdSide/getQty/getPrice/getCreateTime
 * TrdFilterConditions: setBeginTime/setEndTime/setCodeListList
 *
 * Mirrors: examples/32_order_query/main.py from futu-python-samples
 */
public class Example32_OrderQuery implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example32_OrderQuery.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: REAL=1, SIM=2
    private static final int TRD_ENV_SIM = 2;
    // TrdMarket: HK=1
    private static final int TRD_MARKET_HK = 1;

    // OrderStatus values
    private static final int STATUS_SUBMITTED = 5;
    private static final int STATUS_FILLED_ALL = 2;

    private long accId = 0;

    public static void main(String[] args) {
        logger.info("=== Order & Deal Query Demo ===");
        logger.info("NOTE: Requires unlock_trade to work.");
        FTAPI.init();
        Example32_OrderQuery demo = new Example32_OrderQuery();
        demo.start();
    }

    public void start() {
        trd.setClientInfo("javaclient_trd", 1);
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

        // ── unlockTrade (requires MD5 password) ─────────────────────────
        String tradePwd = Config.FUTU_TRADE_PASSWORD;
        if (tradePwd != null && !tradePwd.isEmpty()) {
            logger.info("\n--- unlockTrade ---");
            // Futu API takes MD5(password) as pwdMD5
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

        // ── getAccList: find accID ──────────────────────────────────────
        logger.info("\n--- getAccList ---");
        var accC2s = TrdGetAccList.C2S.newBuilder()
            .setNeedGeneralSecAccount(false)
            .build();
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accC2s).build());
        logger.info("getAccList ret={}", ret);
        sleep(300);

        trd.close();
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
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getAccListCount();
            for (int i = 0; i < count; i++) {
                var acc = s2c.getAccList(i);
                logger.info("    acc[{}] accId={} env={}", i, acc.getAccID(), acc.getTrdEnv());
                if (i == 0) accId = acc.getAccID();
            }
            if (accId > 0) queryOrders();
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
    }

    private void queryOrders() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIM)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // ── orderListQuery: SUBMITTED ───────────────────────────────────
        logger.info("\n=== orderListQuery: SUBMITTED (accId={}) ===", accId);
        var olC2s = TrdGetOrderList.C2S.newBuilder()
            .setHeader(header)
            .addFilterStatusList(STATUS_SUBMITTED)
            .setRefreshCache(false)
            .build();
        int ret = trd.getOrderList(TrdGetOrderList.Request.newBuilder().setC2S(olC2s).build());
        logger.info("orderListQuery ret={}", ret);
        sleep(300);

        // ── historyOrderListQuery: FILLED (last 6 months) ───────────────
        logger.info("\n=== historyOrderListQuery: FILLED_ALL ===");
        var holC2s = TrdGetOrderList.C2S.newBuilder()
            .setHeader(header)
            .addFilterStatusList(STATUS_FILLED_ALL)
            .setFilterConditions(TrdCommon.TrdFilterConditions.newBuilder()
                .setBeginTime("2025-11-01")
                .setEndTime("2026-05-31")
                .build())
            .setRefreshCache(false)
            .build();
        ret = trd.getOrderList(TrdGetOrderList.Request.newBuilder().setC2S(holC2s).build());
        logger.info("historyOrderListQuery ret={}", ret);
        sleep(300);

        // ── dealListQuery: today's fills ─────────────────────────────────
        logger.info("\n=== dealListQuery ===");
        var dlC2s = TrdGetOrderFillList.C2S.newBuilder()
            .setHeader(header)
            .setRefreshCache(false)
            .build();
        ret = trd.getOrderFillList(TrdGetOrderFillList.Request.newBuilder().setC2S(dlC2s).build());
        logger.info("dealListQuery ret={}", ret);
        sleep(300);

        // ── historyDealListQuery ─────────────────────────────────────────
        logger.info("\n=== historyDealListQuery ===");
        var hdlC2s = TrdGetOrderFillList.C2S.newBuilder()
            .setHeader(header)
            .setFilterConditions(TrdCommon.TrdFilterConditions.newBuilder()
                .setBeginTime("2025-11-01")
                .setEndTime("2026-05-31")
                .build())
            .setRefreshCache(false)
            .build();
        ret = trd.getOrderFillList(TrdGetOrderFillList.Request.newBuilder().setC2S(hdlC2s).build());
        logger.info("historyDealListQuery ret={}", ret);
    }

    @Override
    public void onReply_UnlockTrade(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed — trading will not work");
    }

    @Override
    public void onReply_GetOrderList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      TrdGetOrderList.Response rsp) {
        logger.info("  [Trd] onReply_GetOrderList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOrderListCount();
            logger.info("    Orders ({}):", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                var o = s2c.getOrderList(i);
                logger.info("      order[{}] id={} code={} status={} side={} qty={} price={:.2f}",
                    i, o.getOrderID(), o.getCode(), orderStatusLabel(o.getOrderStatus()),
                    trdSideLabel(o.getTrdSide()), o.getQty(), o.getPrice());
                logger.info("        createTime={} updateTime={} fillQty={} fillAvgPrice={:.2f} name={}",
                    o.getCreateTime(), o.getUpdateTime(), o.getFillQty(), o.getFillAvgPrice(), o.getName());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
        } else {
            logger.warn("    getOrderList failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetOrderFillList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          TrdGetOrderFillList.Response rsp) {
        logger.info("  [Trd] onReply_GetOrderFillList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOrderFillListCount();
            logger.info("    Fills ({}):", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                var f = s2c.getOrderFillList(i);
                logger.info("      fill[{}] id={} code={} side={} qty={} price={:.2f} time={}",
                    i, f.getFillID(), f.getCode(), trdSideLabel(f.getTrdSide()),
                    f.getQty(), f.getPrice(), f.getCreateTime());
                logger.info("        orderID={} name={}", f.getOrderID(), f.getName());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
        } else {
            logger.warn("    getOrderFillList failed retCode={}", retCode);
        }
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
            case 3 -> "BUY_BACK";
            case 4 -> "SELL_SHORT";
            case 5 -> "BUY_BACK_SHORT";
            case 6 -> "SELL_LONG";
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
            return input; // fallback
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}