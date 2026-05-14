package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetKL;
import com.futu.openapi.pb.QotRequestHistoryKL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Example 55: EMA Crossover (Golden Cross / Death Cross)
 *
 * Demonstrates:
 *   - requestHistoryKL: fetch daily K-line data for EMA calculation
 *   - Calculate EMA(12), EMA(26), EMA(50), EMA(200) in pure Java
 *   - Detect and report Golden Cross (EMA50 crosses above EMA200) and
 *     Death Cross (EMA50 crosses below EMA200) signals
 *   - Uses HK.00700 as the demo security
 *
 * Mirrors: examples/55_momentum_screener/main.py adapted for EMA
 */
public class Example55_EMA implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example55_EMA.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // KLType: Day=9
    private static final int KL_TYPE_DAY = 9;
    // RehabType: Forward (qfq)=1
    private static final int REHAB_FORWARD = 1;
    // Market: HK=1
    private static final int MARKET_HK = 1;

    // EMA periods
    private static final int EMA_FAST = 12;
    private static final int EMA_MID = 26;
    private static final int EMA_SLOW1 = 50;
    private static final int EMA_SLOW2 = 200;

    public static void main(String[] args) {
        logger.info("=== EMA Crossover Demo ===");
        FTAPI.init();
        Example55_EMA demo = new Example55_EMA();
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

        var sec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();

        // ── requestHistoryKL: last 300 days ─────────────────────────────────
        logger.info("\n=== requestHistoryKL: HK.00700 daily (300 days) ===");
        var endDate = LocalDate.now();
        var beginDate = endDate.minusDays(320);
        var c2s = QotRequestHistoryKL.C2S.newBuilder()
            .setSecurity(sec)
            .setKlType(KL_TYPE_DAY)
            .setRehabType(REHAB_FORWARD)
            .setBeginTime(beginDate.toString())
            .setEndTime(endDate.toString())
            .setMaxAckKLNum(300)
            .build();
        int ret = qot.requestHistoryKL(QotRequestHistoryKL.Request.newBuilder().setC2S(c2s).build());
        logger.info("requestHistoryKL ret={}", ret);

        sleep(1500);
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
    public void onReply_RequestHistoryKL(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotRequestHistoryKL.Response rsp) {
        logger.info("  [Qot] onReply_RequestHistoryKL: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getKlListCount();
            logger.info("    Received {} bars for {}", count, s2c.getSecurity().getCode());

            // Collect closes oldest-first
            List<Double> closes = new ArrayList<>();
            List<String> times = new ArrayList<>();
            for (var bar : s2c.getKlListList()) {
                closes.add(bar.getClosePrice());
                times.add(bar.getTime());
            }

            if (closes.size() < EMA_SLOW2 + 5) {
                logger.warn("    Insufficient data for EMA200. Need {} bars, got {}",
                    EMA_SLOW2 + 5, closes.size());
                return;
            }

            // Compute EMAs
            double[] ema12 = computeEMA(closes, EMA_FAST);
            double[] ema26 = computeEMA(closes, EMA_MID);
            double[] ema50 = computeEMA(closes, EMA_SLOW1);
            double[] ema200 = computeEMA(closes, EMA_SLOW2);

            logger.info("\n  === EMA VALUES (last 5 bars) ===");
            int n = closes.size();
            for (int i = n - 5; i < n; i++) {
                logger.info("    {} | close={:.2f} EMA12={:.2f} EMA26={:.2f} EMA50={:.2f} EMA200={:.2f}",
                    times.get(i), closes.get(i),
                    i < ema12.length ? ema12[i] : 0,
                    i < ema26.length ? ema26[i] : 0,
                    i < ema50.length ? ema50[i] : 0,
                    i < ema200.length ? ema200[i] : 0);
            }

            // Detect crossovers
            logger.info("\n  === CROSSOVER SIGNALS ===");
            detectCrossover(times, ema50, ema200, "Golden", "Death");
            detectCrossover(times, ema12, ema26, "Bullish", "Bearish");

            // Current signal summary
            double curEma50 = ema50[n - 1];
            double curEma200 = ema200[n - 1];
            double curEma12 = ema12[n - 1];
            double curEma26 = ema26[n - 1];

            String trend = curEma50 > curEma200 ? "BULL (EMA50 > EMA200)" : "BEAR (EMA50 < EMA200)";
            String shortTrend = curEma12 > curEma26 ? "POSITIVE" : "NEGATIVE";

            logger.info("\n  === CURRENT SIGNAL ===");
            logger.info("    Trend : {}", trend);
            logger.info("    Short : {} (EMA12 {} EMA26)",
                shortTrend, curEma12 > curEma26 ? ">" : "<");
            logger.info("    Gap   : EMA50-EMA200 = {:.2f}%",
                curEma200 > 0 ? (curEma50 - curEma200) / curEma200 * 100 : 0);
        } else {
            logger.warn("    requestHistoryKL failed retCode={}", retCode);
        }
    }

    /**
     * Compute EMA for a list of close prices.
     * Returns an array where valid EMA values start at index (period-1).
     * EMA formula: EMA_t = price_t * k + EMA_{t-1} * (1-k), k = 2/(period+1)
     */
    private static double[] computeEMA(List<Double> closes, int period) {
        int n = closes.size();
        double[] ema = new double[n];
        double k = 2.0 / (period + 1);

        // Start with SMA for first (period-1) bars
        for (int i = 0; i < n; i++) {
            if (i < period - 1) {
                // Not enough data for EMA yet
                ema[i] = Double.NaN;
            } else if (i == period - 1) {
                // First EMA: SMA of first 'period' bars
                double sum = 0;
                for (int j = 0; j < period; j++) sum += closes.get(j);
                ema[i] = sum / period;
            } else {
                // Standard EMA
                ema[i] = closes.get(i) * k + ema[i - 1] * (1 - k);
            }
        }
        return ema;
    }

    /**
     * Detect EMA crossovers: when fast crosses slow.
     * Looks at the most recent 60 bars for recent signals.
     */
    private static void detectCrossover(List<String> times, double[] emaFast, double[] emaSlow,
                                        String bullishLabel, String bearishLabel) {
        int n = times.size();
        int lookback = Math.min(60, n - 1);
        int signals = 0;

        for (int i = n - lookback; i < n - 1; i++) {
            boolean fastAbove = !Double.isNaN(emaFast[i]) && !Double.isNaN(emaSlow[i])
                && emaFast[i] > emaSlow[i];
            boolean fastAboveNext = !Double.isNaN(emaFast[i + 1]) && !Double.isNaN(emaSlow[i + 1])
                && emaFast[i + 1] > emaSlow[i + 1];
            boolean fastBelow = !Double.isNaN(emaFast[i]) && !Double.isNaN(emaSlow[i])
                && emaFast[i] < emaSlow[i];
            boolean fastBelowNext = !Double.isNaN(emaFast[i + 1]) && !Double.isNaN(emaSlow[i + 1])
                && emaFast[i + 1] < emaSlow[i + 1];

            if (fastBelow && fastAboveNext) {
                logger.info("    [{}] {} CROSS — {} ({} vs {})",
                    times.get(i + 1), bullishLabel, times.get(i + 1),
                    fmt(emaFast[i + 1]), fmt(emaSlow[i + 1]));
                signals++;
            } else if (fastAbove && fastBelowNext) {
                logger.info("    [{}] {} CROSS — {} ({} vs {})",
                    times.get(i + 1), bearishLabel, times.get(i + 1),
                    fmt(emaFast[i + 1]), fmt(emaSlow[i + 1]));
                signals++;
            }
        }
        if (signals == 0) logger.info("    No crossovers in last {} bars", lookback);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}