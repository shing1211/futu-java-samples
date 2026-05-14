package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCodeChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 27: Stock Code Change (get_code_change)
 *
 * Demonstrates:
 *   - getCodeChange: find historical stock code changes
 *   - Useful for tracking delisted/renamed/merged stocks
 *   - All returned fields logged
 *
 * Mirrors: examples/27_code_change/main.py from futu-python-samples
 */
public class Example27_CodeChange implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example27_CodeChange.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=11, SH=31, SZ=32
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Stock Code Change Demo ===");
        FTAPI.init();
        Example27_CodeChange demo = new Example27_CodeChange();
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

        // ── Query code changes for multiple stocks ─────────────────────
        var codes = java.util.List.of(
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00005").build()
        );

        logger.info("\n=== getCodeChange: HK.00700, HK.00005 ===");
        var c2s = QotGetCodeChange.C2S.newBuilder()
            .addAllSecurityList(codes)
            .build();
        int ret = qot.getCodeChange(QotGetCodeChange.Request.newBuilder().setC2S(c2s).build());
        logger.info("getCodeChange ret={}", ret);

        qot.close();
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
    // FTSPI_Qot
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetCodeChange(FTAPI_Conn client, int retCode, QotGetCodeChange.Response rsp) {
        logger.info("  [Qot] onReply_GetCodeChange: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getCodeChangeListCount();
            logger.info("    Code change records: {}", count);
            for (var change : s2c.getCodeChangeListList()) {
                logger.info("      change={}", change);
            }
            logger.info("    raw s2c: {}", s2c);
        } else {
            logger.warn("    getCodeChange failed retCode={}", retCode);
        }
    }

    private static String codeChangeTypeLabel(int type) {
        return switch (type) {
            case 1 -> "DELISTED";
            case 2 -> "RENAMED";
            case 3 -> "MERGED";
            case 4 -> "SPLIT";
            default -> "TYPE(" + type + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
