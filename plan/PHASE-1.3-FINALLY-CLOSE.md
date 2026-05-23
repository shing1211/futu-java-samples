# Phase 1.3: Resource Leaks — `close()` not in `finally` blocks

**Date:** 2026-05-23
**Status:** In Progress
**Risk:** Medium — connections leak on uncaught exceptions

## Problem

Every example file creates `FTAPI_Conn_Qot` and/or `FTAPI_Conn_Trd` instances and calls `close()` at the end of `start()`, but NOT inside a `try/finally` block. If any code between `initConnect` and `close()` throws an uncaught runtime exception, the connection leaks.

## Fix Strategy

Wrap the API call section (between `initConnect` and `close()`) in `try { ... } finally { ... }`.

### Rule summary

1. **Identify scope start:** Find the line AFTER `initConnect` + `await()` for connection. This is where try { begins.
2. **Wrap logic:** Add `try {` before the first API call (after connection is confirmed), and `} finally { close(); }` at the end.
3. **Remove early-inline close():** Files that call `close()` before early `return` statements should have those `close()` calls removed (finally handles it).
4. **Dual connections:** Both `qot.close()` and `trd.close()` go in the same finally block.

### Files by category

#### Category A: QOT only (40 files) — Simple `try { ... } finally { qot.close(); }`

| # | File | Connection | Notes |
|---|------|-----------|-------|
| 1 | Example00_ConnectHA | qot | Uses `run()` not `start()` — special HA pattern |
| 2 | Example01_MarketSnapshot | qot | Simple pattern |
| 3 | Example02_QuotePush | qot | Simple pattern |
| 4 | Example03_StockFilter | qot | Simple pattern |
| 5 | Example07_Kline | qot | Simple pattern |
| 6 | Example08_RtTicker | qot | Simple pattern |
| 7 | Example09_BrokerQueue | qot | Simple pattern |
| 8 | Example10_OrderBook | qot | Simple pattern |
| 9 | Example12_TradingDays | qot | Simple pattern |
| 10 | Example13_Plate | qot | Simple pattern |
| 11 | Example14_CurKline | qot | Simple pattern |
| 12 | Example15_SubList | qot | Simple pattern |
| 13 | Example16_StockQuote | qot | Simple pattern |
| 14 | Example17_OwnerPlate | qot | Simple pattern |
| 15 | Example18_ReferenceStock | qot | Simple pattern |
| 16 | Example19_CapitalFlow | qot | Simple pattern |
| 17 | Example20_IpoList | qot | Simple pattern |
| 18 | Example21_FutureInfo | qot | Simple pattern |
| 19 | Example22_MarketState | qot | Simple pattern |
| 20 | Example23_PriceReminder | qot | Simple pattern |
| 21 | Example24_UserSecurity | qot | Simple pattern |
| 22 | Example25_OptionChain | qot | Simple pattern |
| 23 | Example26_HistoryKLQuota | qot | Simple pattern |
| 24 | Example27_CodeChange | qot | Simple pattern |
| 25 | Example28_Warrant | qot | Simple pattern |
| 26 | Example31_Misc | qot | Simple pattern |
| 27 | Example36_StockBasicInfo | qot | Simple pattern |
| 28 | Example41_Rehab | qot | Simple pattern |
| 29 | Example42_CapitalDistribution | qot | Simple pattern |
| 30 | Example43_SubscribeLifecycle | qot | Simple pattern |
| 31 | Example44_MultiMarketSnapshot | qot | Simple pattern |
| 32 | Example45_StockFilter | qot | Simple pattern |
| 33 | Example46_PlateStockFilter | qot | Simple pattern |
| 34 | Example47_WarrantFilter | qot | Simple pattern |
| 35 | Example48_OptionsStrategy | qot | Simple pattern |
| 36 | Example52_OptionChainFilter | qot | Simple pattern |
| 37 | Example53_MarketHeat | qot | Simple pattern |
| 38 | Example55_EMA | qot | Simple pattern |
| 39 | Example56_ETFComposition | qot | Simple pattern |
| 40 | Example57_VWAPBenchmark | qot | Simple pattern |

#### Category B: TRD only (12 files) — Simple `try { ... } finally { trd.close(); }`

| # | File | Connection | Notes |
|---|------|-----------|-------|
| 1 | Example11_AccInfo | trd | Simple pattern |
| 2 | Example30_UserInfo | trd | Simple pattern |
| 3 | Example32_OrderQuery | trd | Simple pattern |
| 4 | Example33_TradingInfo | trd | Simple pattern |
| 5 | Example34_CancelAll | trd | Simple pattern |
| 6 | Example35_CashFlow | trd | Simple pattern |
| 7 | Example37_MarginRatio | trd | Simple pattern |
| 8 | Example38_OrderFee | trd | Simple pattern |
| 9 | Example39_SysNotify | trd | Simple pattern |
| 10 | Example40_TradePush | trd | Simple pattern |
| 11 | Example49_AccCashFlow | trd | Simple pattern |
| 12 | Example51_AccList | trd | Simple pattern |

#### Category C: Dual (QOT + TRD, 4 files) — `try { ... } finally { qot.close(); trd.close(); }`

| # | File | Connections | Notes |
|---|------|------------|-------|
| 1 | Example04_MacdStrategy | qot + trd | Early return with inline close at ~line 184 |
| 2 | Example05_QuoteTrade | qot + trd | Early return with inline close |
| 3 | Example06_StockSell | qot + trd | Early return with inline close at ~line 154 |
| 4 | Example50_HistoryOrderDeal | qot + trd | Early return with inline close |

### Template for Category A/B (simple, single connection)

**Before:**
```java
public void start() {
    qot.setClientInfo("javaclient-qot", 1);
    qot.setConnCallback(this);
    qot.initConnect(config.getIp(), config.getPort(), false);

    // ... wait for connection ...
    // ... API calls ...
    // ... sleep ...

    qot.close();
}
```

**After:**
```java
public void start() {
    qot.setClientInfo("javaclient-qot", 1);
    qot.setConnCallback(this);
    qot.initConnect(config.getIp(), config.getPort(), false);

    try {
        // ... wait for connection ...
        // ... API calls ...
        // ... sleep ...
    } finally {
        qot.close();
    }
}
```

### Template for Category C (dual connections)

**Before:**
```java
public void start() {
    qot.setClientInfo(...);
    qot.initConnect(...);
    trd.setClientInfo(...);
    trd.initConnect(...);

    // ... wait for connections ...

    if (bad) {
        qot.close(); trd.close();  // <-- early inline close
        return;
    }

    // ... API calls ...

    qot.close();
    trd.close();
}
```

**After:**
```java
public void start() {
    qot.setClientInfo(...);
    qot.initConnect(...);
    trd.setClientInfo(...);
    trd.initConnect(...);

    try {
        // ... wait for connections ...

        if (bad) {
            return;  // close() handled by finally
        }

        // ... API calls ...
    } finally {
        qot.close();
        trd.close();
    }
}
```

### Edge case: Example00_ConnectHA

Uses `run()` method with `FTAPI_Conn_Qot`. Connections are created inside `run()` and are reused across multiple retry attempts. The `close()` at line 186 is already outside the retry loop.

After `initConnect` + connection wait, wrap in try/finally.

### Testing

After all changes, verify:
```
mvn compile -q
```
All 57 files must compile with zero errors.
