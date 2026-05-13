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
 * Example 25: Option Chain (get_option_chain / get_option_expiration_date)
 *
 * Demonstrates:
 *   - getOptionExpirationDate: list expiry dates for index options
 *   - getOptionChain: fetch option contracts for a given expiry
 *   - IndexOptionType: NORMAL=1, WEEKLY=2, MONTHLY=3
 *   - OptionType: CALL=1, PUT=2, ALL=0
 *   - OptionChain: getStrikeTime/getOptionList → OptionItem → getCall/getPut (SecurityStaticInfo)
 *
 * Mirrors: examples/25_option_chain/main.py from futu-python-samples
 */
public class Example25_OptionChain implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example25_OptionChain.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: US=2
    private static final int MARKET_US = 2;

    // IndexOptionType: NORMAL=1, WEEKLY=2, MONTHLY=3
    private static final int OPT_TYPE_NORMAL = 1;

    // OptionType: CALL=1, PUT=2, ALL=0
    private static final int OPT_SIDE_CALL = 1;
    private static final int OPT_SIDE_PUT = 2;
    private static final int OPT_SIDE_ALL = 0;

    public static void main(String[] args) {
        logger.info("=== Option Chain Demo ===");
        FTAPI.init();
        Example25_OptionChain demo = new Example25_OptionChain();
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

        // US.NDX (Nasdaq index options)
        var owner = QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NDX").build();

        // ── getOptionExpirationDate ────────────────────────────────────
        logger.info("\n=== getOptionExpirationDate: US.NDX (NORMAL) ===");
        var expC2s = QotGetOptionExpirationDate.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(OPT_TYPE_NORMAL)
            .build();
        int ret = qot.getOptionExpirationDate(QotGetOptionExpirationDate.Request.newBuilder().setC2S(expC2s).build());
        logger.info("getOptionExpirationDate ret={}", ret);
        sleep(300);

        // ── getOptionChain: CALL options ───────────────────────────────
        logger.info("\n=== getOptionChain: US.NDX [CALL] ===");
        var chainCall = QotGetOptionChain.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(OPT_TYPE_NORMAL)
            .setType(OPT_SIDE_CALL)
            .setBeginTime("2026-05-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getOptionChain(QotGetOptionChain.Request.newBuilder().setC2S(chainCall).build());
        logger.info("getOptionChain(CALL) ret={}", ret);
        sleep(300);

        // ── getOptionChain: PUT options ────────────────────────────────
        logger.info("\n=== getOptionChain: US.NDX [PUT] ===");
        var chainPut = QotGetOptionChain.C2S.newBuilder()
            .setOwner(owner)
            .setIndexOptionType(OPT_TYPE_NORMAL)
            .setType(OPT_SIDE_PUT)
            .setBeginTime("2026-05-01")
            .setEndTime("2026-05-31")
            .build();
        ret = qot.getOptionChain(QotGetOptionChain.Request.newBuilder().setC2S(chainPut).build());
        logger.info("getOptionChain(PUT) ret={}", ret);

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
            for (int i = 0; i < Math.min(count, 10); i++) {
                logger.info("      exp[{}] = {}", i, s2c.getDateList(i));
            }
            if (count > 10) logger.info("      ... ({} more)", count - 10);
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
                logger.info("      strikeTime={} optionCount={}", chain.getStrikeTime(), chain.getOptionCount());
                for (int j = 0; j < Math.min(chain.getOptionCount(), 3); j++) {
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