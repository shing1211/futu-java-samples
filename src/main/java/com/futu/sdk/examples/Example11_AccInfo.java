package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetFunds;
import com.futu.openapi.pb.TrdGetPositionList;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Example 11: Account Info & Positions (accinfo_query + position_list_query)
 *
 * Demonstrates:
 *   - getAccList: discover all trading accounts
 *   - getFunds: query account cash/power/assets (replaces accinfo_query)
 *   - getPositionList: query current positions (qty, cost, P&L)
 *   - Proper unlockTrade flow before account queries
 *   - Logging of funds fields (power, cash, margin, nav)
 *
 * Mirrors: examples/11_accinfo/main.py from futu-python-samples
 */
public class Example11_AccInfo implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example11_AccInfo.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_MARKET_HK = 1;

    private long accId = 0;

    // Latches
    private final CountDownLatch accListLatch = new CountDownLatch(1);
    private final CountDownLatch fundsLatch = new CountDownLatch(1);
    private final CountDownLatch positionLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        logger.info("=== Account Info & Positions Demo ===");
        FTAPI.init();
        Example11_AccInfo demo = new Example11_AccInfo();
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

        // ── Get account list ───────────────────────────────────────────
        logger.info("\n--- getAccList ---");
        var accListC2s = TrdGetAccList.C2S.newBuilder().build();
        int ret = trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accListC2s).build());
        logger.info("getAccList ret={}", ret);
    }

    private void queryAccountInfo() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // ── getFunds (replaces accinfo_query) ──────────────────────────
        logger.info("\n=== getFunds ===");
        var fundsC2s = TrdGetFunds.C2S.newBuilder()
            .setHeader(header)
            .build();
        int ret = trd.getFunds(TrdGetFunds.Request.newBuilder().setC2S(fundsC2s).build());
        logger.info("getFunds ret={}", ret);

        // ── getPositionList ───────────────────────────────────────────
        logger.info("\n=== getPositionList ===");
        var posC2s = TrdGetPositionList.C2S.newBuilder()
            .setHeader(header)
            .build();
        ret = trd.getPositionList(TrdGetPositionList.Request.newBuilder().setC2S(posC2s).build());
        logger.info("getPositionList ret={}", ret);
    }

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

    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int retCode, TrdUnlockTrade.Response rsp) {
        logger.info("  [Trd] onReply_UnlockTrade: retCode={}", retCode);
        if (retCode == 0) logger.info("    unlock successful");
        else logger.warn("    unlock failed");
    }

    @Override
    public void onReply_GetAccList(FTAPI_Conn client, int retCode, TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var accList = s2c.getAccListList();
            if (accList.isEmpty()) {
                logger.warn("    No accounts returned");
            } else {
                logger.info("    Accounts ({}):", accList.size());
                for (var acc : accList) {
                    logger.info("      accId={} trdEnv={} accType={}",
                        acc.getAccID(), acc.getTrdEnv(), acc.getAccType());
                    // Auto-select first simulate account
                    if (accId == 0 && acc.getTrdEnv() == TRD_ENV_SIMULATE) {
                        accId = acc.getAccID();
                        logger.info("        → selected for demo (accId={})", accId);
                    }
                }
            }
            if (accId > 0) queryAccountInfo();
        } else {
            logger.warn("    getAccList failed retCode={}", retCode);
        }
        accListLatch.countDown();
    }

    @Override
    public void onReply_GetFunds(FTAPI_Conn client, int retCode, TrdGetFunds.Response rsp) {
        logger.info("  [Trd] onReply_GetFunds: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var funds = s2c.getFunds();
            logger.info("    power={} cash={} total_assets={} initial_margin={} avail_funds={}",
                funds.getPower(), funds.getCash(), funds.getTotalAssets(),
                funds.getInitialMargin(), funds.getAvailableFunds());
            logger.info("    raw funds: {}", funds);
        } else {
            logger.warn("    getFunds failed retCode={}", retCode);
        }
        fundsLatch.countDown();
    }

    @Override
    public void onReply_GetPositionList(FTAPI_Conn client, int retCode, TrdGetPositionList.Response rsp) {
        logger.info("  [Trd] onReply_GetPositionList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var list = s2c.getPositionListList();
            if (list.isEmpty()) {
                logger.info("    No positions (empty portfolio)");
            } else {
                logger.info("    Positions ({} rows):", list.size());
                for (var pos : list) {
                    logger.info("      {} name={} qty={} price={} cost={} val={} pl={} pl_ratio={}%",
                        pos.getCode(), pos.getName(), (long) pos.getQty(),
                        pos.getPrice(), pos.getCostPrice(), pos.getVal(),
                        pos.getUnrealizedPL(), pos.getPlRatio() * 100);
                    logger.info("        can_sell_qty={}",
                        (long) pos.getCanSellQty());
                }
                logger.info("    raw: {}", s2c);
            }
        } else {
            logger.warn("    getPositionList failed retCode={}", retCode);
        }
        positionLatch.countDown();
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