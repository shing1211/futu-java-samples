package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotRequestHistoryKLQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 26: Historical K-line Quota (get_history_kl_quota)
 *
 * Demonstrates:
 *   - getHistoryKLQuota: check API rate limits for historical K-line requests
 *   - getDetail=true: show per-day quota usage breakdown
 *   - All returned fields logged
 *
 * Rate limits apply to historical K-line requests. Monitor quota to avoid hitting limits.
 *
 * Mirrors: examples/26_history_kl_quota/main.py from futu-python-samples
 */
public class Example26_HistoryKLQuota implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example26_HistoryKLQuota.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    public static void main(String[] args) {
        logger.info("=== Historical K-line Quota Demo ===");
        FTAPI.init();
        Example26_HistoryKLQuota demo = new Example26_HistoryKLQuota();
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

        // ── Basic quota check ───────────────────────────────────────────
        logger.info("\n=== requestHistoryKLQuota (summary) ===");
        var quotaC2s = QotRequestHistoryKLQuota.C2S.newBuilder()
            .setBGetDetail(false)
            .build();
        int ret = qot.requestHistoryKLQuota(QotRequestHistoryKLQuota.Request.newBuilder().setC2S(quotaC2s).build());
        logger.info("requestHistoryKLQuota ret={}", ret);
        sleep(500);

        // ── Detailed quota breakdown ───────────────────────────────────
        logger.info("\n=== requestHistoryKLQuota (detail=true) ===");
        var detailC2s = QotRequestHistoryKLQuota.C2S.newBuilder()
            .setBGetDetail(true)
            .build();
        ret = qot.requestHistoryKLQuota(QotRequestHistoryKLQuota.Request.newBuilder().setC2S(detailC2s).build());
        logger.info("requestHistoryKLQuota (wide) ret={}", ret);

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
    public void onReply_RequestHistoryKLQuota(FTAPI_Conn client, int retCode, QotRequestHistoryKLQuota.Response rsp) {
        logger.info("  [Qot] onReply_RequestHistoryKLQuota: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    used_quota={} remain_quota={}",
                s2c.getUsedQuota(), s2c.getRemainQuota());
            int detailCount = s2c.getDetailListCount();
            if (detailCount > 0) {
                logger.info("    Detail items ({}):", detailCount);
                for (int i = 0; i < detailCount; i++) {
                    var detail = s2c.getDetailList(i);
                    logger.info("      sec={} name={} req_time={}",
                        detail.getSecurity().getCode(), detail.getName(), detail.getRequestTime());
                }
            }
            logger.info("    raw s2c: {}", s2c);
        } else {
            logger.warn("    requestHistoryKLQuota failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
