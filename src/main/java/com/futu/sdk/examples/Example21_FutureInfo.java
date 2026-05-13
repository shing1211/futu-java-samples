package com.futu.sdk.examples;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetFutureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example 21: Future Contract Info (get_future_info)
 *
 * Demonstrates:
 *   - getFutureInfo: fetch contract specifications for futures
 *   - Futures: HK.HSImain (HSI), US.NQmain (Nasdaq), US.ESmain (S&P)
 *   - FutureInfo fields: name, security, lastTradeTime, owner, contractType,
 *     contractSize, quoteCurrency, minVar, tradeTime list, timeZone
 *
 * Mirrors: examples/21_future_info/main.py from futu-python-samples
 */
public class Example21_FutureInfo implements FTSPI_Qot, FTSPI_Conn {

    private static final Logger logger = LoggerFactory.getLogger(Example21_FutureInfo.class);

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;

    // Market: HK=1, US=2
    private static final int MARKET_HK = 1;
    private static final int MARKET_US = 2;

    public static void main(String[] args) {
        logger.info("=== Future Contract Info Demo ===");
        FTAPI.init();
        Example21_FutureInfo demo = new Example21_FutureInfo();
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

        var futures = new QotCommon.Security[] {
            QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NQmain").build(),
            QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("ESmain").build(),
        };

        logger.info("\n=== getFutureInfo: {} futures ===", futures.length);
        var c2s = QotGetFutureInfo.C2S.newBuilder()
            .addSecurityList(QotCommon.Security.newBuilder().setMarket(MARKET_HK).setCode("HSImain"))
            .addSecurityList(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("NQmain"))
            .addSecurityList(QotCommon.Security.newBuilder().setMarket(MARKET_US).setCode("ESmain"))
            .build();
        int ret = qot.getFutureInfo(QotGetFutureInfo.Request.newBuilder().setC2S(c2s).build());
        logger.info("getFutureInfo ret={}", ret);

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
    public void onReply_GetFutureInfo(com.futu.openapi.FTAPI_Conn client, int retCode,
                                       QotGetFutureInfo.Response rsp) {
        logger.info("  [Qot] onReply_GetFutureInfo: retCode={}", retCode);
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            int count = s2c.getFutureInfoListCount();
            logger.info("    Retrieved {} futures:", count);
            for (int i = 0; i < count; i++) {
                var f = s2c.getFutureInfoList(i);
                var sec = f.getSecurity();
                logger.info("\n    === {} ({}) ===", sec.getCode(), f.getName());
                logger.info("      owner={} exchange={} contractType={}",
                    f.hasOwner() ? f.getOwner().getCode() : "n/a",
                    f.getExchange(), f.getContractType());
                logger.info("      contractSize={} {} quoteCurrency={} minVar={} {}",
                    f.getContractSize(), f.getContractSizeUnit(),
                    f.getQuoteCurrency(), f.getMinVar(), f.getMinVarUnit());
                logger.info("      quoteUnit={} lastTradeTime={} timeZone={}",
                    f.getQuoteUnit(), f.getLastTradeTime(), f.getTimeZone());
                logger.info("      ownerOther={}", f.getOwnerOther());
                // Trading times
                int ttCount = f.getTradeTimeCount();
                for (int j = 0; j < Math.min(ttCount, 3); j++) {
                    var tt = f.getTradeTime(j);
                    logger.info("      tradeTime[{}] begin={} end={}",
                        j, tt.getBegin(), tt.getEnd());
                }
            }
        } else {
            logger.warn("    getFutureInfo failed retCode={}", retCode);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}