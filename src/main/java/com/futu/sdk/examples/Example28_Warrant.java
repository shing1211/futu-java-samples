package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetWarrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 28: Warrant Data (get_warrant)
 *
 * Demonstrates:
 *   - getWarrant: list all structured products (warrants/bull-bear certs)
 *     issued against a underlying stock
 *   - WarrantType: Buy=1, Sell=2, Bull=3, Bear=4, InLine=5
 *   - Key fields: strikePrice, maturityTime, conversionRatio, premium,
 *     effectiveLeverage, delta, impliedVolatility
 *
 * Warrants (窝轮/涡轮) are derivative instruments issued by banks.
 * They are popular in HK markets for retail structured products.
 *
 * Mirrors: examples/28_warrant/main.py from futu-python-samples
 */
public class Example28_Warrant implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example28_Warrant.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Warrant Data Demo ===");
        FTAPI.init();
        Example28_Warrant demo = new Example28_Warrant();
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

        for (String code : new String[]{"00700", "HSImain"}) {
            var owner = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode(code).build();
            logger.info("\n=== getWarrant: HK.{} ===", code);

            // Basic request: first 20 warrants, sorted by volume
            var c2s = QotGetWarrant.C2S.newBuilder()
                .setOwner(owner)
                .setBegin(0)
                .setNum(20)
                .build();
            int ret = qot.getWarrant(QotGetWarrant.Request.newBuilder().setC2S(c2s).build());
            logger.info("getWarrant ret={}", ret);
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
    public void onReply_GetWarrant(com.futu.openapi.FTAPI_Conn client, int retCode,
                                   QotGetWarrant.Response rsp) {
        logger.info("  [Qot] onReply_GetWarrant: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getWarrantDataListCount();
            boolean lastPage = s2c.getLastPage();
            int total = s2c.getAllCount();
            logger.info("    Warrants: {} returned (total={}, lastPage={})",
                count, total, lastPage);
            for (int i = 0; i < Math.min(count, 5); i++) {
                var w = s2c.getWarrantDataList(i);
                var sec = w.getStock();
                logger.info("      [{}] {} name={}", i, sec.getCode(), w.getName());
                logger.info("        type={} issuer={} strike={} maturity={}",
                    warrantTypeLabel(w.getType()), w.getIssuer(),
                    w.getStrikePrice(), w.getMaturityTime());
                logger.info("        curPrice={:.3f} changeRate={:.2f}% vol={}",
                    w.getCurPrice(), w.getChangeRate() * 100, w.getVolume());
                logger.info("        convRatio={} lotSize={} status={}",
                    w.getConversionRatio(), w.getLotSize(), w.getStatus());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
        } else {
            logger.warn("    getWarrant failed retCode={}", retCode);
        }
    }

    private static String warrantTypeLabel(int type) {
        return switch (type) {
            case 1 -> "BUY";
            case 2 -> "SELL";
            case 3 -> "BULL";
            case 4 -> "BEAR";
            case 5 -> "INLINE";
            default -> "TYPE(" + type + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}