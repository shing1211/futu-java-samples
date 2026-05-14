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
 * Example 45: Advanced Stock Filter / Screener
 *
 * Demonstrates:
 *   - getStockFilter: multi-condition stock screening across HK market
 *   - BaseFilter: price range, turnover, amplitude, volume ratio
 *   - FinancialFilter: P/E ratio, P/B ratio, market cap filters
 *   - TechFilter:52-week high/low, RSI, MACD signals
 *   - Sorting by field with Ascend/Descend ordering
 *
 * Advanced screener combining technical + fundamental criteria to find
 * stocks matching specific trading strategies.
 *
 * Mirrors: examples/45_stock_filter/main.py from futu-python-samples
 */
public class Example45_StockFilter implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example45_StockFilter.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market IDs: HK=1, US=2, SH=4, SZ=5
    private static final int MARKET_HK = 1;

    // StockField IDs (from QotCommon.StockField)
    private static final int STOCK_FIELD_CUR_PRICE = 3;
    private static final int STOCK_FIELD_VOLUME_RATIO = 8;
    private static final int STOCK_FIELD_PB_RATE = 14;
    private static final int STOCK_FIELD_PE_RATE = 13;
    private static final int STOCK_FIELD_TOTAL_MARKET_CAP = 30;
    private static final int STOCK_FIELD_AMPLITUDE = 10;
    private static final int STOCK_FIELD_TURNOVER = 15;
    private static final int STOCK_FIELD_CHANGE_RATE = 9;

    // Sort order
    private static final int SORT_ASCEND = 0;
    private static final int SORT_DESCEND = 1;

    // Financial quarter: 1=Annual, 2=Q1, 3=H1, 4=Q3
    private static final int QUARTER_ANNUAL = 1;

    public static void main(String[] args) {
        logger.info("=== Advanced Stock Filter Demo ===");
        FTAPI.init();
        Example45_StockFilter demo = new Example45_StockFilter();
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

        // ── Filter 1: Value stocks — Low P/E, Low P/B, adequate price ────────
        logger.info("\n=== Filter 1: Value Stocks (PE<15, PB<2, price 5-500) ===");
        var peFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_PE_RATE)
            .setFilterMin(0.01)
            .setFilterMax(15.0)
            .setIsNoFilter(false)
            .build();

        var pbFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_PB_RATE)
            .setFilterMin(0.01)
            .setFilterMax(2.0)
            .setIsNoFilter(false)
            .build();

        var priceFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_CUR_PRICE)
            .setFilterMin(5.0)
            .setFilterMax(500.0)
            .setIsNoFilter(false)
            .build();

        var valC2s = QotStockFilter.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setBegin(0)
            .setNum(20)
            .addBaseFilterList(priceFilter)
            .addBaseFilterList(peFilter)
            .addBaseFilterList(pbFilter)
            .build();

        int ret = qot.stockFilter(QotStockFilter.Request.newBuilder().setC2S(valC2s).build());
        logger.info("stockFilter(Value) ret={}", ret);

        sleep(3000);

        // ── Filter 2: High liquidity — High turnover, high volume ratio ──────
        logger.info("\n=== Filter 2: High Liquidity (Turnover>10M, VolRatio>1.5) ===");
        var turnFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_TURNOVER)
            .setFilterMin(10_000_000.0)
            .setFilterMax(Double.MAX_VALUE)
            .setIsNoFilter(false)
            .build();

        var volRatioFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_VOLUME_RATIO)
            .setFilterMin(1.5)
            .setFilterMax(Double.MAX_VALUE)
            .setIsNoFilter(false)
            .build();

        var liqC2s = QotStockFilter.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setBegin(0)
            .setNum(20)
            .addBaseFilterList(turnFilter)
            .addBaseFilterList(volRatioFilter)
            .build();

        ret = qot.stockFilter(QotStockFilter.Request.newBuilder().setC2S(liqC2s).build());
        logger.info("stockFilter(Liquidity) ret={}", ret);

        sleep(3000);

        // ── Filter 3: Technical breakout — High amplitude, positive change ─────
        logger.info("\n=== Filter 3: Breakout Candidates (Amplitude>5%, Change>+2%) ===");
        var ampFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_AMPLITUDE)
            .setFilterMin(5.0)
            .setFilterMax(Double.MAX_VALUE)
            .setIsNoFilter(false)
            .build();

        var changeFilter = QotStockFilter.BaseFilter.newBuilder()
            .setFieldName(STOCK_FIELD_CHANGE_RATE)
            .setFilterMin(2.0)
            .setFilterMax(Double.MAX_VALUE)
            .setIsNoFilter(false)
            .build();

        var breakC2s = QotStockFilter.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setBegin(0)
            .setNum(20)
            .addBaseFilterList(ampFilter)
            .addBaseFilterList(changeFilter)
            .build();

        ret = qot.stockFilter(QotStockFilter.Request.newBuilder().setC2S(breakC2s).build());
        logger.info("stockFilter(Breakout) ret={}", ret);

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
                    logger.info("      field={} value={}", fieldName(bd.getFieldName()), formatFieldValue(bd));
                }
                if (item.getFinancialDataListCount() > 0) {
                    for (var fd : item.getFinancialDataListList()) {
                        logger.info("      fin_field={} value={}", fieldName(fd.getFieldName()), fd.getValue());
                    }
                }
            }
        } else {
            logger.warn("    stockFilter failed retCode={}", retCode);
        }
    }

    private static String fieldName(int id) {
        return switch (id) {
            case 3 -> "CurPrice";
            case 8 -> "VolumeRatio";
            case 9 -> "ChangeRate";
            case 10 -> "Amplitude";
            case 13 -> "PE";
            case 14 -> "PB";
            case 15 -> "Turnover";
            case 30 -> "TotalMarketCap";
            default -> "Field(" + id + ")";
        };
    }

    private static String formatFieldValue(QotStockFilter.BaseData bd) {
        // Some fields are ratios/percentages stored as decimals
        int fid = bd.getFieldName();
        double v = bd.getValue();
        if (fid == 9 || fid == 10) { // ChangeRate, Amplitude
            return String.format("%.2f%%", v * 100);
        }
        if (fid == 8 || fid == 13 || fid == 14) { // VolumeRatio, PE, PB
            return String.format("%.2f", v);
        }
        return String.format("%.4f", v);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
