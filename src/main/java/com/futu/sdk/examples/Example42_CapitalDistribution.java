package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCapitalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 42: Capital Distribution (get_capital_distribution)
 *
 * Demonstrates:
 *   - getCapitalDistribution: institutional capital tier breakdown
 *   - Inflows: Super (top 20%), Big, Mid, Small
 *   - Outflows: same tiers
 *   - Net positioning to identify smart money accumulation/distribution
 *
 * Mirrors: examples/42_capital_distribution/main.py from futu-python-samples
 */
public class Example42_CapitalDistribution implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example42_CapitalDistribution.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    // Stock + name pairs
    private record Stock(int market, String code, String name) {}

    public static void main(String[] args) {
        logger.info("=== Capital Distribution Demo ===");
        FTAPI.init();
        Example42_CapitalDistribution demo = new Example42_CapitalDistribution();
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

        Stock[] stocks = {
            new Stock(MARKET_HK, "00700", "Tencent"),
            new Stock(MARKET_HK, "09988", "Alibaba"),
            new Stock(MARKET_HK, "03690", "Meituan"),
            new Stock(MARKET_US, "AAPL", "Apple"),
            new Stock(MARKET_US, "NVDA", "NVIDIA"),
        };

        logger.info("\n{:12} {:>10} {:>10} {:>10} {:>10} {:>10} {:>10} {:>20}",
            "Code", "Super In", "Big In", "Mid In", "Small In", "Super Out", "Big Out", "Update Time");
        logger.info("-".repeat(100));

        for (Stock s : stocks) {
            var sec = QotCommon.Security.newBuilder().setMarket(s.market()).setCode(s.code()).build();
            var c2s = QotGetCapitalDistribution.C2S.newBuilder()
                .setSecurity(sec)
                .build();
            int ret = qot.getCapitalDistribution(QotGetCapitalDistribution.Request.newBuilder().setC2S(c2s).build());
            logger.info("getCapitalDistribution({}) ret={}", s.code(), ret);
            sleep(300);
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
    public void onReply_GetCapitalDistribution(com.futu.openapi.FTAPI_Conn client, int retCode,
                                               QotGetCapitalDistribution.Response rsp) {
        logger.info("  [Qot] onReply_GetCapitalDistribution: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            // IN flows
            double superIn = s2c.getCapitalInSuper();
            double bigIn = s2c.getCapitalInBig();
            double midIn = s2c.getCapitalInMid();
            double smallIn = s2c.getCapitalInSmall();
            // OUT flows
            double superOut = s2c.getCapitalOutSuper();
            double bigOut = s2c.getCapitalOutBig();
            // Net
            double netSuper = superIn - superOut;
            double netBig = bigIn - bigOut;

            logger.info("    updateTime={}", s2c.getUpdateTime());
            logger.info("    IN  — super={:.2f} big={:.2f} mid={:.2f} small={:.2f}",
                superIn, bigIn, midIn, smallIn);
            logger.info("    OUT — super={:.2f} big={:.2f}",
                superOut, bigOut);
            logger.info("    NET — super={:+.2f} big={:+.2f}",
                netSuper, netBig);
            logger.info("    full s2c={}", s2c);
        } else {
            logger.warn("    getCapitalDistribution failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}