package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.GetGlobalState;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetHistoryOrderFillList;
import com.futu.openapi.pb.TrdGetHistoryOrderList;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 50 — Historical Order and Deal History
 *
 * Demonstrates:
 *  - getHistoryOrderList:  closed/filled/cancelled historical orders
 *  - getHistoryOrderFillList: actual fills with time, price, quantity
 *
 * Use this to audit your trading history, calculate win rates,
 * average entry prices, and realised P&L per stock.
 *
 * Note: defaults to trd_env=REAL. For SIMULATE account results,
 * pass trd_env=SIMULATE (or SIMU2 depending on your account type).
 *
 * SDK: FTAPI_Conn_Trd.getHistoryOrderList / getHistoryOrderFillList
 */
public class Example50_HistoryOrderDeal implements FTSPI_Conn, FTSPI_Trd, FTSPI_Qot {

    private static final Logger logger = LoggerFactory.getLogger(Example50_HistoryOrderDeal.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();

    private volatile boolean connected = false;
    private volatile int globalStateRetCode = -1;

    private final CountDownLatch orderLatch = new CountDownLatch(1);
    private final CountDownLatch fillLatch = new CountDownLatch(1);

    private final AtomicReference<List<TrdCommon.Order>> ordersRef = new AtomicReference<>();
    private final AtomicReference<String> orderErrRef = new AtomicReference<>();
    private final AtomicReference<List<TrdCommon.OrderFill>> fillsRef = new AtomicReference<>();
    private final AtomicReference<String> fillErrRef = new AtomicReference<>();

    // TrdEnv: 1=REAL, 2=SIMULATE, 3=SIMU2
    private static final int ENV_REAL = 1;
    private static final int ENV_SIMULATE = 2;

    public static void main(String[] args) {
        new Example50_HistoryOrderDeal().run();
    }

    private void run() {
        FTAPI.init();
        trd.setClientInfo("javaclient-history", 1);
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
        if (!ok) {
            logger.error("initConnect failed!");
            return;
        }
        logger.info("Connecting to {}:{}...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);

        try {
            boolean orderDone = orderLatch.await(15, TimeUnit.SECONDS);
            boolean fillDone = fillLatch.await(15, TimeUnit.SECONDS);
            if (!orderDone) logger.warn("Order query timed out");
            if (!fillDone) logger.warn("Fill query timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("\nDone. Summary:");
        List<TrdCommon.Order> orders = ordersRef.get();
        logger.info("  Orders retrieved: {}", orders != null ? orders.size() : orderErrRef.get());
        List<TrdCommon.OrderFill> fills = fillsRef.get();
        logger.info("  Deals retrieved:  {}", fills != null ? fills.size() : fillErrRef.get());

        qot.close();
        trd.close();
        System.exit(0);
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc='{}'", errCode, desc);
        if (errCode == 0) connected = true;
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd — connection flow
    // -------------------------------------------------------------------------

    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int retCode, TrdUnlockTrade.Response rsp) {
        if (retCode != 0) {
            logger.warn("unlockTrade failed retCode={}", retCode);
        } else {
            logger.info("Trade account unlocked successfully");
        }
        queryHistory();
    }

    @Override
    public void onReply_GetAccList(FTAPI_Conn client, int retCode, TrdGetAccList.Response rsp) {
        if (retCode != 0) {
            logger.error("getAccList failed retCode={}", retCode);
            orderLatch.countDown();
            fillLatch.countDown();
            return;
        }

        var accList = rsp.getS2C().getAccListList();
        if (accList.isEmpty()) {
            logger.warn("No trading accounts found");
            orderLatch.countDown();
            fillLatch.countDown();
            return;
        }

        long accID = accList.get(0).getAccID();
        int trdEnv = accList.get(0).getTrdEnv();
        logger.info("Using account {} (env={})", accID, trdEnv);

        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
            .setAccID(accID)
            .setTrdEnv(trdEnv)
            .build();

        // Empty filter = return all
        TrdCommon.TrdFilterConditions emptyFilter = TrdCommon.TrdFilterConditions.newBuilder().build();

        // Query historical orders (try SIMULATE first for demo, then REAL)
        queryHistoryForEnv(header, ENV_SIMULATE, emptyFilter, true);
    }

    private void queryHistory() {
        // First get account list
        trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(
            TrdGetAccList.C2S.newBuilder().build()
        ).build());
    }

    private void queryHistoryForEnv(TrdCommon.TrdHeader header, int trdEnv,
                                    TrdCommon.TrdFilterConditions filter, boolean isFirst) {
        TrdCommon.TrdHeader envHeader = TrdCommon.TrdHeader.newBuilder()
            .setAccID(header.getAccID())
            .setTrdEnv(trdEnv)
            .build();

        logger.info("\n=== HISTORICAL ORDERS (env={}) ===",
            trdEnv == ENV_REAL ? "REAL" : "SIMULATE");

        TrdGetHistoryOrderList.C2S orderC2s = TrdGetHistoryOrderList.C2S.newBuilder()
            .setHeader(envHeader)
            .setFilterConditions(filter)
            .build();

        int ret = trd.getHistoryOrderList(TrdGetHistoryOrderList.Request.newBuilder()
            .setC2S(orderC2s).build());

        if (ret != 0) {
            logger.error("getHistoryOrderList request failed ret={}", ret);
            orderLatch.countDown();
        }

        logger.info("\n=== HISTORICAL DEALS (env={}) ===",
            trdEnv == ENV_REAL ? "REAL" : "SIMULATE");

        TrdGetHistoryOrderFillList.C2S fillC2s = TrdGetHistoryOrderFillList.C2S.newBuilder()
            .setHeader(envHeader)
            .setFilterConditions(filter)
            .build();

        ret = trd.getHistoryOrderFillList(TrdGetHistoryOrderFillList.Request.newBuilder()
            .setC2S(fillC2s).build());

        if (ret != 0) {
            logger.error("getHistoryOrderFillList request failed ret={}", ret);
            fillLatch.countDown();
        }
    }

    // -------------------------------------------------------------------------
    // FTSPI_Trd — API replies
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetHistoryOrderList(FTAPI_Conn client, int retCode,
                                            TrdGetHistoryOrderList.Response rsp) {
        if (retCode != 0) {
            logger.error("getHistoryOrderList failed retCode={}", retCode);
            orderErrRef.set("retCode=" + retCode);
        } else if (!rsp.hasS2C()) {
            logger.warn("getHistoryOrderList: no S2C");
        } else {
            List<TrdCommon.Order> orders = rsp.getS2C().getOrderListList();
            ordersRef.set(orders);
            logger.info("Historical orders returned: {} orders", orders.size());
            printOrders(orders);
        }
        orderLatch.countDown();
    }

    @Override
    public void onReply_GetHistoryOrderFillList(FTAPI_Conn client, int retCode,
                                                 TrdGetHistoryOrderFillList.Response rsp) {
        if (retCode != 0) {
            logger.error("getHistoryOrderFillList failed retCode={}", retCode);
            fillErrRef.set("retCode=" + retCode);
        } else if (!rsp.hasS2C()) {
            logger.warn("getHistoryOrderFillList: no S2C");
        } else {
            List<TrdCommon.OrderFill> fills = rsp.getS2C().getOrderFillListList();
            fillsRef.set(fills);
            logger.info("Historical deals returned: {} fills", fills.size());
            printDeals(fills);
        }
        fillLatch.countDown();
    }

    // -------------------------------------------------------------------------
    // FTSPI_Qot (unused but required by interface)
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetGlobalState(FTAPI_Conn client, int retCode,
                                       GetGlobalState.Response rsp) {}

    private void printOrders(List<TrdCommon.Order> orders) {
        if (orders.isEmpty()) {
            logger.info("  (no historical orders)");
            return;
        }
        logger.info("  {:<12} {:<10} {:<6} {:<10} {:>10} {:>10} {:<20}",
            "order_id", "code", "side", "type", "price", "qty", "status");
        for (var o : orders) {
            String side = o.getTrdSide() == 1 ? "BUY" : "SELL";
            String type = orderTypeName(o.getOrderType());
            String status = orderStatusName(o.getOrderStatus());
            logger.info("  {:<12} {:<10} {:<6} {:<10} {:>10.2f} {:>10.2f} {:<20}",
                o.getOrderID(), o.getCode(), side, type, o.getPrice(), o.getQty(), status);
        }
    }

    private void printDeals(List<TrdCommon.OrderFill> fills) {
        if (fills.isEmpty()) {
            logger.info("  (no historical deals)");
            return;
        }
        logger.info("  {:<12} {:<12} {:<10} {:<6} {:>10} {:>10} {:<20}",
            "fill_id", "order_id", "code", "side", "price", "qty", "create_time");
        for (var d : fills) {
            String side = d.getTrdSide() == 1 ? "BUY" : "SELL";
            logger.info("  {:<12} {:<12} {:<10} {:<6} {:>10.2f} {:>10.2f} {:<20}",
                d.getFillID(), d.getOrderID(), d.getCode(), side,
                d.getPrice(), d.getQty(), d.getCreateTime());
        }
    }

    private String orderTypeName(int type) {
        return switch (type) {
            case 0 -> "NORMAL";
            case 1 -> "MKT";
            case 2 -> "LMT";
            case 3 -> "SMT";
            case 4 -> "STOP";
            case 5 -> "STOP_LMT";
            case 6 -> "TRAIL";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    private String orderStatusName(int status) {
        return switch (status) {
            case 0 -> "SUBMITTING";
            case 1 -> "SUBMITTED";
            case 2 -> "SUBMIT_FAILED";
            case 3 -> "QUEUED";
            case 4 -> "PART_FILLED";
            case 5 -> "FILLED";
            case 6 -> "CANCELLED";
            case 7 -> "CANCELLING";
            case 8 -> "SUBMITTED_PARTIAL";
            case 9 -> "CANCEL_PARTIAL";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
