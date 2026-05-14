package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetStaticInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Example 36: Stock Basic Info (get_stock_basicinfo)
 *
 * Demonstrates:
 *   - getStockBasicInfo: fetch basic info for stocks by market or code list
 *   - SecurityType: STOCK, IDX, ETF, WARRANT, BOND, FUTURE, OPTION
 *   - Query by market + type, or by specific code list
 *   - All returned fields logged
 *
 * Mirrors: examples/36_stock_basicinfo/main.py from futu-python-samples
 */
public class Example36_StockBasicInfo implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example36_StockBasicInfo.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // SecurityType: Stock=1, IDX=2, ETF=6, Warrant=7, Bond=8, Future=9, Option=10
    private static final int SEC_TYPE_STOCK = 1;

    public static void main(String[] args) {
        logger.info("=== Stock Basic Info Demo ===");
        FTAPI.init();
        Example36_StockBasicInfo demo = new Example36_StockBasicInfo();
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

        // ── By market + type (HK stocks) ────────────────────────────────
        logger.info("\n=== getStockBasicInfo: HK STOCK (first 5) ===");
        var marketC2s = QotGetStaticInfo.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setSecType(SEC_TYPE_STOCK)
            .build();
        int ret = qot.getStaticInfo(QotGetStaticInfo.Request.newBuilder().setC2S(marketC2s).build());
        logger.info("getStaticInfo (market) ret={}", ret);
        sleep(500);

        // ── By specific code list ────────────────────────────────────────
        logger.info("\n=== getStockBasicInfo: specific codes ===");
        var codes = List.of(
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00005").build()
        );
        var codeListC2s = QotGetStaticInfo.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .addAllSecurityList(codes)
            .build();
        ret = qot.getStaticInfo(QotGetStaticInfo.Request.newBuilder().setC2S(codeListC2s).build());
        logger.info("getStaticInfo (code_list) ret={}", ret);

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
    public void onReply_GetStaticInfo(FTAPI_Conn client, int retCode, QotGetStaticInfo.Response rsp) {
        logger.info("  [Qot] onReply_GetStaticInfo: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Total stocks: {}", count);
            int displayCount = Math.min(count, 5);
            for (int i = 0; i < displayCount; i++) {
                var info = s2c.getStaticInfoList(i);
                var basic = info.getBasic();
                var sec = basic.getSecurity();
                logger.info("      [{}] code={} name={} lot_size={} sec_type={} list_time={}",
                    i, sec.getCode(), basic.getName(), basic.getLotSize(),
                    securityTypeLabel(basic.getSecType()), basic.getListTime());
            }
            if (count > 5) logger.info("      ... ({} more)", count - 5);
            logger.info("    raw s2c: {}", s2c);
        } else {
            logger.warn("    getStockBasicInfo failed retCode={}", retCode);
        }
    }

    private static String securityTypeLabel(int type) {
        return switch (type) {
            case 1 -> "STOCK";
            case 2 -> "IDX";
            case 3 -> "FUTURE";
            case 4 -> "OPTION";
            case 5 -> "BOND";
            case 6 -> "ETF";
            case 7 -> "WARRANT";
            case 8 -> "STRUCTURED";
            default -> "TYPE(" + type + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
