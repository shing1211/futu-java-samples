package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetKL;
import com.futu.openapi.pb.QotGetStaticInfo;
import com.futu.openapi.pb.QotRequestHistoryKL;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateKL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Example 57: VWAP Benchmark Demo
 *
 * Demonstrates:
 *   - Subscribe to real-time K-line data for a stock
 *   - Fetch minute-level historical K-line data for last N days
 *   - Calculate theoretical VWAP = sum(price * volume) / sum(volume) for each bar
 *   - Compare VWAP against close price
 *   - Show VWAP crossover signals (price crossing above/below VWAP)
 *
 * VWAP (Volume Weighted Average Price) is a key benchmark used by:
 *   - Institutional traders to evaluate execution quality
 *   - Mean-reversion strategies (price far above VWAP = overvalued)
 *   - Momentum strategies (price breaking above VWAP = bullish signal)
 *
 * Mirrors: examples/57_vwap_benchmark/main.py from futu-python-samples
 */
public class Example57_VWAPBenchmark implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example57_VWAPBenchmark.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market constants
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 11;

    // KLType constants
    private static final int KL_TYPE_1MIN = 1;
    private static final int KL_TYPE_5MIN = 5;
    private static final int KL_TYPE_15MIN = 6;
    private static final int KL_TYPE_30MIN = 7;
    private static final int KL_TYPE_60MIN = 8;
    private static final int KL_TYPE_DAY = 9;

    // RehabType
    private static final int REHAB_FORWARD = 1;

    // VWAP accumulator: stores running sum(price*volume) and sum(volume)
    private double vwapNumerator = 0.0;
    private double vwapDenominator = 0.0;
    private double lastVwap = 0.0;
    private double lastClosePrice = 0.0;

    // History data storage for VWAP calculation
    private final List<BarData> historyBars = new ArrayList<>();

    public static void main(String[] args) {
        logger.info("=== VWAP Benchmark Demo ===");
        FTAPI.init();
        Example57_VWAPBenchmark demo = new Example57_VWAPBenchmark();
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

        // Use HK.00700 as default stock
        String stockCode = "HK.00700";
        int market = MARKET_HK;
        String code = "00700";

        // ── Step 1: Get stock metadata via QotGetStaticInfo ─────────────────────
        logger.info("\n=== Step 1: Fetching stock metadata for {} ===", stockCode);
        var sec = QotCommon.Security.newBuilder()
            .setMarket(market)
            .setCode(code)
            .build();

        var staticInfoC2s = QotGetStaticInfo.C2S.newBuilder()
            .addSecurityList(sec)
            .build();
        int ret = qot.getStaticInfo(QotGetStaticInfo.Request.newBuilder().setC2S(staticInfoC2s).build());
        logger.info("getStaticInfo ret={}", ret);
        sleep(500);

        // ── Step 2: Fetch minute-level historical K-line data ──────────────────
        logger.info("\n=== Step 2: Fetching {} bars (last {} days) for VWAP calculation ===",
            getKlTypeLabel(KL_TYPE_5MIN), 5);

        LocalDate endDate = LocalDate.now();
        LocalDate beginDate = endDate.minusDays(5);

        var histC2s = QotRequestHistoryKL.C2S.newBuilder()
            .setSecurity(sec)
            .setKlType(KL_TYPE_5MIN)  // 5-minute bars for intraday VWAP
            .setRehabType(REHAB_FORWARD)
            .setBeginTime(beginDate.toString())
            .setEndTime(endDate.toString())
            .setMaxAckKLNum(500)  // Get enough bars for meaningful VWAP
            .build();

        ret = qot.requestHistoryKL(QotRequestHistoryKL.Request.newBuilder().setC2S(histC2s).build());
        logger.info("requestHistoryKL ret={}", ret);

        sleep(1500);

        // ── Step 3: Calculate VWAP from historical data ─────────────────────────
        logger.info("\n=== Step 3: VWAP Calculation Results ===");
        calculateHistoricalVWAP();

        // ── Step 4: Subscribe to real-time K-line for live VWAP ───────────────
        logger.info("\n=== Step 4: Subscribing to real-time K-line (5Min) ===");
        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec)
            .addSubTypeList(QotCommon.SubType.SubType_KL_5Min.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(true)  // Get current bar immediately
            .build();
        ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);

        // Watch for 10 seconds to see live VWAP updates
        logger.info("Watching live K-line pushes for 10 seconds...");
        sleep(10000);

        // ── Step 5: Show VWAP crossover signals interpretation ─────────────────
        logger.info("\n=== Step 5: VWAP Crossover Signal Guide ===");
        showVwapSignalGuide();

        qot.close();
        logger.info("Done.");
    }

    /**
     * Calculate VWAP from stored historical bars.
     * VWAP = sum(price * volume) / sum(volume)
     * Using typical price = (High + Low + Close) / 3 for intrabar estimation
     */
    private void calculateHistoricalVWAP() {
        if (historyBars.isEmpty()) {
            logger.info("  No historical bars available for VWAP calculation.");
            return;
        }

        logger.info("  Calculating VWAP from {} bars...", historyBars.size());

        double totalNumerator = 0.0;
        double totalDenominator = 0.0;

        for (int i = 0; i < historyBars.size(); i++) {
            BarData bar = historyBars.get(i);
            // Use typical price for VWAP calculation
            double typicalPrice = (bar.high + bar.low + bar.close) / 3.0;
            double pv = typicalPrice * bar.volume;

            totalNumerator += pv;
            totalDenominator += bar.volume;

            // Calculate running VWAP for each bar
            double runningVwap = totalDenominator > 0 ? totalNumerator / totalDenominator : bar.close;
            double deviation = bar.close - runningVwap;
            double deviationPct = runningVwap > 0 ? (deviation / runningVwap) * 100 : 0;

            // Show first 10 and last 5 bars
            if (i < 10 || i >= historyBars.size() - 5) {
                logger.info("    Bar {} | {} | O={:.2f} C={:.2f} H={:.2f} L={:.2f} Vol={} | VWAP={:.4f} | Dev={:+.2f}% ({:+.4f})",
                    i, bar.time, bar.open, bar.close, bar.high, bar.low, bar.volume,
                    runningVwap, deviationPct, deviation);
            }
        }

        double finalVwap = totalDenominator > 0 ? totalNumerator / totalDenominator : 0;
        logger.info("\n  === Overall VWAP ===");
        logger.info("    Total Volume: {}", (long) totalDenominator);
        logger.info("    Total Turnover (approx): {:.2f}", totalNumerator);
        logger.info("    Final VWAP: {:.4f}", finalVwap);

        if (!historyBars.isEmpty()) {
            BarData lastBar = historyBars.get(historyBars.size() - 1);
            double lastDeviation = lastBar.close - finalVwap;
            double lastDevPct = finalVwap > 0 ? (lastDeviation / finalVwap) * 100 : 0;
            logger.info("    Last Close: {:.2f}", lastBar.close);
            logger.info("    Deviation from VWAP: {:+.2f}% ({:+.4f})", lastDevPct, lastDeviation);

            if (lastBar.close > finalVwap) {
                logger.info("    Interpretation: Price is trading ABOVE VWAP (bullish bias)");
            } else if (lastBar.close < finalVwap) {
                logger.info("    Interpretation: Price is trading BELOW VWAP (bearish bias)");
            } else {
                logger.info("    Interpretation: Price is AT VWAP (neutral)");
            }
        }
    }

    /**
     * Display VWAP crossover signal interpretation guide.
     */
    private void showVwapSignalGuide() {
        logger.info("  === VWAP Crossover Signal Types ===");
        logger.info("  ");
        logger.info("  1. PRICE CROSSES ABOVE VWAP (Bullish Signal)");
        logger.info("     - Indicates institutional buying pressure");
        logger.info("     - Price accepted above average execution price");
        logger.info("     - Consider LONG entries or covering shorts");
        logger.info("  ");
        logger.info("  2. PRICE CROSSES BELOW VWAP (Bearish Signal)");
        logger.info("     - Indicates institutional selling pressure");
        logger.info("     - Price accepted below average execution price");
        logger.info("     - Consider SHORT entries or selling longs");
        logger.info("  ");
        logger.info("  3. PRICE FAR ABOVE VWAP");
        logger.info("     - Stock may be overvalued / overbought");
        logger.info("     - Mean-reversion traders may take profits");
        logger.info("     - Distance from VWAP indicates strength of move");
        logger.info("  ");
        logger.info("  4. PRICE FAR BELOW VWAP");
        logger.info("     - Stock may be undervalued / oversold");
        logger.info("     - Potential mean-reversion long opportunities");
        logger.info("     - Institutional anchors may buy near VWAP");
        logger.info("  ");
        logger.info("  5. VWAP AS SUPPORT/RESISTANCE");
        logger.info("     - In strong trends, VWAP acts as dynamic support/resistance");
        logger.info("     - In ranging markets, price oscillates around VWAP");
        logger.info("  ");
        logger.info("  NOTE: VWAP is primarily an INTRADAY indicator.");
        logger.info("        It resets at market open and accumulates through the day.");
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
    public void onReply_GetStaticInfo(FTAPI_Conn client, int retCode, QotGetStaticInfo.Response rsp) {
        logger.info("  [Qot] onReply_GetStaticInfo: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Stocks found: {}", count);
            for (int i = 0; i < count; i++) {
                var info = s2c.getStaticInfoList(i);
                var basic = info.getBasic();
                var sec = basic.getSecurity();
                logger.info("    [{}] {} ({}) lot_size={} sec_type={}",
                    i, sec.getCode(), basic.getName(), basic.getLotSize(),
                    securityTypeLabel(basic.getSecType()));
            }
        } else {
            logger.warn("    getStaticInfo failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetKL(com.futu.openapi.FTAPI_Conn client, int retCode, QotGetKL.Response rsp) {
        logger.info("  [Qot] onReply_GetKL: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    {} {}: {} bars", sec.getCode(), s2c.getName(), s2c.getKlListCount());
            for (var bar : s2c.getKlListList()) {
                historyBars.add(new BarData(
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume()));
                logger.info("      {} O={:.2f} C={:.2f} H={:.2f} L={:.2f} Vol={}",
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume());
            }
        }
    }

    @Override
    public void onReply_RequestHistoryKL(com.futu.openapi.FTAPI_Conn client, int retCode,
                                         QotRequestHistoryKL.Response rsp) {
        logger.info("  [Qot] onReply_RequestHistoryKL: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    {} {}: {} bars (nextReqKey present={})",
                sec.getCode(), s2c.getName(), s2c.getKlListCount(), s2c.hasNextReqKey());
            for (var bar : s2c.getKlListList()) {
                historyBars.add(new BarData(
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume()));
                logger.info("      {} O={:.2f} C={:.2f} H={:.2f} L={:.2f} Vol={}",
                    bar.getTime(), bar.getOpenPrice(), bar.getClosePrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getVolume());
            }
        } else {
            logger.warn("    requestHistoryKL failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode != 0) logger.warn("    subscribe failed retCode={}", retCode);
    }

    @Override
    public void onPush_UpdateKL(com.futu.openapi.FTAPI_Conn client, QotUpdateKL.Response rsp) {
        if (!rsp.hasS2C()) return;
        var s2c = rsp.getS2C();
        var sec = s2c.getSecurity();
        int klType = s2c.getKlType();

        for (int i = 0; i < s2c.getKlListCount(); i++) {
            var k = s2c.getKlList(i);
            double typicalPrice = (k.getHighPrice() + k.getLowPrice() + k.getClosePrice()) / 3.0;
            double pv = typicalPrice * k.getVolume();

            // Update running VWAP
            vwapNumerator += pv;
            vwapDenominator += k.getVolume();
            double currentVwap = vwapDenominator > 0 ? vwapNumerator / vwapDenominator : k.getClosePrice();

            // Detect crossover
            String signal = "";
            if (lastVwap > 0 && lastClosePrice > 0) {
                if (k.getClosePrice() > currentVwap && lastClosePrice <= lastVwap) {
                    signal = " [BULLISH CROSSOVER]";
                } else if (k.getClosePrice() < currentVwap && lastClosePrice >= lastVwap) {
                    signal = " [BEARISH CROSSOVER]";
                }
            }

            logger.info("  [Live {}] {} time={} O={:.2f} C={:.2f} H={:.2f} L={:.2f} Vol={} | VWAP={:.4f}{}",
                getKlTypeLabel(klType), sec.getCode(), k.getTime(),
                k.getOpenPrice(), k.getClosePrice(), k.getHighPrice(), k.getLowPrice(),
                k.getVolume(), currentVwap, signal);

            lastVwap = currentVwap;
            lastClosePrice = k.getClosePrice();
        }
    }

    /**
     * Bar data holder for VWAP calculation.
     */
    private static class BarData {
        final String time;
        final double open;
        final double close;
        final double high;
        final double low;
        final long volume;

        BarData(String time, double open, double close, double high, double low, long volume) {
            this.time = time;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
            this.volume = volume;
        }
    }

    private static String getKlTypeLabel(int klType) {
        return switch (klType) {
            case KL_TYPE_1MIN -> "1Min";
            case 2 -> "2Min";
            case KL_TYPE_5MIN -> "5Min";
            case KL_TYPE_15MIN -> "15Min";
            case KL_TYPE_30MIN -> "30Min";
            case KL_TYPE_60MIN -> "60Min";
            case KL_TYPE_DAY -> "Day";
            default -> "KL(" + klType + ")";
        };
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
