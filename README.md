# futu-java-samples

Java examples for [Futu OpenAPI](https://openapi.futunn.com/) — mirrors the
[futu-python-samples](https://github.com/shing1211/futu-python-samples) project,
porting each example to the Java SDK's native callback-driven async model.

**SDK:** `com.futunn.openapi:futu-api:10.5.6508` (Maven)
**Build:** Java 17+ · Maven 3.6+
**Gateway:** [FutuOpenD](https://openapi.futunn.com/futu-api-doc/quick/opend-base.html) on `:11111`
**Examples:** 56 (00–57, missing 29 and 54)

---

## Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [All Examples](#examples)
- [SDK Patterns & Conventions](#sdk-patterns--conventions)
- [Environment Variables](#environment-variables)
- [Building & Running](#building--running)
- [FutuOpenD Setup](#futuopend-setup)
- [Troubleshooting](#troubleshooting)
- [Further Reading](#further-reading)
- [License](#license)

---

## Overview

Each file in `src/main/java/com/futu/sdk/examples/` is a self-contained demo.
They follow the numbering of futu-python-samples and can be run independently via
Maven exec.

```bash
mvn compile exec:java -Dexec.mainClass=com.futu.sdk.examples.Example00_ConnectHA
```

### Key differences from Python

| Aspect | Python SDK | Java SDK |
|--------|-----------|----------|
| API style | Synchronous (blocking) | **SPI callbacks** (async, fire-and-forget) |
| Context wrapper | `OpenQuoteContext` / `OpenSecTradeContext` | `FTAPI_Conn_Qot` / `FTAPI_Conn_Trd` directly |
| Call model | `ret, data = ctx.get_global_state()` | `qot.getGlobalState(req)` → `onReply_GetGlobalState` callback |
| Connection | `ctx = OpenQuoteContext(host, port)` | `qot.initConnect(host, port, isRSA)` |
| RSA setup | `SysConfig.set_init_rsa_file(path)` | `qot.setRSAPrivateKey(pkcs1Content)` |
| SSL/RSA format | Detects PKCS#1/PKCS#8 automatically | Requires **PKCS#1** (`-----BEGIN RSA PRIVATE KEY-----`); `Config.java` auto-converts PKCS#8 → PKCS#1 via `openssl rsa -traditional` |

### Important — retCode convention

The Futu Java SDK uses `retCode` differently depending on context:

| Context | `retCode=0` | `retCode≠0` |
|---------|-------------|-------------|
| **Sync return** (immediate) | Success | Error |
| **Async callback** (e.g. `onReply_GetGlobalState`) | **Not used for success** | Server logged in — data is in `s2C` |

**In async callbacks**, `retCode=2` means the server successfully processed
the request and returned login data (`qotLogined=true`, `trdLogined=true`).
This is **not an error** — it is the expected success path for `getGlobalState`
and all async callbacks in this SDK version.

Always check `rsp.hasS2C()` AND `rsp.getS2C().getQotLogined()` in async callbacks
rather than relying solely on `retCode`.

---

## Quick Start

### 1. Configure `.env`

```bash
cp .env.example .env
```

Edit `.env` with your OpenD host and RSA key path:

```env
FUTU_OPEND_HOSTS=172.18.208.88:11111:true
FUTU_RSA_ENABLED=true
FUTU_RSA_KEY_PATH=/etc/futu/keys/private_key.pem
```

### 2. Start FutuOpenD

```bash
# Download from https://openapi.futunn.com/
# Run on your host :11111
./futuopend
```

### 3. Build and run

```bash
mvn compile

# HA gateway demo + getGlobalState
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"

# Market snapshot (live quotes for HK.00700, US.AAPL)
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example01_MarketSnapshot"
```

---

## Examples

### Quote / Market Data

| # | Class | Description |
|---|-------|-------------|
| 00 | `Example00_ConnectHA` | HA gateway selection — parallel TCP probe, fastest host wins, `getGlobalState` with RSA |
| 01 | `Example01_MarketSnapshot` | `getSecuritySnapshot` for HK/US securities (no subscription needed) |
| 02 | `Example02_QuotePush` | `sub()` + real-time pushes via `onPush_UpdateBasicQuote`, `onPush_UpdateOrderBook` |
| 03 | `Example03_StockFilter` | `stockFilter` screener with `BaseFilter` + `FinancialFilter` |
| 04 | `Example04_MacdStrategy` | MACD-based trend strategy using `getKL` + `onPush_UpdateKL` push |
| 07 | `Example07_Kline` | `getKL` (current bars) + `requestHistoryKL` (historical with pagination) |
| 08 | `Example08_RtTicker` | `getTicker` (tick-by-tick trades) + `getRT` (intraday OHLCV) |
| 09 | `Example09_BrokerQueue` | `getBroker` — broker buy/sell queue (bid/ask wall) |
| 10 | `Example10_OrderBook` | `getOrderBook` — N-level bid/ask depth |
| 12 | `Example12_TradingDays` | `requestTradeDate` — market trading days |
| 13 | `Example13_Plate` | `getPlateSet` / `getPlateSecurity` — sector/plate membership |
| 14 | `Example14_CurKline` | `getKL` with `onPush_UpdateKL` live K-line push |
| 15 | `Example15_SubList` | `getSubInfo` — subscription info; `sub` / `unsub` |
| 16 | `Example16_StockQuote` | `getBasicQot` — full quote snapshot (**requires prior subscription**) |
| 17 | `Example17_OwnerPlate` | `getOwnerPlate` / `getReference` — owner and reference data |
| 18 | `Example18_ReferenceStock` | `getReference` — reference stocks |
| 19 | `Example19_CapitalFlow` | `getCapitalFlow` / `getCapitalDistribution` — money flow analysis |
| 20 | `Example20_IpoList` | `getIpoList` — upcoming and historical IPOs |
| 21 | `Example21_FutureInfo` | `getFutureInfo` — futures contract info |
| 22 | `Example22_MarketState` | `getMarketState` — market open/close/halt state |
| 23 | `Example23_PriceReminder` | `setPriceReminder` / `getPriceReminder` — price alert management |
| 24 | `Example24_UserSecurity` | `getUserSecurity` / `modifyUserSecurity` — user security lists |
| 25 | `Example25_OptionChain` | `getOptionExpirationDate` + `getOptionChain` — index options |
| 26 | `Example26_HistoryKLQuota` | `requestHistoryKL` quota tracking |
| 27 | `Example27_CodeChange` | `getCodeChange` — stock code/name changes |
| 28 | `Example28_Warrant` | `getWarrant` — warrant data |
| 31 | `Example31_Misc` | `getHoldingChangeList`, `requestRehab`, user security group ops |
| 36 | `Example36_StockBasicInfo` | `getSecurityStaticInfo` — static security metadata |
| 41 | `Example41_Rehab` | `requestRehab` — split/dividend adjustment data |
| 42 | `Example42_CapitalDistribution` | `getCapitalDistribution` — granular capital flow |
| 44 | `Example44_MultiMarketSnapshot` | `getSecuritySnapshot` across HK/US/SH/SZ markets |
| 45 | `Example45_StockFilter` | `stockFilter` with extended filter criteria |
| 46 | `Example46_PlateStockFilter` | `getPlateSecurity` + `stockFilter` — plate-based screener |
| 47 | `Example47_WarrantFilter` | `getWarrant` + filter for warrant screener |
| 53 | `Example53_MarketHeat` | `getMarketHeat` — market activity heat map |
| 55 | `Example55_EMA` | EMA indicator calculation from K-line data |
| 56 | `Example56_ETFComposition` | `getETFComponent` — ETF holdings/constituents |
| 57 | `Example57_VWAPBenchmark` | VWAP benchmark analysis for US stocks |

### Trading (requires unlock in REAL mode)

| # | Class | Description |
|---|-------|-------------|
| 05 | `Example05_QuoteTrade` | Dual `FTAPI_Conn_Qot` + `FTAPI_Conn_Trd` — quote + buy order in SIMULATE |
| 06 | `Example06_StockSell` | Sell order placement — position query + sell in SIMULATE |
| 11 | `Example11_AccInfo` | `getAccList` + `getFunds` + `getPositionList` — account overview |
| 30 | `Example30_UserInfo` | `getAccList` — list accounts; `subAccPush` — account update push |
| 32 | `Example32_OrderQuery` | `unlockTrade` → `getOrderList` / `getOrderFillList` — order & fill history |
| 33 | `Example33_TradingInfo` | `getMaxTrdQtys` — max buy/sell quantities, margin requirements |
| 34 | `Example34_CancelAll` | `getOrderList` + `modifyOrder` batch cancel |
| 35 | `Example35_CashFlow` | `getCashFlow` — cash flow statement |
| 37 | `Example37_MarginRatio` | `getMarginRatio` — margin/short selling data |
| 38 | `Example38_OrderFee` | `getOrderFee` — per-order fee breakdown |
| 39 | `Example39_SysNotify` | `subSysNotify` — system notifications push |
| 40 | `Example40_TradePush` | `onPush_UpdateOrder` / `onPush_UpdateFill` — live order/fill push |
| 48 | `Example48_OptionsStrategy` | Options combo strategies (vertical spreads, straddles) |
| 49 | `Example49_AccCashFlow` | `getCashFlow` — per-account cash flow |
| 50 | `Example50_HistoryOrderDeal` | `getOrderList` / `getOrderFillList` historical records |
| 51 | `Example51_AccList` | `getAccList` — list all accounts with securities firm info |
| 52 | `Example52_OptionChainFilter` | `getOptionChain` + `stockFilter` — options screener |
|

---

## SDK Patterns & Conventions

### Pattern 1 — Connect and wait for callback

```java
FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
qot.setClientInfo("javaclient", 1);
qot.setConnSpi(this);     // "this" implements FTSPI_Conn
qot.setQotSpi(this);      // "this" implements FTSPI_Qot

// RSA key must be PKCS#1 format; Config.java auto-converts PKCS#8 → PKCS#1
if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
    qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
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

### Pattern 2 — Request → async callback (fire and forget)

```java
// Request fires immediately; response arrives via callback on a SDK thread
int ret = qot.getSecuritySnapshot(request);
logger.info("getSecuritySnapshot ret={}", ret); // ret is NOT the final result

// Callback:
@Override
public void onReply_GetSecuritySnapshot(FTAPI_Conn client, int retCode,
                                         QotGetSecuritySnapshot.Response rsp) {
    // retCode here is the server-side retType, not a simple success flag
    // Check hasS2C() AND examine the actual data
    if (rsp.hasS2C() && !rsp.getS2C().getSnapshotListList().isEmpty()) {
        for (var snap : rsp.getS2C().getSnapshotListList()) {
            var basic = snap.getBasic();
            logger.info("{} @ {}", basic.getSecurity().getCode(), basic.getCurPrice());
        }
    }
}
```

### Pattern 3 — getGlobalState (the connection health check)

```java
GetGlobalState.C2S c2s = GetGlobalState.C2S.newBuilder().setUserID(0).build();
GetGlobalState.Request req = GetGlobalState.Request.newBuilder().setC2S(c2s).build();

// IMPORTANT: Do NOT use getDefaultInstance() — it creates an uninitialized request
// that will throw UninitializedMessageException at serialization time.

int ret = qot.getGlobalState(req);

@Override
public void onReply_GetGlobalState(FTAPI_Conn client, int retCode,
                                     GetGlobalState.Response rsp) {
    // In this SDK version, retCode=2 + qotLogined=true + trdLogined=true = SUCCESS
    if (rsp.hasS2C() && rsp.getS2C().getQotLogined()) {
        var s2c = rsp.getS2C();
        logger.info("✅ Connected! serverVer={} qotLogined={} trdLogined={}",
            s2c.getServerVer(), s2c.getQotLogined(), s2c.getTrdLogined());
    }
}
```

### Pattern 4 — Subscribe for real-time pushes

```java
QotSub.C2S c2s = QotSub.C2S.newBuilder()
    .addSecurityList(sec)
    .addSubTypeList(QotCommon.SubType.SubType_Quote.getNumber())
    .setIsSubOrUnSub(true)        // true = subscribe, false = unsubscribe
    .setIsRegOrUnRegPush(true)   // register push handlers
    .setIsFirstPush(true)        // send current snapshot immediately
    .build();
qot.sub(QotSub.Request.newBuilder().setC2S(c2s).build());
// Pushes arrive as onPush_UpdateBasicQuote, onPush_UpdateOrderBook, etc.
```

### Pattern 5 — Trading header (required on every trd request)

```java
TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
    .setTrdEnv(TRD_ENV_SIMULATE)   // 1=SIMULATE, 2=REAL
    .setAccID(accId)
    .setTrdMarket(TRD_MARKET_HK)   // 1=HK, 2=US, 4=SH, 5=SZ
    .build();

TrdPlaceOrder.C2S orderC2s = TrdPlaceOrder.C2S.newBuilder()
    .setHeader(header)
    .setTrdSide(TrdCommon.TrdSide.BUY)  // BUY=1, SELL=2
    .setOrderType(0)
    .setCode("00700")
    .setPrice(400.0)
    .setQty(qty)
    .setSecMarket(MARKET_HK)
    .build();
trd.placeOrder(TrdPlaceOrder.Request.newBuilder().setC2S(orderC2s).build());
```

### Pattern 6 — RSA key (PKCS#8 → PKCS#1 auto-conversion)

```java
// Config.java auto-converts PKCS#8 (-----BEGIN PRIVATE KEY-----)
// to PKCS#1 (-----BEGIN RSA PRIVATE KEY-----) via OpenSSL at startup.
// The converted key is stored as Config.RSA_KEY_CONTENT.
if (host.isRSA && !Config.RSA_KEY_CONTENT.isEmpty()) {
    qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
}
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FUTU_OPEND_HOST` | `127.0.0.1` | OpenD host (single mode fallback) |
| `FUTU_OPEND_PORT` | `11111` | OpenD port |
| `FUTU_OPEND_HOSTS` | _(empty)_ | HA mode — comma-separated `host:port:isRSA` tuples |
| `FUTU_RSA_ENABLED` | `false` | Enable RSA encryption (required for remote OpenD) |
| `FUTU_RSA_KEY_PATH` | `/etc/futu/keys/private_key.pem` | Path to RSA private key |
| `FUTU_SECURITY_FIRM` | `FUTU HK` | Security firm identifier |
| `FUTU_TRADE_PASSWORD` | _(empty)_ | Trading password (MD5-hashed before sending) |
| `FUTU_ENV` | `dev` | Environment label |

### HA mode example

```env
FUTU_OPEND_HOSTS=172.18.208.88:11111:true,172.20.208.88:11111:true
```

`Example00_ConnectHA` probes all hosts in parallel (3s TCP timeout), picks the
fastest responding host, and connects to it with the appropriate RSA setting.

---

## Building & Running

```bash
# Compile
mvn compile

# Run a specific example
mvn compile exec:java -Dexec.mainClass=com.futu.sdk.examples.Example07_Kline

# Build a fat JAR (all examples packaged)
mvn package

# Run tests
mvn test

# Generate Javadoc
mvn javadoc:javadoc
```

---

## FutuOpenD Setup

1. Download FutuOpenD from [openapi.futunn.com](https://openapi.futunn.com/)
2. Run on your host `:11111`
3. Enable API permission in OpenD settings
4. For trading: configure your account and unlock with password

```bash
# Default host/port
./futuopend --host 127.0.0.1 --port 11111

# With RSA enabled (for remote connections)
# Configure the RSA public key in OpenD settings first
```

---

## Troubleshooting

### `retCode=2` from `getGlobalState` but connection appears working

This is **expected behavior** in SDK version `10.5.6508`. The server
successfully authenticates and returns `qotLogined=true, trdLogined=true`.
`retCode=2` in async callbacks indicates a successful response with data —
**not an error**. Check `rsp.hasS2C()` and the login flags instead.

### `getBasicQot` returns retCode=3: "please subscribe to Basic data first"

`getBasicQot` requires a prior subscription. Use `getSecuritySnapshot`
instead for one-shot quote retrieval without subscription — it works
immediately on any security with no prior `sub()` needed.

### "Failed to parse protobuf protocol" error

Usually means the SDK version and OpenD version are mismatched.
SDK `10.5.6508` works with OpenD `10.5.6508` and later.
Downgrade the SDK to match your OpenD version in `pom.xml`:

```xml
<futu-api.version>10.2.6208</futu-api.version>
```

### `UninitializedMessageException: Message missing required fields: c2s`

Your protobuf request was built with `getDefaultInstance()` which creates
an uninitialized message. Always build the C2S struct explicitly:

```java
// Wrong — will throw at serialization:
GetGlobalState.Request.getDefaultInstance()

// Correct:
GetGlobalState.C2S.newBuilder().setUserID(0).build()
GetGlobalState.Request.newBuilder().setC2S(c2s).build()
```

---

## Further Reading

- [ARCHITECTURE.md](ARCHITECTURE.md) — full architecture documentation with
  execution flow diagrams and mermaid component diagram
- [FUTU_JAVA_SDK_DOC.md](FUTU_JAVA_SDK_DOC.md) — SDK documentation with
  protobuf field IDs and API reference (imported from Futu OpenAPI docs)
- [futu-python-samples](https://github.com/shing1211/futu-python-samples) — the
  Python reference implementation
- [futuapi4go](https://github.com/shing1211/futuapi4go) — a Go port of the same
  SDK, useful for comparing RSA implementation across languages

---

## License

MIT — see [LICENSE](LICENSE)