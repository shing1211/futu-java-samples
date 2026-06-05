package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.SkillWrapAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.futu.sdk.examples.Helpers.sleep;
import static com.futu.sdk.examples.Helpers.CONNECTION_TIMEOUT_MS;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example 69: Unusual Activity Scanner (SDK 10.7+)
 *
 * Demonstrates the three "unusual activity" APIs added in futu-api 10.7:
 *   - getTechnicalUnusual:   stocks with unusual technical patterns
 *   - getFinancialUnusual:   stocks with unusual financial metric movements
 *   - getDerivativeUnusual:  derivatives with unusual activity
 *
 * All three live in the SkillWrapAPI package (vs. QotCommon used by the
 * regular quote APIs). Responses carry the result as a `content` string
 * (HTML / formatted text) rather than structured proto fields.
 *
 * Indicator filters (passed as strings):
 *   - For technical: "MACD金叉", "RSI超买", "BOLL突破", etc. (Chinese-language tags)
 *   - For financial: PE/PS/PCF band breaches, gross margin moves, etc.
 *   - For derivative: large open-interest change, unusual volume, etc.
 * Server accepts empty indicatorFilters list for "all".
 *
 * Requires OpenD 10.7+ and SDK 10.7.6708+.
 */
public class Example69_UnusualActivity implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example69_UnusualActivity.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private CountDownLatch queryLatch;

    public static void main(String[] args) {
        logger.info("=== Unusual Activity Demo (SDK 10.7+) ===");
        FTAPI.init();
        new Example69_UnusualActivity().start();
    }

    public void start() {
        qot.setClientInfo("javaclient-qot", 1);
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

        int waited = 0;
        while (!connected && waited < CONNECTION_TIMEOUT_MS) {
            sleep(50);
            waited += 50;
        }
        if (!connected) {
            logger.error("Connection timed out.");
            return;
        }

        try {
            // ── Technical unusual: timeRange 1 (intraday), empty filter list ──
            // 1=US 2=CN 3=HK 4=SG
            queryLatch = new CountDownLatch(1);
            var techReq = SkillWrapAPI.TechnicalUnusualReq.newBuilder()
                .setStockSymbol("US.AAPL")
                .setTimeRange(1)
                .addAllIndicatorFilters(List.<String>of())
                .setLanguageId(2)
                .build();
            int ret = qot.getTechnicalUnusual(techReq);
            logger.info("getTechnicalUnusual ret={}", ret);
            try { queryLatch.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // ── Financial unusual: same symbol, broad scan ─────────────────
            queryLatch = new CountDownLatch(1);
            var finReq = SkillWrapAPI.FinancialUnusualReq.newBuilder()
                .setStockSymbol("US.AAPL")
                .setTimeRange(1)
                .addAllAnalysisDimensions(List.<String>of())
                .setLanguageId(2)
                .build();
            ret = qot.getFinancialUnusual(finReq);
            logger.info("getFinancialUnusual ret={}", ret);
            try { queryLatch.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // ── Derivative unusual: HK options on 00700 ───────────────────
            queryLatch = new CountDownLatch(1);
            var derReq = SkillWrapAPI.DerivativeUnusualReq.newBuilder()
                .setStockSymbol("HK.00700")
                .setTimeRange(1)
                .addAllAnalysisDimensions(List.<String>of())
                .setLanguageId(2)
                .build();
            ret = qot.getDerivativeUnusual(derReq);
            logger.info("getDerivativeUnusual ret={}", ret);
            try { queryLatch.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } finally {
            qot.close();
        }
        logger.info("Done.");
    }

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

    @Override
    public void onReply_GetTechnicalUnusual(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             SkillWrapAPI.TechnicalUnusualRsp rsp) {
        logger.info("  [Qot] onReply_GetTechnicalUnusual: retCode={}", retCode);
        logUnusualRsp(rsp, "technical");
        queryLatch.countDown();
    }

    @Override
    public void onReply_GetFinancialUnusual(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             SkillWrapAPI.FinancialUnusualRsp rsp) {
        logger.info("  [Qot] onReply_GetFinancialUnusual: retCode={}", retCode);
        logUnusualRsp(rsp, "financial");
        queryLatch.countDown();
    }

    @Override
    public void onReply_GetDerivativeUnusual(com.futu.openapi.FTAPI_Conn client, int retCode,
                                              SkillWrapAPI.DerivativeUnusualRsp rsp) {
        logger.info("  [Qot] onReply_GetDerivativeUnusual: retCode={}", retCode);
        logUnusualRsp(rsp, "derivative");
        queryLatch.countDown();
    }

    /**
     * The three *UnusualRsp messages share the same shape:
     *   retType, retMsg, errCode, timeRange, content (string blob).
     */
    private void logUnusualRsp(SkillWrapAPI.TechnicalUnusualRsp rsp, String label) {
        if (rsp == null) return;
        logger.info("    [{}] retType={} errCode={} timeRange={}",
            label, rsp.getRetType(), rsp.getErrCode(), rsp.getTimeRange());
        if (rsp.hasRetMsg() && !rsp.getRetMsg().isEmpty()) {
            logger.info("    [{}] retMsg={}", label, rsp.getRetMsg());
        }
        if (rsp.hasContent()) {
            String content = rsp.getContent();
            // Truncate to avoid log spam — server may return large HTML/markdown blobs
            if (content.length() > 500) {
                logger.info("    [{}] content (first 500 chars):\n{}...",
                    label, content.substring(0, 500));
                logger.info("    [{}] ... ({} more chars)", label, content.length() - 500);
            } else {
                logger.info("    [{}] content:\n{}", label, content);
            }
        } else {
            logger.info("    [{}] (no content)", label);
        }
    }

    private void logUnusualRsp(SkillWrapAPI.FinancialUnusualRsp rsp, String label) {
        if (rsp == null) return;
        logger.info("    [{}] retType={} errCode={} timeRange={}",
            label, rsp.getRetType(), rsp.getErrCode(), rsp.getTimeRange());
        if (rsp.hasRetMsg() && !rsp.getRetMsg().isEmpty()) {
            logger.info("    [{}] retMsg={}", label, rsp.getRetMsg());
        }
        if (rsp.hasContent()) {
            String content = rsp.getContent();
            if (content.length() > 500) {
                logger.info("    [{}] content (first 500 chars):\n{}...",
                    label, content.substring(0, 500));
                logger.info("    [{}] ... ({} more chars)", label, content.length() - 500);
            } else {
                logger.info("    [{}] content:\n{}", label, content);
            }
        } else {
            logger.info("    [{}] (no content)", label);
        }
    }

    private void logUnusualRsp(SkillWrapAPI.DerivativeUnusualRsp rsp, String label) {
        if (rsp == null) return;
        logger.info("    [{}] retType={} errCode={} timeRange={}",
            label, rsp.getRetType(), rsp.getErrCode(), rsp.getTimeRange());
        if (rsp.hasRetMsg() && !rsp.getRetMsg().isEmpty()) {
            logger.info("    [{}] retMsg={}", label, rsp.getRetMsg());
        }
        if (rsp.hasContent()) {
            String content = rsp.getContent();
            if (content.length() > 500) {
                logger.info("    [{}] content (first 500 chars):\n{}...",
                    label, content.substring(0, 500));
                logger.info("    [{}] ... ({} more chars)", label, content.length() - 500);
            } else {
                logger.info("    [{}] content:\n{}", label, content);
            }
        } else {
            logger.info("    [{}] (no content)", label);
        }
    }
}
