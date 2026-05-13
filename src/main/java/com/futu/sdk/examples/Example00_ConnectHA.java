package com.futu.sdk.examples;

import com.futu.openapi.*;
import com.futu.openapi.pb.GetGlobalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * HA Gateway Selection Demo.
 *
 * TCP probes all configured OpenD hosts in parallel, connects to the fastest.
 * Per-host RSA config with auto-fallback.
 *
 * Hosts from Config.getHosts() (reads .env via dotenv-java).
 * Format: "host:port:is_rsa" entries, e.g. "172.18.208.88:11111:true,172.20.208.88:11111:true"
 *
 * RSA key: Auto-converts PKCS#8 (-----BEGIN PRIVATE KEY-----) to PKCS#1 (-----BEGIN RSA PRIVATE KEY-----)
 *          as required by the Futu Java SDK.
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"
 */
public class Example00_ConnectHA implements FTSPI_Conn, FTSPI_Qot {

    private static final Logger logger = LoggerFactory.getLogger(Example00_ConnectHA.class);

    private static final int TCP_TIMEOUT_SECONDS = 3;

    private final FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    private volatile boolean connected = false;
    private volatile int globalStateRetCode = -1;

    public static void main(String[] args) {
        logger.info("=== HA Gateway Selection Demo ===");
        logger.info("FUTU_OPEND_HOSTS from .env: {}", String.join(",", Config.getHosts()));
        logger.info("RSA enabled={} key={}", Config.FUTU_RSA_ENABLED, Config.FUTU_RSA_KEY_PATH);

        FTAPI.init();
        Example00_ConnectHA demo = new Example00_ConnectHA();
        demo.run();
    }

    public void run() {
        String[] hostEntries = Config.getHosts();
        List<HostEntry> hosts = new ArrayList<>();
        for (String entry : hostEntries) {
            hosts.add(HostEntry.parse(entry));
        }

        logger.info("TCP probing {} hosts (timeout={}s)...", hosts.size(), TCP_TIMEOUT_SECONDS);
        for (HostEntry h : hosts) {
            logger.info("  probe {}:{} isRSA={}", h.host, h.port, h.isRSA);
        }

        // Parallel TCP probe
        Map<HostEntry, Long> tcpResults = tcpProbe(hosts);

        List<HostEntry> reachable = tcpResults.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Comparator.comparingLong(e -> e.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (reachable.isEmpty()) {
            logger.error("No reachable gateways!");
            System.exit(1);
        }

        HostEntry fastest = reachable.get(0);
        logger.info("\nFastest: {}:{} (TCP {}ms)", fastest.host, fastest.port, tcpResults.get(fastest));

        tryConnect(fastest);
    }

    // -------------------------------------------------------------------------
    // TCP probe
    // -------------------------------------------------------------------------

    private static Map<HostEntry, Long> tcpProbe(List<HostEntry> hosts) {
        Map<HostEntry, Long> results = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(hosts.size());

        for (HostEntry host : hosts) {
            executor.submit(() -> {
                long start = System.currentTimeMillis();
                Long latency = tcpConnect(host.host, host.port);
                results.put(host, latency);
                String status = latency != null ? latency + "ms" : "unreachable";
                String rsaTag = host.isRSA ? "[RSA]" : "[noRSA]";
                logger.info("  {} {}:{} -> {}", rsaTag, host.host, host.port, status);
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(TCP_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        return results;
    }

    private static Long tcpConnect(String host, int port) {
        try (SocketChannel ch = SocketChannel.open()) {
            ch.configureBlocking(true);
            long t0 = System.currentTimeMillis();
            ch.connect(new InetSocketAddress(host, port));
            return System.currentTimeMillis() - t0;
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Connection + API calls
    // -------------------------------------------------------------------------

    private void tryConnect(HostEntry host) {
        qot.setClientInfo("javaclient", 1);
        qot.setConnSpi(this);
        qot.setQotSpi(this);

        // RSA key is pre-loaded by Config.java (PKCS#1 format, ready for SDK)
        if (host.isRSA && !Config.RSA_KEY_CONTENT.isEmpty()) {
            qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
            logger.info("RSA key set");
        }

        logger.info("Connecting to {}:{} (RSA={})...", host.host, host.port, host.isRSA);
        boolean ok = qot.initConnect(host.host, host.port, host.isRSA);
        if (!ok) {
            logger.error("initConnect returned false!");
            System.exit(1);
        }

        int waited = 0;
        while (!connected && waited < 8000) {
            sleep(50);
            waited += 50;
        }

        if (!connected) {
            logger.error("Connection timed out. Status={}", qot.getConnStatus());
            System.exit(1);
        }

        logger.info("✅ Connected! connID={} status={}", qot.getConnectID(), qot.getConnStatus());

        // Get global state
        logger.info("\n--- getGlobalState ---");
        int ret = qot.getGlobalState(GetGlobalState.Request.getDefaultInstance());
        logger.info("getGlobalState ret={}", ret);

        int waited2 = 0;
        while (globalStateRetCode == -1 && waited2 < 5000) {
            sleep(30);
            waited2 += 30;
        }

        if (globalStateRetCode == 0) {
            logger.info("✅ getGlobalState success");
        } else {
            logger.warn("⚠️ getGlobalState retCode={} (check qot/trd login)", globalStateRetCode);
        }

        logger.info("\nKeeping connection alive for 3s...");
        sleep(3000);

        qot.close();
        logger.info("Connection closed. Done.");
    }

    // -------------------------------------------------------------------------
    // FTSPI_Conn
    // -------------------------------------------------------------------------

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        logger.info("  [Conn] onInitConnect: errCode={} desc='{}'", errCode, desc);
        if (errCode == 0) connected = true;
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        logger.info("  [Conn] onDisconnect: errCode={}", errCode);
        connected = false;
    }

    // -------------------------------------------------------------------------
    // FTSPI_Qot
    // -------------------------------------------------------------------------

    @Override
    public void onReply_GetGlobalState(FTAPI_Conn client, int retCode,
                                        GetGlobalState.Response rsp) {
        logger.info("  [Qot] onReply_GetGlobalState: retCode={}", retCode);
        globalStateRetCode = retCode;
        if (retCode == 0 && rsp.hasS2C()) {
            var s2c = rsp.getS2C();
            logger.info("    serverVer={} qotLogined={} trdLogined={} time={}",
                s2c.getServerVer(), s2c.getQotLogined(), s2c.getTrdLogined(), s2c.getTime());
        }
    }

    // -------------------------------------------------------------------------
    // Host entry
    // -------------------------------------------------------------------------

    private record HostEntry(String host, int port, boolean isRSA) {
        static HostEntry parse(String entry) {
            String[] p = entry.trim().split(":");
            String h = p[0].trim();
            int port = p.length > 1 ? Integer.parseInt(p[1].trim()) : 11111;
            boolean rsa = p.length <= 2 || "true".equalsIgnoreCase(p[2].trim());
            return new HostEntry(h, port, rsa);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
