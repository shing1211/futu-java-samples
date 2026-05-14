package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetCapitalFlow;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 53: Market Heat Analysis
 *
 * Demonstrates:
 *   - getSecuritySnapshot: multi-stock snapshot for breadth indicators
 *   - getCapitalFlow: intraday capital flow to gauge market energy
 *   - Computes a simple "market heat gauge" from turnover rate,
 *     volatility (high-low range), and advance/decline breadth
 *   - Uses HK.00700, HK.09988, HK.00388 as a representative basket
 *
 * Mirrors: examples/53_option_expiration_cycle/main.py pattern adapted for market heat
 */
public class Example53_MarketHeat implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example53_MarketHeat.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // PeriodType: Intraday=1, Day=2
    private static final int PERIOD_TYPE_INTRADAY = 1;

    public static void main(String[] args) {
        logger.info("=== Market Heat Analysis Demo ===");
        FTAPI.init();
        Example53_MarketHeat demo = new Example53_MarketHeat();
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

        // ── Representative basket ──────────────────────────────────────────
        QotCommon.Security[] basket = {
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("09988").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00388").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00941").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("02318").build(),
        };

        // ── getSecuritySnapshot: breadth + volatility ─────────────────────
        logger.info("\n=== getSecuritySnapshot: {} stocks ===", basket.length);
        var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder();
        for (var sec : basket) snapC2s.addSecurityList(sec);
        int ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
        logger.info("getSecuritySnapshot ret={}", ret);
        sleep(500);

        // ── getCapitalFlow: HK.00700 as market proxy ─────────────────────
        logger.info("\n=== getCapitalFlow: HK.00700 INTRADAY ===");
        var flowC2s = QotGetCapitalFlow.C2S.newBuilder()
            .setSecurity(QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build())
            .setPeriodType(PERIOD_TYPE_INTRADAY)
            .build();
        ret = qot.getCapitalFlow(QotGetCapitalFlow.Request.newBuilder().setC2S(flowC2s).build());
        logger.info("getCapitalFlow ret={}", ret);

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
    public void onReply_GetSecuritySnapshot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                            QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int advancing = 0, declining = 0;
            double totalRangePct = 0.0;
            double totalTurnover = 0.0;

            logger.info("  Snapshot items: {}", s2c.getSnapshotListCount());
            for (var snap : s2c.getSnapshotListList()) {
                var basic = snap.getBasic();
                double cur = basic.getCurPrice();
                double open = basic.getOpenPrice();
                double high = basic.getHighPrice();
                double low = basic.getLowPrice();
                double lastClose = basic.getLastClosePrice();

                double chgPct = lastClose > 0 ? (cur - lastClose) / lastClose * 100 : 0;
                double rangePct = low > 0 ? (high - low) / low * 100 : 0;
                totalRangePct += rangePct;
                totalTurnover += basic.getTurnover();

                if (chgPct > 0) advancing++;
                else if (chgPct < 0) declining++;

                logger.info("    {}: price={} ({:+.2f}%) range={:.2f}% vol={} turnover={}",
                    basic.getSecurity().getCode(), cur, chgPct, rangePct,
                    basic.getVolume(), formatTurnover(basic.getTurnover()));
            }

            int total = advancing + declining;
            double breadthPct = total > 0 ? (double) (advancing - declining) / total * 100 : 0;
            double avgRange = s2c.getSnapshotListCount() > 0 ? totalRangePct / s2c.getSnapshotListCount() : 0;

            // Simple heat gauge: combine breadth + avg volatility
            String heatLabel;
            if (breadthPct >= 20 && avgRange >= 2.0) heatLabel = "VERY HOT";
            else if (breadthPct >= 10 && avgRange >= 1.5) heatLabel = "HOT";
            else if (breadthPct <= -20 && avgRange >= 2.0) heatLabel = "VERY COLD";
            else if (breadthPct <= -10 && avgRange >= 1.5) heatLabel = "COLD";
            else if (breadthPct > 0) heatLabel = "BULLISH";
            else if (breadthPct < 0) heatLabel = "BEARISH";
            else heatLabel = "NEUTRAL";

            logger.info("\n  === MARKET HEAT GAUGE ===");
            logger.info("    Advancing: {}  Declining: {}  Breadth: {:+.1f}%", advancing, declining, breadthPct);
            logger.info("    Avg intraday range: {:.2f}%", avgRange);
            logger.info("    Total turnover: {}", formatTurnover(totalTurnover));
            logger.info("    Heat label: {}", heatLabel);
        } else {
            logger.warn("    getSecuritySnapshot failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetCapitalFlow(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetCapitalFlow.Response rsp) {
        logger.info("  [Qot] onReply_GetCapitalFlow: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getFlowItemListCount();
            logger.info("    Flow items: {} bars, lastValidTime={}", count, s2c.getLastValidTime());

            double totalIn = 0, totalOut = 0, netFlow = 0;
            for (int i = Math.max(0, count - 10); i < count; i++) {
                var item = s2c.getFlowItemList(i);
                totalIn += item.getInFlow();
                totalOut += 0;  // SDK does not provide granular out-flow; show in-flow breakdown instead
                netFlow += item.getInFlow();
            }
            logger.info("    Recent 10 bars — In: {} Out: {} Net: {:+.2f}",
                formatTurnover(totalIn), formatTurnover(totalOut), formatTurnover(netFlow));
        } else {
            logger.warn("    getCapitalFlow failed retCode={}", retCode);
        }
    }

    private static String formatTurnover(double t) {
        if (t >= 1_000_000_000) return String.format("%.2fB", t / 1_000_000_000);
        if (t >= 1_000_000) return String.format("%.2fM", t / 1_000_000);
        if (t >= 1_000) return String.format("%.2fK", t / 1_000);
        return String.format("%.2f", t);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}