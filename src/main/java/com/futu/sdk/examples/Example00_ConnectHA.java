package com.futu.sdk.examples;

import com.futu.openapi.ConnStatus;
import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.GetGlobalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * HA Gateway Selection Demo (standalone).
 *
 * TCP probes all configured OpenD hosts in parallel, connects to the fastest.
 * Per-host RSA config with auto-fallback. Demonstrates the full HA algorithm.
 *
 * Configuration (environment variables override hardcoded defaults):
 *   FUTU_OPEND_HOSTS - comma-separated host:port:is_rsa entries
 *   FUTU_ADDR        - single host fallback
 *   FUTU_RSA_KEY     - path to RSA private key
 *   FUTU_TCP_TIMEOUT - TCP probe timeout in seconds
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"
 */
public class Example00_ConnectHA {

    private static final Logger logger = LoggerFactory.getLogger(Example00_ConnectHA.class);

    private static final String FUTU_OPEND_HOSTS = Config.FUTU_RSA_ENABLED ? "" : "";
    private static final String FUTU_ADDR = Config.FUTU_OPEND_HOST + ":" + Config.FUTU_OPEND_PORT;
    private static final int TCP_TIMEOUT_SECONDS = 3;

    public static void main(String[] args) {
        logger.info("=== HA Gateway Selection Demo ===");
        logger.info("OpenD: {}:{}", Config.FUTU_OPEND_HOST, Config.FUTU_OPEND_PORT);
        logger.info("RSA: {} (key={})", Config.FUTU_RSA_ENABLED, Config.FUTU_RSA_KEY_PATH);

        List<HostEntry> hosts = parseHosts();
        logger.info("TCP probing {} hosts (timeout={}s)...", hosts.size(), TCP_TIMEOUT_SECONDS);
        for (HostEntry h : hosts) {
            logger.info("  host={}:{} isRSA={}", h.host, h.port, h.isRSA);
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
        long fastestTcpMs = tcpResults.get(fastest);
        logger.info("\nFastest: {}:{} (TCP {}ms)", fastest.host, fastest.port, fastestTcpMs);
        logger.info("Attempting connection...");

        tryConnect(fastest);

        logger.info("\nDone. Run other examples to use the full SDK.");
    }

    // -------------------------------------------------------------------------
    // Host parsing
    // -------------------------------------------------------------------------

    private static List<HostEntry> parseHosts() {
        List<HostEntry> result = new ArrayList<>();
        String hostsEnv = System.getenv("FUTU_OPEND_HOSTS");
        if (hostsEnv != null && !hostsEnv.trim().isEmpty()) {
            for (String entry : hostsEnv.split(",")) {
                String[] parts = entry.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 11111;
                boolean isRSA = parts.length > 2 ? "true".equalsIgnoreCase(parts[2]) : true;
                result.add(new HostEntry(host, port, isRSA));
            }
        } else {
            // Fallback: single host
            String[] parts = FUTU_ADDR.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 11111;
            result.add(new HostEntry(host, port, Config.FUTU_RSA_ENABLED));
        }
        return result;
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
                logger.info("  TCP {} {}:{} -> {}", rsaTag, host.host, host.port, status);
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(TCP_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
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
    // Connection attempt
    // -------------------------------------------------------------------------

    private static void tryConnect(HostEntry host) {
        FTAPI_Conn_Qot ctx = new FTAPI_Conn_Qot();

        // Set RSA private key if enabled
        if (host.isRSA) {
            ctx.setRSAPrivateKey(Config.FUTU_RSA_KEY_PATH);
        }

        // Connect: initConnect(host, port, isEncrypt)
        boolean ok = ctx.initConnect(host.host, host.port, host.isRSA);
        if (!ok) {
            // Try without RSA if primary failed
            if (host.isRSA) {
                logger.info("  Primary (RSA) failed, trying without RSA...");
                ok = ctx.initConnect(host.host, host.port, false);
            }
            if (!ok) {
                logger.error("All connection attempts failed!");
                System.exit(1);
            }
        }

        // Wait for connection to be ready
        int waitCount = 0;
        while (ctx.getConnStatus() == ConnStatus.START ||
               ctx.getConnStatus() == ConnStatus.CONNECTING) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            waitCount++;
            if (waitCount > 300) {
                logger.error("Connection timeout!");
                System.exit(1);
            }
        }

        if (ctx.getConnStatus() != ConnStatus.CONNECTED &&
            ctx.getConnStatus() != ConnStatus.READY) {
            logger.error("Connection failed, status={}", ctx.getConnStatus());
            System.exit(1);
        }

        // Get global state via callback SPI
        // The Java SDK uses async callback pattern - for demo, just show connected
        logger.info("✅ Connected to {}:{}", host.host, host.port);
        logger.info("   Status: {}", ctx.getConnStatus());
        logger.info("   (Java SDK uses async callbacks for API responses)");
        logger.info("   Run with a FTSPI_Qot implementation to receive API responses.");

        ctx.close();
        logger.info("Connection closed.");
    }

    // -------------------------------------------------------------------------
    // Host entry
    // -------------------------------------------------------------------------

    private record HostEntry(String host, int port, boolean isRSA) {}
}