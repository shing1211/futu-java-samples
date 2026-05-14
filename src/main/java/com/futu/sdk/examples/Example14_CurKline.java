package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateKL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 14: Real-time K-Line Push (CurKline)
 *
 * Demonstrates:
 *   - subscribe: subscribe to KL_Day, KL_30M subtype
 *   - onPush_UpdateKL: handle real-time K-line bar push updates
 *   - K-line fields: open, high, low, close, volume, time
 *
 * Note: Push is continuous. Run with Thread.sleep() to observe live updates.
 *
 * Mirrors: examples/14_cur_kline/main.py from futu-python-samples
 */
public class Example14_CurKline implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example14_CurKline.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Real-time K-line Push Demo ===");
        FTAPI.init();
        Example14_CurKline demo = new Example14_CurKline();
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

        String code = "00700";
        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode(code).build();

        // ── Subscribe to K-line subtypes ───────────────────────────────
        logger.info("\n--- Subscribe: HK.00700 KL_Day + KL_30M ---");
        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec)
            .addSubTypeList(QotCommon.SubType.SubType_KL_Day.getNumber())
            .addSubTypeList(QotCommon.SubType.SubType_KL_30Min.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(false)
            .build();
        int ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);

        logger.info("Watching {} K-line pushes for 15 seconds...", code);
        sleep(15000);
        logger.info("Finished.");

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
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode != 0) logger.warn("    subscribe failed retCode={}", retCode);
    }

    @Override
    public void onPush_UpdateKL(com.futu.openapi.FTAPI_Conn client, QotUpdateKL.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        var sec = s2c.getSecurity();
        int klType = s2c.getKlType();
        String label = klTypeLabel(klType);
        int count = s2c.getKlListCount();
        for (int i = 0; i < count; i++) {
            var k = s2c.getKlList(i);
            logger.info("  [Kline] {} {} time={} O={:.2f} H={:.2f} L={:.2f} C={:.2f} vol={}",
                sec.getCode(), label, k.getTime(),
                k.getOpenPrice(), k.getHighPrice(), k.getLowPrice(), k.getClosePrice(),
                k.getVolume());
        }
    }

    private static String klTypeLabel(int klType) {
        return switch (klType) {
            case 1 -> "1Min";
            case 2 -> "2Min";
            case 9 -> "Day";
            case 10 -> "Week";
            case 11 -> "Month";
            case 12 -> "Year";
            default -> "KL(" + klType + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}