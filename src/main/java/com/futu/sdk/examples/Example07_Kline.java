package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetKL;
import com.futu.openapi.pb.QotRequestHistoryKL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 07: K-Line Data (get_cur_kline / request_history_kline)
 *
 * Demonstrates:
 *   - getKL: current K-line bars (last N bars) for multiple periods
 *   - requestHistoryKL: historical K-line with date range + pagination
 *   - KLType: Day, 60M, 30M, 5M, etc.
 *   - RehabType: Forward (qfq) adjustment vs None (bfq)
 *
 * Mirrors: examples/07_kline/main.py from futu-python-samples
 */
public class Example07_Kline implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example07_Kline.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // KLType enum values (from QotCommon.KLType): KL_1Min=1, KL_2Min=2, KL_Day=9, KL_Week=10, KL_Month=11, KL_Year=12
    private static final int KL_TYPE_1MIN = 1;
    private static final int KL_TYPE_2MIN = 2;
    private static final int KL_TYPE_DAY = 9;
    private static final int KL_TYPE_WEEK = 10;
    private static final int KL_TYPE_MONTH = 11;
    private static final int KL_TYPE_YEAR = 12;

    // RehabType: None=0 (bfq), Forward=1 (qfq)
    private static final int REHAB_NONE = 0;
    private static final int REHAB_FORWARD = 1;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== K-Line Data Demo ===");
        FTAPI.init();
        Example07_Kline demo = new Example07_Kline();
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
        if (!ok) {
            logger.error("initConnect failed!");
            return;
        }
        logger.info("Connecting to {}:{} (RSA={})...", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT, Config.FUTU_RSA_ENABLED);

        int waited = 0;
        while (!connected && waited < 8000) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out. Status={}", qot.getConnStatus());
            return;
        }

        String code = "HK.00700";
        var sec = QotCommon.Security.newBuilder()
            .setMarket(MARKET_HK)
            .setCode("00700")
            .build();

        // ── getKL: current K-line for multiple periods ─────────────────────────
        String[] periodLabels = {"K_DAY (daily)", "K_60M (hourly)", "K_30M", "K_5M"};
        int[] periodTypes = {KL_TYPE_DAY, KL_TYPE_60MIN, KL_TYPE_30MIN, KL_TYPE_5MIN};

        for (int i = 0; i < periodLabels.length; i++) {
            logger.info("\n--- getKL: {} last 5 bars ---", periodLabels[i]);
            var c2s = QotGetKL.C2S.newBuilder()
                .setSecurity(sec)
                .setKlType(periodTypes[i])
                .setRehabType(REHAB_FORWARD)  // qfq adjusted
                .setReqNum(5)
                .build();

            int ret = qot.getKL(QotGetKL.Request.newBuilder().setC2S(c2s).build());
            logger.info("getKL ret={}", ret);
            sleep(200);
        }

        sleep(500);

        // ── requestHistoryKL: historical K-line ───────────────────────────────
        logger.info("\n=== requestHistoryKL: {} last 30 days ===", code);
        var histC2s = QotRequestHistoryKL.C2S.newBuilder()
            .setSecurity(sec)
            .setKlType(KL_TYPE_DAY)
            .setRehabType(REHAB_FORWARD)
            .setBeginTime("2026-04-01")
            .setEndTime("2026-05-13")
            .setMaxAckKLNum(100)
            .build();

        int ret = qot.requestHistoryKL(QotRequestHistoryKL.Request.newBuilder().setC2S(histC2s).build());
        logger.info("requestHistoryKL ret={}", ret);

        sleep(1500);

        // ── requestHistoryKL: bfq vs qfq comparison ─────────────────────────
        logger.info("\n--- requestHistoryKL: hfq (no adj) vs qfq (adj) comparison ---");
        for (var label : new String[]{"hfq (no adj)", "qfq (adj)"}) {
            int rehabType = label.startsWith("qfq") ? REHAB_FORWARD : REHAB_NONE;
            var c2s = QotRequestHistoryKL.C2S.newBuilder()
                .setSecurity(sec)
                .setKlType(KL_TYPE_DAY)
                .setRehabType(rehabType)
                .setBeginTime("2026-04-01")
                .setEndTime("2026-05-13")
                .setMaxAckKLNum(10)
                .build();

            ret = qot.requestHistoryKL(QotRequestHistoryKL.Request.newBuilder().setC2S(c2s).build());
            logger.info("requestHistoryKL ({}) ret={}", label, ret);
            sleep(500);
        }

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
    public void onReply_GetKL(com.futu.openapi.FTAPI_Conn client, int retCode, QotGetKL.Response rsp) {
        logger.info("  [Qot] onReply_GetKL: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    security={} name={} kline_count={}", sec.getCode(), s2c.getName(), s2c.getKlListCount());
            for (var bar : s2c.getKlListList()) {
                logger.info("      time={} open={:.2f} close={:.2f} high={:.2f} low={:.2f} vol={} turnover={}",
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume(), bar.getTurnover());
            }
        }
    }

    @Override
    public void onReply_RequestHistoryKL(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotRequestHistoryKL.Response rsp) {
        logger.info("  [Qot] onReply_RequestHistoryKL: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    {} {}: {} bars (nextReqKey present={})",
                sec.getCode(), s2c.getName(), s2c.getKlListCount(), s2c.hasNextReqKey());
            for (var bar : s2c.getKlListList()) {
                logger.info("      {} open={:.2f} close={:.2f} high={:.2f} low={:.2f} vol={}",
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume());
            }
        } else {
            logger.warn("    requestHistoryKL failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}