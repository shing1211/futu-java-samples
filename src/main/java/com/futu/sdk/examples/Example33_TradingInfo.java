package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetAccList;
import com.futu.openapi.pb.TrdGetMaxTrdQtys;
import com.futu.openapi.pb.TrdUnlockTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 33: Account Trading Info (getMaxTrdQtys)
 *
 * Demonstrates:
 *   - getMaxTrdQtys: max buy/sell quantities for an order
 *   - MaxTrdQtys fields: maxCashBuy, maxCashAndMarginBuy, maxPositionSell,
 *     maxSellShort, maxBuyBack, longRequiredIM, shortRequiredIM
 *   - Requires unlockTrade to work
 *
 * Note: Requires unlocked trading context.
 *
 * Mirrors: examples/33_trading_info/main.py from futu-python-samples
 */
public class Example33_TradingInfo implements FTSPI_Trd, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example33_TradingInfo.class);

    private final FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    private volatile boolean connected = false;

    // TrdEnv: Simulate=0, Real=1
    private static final int TRD_ENV_SIMULATE = 0;
    private static final int TRD_ENV_REAL = 1;
    private static final int TRD_MARKET_HK = 1;
    // OrderType: NORMAL=1
    private static final int ORDER_TYPE_NORMAL = 1;

    private long accId = 0;

    public static void main(String[] args) {
        logger.info("=== Account Trading Info Demo ===");
        FTAPI.init();
        Example33_TradingInfo demo = new Example33_TradingInfo();
        demo.start();
    }

    public void start() {
        trd.setClientInfo("javaclient_trd", 1);
        trd.setConnSpi(this);
        trd.setTrdSpi(this);

        if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
            trd.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
        }

        boolean ok = trd.initConnect(
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

        // Unlock
        String tradePwd = Config.FUTU_TRADE_PASSWORD;
        if (tradePwd != null && !tradePwd.isEmpty()) {
            logger.info("\n--- unlockTrade ---");
            String pwdMd5 = md5(tradePwd);
            var unlockC2s = TrdUnlockTrade.C2S.newBuilder()
                .setUnlock(true)
                .setPwdMD5(pwdMd5)
                .build();
            int ret = trd.unlockTrade(TrdUnlockTrade.Request.newBuilder().setC2S(unlockC2s).build());
            logger.info("unlockTrade ret={}", ret);
            sleep(300);
        } else {
            logger.info("FUTU_TRADE_PASSWORD not set — skipping unlock.");
        }

        // Get acc list
        var accC2s = TrdGetAccList.C2S.newBuilder()
            .setNeedGeneralSecAccount(false)
            .build();
        trd.getAccList(TrdGetAccList.Request.newBuilder().setC2S(accC2s).build());
        sleep(300);

        trd.close();
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
    // FTSPI_Trd
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetAccList(com.futu.openapi.FTAPI_Conn client, int retCode,
                                    TrdGetAccList.Response rsp) {
        logger.info("  [Trd] onReply_GetAccList: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getAccListCount();
            for (int i = 0; i < count; i++) {
                var acc = s2c.getAccList(i);
                logger.info("    acc[{}] accId={} env={}", i, acc.getAccID(), acc.getTrdEnv());
                if (i == 0) accId = acc.getAccID();
            }
            if (accId > 0) queryTradingInfo();
        }
    }

    private void queryTradingInfo() {
        var header = TrdCommon.TrdHeader.newBuilder()
            .setTrdEnv(TRD_ENV_SIMULATE)
            .setAccID(accId)
            .setTrdMarket(TRD_MARKET_HK)
            .build();

        // Query max trading quantities for HK.00700 at price 400
        // The response MaxTrdQtys will show both buy/sell sides
        String code = "00700";
        double price = 400.0;
        logger.info("\n=== getMaxTrdQtys: HK.{} @ {:.2f} ===", code, price);
        var c2s = TrdGetMaxTrdQtys.C2S.newBuilder()
            .setHeader(header)
            .setOrderType(ORDER_TYPE_NORMAL)
            .setCode(code)
            .setPrice(price)
            .build();
        int ret = trd.getMaxTrdQtys(TrdGetMaxTrdQtys.Request.newBuilder().setC2S(c2s).build());
        logger.info("getMaxTrdQtys ret={}", ret);
    }

    @Override
    public void onReply_GetMaxTrdQtys(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       TrdGetMaxTrdQtys.Response rsp) {
        logger.info("  [Trd] onReply_GetMaxTrdQtys: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            if (!s2c.hasMaxTrdQtys()) {
                logger.info("    No trading quantities returned");
                return;
            }
            var qtys = s2c.getMaxTrdQtys();
            logger.info("    maxCashBuy={:.0f} maxCashAndMarginBuy={:.0f}",
                qtys.getMaxCashBuy(), qtys.getMaxCashAndMarginBuy());
            logger.info("    maxPositionSell={:.0f} maxSellShort={:.0f} maxBuyBack={:.0f}",
                qtys.getMaxPositionSell(), qtys.getMaxSellShort(), qtys.getMaxBuyBack());
            logger.info("    longRequiredIM={:.2f} shortRequiredIM={:.2f}",
                qtys.getLongRequiredIM(), qtys.getShortRequiredIM());
        } else {
            logger.warn("    getMaxTrdQtys failed retCode={}", retCode);
        }
    }

    private static String md5(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}