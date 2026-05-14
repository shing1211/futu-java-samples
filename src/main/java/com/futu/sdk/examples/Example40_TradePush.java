package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 40: Trade Push Notifications (order + deal push handlers)
 *
 * Demonstrates:
 *   - TradeOrder push via onPush_UpdateOrder
 *   - TradeDeal push via onPush_UpdateOrderFill
 *   - Full push payload logging (order status, fills, qty, price)
 *   - Proper handler registration via setTrdSpi
 *
 * Note: Requires unlock_trade before push subscriptions are active.
 * Place orders in SIMULATE mode to trigger test pushes.
 *
 * Mirrors: examples/40_push_trade/main.py from futu-python-samples
 */
public class Example40_TradePush implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example40_TradePush.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;
    private long accId = 0;
    private int trdMarket = 1;  // HK=1

    public static void main(String[] args) {
        logger.info("=== Trade Order & Deal Push Demo ===");
        FTAPI.init();
        Example40_TradePush demo = new Example40_TradePush();
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
        while (!connected && waited < 10000) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        // Unlock trade to enable push subscriptions
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

        logger.info("\nListening for order/deal pushes (15s)...");
        logger.info("Place orders in SIMULATE mode to trigger pushes...");
        sleep(15000);

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
    // FTSPI_Trd — push handlers
    // -------------------------------------------------------------------------

    /**
     * onPush_UpdateOrder — triggered when order status changes.
     * Includes: orderID, code, side, price, qty, status, createTime, updateTime.
     */
    @Override
    public void onPush_UpdateOrder(com.futu.openapi.FTAPI_Conn client,
                                   com.futu.openapi.pb.TrdUpdateOrder.Response rsp) {
        logger.info("  [Trd] onPush_UpdateOrder");
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        var header = s2c.getHeader();
        logger.info("    header: accId={} trdEnv={} trdMarket={}",
            header.getAccID(), header.getTrdEnv(), header.getTrdMarket());
        if (s2c.hasOrder()) {
            var o = s2c.getOrder();
            logger.info("    orderID={} orderIDEx={}", o.getOrderID(), o.getOrderIDEx());
            logger.info("    code={} name={}", o.getCode(), o.getName());
            logger.info("    side={} orderType={} price={} qty={}",
                trdSideLabel(o.getTrdSide()), o.getOrderType(), o.getPrice(), o.getQty());
            logger.info("    status={} createTime={} updateTime={}",
                orderStatusLabel(o.getOrderStatus()), o.getCreateTime(), o.getUpdateTime());
            logger.info("    fillQty={}", o.getFillQty());
        }
    }

    /**
     * onPush_UpdateOrderFill — triggered when a fill/execution occurs.
     * Includes: dealID, code, side, price, qty, createTime.
     */
    @Override
    public void onPush_UpdateOrderFill(com.futu.openapi.FTAPI_Conn client,
                                      com.futu.openapi.pb.TrdUpdateOrderFill.Response rsp) {
        logger.info("  [Trd] onPush_UpdateOrderFill");
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        var header = s2c.getHeader();
        logger.info("    header: accId={} trdEnv={} trdMarket={}",
            header.getAccID(), header.getTrdEnv(), header.getTrdMarket());
        if (s2c.hasOrderFill()) {
            var f = s2c.getOrderFill();
            logger.info("    fillID={} orderID={} orderIDEx={}",
                f.getFillID(), f.getOrderID(), f.getOrderIDEx());
            logger.info("    code={} name={}", f.getCode(), f.getName());
            logger.info("    side={} price={} qty={} createTime={}",
                trdSideLabel(f.getTrdSide()), f.getPrice(), f.getQty(), f.getCreateTime());
        }
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd — reply callbacks (satisfy interface contract)
    // -------------------------------------------------------------------------

    @Override
    public void onReply_UnlockTrade(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed retCode={}", retCode);
    }

    @Override
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   TrdGetAccList.Response rsp) {
        // Not called in this demo
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String trdSideLabel(int side) {
        return switch (side) {
            case 1 -> "BUY";
            case 2 -> "SELL";
            default -> "Side(" + side + ")";
        };
    }

    private static String orderStatusLabel(int status) {
        return switch (status) {
            case 0 -> "UNSUBMITTED";
            case 1 -> "SUBMITTING";
            case 2 -> "SUBMITTED";
            case 3 -> "SUBMIT_FAILED";
            case 4 -> "PART_FILLED";
            case 5 -> "FILLED";
            case 6 -> "CANCEL_SUBMITTING";
            case 7 -> "CANCEL_SUBMITTED";
            case 8 -> "CANCEL_FAILED";
            case 9 -> "CANCELLED";
            default -> "Status(" + status + ")";
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