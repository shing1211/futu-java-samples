package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetHoldingChangeList;
import com.futu.openapi.pb.QotGetUserSecurityGroup;
import com.futu.openapi.pb.QotRequestRehab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 31: Misc — Holding Changes, Rehab, User Security Groups
 *
 * Demonstrates:
 *   - getHoldingChangeList: top holders' position changes (executives/funds)
 *   - getRehab: dividend/split adjustment records (复权数据)
 *   - getUserSecurityGroup: list all watchlist groups
 *
 * Mirrors: examples/31_misc/main.py from futu-python-samples
 */
public class Example31_Misc implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example31_Misc.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    private static final int MARKET_HK = 1;
    // HolderCategory: Executive=1, Fund=2 (from FutuConst)
    private static final int HOLDER_EXECUTIVE = 1;
    private static final int HOLDER_FUND = 2;

    public static void main(String[] args) {
        logger.info("=== Misc Data Demo (Holding Changes / Rehab / Groups) ===");
        FTAPI.init();
        Example31_Misc demo = new Example31_Misc();
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

        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

        // ── Holding Change: EXECUTIVE ───────────────────────────────
        logger.info("\n=== getHoldingChangeList: HK.00700 [EXECUTIVE] ===");
        var hcExec = QotGetHoldingChangeList.C2S.newBuilder()
            .setSecurity(sec)
            .setHolderCategory(HOLDER_EXECUTIVE)
            .setBeginTime("2025-01-01")
            .setEndTime("2026-05-31")
            .build();
        int ret = qot.getHoldingChangeList(QotGetHoldingChangeList.Request.newBuilder().setC2S(hcExec).build());
        logger.info("getHoldingChangeList(EXEC) ret={}", ret);
        sleep(300);

        // ── Holding Change: FUND ────────────────────────────────────
        logger.info("\n=== getHoldingChangeList: HK.00700 [FUND] ===");
        var hcFund = QotGetHoldingChangeList.C2S.newBuilder()
            .setSecurity(sec)
            .setHolderCategory(HOLDER_FUND)
            .setBeginTime("2025-01-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getHoldingChangeList(QotGetHoldingChangeList.Request.newBuilder().setC2S(hcFund).build());
        logger.info("getHoldingChangeList(FUND) ret={}", ret);
        sleep(300);

        // ── Rehab (dividend/split adjustment data) ─────────────────────
        logger.info("\n=== getRehab: HK.00700 ===");
        var rehabC2s = QotRequestRehab.C2S.newBuilder()
            .setSecurity(sec)
            .build();
        ret = qot.requestRehab(QotRequestRehab.Request.newBuilder().setC2S(rehabC2s).build());
        logger.info("requestRehab ret={}", ret);
        sleep(300);

        // ── User Security Groups (watchlists) ──────────────────────────
        logger.info("\n=== getUserSecurityGroup ===");
        var groupC2s = QotGetUserSecurityGroup.C2S.newBuilder()
            .setGroupType(0)  // 0 = all types
            .build();
        ret = qot.getUserSecurityGroup(QotGetUserSecurityGroup.Request.newBuilder().setC2S(groupC2s).build());
        logger.info("getUserSecurityGroup ret={}", ret);

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
    public void onReply_GetHoldingChangeList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                              QotGetHoldingChangeList.Response rsp) {
        logger.info("  [Qot] onReply_GetHoldingChangeList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getHoldingChangeListCount();
            if (count == 0) {
                logger.info("    No holding change records");
            } else {
                logger.info("    Records ({}):", count);
                for (int i = 0; i < Math.min(count, 10); i++) {
                    var h = s2c.getHoldingChangeList(i);
                    logger.info("      [{}] holder={} time={} holdingQty={} holdingRatio={:.2f}% changeQty={} changeRatio={:.2f}%",
                        i, h.getHolderName(), h.getTime(),
                        h.getHoldingQty(), h.getHoldingRatio() * 100,
                        h.getChangeQty(), h.getChangeRatio() * 100);
                }
                if (count > 10) logger.info("      ... ({} more)", count - 10);
            }
        } else {
            logger.warn("    getHoldingChangeList failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_RequestRehab(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      QotRequestRehab.Response rsp) {
        logger.info("  [Qot] onReply_RequestRehab: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getRehabListCount();
            logger.info("    Rehab records ({}):", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                var r = s2c.getRehabList(i);
                logger.info("      [{}] time={} dividend={:.4f} splitBase={}/{} fwdFactorA={:.6f} bwdFactorA={:.6f}",
                    i, r.getTime(), r.getDividend(), r.getSplitBase(), r.getSplitErt(),
                    r.getFwdFactorA(), r.getBwdFactorA());
            }
            if (count > 10) logger.info("      ... ({} more)", count - 10);
        } else {
            logger.warn("    requestRehab failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetUserSecurityGroup(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetUserSecurityGroup.Response rsp) {
        logger.info("  [Qot] onReply_GetUserSecurityGroup: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getGroupListCount();
            if (count == 0) {
                logger.info("    No watchlist groups found");
            } else {
                logger.info("    Groups ({}):", count);
                for (int i = 0; i < count; i++) {
                    var g = s2c.getGroupList(i);
                    logger.info("      group[{}] name={} type={}", i, g.getGroupName(), g.getGroupType());
                }
            }
        } else {
            logger.warn("    getUserSecurityGroup failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}