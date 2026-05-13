package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetPriceReminder;
import com.futu.openapi.pb.QotSetPriceReminder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 23: Price Reminder (set_price_reminder / get_price_reminder)
 *
 * Demonstrates:
 *   - setPriceReminder: add/delete price alert conditions
 *   - getPriceReminder: list all active alerts for a stock
 *   - PriceReminderOp: ADD=1, DEL=2
 *   - PriceReminderType: PRICE_UP=1, PRICE_DOWN=2
 *   - PriceReminderFreq: ONCE=1, ALWAYS=2
 *
 * Mirrors: examples/23_price_reminder/main.py from futu-python-samples
 */
public class Example23_PriceReminder implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example23_PriceReminder.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1
    private static final int MARKET_HK = 1;

    // PriceReminderOp: ADD=1, DEL=2
    private static final int OP_ADD = 1;
    private static final int OP_DEL = 2;

    // PriceReminderType: PRICE_UP=1, PRICE_DOWN=2
    private static final int TYPE_PRICE_UP = 1;
    private static final int TYPE_PRICE_DOWN = 2;

    // PriceReminderFreq: ONCE=1, ALWAYS=2
    private static final int FREQ_ONCE = 1;
    private static final int FREQ_ALWAYS = 2;

    public static void main(String[] args) {
        logger.info("=== Price Reminder Demo ===");
        FTAPI.init();
        Example23_PriceReminder demo = new Example23_PriceReminder();
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

        // ── setPriceReminder: ADD above threshold ──────────────────────
        logger.info("\n=== setPriceReminder: ADD above_500 (PRICE_UP) ===");
        var addAbove = QotSetPriceReminder.C2S.newBuilder()
            .setSecurity(sec)
            .setOp(OP_ADD)
            .setKey(0)
            .setType(TYPE_PRICE_UP)
            .setFreq(FREQ_ONCE)
            .setValue(500.0)
            .setNote("TCEH above 500!")
            .build();
        int ret = qot.setPriceReminder(QotSetPriceReminder.Request.newBuilder().setC2S(addAbove).build());
        logger.info("setPriceReminder(ADD UP) ret={}", ret);
        sleep(300);

        // ── setPriceReminder: ADD below threshold ──────────────────────
        logger.info("\n=== setPriceReminder: ADD below_300 (PRICE_DOWN) ===");
        var addBelow = QotSetPriceReminder.C2S.newBuilder()
            .setSecurity(sec)
            .setOp(OP_ADD)
            .setKey(0)
            .setType(TYPE_PRICE_DOWN)
            .setFreq(FREQ_ONCE)
            .setValue(300.0)
            .setNote("TCEH below 300!")
            .build();
        ret = qot.setPriceReminder(QotSetPriceReminder.Request.newBuilder().setC2S(addBelow).build());
        logger.info("setPriceReminder(ADD DOWN) ret={}", ret);
        sleep(300);

        // ── getPriceReminder: list all active alerts ───────────────────
        logger.info("\n=== getPriceReminder: HK.00700 ===");
        var getC2s = QotGetPriceReminder.C2S.newBuilder()
            .setSecurity(sec)
            .setMarket(MARKET_HK)
            .build();
        ret = qot.getPriceReminder(QotGetPriceReminder.Request.newBuilder().setC2S(getC2s).build());
        logger.info("getPriceReminder ret={}", ret);
        sleep(300);

        // ── Delete alerts (in practice, read key from get response) ────
        // For demo, we just try to delete key=0
        logger.info("\n=== setPriceReminder: DEL key=0 ===");
        var delC2s = QotSetPriceReminder.C2S.newBuilder()
            .setSecurity(sec)
            .setOp(OP_DEL)
            .setKey(0)
            .build();
        ret = qot.setPriceReminder(QotSetPriceReminder.Request.newBuilder().setC2S(delC2s).build());
        logger.info("setPriceReminder(DEL) ret={}", ret);

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
    public void onReply_SetPriceReminder(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotSetPriceReminder.Response rsp) {
        logger.info("  [Qot] onReply_SetPriceReminder: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            logger.info("    key={}", rsp.getS2C().getKey());
        } else {
            logger.warn("    setPriceReminder failed retCode={}", retCode);
        }
    }

    @Override
    public void onReply_GetPriceReminder(com.futu.openapi.FTAPI_Conn client, int retCode,
                                          QotGetPriceReminder.Response rsp) {
        logger.info("  [Qot] onReply_GetPriceReminder: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getPriceReminderListCount();
            if (count == 0) {
                logger.info("    No active price reminders");
            } else {
                logger.info("    Active reminders ({}):", count);
                for (int i = 0; i < count; i++) {
                    var pr = s2c.getPriceReminderList(i);
                    var sec = pr.getSecurity();
                    logger.info("      {} ({}):", sec.getCode(), pr.getName());
                    int itemCount = pr.getItemListCount();
                    for (int j = 0; j < itemCount; j++) {
                        var item = pr.getItemList(j);
                        logger.info("        key={} type={} freq={} value={:.2f} note={} enable={}",
                            item.getKey(), reminderTypeLabel(item.getType()),
                            reminderFreqLabel(item.getFreq()), item.getValue(),
                            item.getNote(), item.getIsEnable());
                    }
                }
            }
        } else {
            logger.warn("    getPriceReminder failed retCode={}", retCode);
        }
    }

    private static String reminderTypeLabel(int type) {
        return switch (type) {
            case 1 -> "PRICE_UP";
            case 2 -> "PRICE_DOWN";
            case 3 -> "CHANGE_RATE";
            default -> "TYPE(" + type + ")";
        };
    }

    private static String reminderFreqLabel(int freq) {
        return switch (freq) {
            case 1 -> "ONCE";
            case 2 -> "ALWAYS";
            default -> "FREQ(" + freq + ")";
        };
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}