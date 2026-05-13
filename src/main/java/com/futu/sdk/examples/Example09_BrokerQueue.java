package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetBroker;
import com.futu.openapi.pb.QotSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 09: Broker Queue
 *
 * Demonstrates:
 *   - subscribe: subscribe to broker queue subtype
 *   - get_broker_queue: fetch current broker bid/ask queue
 *   - Broker data: top N brokers with their bid/ask volumes
 *
 * Broker queue shows which brokerage firms are at the bid/ask,
 * useful for understanding institutional order flow.
 *
 * Mirrors: examples/09_broker_queue/main.py from futu-python-samples
 */
public class Example09_BrokerQueue implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example09_BrokerQueue.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    public static void main(String[] args) {
        logger.info("=== Broker Queue Demo ===");
        FTAPI.init();
        Example09_BrokerQueue demo = new Example09_BrokerQueue();
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

        // ── Subscribe to BROKER for HK.00700 ─────────────────────────────
        logger.info("\n--- Subscribe: HK.00700 BROKER ---");
        var sec00700 = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("00700").build();
        var subC2s = QotSub.C2S.newBuilder()
            .addSecurityList(sec00700)
            .addSubTypeList(QotCommon.SubType.SubType_Broker.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(false)
            .build();
        int ret = qot.sub(QotSub.Request.newBuilder().setC2S(subC2s).build());
        logger.info("subscribe ret={}", ret);
        sleep(200);

        // ── getBrokerQueue: HK.00700 ───────────────────────────────────────
        logger.info("\n=== getBrokerQueue: HK.00700 ===");
        var brokerC2s = QotGetBroker.C2S.newBuilder()
            .setSecurity(sec00700)
            .build();
        ret = qot.getBroker(QotGetBroker.Request.newBuilder().setC2S(brokerC2s).build());
        logger.info("getBroker ret={}", ret);
        sleep(300);

        // ── Also try HK.HSImain ───────────────────────────────────────────
        logger.info("\n=== getBrokerQueue: HK.HSImain ===");
        var secHSI = QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build();
        var subHSI = QotSub.C2S.newBuilder()
            .addSecurityList(secHSI)
            .addSubTypeList(QotCommon.SubType.SubType_Broker.getNumber())
            .setIsSubOrUnSub(true)
            .setIsRegOrUnRegPush(true)
            .setIsFirstPush(false)
            .build();
        ret = qot.sub(QotSub.Request.newBuilder().setC2S(subHSI).build());
        logger.info("subscribe HSImain ret={}", ret);
        sleep(200);

        var brokerHSI = QotGetBroker.C2S.newBuilder()
            .setSecurity(secHSI)
            .build();
        ret = qot.getBroker(QotGetBroker.Request.newBuilder().setC2S(brokerHSI).build());
        logger.info("getBroker(HSImain) ret={}", ret);

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
    public void onReply_Sub(com.futu.openapi.FTAPI_Conn client, int retCode, QotSub.Response rsp) {
        logger.info("  [Qot] onReply_Sub: retCode={}", retCode);
        if (retCode != 0) {
            logger.warn("    subscribe failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetBroker(com.futu.openapi.FTAPI_Conn client, int retCode,
                                  QotGetBroker.Response rsp) {
        logger.info("  [Qot] onReply_GetBroker: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            var sec = s2c.getSecurity();
            logger.info("    {} name={}:", sec.getCode(), s2c.getName());
            int bidCount = s2c.getBrokerBidListCount();
            int askCount = s2c.getBrokerAskListCount();
            logger.info("    BID Brokers ({} entries):", bidCount);
            for (int i = 0; i < bidCount; i++) {
                var b = s2c.getBrokerBidList(i);
                logger.info("      bid[{}] id={} name={} pos={} vol={}",
                    i, b.getId(), b.getName(), b.getPos(), b.getVolume());
            }
            logger.info("    ASK Brokers ({} entries):", askCount);
            for (int i = 0; i < askCount; i++) {
                var b = s2c.getBrokerAskList(i);
                logger.info("      ask[{}] id={} name={} pos={} vol={}",
                    i, b.getId(), b.getName(), b.getPos(), b.getVolume());
            }
        } else {
            logger.warn("    getBroker failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}