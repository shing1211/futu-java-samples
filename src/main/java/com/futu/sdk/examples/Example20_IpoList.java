package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetIpoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 20: IPO List
 *
 * Demonstrates:
 *   - getIpoList: upcoming and recent IPOs for a market
 *   - Market: HK=1, US=2
 *   - IpoData → BasicIpoData (security, name, listTime)
 *   - IpoData → HKIpoExData / CNIpoExData / USIpoExData for market-specific fields
 *
 * Mirrors: examples/20_ipo_list/main.py from futu-python-samples
 */
public class Example20_IpoList implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example20_IpoList.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== IPO List Demo ===");
        FTAPI.init();
        Example20_IpoList demo = new Example20_IpoList();
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

        for (int[] marketAndLabel : new int[][] { {MARKET_HK, 1}, {MARKET_US, 2} }) {
            int market = marketAndLabel[0];
            logger.info("\n=== getIpoList: {} ===", market == 1 ? "HK" : "US");
            var c2s = QotGetIpoList.C2S.newBuilder()
                .setMarket(market)
                .build();
            int ret = qot.getIpoList(QotGetIpoList.Request.newBuilder().setC2S(c2s).build());
            logger.info("getIpoList ret={}", ret);
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
    public void onReply_GetIpoList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    QotGetIpoList.Response rsp) {
        logger.info("  [Qot] onReply_GetIpoList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getIpoListCount();
            if (count == 0) {
                logger.info("    No IPO records");
            } else {
                logger.info("    IPO records ({}):", count);
                for (int i = 0; i < Math.min(count, 5); i++) {
                    var ipo = s2c.getIpoList(i);
                    var basic = ipo.getBasic();
                    logger.info("      ipo[{}] code={} name={} listTime={}",
                        i, basic.getSecurity().getCode(), basic.getName(), basic.getListTime());
                    if (ipo.hasHkExData()) {
                        var hk = ipo.getHkExData();
                        logger.info("        listPrice={:.2f} ipPriceRange={:.2f}-{:.2f} lotSize={}",
                            hk.getListPrice(), hk.getIpoPriceMin(), hk.getIpoPriceMax(), hk.getLotSize());
                    } else if (ipo.hasUsExData()) {
                        var us = ipo.getUsExData();
                        logger.info("        ipPriceRange={:.2f}-{:.2f} issueSize={}",
                            us.getIpoPriceMin(), us.getIpoPriceMax(), us.getIssueSize());
                    }
                }
                if (count > 5) logger.info("      ... ({} more)", count - 5);
            }
        } else {
            logger.warn("    getIpoList failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}