package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetUserSecurity;
import com.futu.openapi.pb.QotModifyUserSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 24: Watchlist / User Security Group Management
 *
 * Demonstrates:
 *   - getUserSecurity: list stocks in a watchlist group
 *   - modifyUserSecurity: ADD/DEL stocks to/from a group
 *   - ModifyUserSecurityOp: ADD=1, DEL=2, SORT=3
 *   - SecurityStaticInfo → SecurityStaticBasic → getSecurity().getCode()/getName()
 *
 * Mirrors: examples/24_user_security/main.py from futu-python-samples
 */
public class Example24_UserSecurity implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example24_UserSecurity.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // ModifyUserSecurityOp: ADD=1, DEL=2, SORT=3
    private static final int OP_ADD = 1;
    private static final int OP_DEL = 2;

    public static void main(String[] args) {
        logger.info("=== Watchlist (User Security) Group Demo ===");
        FTAPI.init();
        Example24_UserSecurity demo = new Example24_UserSecurity();
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

        String group = "MyTechStocks";
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

        // ── getUserSecurity: list group contents ──────────────────────
        logger.info("\n=== getUserSecurity: {} ===", group);
        var getC2s = QotGetUserSecurity.C2S.newBuilder()
            .setGroupName(group)
            .build();
        int ret = qot.getUserSecurity(QotGetUserSecurity.Request.newBuilder().setC2S(getC2s).build());
        logger.info("getUserSecurity ret={}", ret);
        sleep(300);

        // ── modifyUserSecurity: ADD stock ─────────────────────────────
        logger.info("\n=== modifyUserSecurity: ADD {} to '{}' ===", sec.getCode(), group);
        var addC2s = QotModifyUserSecurity.C2S.newBuilder()
            .setGroupName(group)
            .setOp(OP_ADD)
            .addSecurityList(sec)
            .build();
        ret = qot.modifyUserSecurity(QotModifyUserSecurity.Request.newBuilder().setC2S(addC2s).build());
        logger.info("modifyUserSecurity(ADD) ret={}", ret);
        sleep(300);

        // ── getUserSecurity: list again after add ──────────────────────
        logger.info("\n=== getUserSecurity after ADD ===");
        ret = qot.getUserSecurity(QotGetUserSecurity.Request.newBuilder().setC2S(getC2s).build());
        logger.info("getUserSecurity ret={}", ret);
        sleep(300);

        // ── modifyUserSecurity: DEL stock ──────────────────────────────
        logger.info("\n=== modifyUserSecurity: DEL {} from '{}' ===", sec.getCode(), group);
        var delC2s = QotModifyUserSecurity.C2S.newBuilder()
            .setGroupName(group)
            .setOp(OP_DEL)
            .addSecurityList(sec)
            .build();
        ret = qot.modifyUserSecurity(QotModifyUserSecurity.Request.newBuilder().setC2S(delC2s).build());
        logger.info("modifyUserSecurity(DEL) ret={}", ret);

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
    public void onReply_GetUserSecurity(com.futu.openapi.FTAPI_Conn client, int retCode,
                                         QotGetUserSecurity.Response rsp) {
        logger.info("  [Qot] onReply_GetUserSecurity: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            if (count == 0) {
                logger.info("    Group is empty");
            } else {
                logger.info("    Stocks in group ({}):", count);
                for (int i = 0; i < count; i++) {
                    var s = s2c.getStaticInfoList(i);
                    if (!s.hasBasic()) continue;
                    var basic = s.getBasic();
                    logger.info("      [{}] code={} name={}",
                        i, basic.getSecurity().getCode(), basic.getName());
                }
            }
        } else {
            logger.warn("    getUserSecurity failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_ModifyUserSecurity(com.futu.openapi.FTAPI_Conn client, int retCode,
                                            QotModifyUserSecurity.Response rsp) {
        logger.info("  [Qot] onReply_ModifyUserSecurity: retCode={}", retCode);
        if (retCode != 0) {
            logger.warn("    modifyUserSecurity failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}