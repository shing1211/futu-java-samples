# futu-java-samples

Java examples for [Futu OpenAPI](https://openapi.futunn.com/) — mirrors the
[futu-python-samples](https://github.com/shing1211/futu-python-samples) project,
porting each example to the Java SDK's native callback-driven async model.

**SDK:** `com.futunn.openapi:futu-api:10.5.6508` (Maven)  
**Build:** Java 25 · Maven 3.6+  
**Gateway:** [FutuOpenD](https://openapi.futunn.com/futu-api-doc/quick/opend-base.html) on `:11111`

---

## Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [All Examples](#examples)
- [SDK Patterns](#sdk-patterns)
- [Environment Variables](#environment-variables)
- [Building & Running](#building--running)
- [FutuOpenD Setup](#futuopend-setup)
- [License](#license)

---

## Overview

Each file in `src/main/java/com/futu/sdk/examples/` is a self-contained demo.
They follow the numbering of futu-python-samples and can be run independently via
Maven exec.

```
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example01_MarketSnapshot"
```

### Key difference from Python

The Java SDK uses **SPI callbacks** — all API calls return `int` immediately and the
response arrives asynchronously via an interface method (e.g. `onReply_GetBasicQot`).
There is no `FutOpendContext` wrapper; you implement `FTSPI_Qot` / `FTSPI_Conn` /
`FTSPI_Trd` and register instances via `setQotSpi()` / `setConnSpi()` / `setTrdSpi()`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Config.java                                             │
│  .env loading · RSA PKCS#1 key conversion · host parsing │
└────────────────┬────────────────────────────────────────┘
                 │ provides host, RSA key, connection config
                 ▼
┌─────────────────────────────────────────────────────────┐
│  FTAPI.init()                                            │
│  Global SDK init (call once at startup)                  │
└────────┬────────────────────────────┬────────────────────┘
         │ creates                   │ creates
         ▼                           ▼
┌────────────────────┐     ┌────────────────────┐
│  FTAPI_Conn_Qot    │     │  FTAPI_Conn_Trd    │
│  Quote connection  │     │  Trading connection│
│  getBasicQot()     │     │  placeOrder()      │
│  sub()             │     │  getAccList()      │
│  getKL()           │     │  unlockTrade()     │
└────────┬───────────┘     └──────────┬─────────┘
         │ setQotSpi() / setConnSpi() │ setTrdSpi() / setConnSpi()
         ▼                            ▼
┌──────────────────────────────────────────────────────┐
│  Your example implements:                              │
│    FTSPI_Qot   — onReply_GetBasicQot, onPush_*        │
│    FTSPI_Conn  — onInitConnect, onDisconnect          │
│    FTSPI_Trd   — onReply_PlaceOrder (trading only)     │
└──────────────────────────────────────────────────────┘
         │ initConnect(host, port, rsa)
         ▼
┌────────────────────┐
│  FutuOpenD         │
│  :11111 (TCP)      │
└────────────────────┘
```

### Core SPI interfaces (most-connected nodes in the codebase)

| Interface | Role | Key callbacks |
|-----------|------|---------------|
| `FTSPI_Conn` | Connection lifecycle | `onInitConnect`, `onDisconnect` |
| `FTSPI_Qot` | Quote/market data | `onReply_*` (one per API), `onPush_*` (subscription updates) |
| `FTSPI_Trd` | Trading operations | `onReply_*`, `onPush_UpdateOrder`, `onPush_UpdateOrderFill` |

> **Why `FTSPI_Conn` appears 58 times in the graph:** Every example registers
> `FTSPI_Conn` to receive connection events — it is the shared backbone of all
> 28 examples. The same applies to `FTSPI_Qot` (51 connections) for quote examples.

### Two trading environments

| Env | ID | Unlock required |
|-----|----|----------------|
| SIMULATE | 1 | No |
| REAL | 2 | Yes — `unlockTrade(MD5(password))` |

---

## Quick Start

### 1. Start FutuOpenD

```bash
# Download from https://openapi.futunn.com/
# Run on 127.0.0.1:11111 (default)
./futuopend
```

### 2. Clone and build

```bash
git clone https://github.com/shing1211/futu-java-samples
cd futu-java-samples
mvn compile
```

### 3. Configure (optional)

```bash
cp .env.example .env
# Edit .env with your OpenD host/port/RSA settings
```

### 4. Run an example

```bash
# Example 00 — HA gateway selection + connection
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"

# Example 01 — market snapshot
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example01_MarketSnapshot"
```

---

## Examples

### Quote / Market Data

| # | Class | Description |
|---|-------|-------------|
| 00 | `Example00_ConnectHA` | HA gateway selection — parallel TCP probe, fastest host wins, auto-fallback |
| 01 | `Example01_MarketSnapshot` | `getBasicQot` for HK/US/SH/SZ securities |
| 02 | `Example02_QuotePush` | Subscribe to quotes, order book, tickers, brokers — receive real-time pushes |
| 03 | `Example03_StockFilter` | `stockFilter` screener with `BaseFilter` + `FinancialFilter` |
| 07 | `Example07_Kline` | `getKL` (current bars) + `requestHistoryKL` (historical with pagination) |
| 08 | `Example08_RtTicker` | `getTicker` (tick-by-tick trades) + `getRT` (intraday minute OHLCV) |
| 09 | `Example09_BrokerQueue` | `getBroker` — broker buy/sell queue (bid/ask wall) |
| 10 | `Example10_OrderBook` | `getOrderBook` — N-level bid/ask depth |
| 12 | `Example12_TradingDays` | `requestTradeDate` — market trading days |
| 13 | `Example13_Plate` | `getPlateSet` / `getPlateSecurity` — sector/plate membership |
| 14 | `Example14_CurKline` | `getKL` with `onPush_UpdateKL` live K-line push |
| 15 | `Example15_SubList` | `getSubInfo` — subscription info; `sub` / `unsub` |
| 16 | `Example16_StockQuote` | `getBasicQot` — full quote snapshot |
| 17 | `Example17_OwnerPlate` | `getOwnerPlate` / `getReference` — owner and reference data |
| 18 | `Example18_ReferenceStock` | `getReference` — reference stocks |
| 19 | `Example19_CapitalFlow` | `getCapitalFlow` / `getCapitalDistribution` — money flow analysis |
| 20 | `Example20_IpoList` | `getIpoList` — upcoming and historical IPOs |
| 21 | `Example21_FutureInfo` | `getFutureInfo` — futures contract info |
| 22 | `Example22_MarketState` | `getMarketState` — market open/close/halt state |
| 23 | `Example23_PriceReminder` | `setPriceReminder` / `getPriceReminder` — price alert management |
| 24 | `Example24_UserSecurity` | `getUserSecurity` / `modifyUserSecurity` — user security lists |
| 25 | `Example25_OptionChain` | `getOptionExpirationDate` + `getOptionChain` — index options |
| 28 | `Example28_Warrant` | `getWarrant` — warrant data |
| 31 | `Example31_Misc` | `getHoldingChangeList`, `requestRehab`, user security group ops |

### Trading (requires unlock in REAL mode)

| # | Class | Description |
|---|-------|-------------|
| 05 | `Example05_QuoteTrade` | Dual `FTAPI_Conn_Qot` + `FTAPI_Conn_Trd` — quote + buy order in SIM |
| 30 | `Example30_UserInfo` | `getAccList` — list accounts; `subAccPush` — account update push |
| 32 | `Example32_OrderQuery` | `unlockTrade` → `getOrderList` / `getOrderFillList` — order & fill history |
| 33 | `Example33_TradingInfo` | `getMaxTrdQtys` — max buy/sell quantities, margin requirements |

---

## SDK Patterns

### Pattern 1 — Connect and wait for callback

```java
FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
qot.setClientInfo("javaclient", 1);
qot.setConnSpi(this);     // "this" implements FTSPI_Conn
qot.setQotSpi(this);      // "this" implements FTSPI_Qot

if (Config.FUTU_RSA_ENABLED) {
    qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT); // PKCS#1 format
}

boolean ok = qot.initConnect(host, port, rsaEnabled);
if (!ok) { /* handle error */ }

// Wait for onInitConnect callback (poll connected flag, 8s timeout)
int waited = 0;
while (!connected && waited < 8000) {
    Thread.sleep(50);
    waited += 50;
}
```

### Pattern 2 — Request → callback (fire and forget)

```java
// Request fires immediately, response arrives via callback
int ret = qot.getBasicQot(request);

// Callback (on the same thread, or a SDK thread):
@Override
public void onReply_GetBasicQot(FTAPI_Conn client, int retCode,
                                QotGetBasicQot.Response rsp) {
    if (retCode == 0 && rsp.hasS2C()) {
        for (var basic : rsp.getS2C().getBasicQotListList()) {
            logger.info("{} @ {}", basic.getSecurity().getCode(),
                        basic.getCurPrice());
        }
    }
}
```

### Pattern 3 — Subscribe for real-time pushes

```java
QotSub.C2S c2s = QotSub.C2S.newBuilder()
    .addSecurityList(sec)
    .addSubTypeList(QotCommon.SubType.SubType_Quote.getNumber())
    .setIsSubOrUnSub(true)        // subscribe (false = unsubscribe)
    .setIsRegOrUnRegPush(true)   // register push handlers
    .setIsFirstPush(true)        // send current snapshot immediately
    .build();
qot.sub(QotSub.Request.newBuilder().setC2S(c2s).build());
// Pushes arrive as onPush_UpdateBasicQuote, onPush_UpdateOrderBook, etc.
```

### Pattern 4 — Trading header (required on every trd request)

```java
TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
    .setTrdEnv(TRD_ENV_SIMULATE)  // 1=SIM, 2=REAL
    .setAccID(accId)
    .setTrdMarket(TRD_MARKET_HK)  // 1=HK, 2=US, 4=SH, 5=SZ
    .build();

TrdPlaceOrder.C2S orderC2s = TrdPlaceOrder.C2S.newBuilder()
    .setHeader(header)
    .setTrdSide(TRD_SIDE_BUY)     // 1=BUY, 2=SELL
    .setOrderType(0)
    .setCode("00700")
    .setPrice(400.0)
    .setQty(qty)
    .setSecMarket(MARKET_HK)
    .build();
trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
```

### Pattern 5 — RSA key (PKCS#8 → PKCS#1 auto-conversion)

```java
// Config.RSA_KEY_CONTENT is pre-loaded and auto-converted:
// - Detects PKCS#8 (-----BEGIN PRIVATE KEY-----)
// - Converts to PKCS#1 (-----BEGIN RSA PRIVATE KEY-----) via OpenSSL
// - Stored as Config.RSA_KEY_CONTENT (empty string if RSA disabled)
if (host.isRSA && !Config.RSA_KEY_CONTENT.isEmpty()) {
    qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
}
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FUTU_OPEND_HOST` | `127.0.0.1` | OpenD host (single mode) |
| `FUTU_OPEND_PORT` | `11111` | OpenD port |
| `FUTU_OPEND_HOSTS` | _(empty)_ | HA mode — comma-separated `host:port:isRSA` tuples |
| `FUTU_RSA_ENABLED` | `false` | Enable RSA encryption |
| `FUTU_RSA_KEY_PATH` | `/etc/futu/keys/private_key.pem` | Path to RSA private key |
| `FUTU_SECURITY_FIRM` | `FUTU HK` | Security firm identifier |
| `FUTU_TRADE_PASSWORD` | _(empty)_ | Trading password (MD5-hashed before sending) |
| `FUTU_ENV` | `dev` | Environment label |

### HA mode example

```env
FUTU_OPEND_HOSTS=172.18.208.88:11111:true,172.20.208.88:11111:true
```

`Example00_ConnectHA` probes all hosts in parallel (3s timeout), picks the
fastest TCP response, and connects to it.

---

## Building & Running

```bash
# Compile
mvn compile

# Run a specific example
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example07_Kline"

# Build a fat JAR (all examples packaged)
mvn package

# Run tests
mvn test

# Generate Javadoc
mvn javadoc:javadoc
```

### Maven dependency

```xml
<dependency>
    <groupId>com.futunn.openapi</groupId>
    <artifactId>futu-api</artifactId>
    <version>10.5.6508</version>
</dependency>
```

---

## FutuOpenD Setup

1. Download FutuOpenD from [openapi.futunn.com](https://openapi.futunn.com/)
2. Run on `127.0.0.1:11111` (default)
3. Enable API permission in OpenD settings
4. For trading: configure your account and unlock with password

```bash
# Default host/port
./futuopend --host 127.0.0.1 --port 11111
```

---

## Further Reading

- [ARCHITECTURE.md](ARCHITECTURE.md) — full architecture documentation with execution
  flow diagrams and mermaid component diagram
- [FUTU_JAVA_SDK_DOC.md](FUTU_JAVA_SDK_DOC.md) — SDK documentation with protobuf
  field IDs and API reference
- `graphify-out/graph.html` — interactive knowledge graph of the codebase
  (open in browser)
- [futu-python-samples](https://github.com/shing1211/futu-python-samples) — the
  Python reference implementation

---

## License

MIT — see [LICENSE](LICENSE)