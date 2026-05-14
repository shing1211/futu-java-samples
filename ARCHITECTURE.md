# Architecture Documentation

## Overview

`futu-java-samples` is a Java port of the [futu-python-samples](https://github.com/shing1211/futu-python-samples) project, demonstrating the Futu OpenAPI Java SDK (`com.futunn.openapi:futu-api:10.5.6508`). Each example is a standalone Java file runnable via Maven.

The SDK uses a **callback-driven async model** — all API calls return immediately, and responses arrive via SPI (Service Provider Interface) callbacks. This differs significantly from the synchronous Python SDK and requires careful handling of the async flow.

## Key discoveries from debugging

### retCode convention (SDK `10.5.6508`)

The SDK uses `retCode` differently across sync vs async contexts:

| Context | `retCode=0` | `retCode=2` | Action |
|---------|-------------|-------------|--------|
| Sync return (`qot.getGlobalState(req)`) | Success | Error | Check ret value |
| Async callback (`onReply_GetGlobalState`) | Not used for success | Server returned data in s2C | Check `rsp.getS2C().getQotLogined()` |

**In async callbacks**, `retCode=2` with `qotLogined=true trdLogined=true` is the
**expected success path** for `getGlobalState`. Never treat `retCode=2` as an
error without also checking `hasS2C()` and the login flags.

### Protobuf request construction

`GetGlobalState.Request.getDefaultInstance()` creates an **uninitialized message**
that throws `UninitializedMessageException` at serialization time. Always build
the C2S struct explicitly:

```java
// Wrong:
qot.getGlobalState(GetGlobalState.Request.getDefaultInstance())
// → UninitializedMessageException: Message missing required fields: c2s

// Correct:
GetGlobalState.C2S c2s = GetGlobalState.C2S.newBuilder().setUserID(0).build();
GetGlobalState.Request req = GetGlobalState.Request.newBuilder().setC2S(c2s).build();
qot.getGlobalState(req);
```

### `getBasicQot` requires subscription

`getBasicQot` returns retCode=3 with message "Before calling the Get Real-time
Quotes interface, please subscribe to Basic data first." Use `getSecuritySnapshot`
instead for one-shot quote retrieval without any prior subscription.

### RSA key format

The Java SDK requires PKCS#1 format (`-----BEGIN RSA PRIVATE KEY-----`).
`Config.java` auto-converts PKCS#8 (`-----BEGIN PRIVATE KEY-----`) to PKCS#1
via `openssl rsa -traditional` at startup. The converted key content is stored
as `Config.RSA_KEY_CONTENT`.

---

## Functional Areas

### 1. Configuration (`Config.java`)
Loads environment variables from `.env` using dotenv-java. Supports:
- **Single host mode**: `FUTU_OPEND_HOST` / `FUTU_OPEND_PORT`
- **HA mode**: `FUTU_OPEND_HOSTS` — comma-separated `host:port:isRSA` tuples
- **RSA key loading**: Auto-converts PKCS#8 → PKCS#1 via OpenSSL at startup

### 2. Connection Management (`Example00_ConnectHA`)
The foundation for all examples. Two modes:
- **Single connect**: calls `initConnect()` directly
- **HA mode**: parallel TCP probe across all hosts → pick fastest → connect
Flow: `tcpProbe()` → `HostEntry.parse()` → `initConnect()` → `onInitConnect` callback

### 3. Quote/Market Data (`FTAPI_Conn_Qot`)
All quote-related examples use `FTAPI_Conn_Qot`. Two API flavors:
- **Request/Reply**: `getSecuritySnapshot`, `getKL`, `getOrderBook`, `getTicker`, `getRT`, `stockFilter`, `getOptionChain`
- **Subscribe/Push**: `sub()` to register, then push callbacks fire continuously

Note: `getBasicQot` requires prior subscription; use `getSecuritySnapshot` instead for no-subscription one-shot quotes.

### 4. Trading (`FTAPI_Conn_Trd`)
Separate connection for trading operations. Requires:
- Trading context unlock (`unlockTrade` with MD5(password))
- `TrdHeader` built from accId + trdEnv + trdMarket on every request

### 5. Advanced Quote Data
- **Stock Filter** (`Example03_StockFilter`): `stockFilter()` with `BaseFilter` + `FinancialFilter`
- **Option Chain** (`Example25_OptionChain`): `getOptionExpirationDate()` + `getOptionChain()`
- **K-Line** (`Example07_Kline`): `getKL()` (current bars) + `requestHistoryKL()` (historical, with pagination via `nextReqKey`)

---

## Key Execution Flows

### Flow 1: HA Gateway Connection
```
main() → FTAPI.init()
       → run(): getHosts() [parses hosts from Config]
       → run(): tcpProbe() [parallel TCP connect, timeout 3s]
         → tcpConnect() × N hosts in parallel
       → run(): sort by latency → pick fastest
       → tryConnect(fastest)
         → qot.setClientInfo()
         → qot.setConnSpi(this)
         → qot.setRSAPrivateKey()   [PKCS#1 key from Config]
         → qot.initConnect(host, port, isRSA)
         → poll connected flag      [wait up to 8s]
         → onInitConnect callback fires (errCode=0 → connected=true)
         → qot.getGlobalState(GetGlobalState.C2S.setUserID(0))
         → onReply_GetGlobalState callback
           [retCode=2 + qotLogined=true + trdLogined=true = SUCCESS]
```

### Flow 2: Quote Subscribe + Push
```
main() → start()
      → qot.initConnect() → onInitConnect
      → qot.sub(QotSub)  [isSubOrUnSub=true, isRegPush=true, isFirstPush=true]
      → onReply_Sub callback      [confirms subscription]
      → push callbacks fire: onPush_UpdateBasicQuote, onPush_UpdateOrderBook,
                           onPush_UpdateTicker, onPush_UpdateBroker
      → qot.sub(QotSub)  [isSubOrUnSub=false] to unsubscribe
      → qot.close()
```

### Flow 3: Trading Order Placement (Example05)
```
main() → start()
      → qot.initConnect() + trd.initConnect()  [dual connections]
      → trd.getAccList()
      → onReply_GetAccList: accList populated, first acc selected
      → build TrdHeader (accId + trdEnv=SIM + trdMarket)
      → qot.getSecuritySnapshot() [get current price + lot_size]
      → onReply_GetSecuritySnapshot: lastSnapshotPrice, lotSize saved
      → trd.getFunds()
      → onReply_GetFunds: lastFundsPower saved
      → trd.getPositionList()
      → onReply_GetPositionList
      → qty = floor(lastFundsPower / lastSnapshotPrice / lotSize) * lotSize
      → trd.placeOrder(TrdSide=BUY, qty, price)
      → onReply_PlaceOrder: orderID returned
      → qot.close() + trd.close()
```

### Flow 4: Historical K-Line Query
```
main() → start()
      → qot.initConnect() → onInitConnect
      → qot.getKL()       [for DAY, 60M, 30M, 5M periods]
      → onReply_GetKL callback
      → qot.requestHistoryKL(beginTime, endTime, maxAckNum=100)
      → onReply_RequestHistoryKL
        [may have nextReqKey if more data]
      → if nextReqKey present: repeat with nextReqKey
```

### Flow 5: Order Book Depth
```
main() → start()
      → qot.initConnect()
      → qot.sub(QotSub ORDER_BOOK)  [subscribe first]
      → onReply_Sub
      → qot.getOrderBook(num=10)     [fetch 10-level depth]
      → onReply_GetOrderBook: logs bid/ask levels, spread, volume ratio
      → qot.getOrderBook(num=50)     [fetch 50-level for total volume]
```

---

## Market Codes

| Market | ID | Notes |
|--------|-----|-------|
| HK Securities | 1 | `00700`, `HSI` (index — not a stock, use futures合约) |
| US Securities | 11 | `AAPL`, `NDX` (not market ID 2) |
| SH | 4 | |
| SZ | 5 | |
| HK Future | 7 | `HSImain` |
| US Future | 23 | |
| SG Future | 13 | |
| JP Future | 25 | |

> Note: US securities use market ID `11` in `QotCommon.QotMarket`, not `2`.

---

## Trading Environments

| Env | ID | Unlock required |
|-----|-----|----------------|
| SIMULATE | 1 | No |
| REAL | 2 | Yes — `unlockTrade(MD5(password))` |

---

## Component Diagram

```mermaid
flowchart TB
    subgraph "External Services"
        OpenD["FutuOpenD Gateway<br/>:11111"]
    end

    subgraph "Configuration"
        Config["Config.java<br/>.env loader<br/>RSA PKCS#1 key<br/>Host resolution"]
    end

    subgraph "Connection Layer"
        FTAPI["FTAPI.init()"]
        FTAPI_Conn_Qot["FTAPI_Conn_Qot<br/>Quote connection"]
        FTAPI_Conn_Trd["FTAPI_Conn_Trd<br/>Trading connection"]
    end

    subgraph "SPI Callbacks"
        FTSPI_Conn["FTSPI_Conn<br/>onInitConnect<br/>onDisconnect"]
        FTSPI_Qot["FTSPI_Qot<br/>onReply_*<br/>onPush_*"]
        FTSPI_Trd["FTSPI_Trd<br/>onReply_*<br/>onPush_*"]
    end

    subgraph "Quote Examples"
        Ex00["Example00_ConnectHA<br/>HA + getGlobalState"]
        Ex01["Example01_MarketSnapshot<br/>getSecuritySnapshot"]
        Ex02["Example02_QuotePush<br/>sub + push"]
        Ex03["Example03_StockFilter<br/>stockFilter screener"]
        Ex07["Example07_Kline<br/>getKL + historyKL"]
        Ex08["Example08_RtTicker<br/>getTicker + getRT"]
        Ex10["Example10_OrderBook<br/>getOrderBook depth"]
        Ex25["Example25_OptionChain<br/>getOptionChain"]
    end

    subgraph "Trading Examples"
        Ex05["Example05_QuoteTrade<br/>qot + trd dual"]
        Ex30["Example30_UserInfo<br/>getAccList"]
        Ex32["Example32_OrderQuery<br/>unlock + query"]
        Ex33["Example33_TradingInfo<br/>getMaxTrdQtys"]
    end

    Config -->|RSA key, host config| FTAPI
    FTAPI --> FTAPI_Conn_Qot
    FTAPI --> FTAPI_Conn_Trd

    FTAPI_Conn_Qot -->|setConnSpi| FTSPI_Conn
    FTAPI_Conn_Qot -->|setQotSpi| FTSPI_Qot
    FTAPI_Conn_Trd -->|setConnSpi| FTSPI_Conn
    FTAPI_Conn_Trd -->|setTrdSpi| FTSPI_Trd

    Ex00 -->|TCP probe + connect| OpenD
    Ex01 -->|getSecuritySnapshot| OpenD
    Ex02 -->|sub + push| OpenD
    Ex03 -->|stockFilter| OpenD
    Ex07 -->|getKL + historyKL| OpenD
    Ex08 -->|getTicker + getRT| OpenD
    Ex10 -->|sub + getOrderBook| OpenD
    Ex25 -->|getOptionChain| OpenD

    Ex05 -->|qot + trd| OpenD
    Ex30 -->|trd getAccList| OpenD
    Ex32 -->|trd unlock + query| OpenD
    Ex33 -->|trd getMaxTrdQtys| OpenD

    style Config fill:#e1f5fe
    style FTAPI fill:#fff3e0
    style OpenD fill:#f3e5f5
```

---

## Data Flow Patterns

### Request → Callback Pattern

All SDK calls are fire-and-forget with a matching callback:

```
qot.getSecuritySnapshot(req)  →  onReply_GetSecuritySnapshot(client, retCode, rsp)
qot.getKL(req)                 →  onReply_GetKL(client, retCode, rsp)
qot.sub(req)                   →  onReply_Sub(client, retCode, rsp)
qot.getOrderBook(req)          →  onReply_GetOrderBook(client, retCode, rsp)
qot.stockFilter(req)          →  onReply_StockFilter(client, retCode, rsp)
trd.unlockTrade(req)           →  onReply_UnlockTrade(client, retCode, rsp)
trd.placeOrder(req)           →  onReply_PlaceOrder(client, retCode, rsp)
trd.getAccList(req)            →  onReply_GetAccList(client, retCode, rsp)
trd.getFunds(req)              →  onReply_GetFunds(client, retCode, rsp)
```

### Connection State Machine

```
initConnect() → [connecting] → onInitConnect(errCode=0) → [connected]
                           → onDisconnect() → [disconnected]
```

---

## Directory Structure

```
src/main/java/com/futu/sdk/examples/
├── Config.java                    # .env loading, RSA key PKCS#8→PKCS#1 conversion
├── Example00_ConnectHA.java        # HA TCP probe + getGlobalState (RSA auth)
├── Example01_MarketSnapshot.java   # getSecuritySnapshot (no subscription needed)
├── Example02_QuotePush.java        # sub() + onPush_UpdateBasicQuote, etc.
├── Example03_StockFilter.java      # stockFilter screener
├── Example04_MacdStrategy.java     # MACD trend strategy + getKL + onPush_UpdateKL
├── Example05_QuoteTrade.java       # Dual qot+trd connections, SIMULATE order
├── Example06_StockSell.java       # Sell order — position query + sell in SIMULATE
├── Example07_Kline.java           # getKL + requestHistoryKL with pagination
├── Example08_RtTicker.java         # getTicker + getRT
├── Example09_BrokerQueue.java      # getBroker — broker bid/ask wall
├── Example10_OrderBook.java        # getOrderBook depth (10-level + 50-level)
├── Example11_AccInfo.java         # getAccList + getFunds + getPositionList
├── Example12_TradingDays.java       # requestTradeDate
├── Example13_Plate.java           # getPlateSet / getPlateSecurity
├── Example14_CurKline.java         # getKL + onPush_UpdateKL live push
├── Example15_SubList.java          # getSubInfo, sub/unsub
├── Example16_StockQuote.java        # getBasicQot (requires subscription)
├── Example17_OwnerPlate.java        # getOwnerPlate / getReference
├── Example18_ReferenceStock.java    # getReference
├── Example19_CapitalFlow.java      # getCapitalFlow / getCapitalDistribution
├── Example20_IpoList.java          # getIpoList
├── Example21_FutureInfo.java        # getFutureInfo
├── Example22_MarketState.java      # getMarketState
├── Example23_PriceReminder.java   # setPriceReminder / getPriceReminder
├── Example24_UserSecurity.java     # getUserSecurity / modifyUserSecurity
├── Example25_OptionChain.java      # getOptionExpirationDate + getOptionChain
├── Example26_HistoryKLQuota.java  # requestHistoryKL quota tracking
├── Example27_CodeChange.java      # getCodeChange — code/name changes
├── Example28_Warrant.java          # getWarrant
├── Example30_UserInfo.java         # getAccList + subAccPush
├── Example31_Misc.java             # getHoldingChangeList, requestRehab, user security group
├── Example32_OrderQuery.java       # unlockTrade + getOrderList + getOrderFillList
├── Example33_TradingInfo.java      # getMaxTrdQtys, margin requirements
├── Example34_CancelAll.java        # getOrderList + modifyOrder batch cancel
├── Example35_CashFlow.java         # getCashFlow
├── Example36_StockBasicInfo.java   # getSecurityStaticInfo
├── Example37_MarginRatio.java      # getMarginRatio
├── Example38_OrderFee.java         # getOrderFee
├── Example39_SysNotify.java        # subSysNotify push
├── Example40_TradePush.java        # onPush_UpdateOrder / onPush_UpdateFill
├── Example41_Rehab.java            # requestRehab
├── Example42_CapitalDistribution.java # getCapitalDistribution
├── Example43_SubscribeLifecycle.java # subscription lifecycle demo
├── Example44_MultiMarketSnapshot.java # getSecuritySnapshot multi-market
├── Example45_StockFilter.java      # stockFilter extended criteria
├── Example46_PlateStockFilter.java # getPlateSecurity + stockFilter
├── Example47_WarrantFilter.java   # getWarrant + filter screener
├── Example48_OptionsStrategy.java  # Options combo strategies
├── Example49_AccCashFlow.java     # getCashFlow per-account
├── Example50_HistoryOrderDeal.java # getOrderList / getOrderFillList historical
├── Example51_AccList.java         # getAccList with securities firm info
├── Example52_OptionChainFilter.java # getOptionChain + stockFilter
├── Example53_MarketHeat.java      # getMarketHeat
├── Example55_EMA.java             # EMA indicator calculation
├── Example56_ETFComposition.java  # getETFComponent
└── Example57_VWAPBenchmark.java   # VWAP benchmark analysis
```

---

## Related Projects

| Project | Language | Description |
|---------|----------|-------------|
| [futu-python-samples](https://github.com/shing1211/futu-python-samples) | Python | Original reference implementation |
| [futuapi4go](https://github.com/shing1211/futuapi4go) | Go | Go port of the same SDK, reference for RSA implementation |