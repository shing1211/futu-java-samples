package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOwnerPlate;
import com.futu.openapi.pb.QotGetPlateSecurity;
import com.futu.openapi.pb.QotGetPlateSet;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Example 56: ETF Composition & Weight Analysis
 *
 * Demonstrates:
 *   - getOwnerPlate: find plates/concepts an ETF belongs to
 *   - getPlateSecurity: get constituent stocks from a related concept plate
 *   - getSecuritySnapshot: current prices for all constituents
 *   - Calculate equal-weight vs market-cap weight comparison vs VWAP benchmark
 *   - Uses a HK ETF (恒生科技指数ETF) as the demo basket
 *
 * Note: Futu API does not expose direct ETF holdings/weights.
 *       This example approximates by taking the top stocks from the
 *       ETF's primary sector plate and treating them as the basket.
 *
 * Mirrors: conceptual pattern from ETF analysis tools adapted for Futu API
 */
public class Example56_ETFComposition implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example56_ETFComposition.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private final List<QotCommon.Security> constituents = new ArrayList<>();
    private double totalMarketCap = 0;

    // Market: HK=1
    private static final int MARKET_HK = 1;
    // PlateSetType: Concept=3
    private static final int PLATE_SET_CONCEPT = 3;

    public static void main(String[] args) {
        logger.info("=== ETF Composition & Benchmark Demo ===");
        FTAPI.init();
        Example56_ETFComposition demo = new Example56_ETFComposition();
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

        // Step 1: getOwnerPlate for a tech-related HK ETF to find its sector plates
        // Using HK.03033 (ChinaAMC Hang Seng TECH Index ETF) as example
        logger.info("\n=== Step 1: getOwnerPlate for HK.03033 ===");
        var etfSec = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("03033").build();
        var ownerC2s = QotGetOwnerPlate.C2S.newBuilder().addSecurityList(etfSec).build();
        int ret = qot.getOwnerPlate(QotGetOwnerPlate.Request.newBuilder().setC2S(ownerC2s).build());
        logger.info("getOwnerPlate ret={}", ret);
        sleep(500);

        // Step 2: getPlateSet to get top concept plates (we'll pick one as our basket)
        logger.info("\n=== Step 2: getPlateSet CONCEPT plates ===");
        var plateSetC2s = QotGetPlateSet.C2S.newBuilder()
            .setMarket(MARKET_HK)
            .setPlateSetType(PLATE_SET_CONCEPT)
            .build();
        ret = qot.getPlateSet(QotGetPlateSet.Request.newBuilder().setC2S(plateSetC2s).build());
        logger.info("getPlateSet ret={}", ret);

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
    public void onReply_GetOwnerPlate(com.futu.openapi.FTAPI_Conn client, int retCode,
                                      QotGetOwnerPlate.Response rsp) {
        logger.info("  [Qot] onReply_GetOwnerPlate: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getOwnerPlateListCount();
            logger.info("    ETF belongs to {} plates:", count);
            for (int i = 0; i < count; i++) {
                var item = s2c.getOwnerPlateList(i);
                var sec = item.getSecurity();
                int pCount = item.getPlateInfoListCount();
                logger.info("      ETF={} name={} plateCount={}", sec.getCode(), item.getName(), pCount);
                for (int j = 0; j < Math.min(pCount, 3); j++) {
                    var p = item.getPlateInfoList(j);
                    logger.info("        plate[{}]: code={} name={} type={}",
                        j, p.getPlate().getCode(), p.getName(), p.getPlateType());
                }
            }
        } else {
            logger.warn("    getOwnerPlate failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetPlateSet(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    QotGetPlateSet.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSet: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getPlateInfoListCount();
            logger.info("    Total concept plates: {}", count);

            // Pick a tech-related plate as our basket
            QotCommon.Security chosenPlate = null;
            String chosenName = null;
            for (int i = 0; i < Math.min(count, 20); i++) {
                var p = s2c.getPlateInfoList(i);
                String name = p.getName().toLowerCase();
                // Look for tech/互联网/innovation related plates
                if (name.contains("科技") || name.contains("互联网") || name.contains("创新") || name.contains("tech")) {
                    chosenPlate = p.getPlate();
                    chosenName = p.getName();
                    break;
                }
            }

            // Fallback: just pick the first plate
            if (chosenPlate == null && count > 0) {
                var first = s2c.getPlateInfoList(0);
                chosenPlate = first.getPlate();
                chosenName = first.getName();
            }

            if (chosenPlate != null) {
                logger.info("\n    Chosen plate for basket: {} ({})", chosenPlate.getCode(), chosenName);
                var psC2s = QotGetPlateSecurity.C2S.newBuilder()
                    .setPlate(chosenPlate)
                    .build();
                int ret = qot.getPlateSecurity(QotGetPlateSecurity.Request.newBuilder().setC2S(psC2s).build());
                logger.info("getPlateSecurity ret={}", ret);
            }
        } else {
            logger.warn("    getPlateSet failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetPlateSecurity(com.futu.openapi.FTAPI_Conn client, int retCode,
                                         QotGetPlateSecurity.Response rsp) {
        logger.info("  [Qot] onReply_GetPlateSecurity: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getStaticInfoListCount();
            logger.info("    Constituents in plate: {}", count);

            // Collect up to 20 stocks as our basket
            constituents.clear();
            for (int i = 0; i < Math.min(count, 20); i++) {
                var s = s2c.getStaticInfoList(i);
                if (!s.hasBasic()) continue;
                var sec = s.getBasic().getSecurity();
                constituents.add(sec);
                logger.info("      constituent[{}]: {} {}", i, sec.getCode(), s.getBasic().getName());
            }

            if (!constituents.isEmpty()) {
                // Fetch snapshot for all constituents
                logger.info("\n    Fetching snapshot for {} constituents...", constituents.size());
                var snapC2s = QotGetSecuritySnapshot.C2S.newBuilder();
                for (var sec : constituents) snapC2s.addSecurityList(sec);
                int ret = qot.getSecuritySnapshot(QotGetSecuritySnapshot.Request.newBuilder().setC2S(snapC2s).build());
                logger.info("getSecuritySnapshot ret={}", ret);
            }
        } else {
            logger.warn("    getPlateSecurity failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetSecuritySnapshot(com.futu.openapi.FTAPI_Conn client, int retCode,
                                             QotGetSecuritySnapshot.Response rsp) {
        logger.info("  [Qot] onReply_GetSecuritySnapshot: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getSnapshotListCount();
            logger.info("    Snapshot items received: {}", count);

            // Build equal-weight basket stats
            double totalPrice = 0;
            double totalTurnover = 0;
            double[] weights = new double[count];
            double[] prices = new double[count];
            String[] codes = new String[count];

            for (int i = 0; i < count; i++) {
                var snap = s2c.getSnapshotList(i);
                var basic = snap.getBasic();
                double price = basic.getCurPrice();
                double vol = basic.getVolume();
                double turnover = basic.getTurnover();
                String code = basic.getSecurity().getCode();

                totalPrice += price;
                totalTurnover += turnover;
                prices[i] = price;
                codes[i] = code;

                logger.info("      {}: price={} vol={} turnover={}",
                    code, price, vol, formatTurnover(turnover));
            }

            if (count == 0) return;

            // Equal-weight VWAP proxy (average price)
            double equalWeightVwap = totalPrice / count;

            // Turnover-weighted proxy (theoretical VWAP from snapshot)
            double turnoverWeightedVwap = totalTurnover / totalVolume(s2c);

            // Summary
            logger.info("\n  === ETF BASKET ANALYSIS ===");
            logger.info("    Constituents: {}", count);
            logger.info("    Equal-weight avg price: {:.4f}", equalWeightVwap);
            logger.info("    Turnover-weighted avg price: {:.4f}", turnoverWeightedVwap);
            logger.info("    Total basket turnover: {}", formatTurnover(totalTurnover));

            // Show top 5 by turnover (proxy for weight)
            logger.info("\n  === TOP 5 BY TURNOVER (weight proxy) ===");
            // Simple bubble sort for top 5
            for (int i = 0; i < count - 1; i++) {
                for (int j = i + 1; j < count; j++) {
                    var s2cInner = rsp.getS2C();
                    double volI = s2cInner.getSnapshotList(i).getBasic().getTurnover();
                    double volJ = s2cInner.getSnapshotList(j).getBasic().getTurnover();
                    if (volJ > volI) {
                        // swap
                        double tmpP = prices[i]; prices[i] = prices[j]; prices[j] = tmpP;
                        double tmpW = weights[i]; weights[i] = weights[j]; weights[j] = tmpW;
                        String tmpC = codes[i]; codes[i] = codes[j]; codes[j] = tmpC;
                    }
                }
            }

            // Just display first 5 from current order
            for (int i = 0; i < Math.min(5, count); i++) {
                double weight = count > 0 ? (prices[i] / totalPrice) * 100 : 0;
                logger.info("      [{}] {} price={:.2f} weight={:.2f}%",
                    i + 1, codes[i], prices[i], weight);
            }
        } else {
            logger.warn("    getSecuritySnapshot failed retCode={}", retCode);
        }
    }

    private double totalVolume(QotGetSecuritySnapshot.S2C s2c) {
        double total = 0;
        for (var snap : s2c.getSnapshotListList()) {
            total += snap.getBasic().getVolume();
        }
        return total;
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