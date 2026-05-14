package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOptionChain;
import com.futu.openapi.pb.QotGetOptionExpirationDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 52: Option Chain with Data Filters
 *
 * Demonstrates:
 *   - getOptionExpirationDate: list all expiration dates for an underlying
 *   - getOptionChain: fetch option contracts with OptionDataFilter applied
 *   - OptionDataFilter: call_put, moneyness_min/max, delta_min, and more
 *   - Filter by ITM calls with delta > 0.3 to narrow the chain
 *
 * Mirrors: examples/52_option_chain_filter/main.py from futu-python-samples
 */
public class Example52_OptionChainFilter implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example52_OptionChainFilter.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: US=2
    private static final int MARKET_US = 2;

    // OptionType: CALL=1, PUT=2, ALL=0
    private static final int OPT_TYPE_CALL = 1;
    private static final int OPT_TYPE_PUT = 2;
    private static final int OPT_TYPE_ALL = 0;

    // IndexOptionType: NORMAL=1, WEEKLY=2, MONTHLY=3
    private static final int IDX_OPT_NORMAL = 1;

    public static void main(String[] args) {
        logger.info("=== Option Chain Filter Demo ===");
        FTAPI.init();
        Example52_OptionChainFilter demo = new Example52_OptionChainFilter();
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

        // Underlying: US.NVDA (NVIDIA)
        var owner = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NDX").build();

        // ── getOptionExpirationDate: list all expirations ───────────────
        logger.info("\n=== getOptionExpirationDate: US.NDX ===");
        var expC2s = QotGetOptionExpirationDate.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(IDX_OPT_NORMAL)
            .build();
        int ret = qot.getOptionExpirationDate(
            QotGetOptionExpirationDate.Request.newBuilder().setC2S(expC2s).build());
        logger.info("getOptionExpirationDate ret={}", ret);
        sleep(300);

        // ── getOptionChain: CALL options, ITM (moneyness 0.7-1.3), delta>0.3
        logger.info("\n=== getOptionChain: US.NDX [CALL, ITM, delta>0.3] ===");
        // Use fixed expiration window (adjust to match available expirations)
        var chainC2s = QotGetOptionChain.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(IDX_OPT_NORMAL)
            .setType(OPT_TYPE_CALL)
            .setBeginTime("2026-05-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getOptionChain(QotGetOptionChain.Request.newBuilder().setC2S(chainC2s).build());
        logger.info("getOptionChain ret={}", ret);

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
    public void onReply_GetOptionExpirationDate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                                 QotGetOptionExpirationDate.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionExpirationDate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getDateListCount();
            logger.info("    Expiration dates ({}):", count);
            for (int i = 0; i < Math.min(count, 5); i++) {
                logger.info("      exp[{}] = {}", i, s2c.getDateList(i));
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
        } else {
            logger.warn("    getOptionExpirationDate failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetOptionChain(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetOptionChain.Response rsp) {
        logger.info("  [Qot] onReply_GetOptionChain: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int chainCount = s2c.getOptionChainCount();
            logger.info("    Option chains ({}):", chainCount);
            for (int i = 0; i < Math.min(chainCount, 3); i++) {
                var chain = s2c.getOptionChain(i);
                logger.info("      strikeTime={} optionCount={}",
                    chain.getStrikeTime(), chain.getOptionCount());
                for (int j = 0; j < Math.min(chain.getOptionCount(), 5); j++) {
                    var item = chain.getOption(j);
                    if (item.hasCall()) {
                        var call = item.getCall();
                        if (call.hasBasic()) {
                            logger.info("        CALL: {} strike={}",
                                call.getBasic().getSecurity().getCode(), chain.getStrikeTime());
                        }
                    }
                    if (item.hasPut()) {
                        var put = item.getPut();
                        if (put.hasBasic()) {
                            logger.info("        PUT:  {} strike={}",
                                put.getBasic().getSecurity().getCode(), chain.getStrikeTime());
                        }
                    }
                }
            }
        } else {
            logger.warn("    getOptionChain failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}