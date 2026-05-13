package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotStockFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 03: Stock Filter / Screener
 *
 * Demonstrates filtering stocks by:
 *   - Simple numeric fields (price, volume, etc.)
 *   - Financial metrics (ratios, earnings, etc.)
 *
 * Mirrors: examples/03_filter/main.py from futu-python-samples
 *
 * Field IDs (from proto definitions):
 *   StockField.CurPrice = 3, VolumeRatio = 8, PbRate = 14, etc.
 *   FinancialField.CurrentRatio = 20, NetProfit = 1, etc.
 *   See FUTU_JAVA_SDK_DOC.md for the complete enum definitions.
 */
public class Example03_StockFilter implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example03_StockFilter.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // StockField IDs (from proto QotCommon.StockField)
    private static final int STOCK_FIELD_CUR_PRICE = 3;
    private static final int STOCK_FIELD_VOLUME_RATIO = 8;
    private static final int STOCK_FIELD_PB_RATE = 14;

    // FinancialField IDs (from proto QotCommon.FinancialField)
    private static final int FINANCIAL_FIELD_CURRENT_RATIO = 20;
    private static final int FINANCIAL_FIELD_NET_PROFIT = 1;

    // Market IDs: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== Stock Filter (Screener) Demo ===");
        FTAPI.init();
        Example03_StockFilter demo = new Example03_StockFilter();
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

        // ── Simple filter: CUR_PRICE 2-1000 HKD ─────────────────────────────────
        logger.info("\n--- SimpleFilter: CUR_PRICE 2-1000 HKD ---");
        var simpleFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_CUR_PRICE)
            .setFilterMin(2.0)
            .setFilterMax(1000.0)
            .setIsNoFilter(false)
            .build();

        // Financial filter: CURRENT_RATIO 0.5-50 (Annual)
        // Note: FinancialFilter uses FinancialField IDs, NOT StockField IDs
        var finFilter = QotStockFilter.FinancialFilter.newBuilder()
            .setFieldName(FINANCIAL_FIELD_CURRENT_RATIO)
            .setFilterMin(0.5)
            .setFilterMax(50.0)
            .setIsNoFilter(false)
            .setQuarter(1)  // 1=Annual, 2=Q1, 3=H1, 4=Q3
            .build();

        var c2s = QotStockFilter.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setBegin(0)
            .setNum(20)
            .addBaseFilterList(simpleFilter)
            .addFinancialFilterList(finFilter)
            .build();

        int ret = qot.stockFilter(QotStockFilter.Request.newBuilder().setC2S(c2s).build());
        logger.info("stockFilter ret={}", ret);

        waited = 0;
        while (waited < 5000) {
            sleep(100);
            waited += 100;
        }

        // ── US market: simple price filter ───────────────────────────────────
        logger.info("\n--- SimpleFilter: US market CUR_PRICE 1-10000 ---");
        var usFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_CUR_PRICE)
            .setFilterMin(1.0)
            .setFilterMax(10000.0)
            .setIsNoFilter(false)
            .build();

        var usC2s = QotStockFilter.C2S.newBuilder()
            .setMarket(MARKET_US)
            .setBegin(0)
            .setNum(10)
            .addBaseFilterList(usFilter)
            .build();

        ret = qot.stockFilter(QotStockFilter.Request.newBuilder().setC2S(usC2s).build());
        logger.info("stockFilter(US) ret={}", ret);

        waited = 0;
        while (waited < 5000) {
            sleep(100);
            waited += 100;
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
    public void onReply_StockFilter(com.futu.openapi.FTAPI_Conn client, int retCode,
                                     QotStockFilter.Response rsp) {
        logger.info("  [Qot] onReply_StockFilter: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    lastPage={} allCount={} items={}",
                s2c.getLastPage(), s2c.getAllCount(), s2c.getDataListCount());
            for (var item : s2c.getDataListList()) {
                var sec = item.getSecurity();
                logger.info("    {} {}:", sec.getCode(), item.getName());
                for (var bd : item.getBaseDataListList()) {
                    logger.info("      field={} value={}", bd.getFieldName(), bd.getValue());
                }
                if (item.getFinancialDataListCount() > 0) {
                    for (var fd : item.getFinancialDataListList()) {
                        logger.info("      fin_field={} value={}", fd.getFieldName(), fd.getValue());
                    }
                }
            }
        } else {
            logger.warn("    stockFilter failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}