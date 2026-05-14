package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdFlowSummary;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Example 35: Account Cash Flow (get_history_cashflow)
 *
 * Demonstrates:
 *   - getCashFlow: account cash flow history with direction filter
 *   - CashFlowDirection: ALL, IN, OUT
 *   - All returned fields logged
 *
 * Mirrors: examples/35_cashflow/main.py from futu-python-samples
 */
public class Example35_CashFlow implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example35_CashFlow.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_MARKET_HK = 1;

    // CashFlowDirection: All=0, In=1, Out=2
    private static final int CF_DIR_ALL = 0;
    private static final int CF_DIR_IN = 1;
    private static final int CF_DIR_OUT = 2;

    private long accId = 0;

    // Latches
    private final CountDownLatch accListLatch = new CountDownLatch(1);
    private final CountDownLatch cashFlowLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        logger.info("=== Account Cash Flow Demo ===");
        FTAPI.init();
        Example35_CashFlow demo = new Example35_CashFlow();
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
            if (accId > 0) queryCashFlows();
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
        accListLatch.countDown();
    }

    private void queryCashFlows() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // Query ALL, IN, and OUT directions
        int[] directions = {CF_DIR_ALL, CF_DIR_IN, CF_DIR_OUT};
        String[] labels = {"ALL", "IN (deposits)", "OUT (withdrawals)"};

        for (int i = 0; i < directions.length; i++) {
            logger.info("\n=== getCashFlow: direction={} ({}) ===", directions[i], labels[i]);
            var c2s = TrdFlowSummary.C2S.newBuilder()
                .setHeader(header)
                .setCashFlowDirection(directions[i])
                .setStartCreateDate("2025-01-01")
                .setEndCreateDate("2026-05-12")
                .build();
            int ret = trd.getFlowSummary(TrdFlowSummary.Request.newBuilder().setC2S(c2s).build());
            logger.info("getFlowSummary ret={}", ret);
            sleep(500);
        }
    }

    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int retCode, TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed");
    }

    @Override
    public void onReply_GetFlowSummary(FTAPI_Conn client, int retCode, TrdFlowSummary.Response rsp) {
        logger.info("  [Trd] onReply_GetFlowSummary: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var flowList = s2c.getFlowSummaryInfoListList();
            int count = flowList.size();
            logger.info("    Cash flow records: {}", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                var cf = flowList.get(i);
                logger.info("      [{}] direction={} amount={:.2f} clearing_date={} remark={}",
                    i, cashFlowDirLabel(cf.getCashFlowDirection()),
                    cf.getCashFlowAmount(), cf.getClearingDate(), cf.getCashFlowRemark());
            }
            if (count > 10) logger.info("      ... ({} more)", count - 10);
            logger.info("    raw s2c: {}", s2c);
        } else {
            logger.warn("    getFlowSummary failed retCode={}", retCode);
        }
        cashFlowLatch.countDown();
    }

    private static String cashFlowDirLabel(int dir) {
        return switch (dir) {
            case 0 -> "ALL";
            case 1 -> "IN";
            case 2 -> "OUT";
            default -> "DIR(" + dir + ")";
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
