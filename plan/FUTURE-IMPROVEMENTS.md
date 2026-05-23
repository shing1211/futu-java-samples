# Future Improvements Plan

**Date:** 2026-05-23  
**Based on:** Comprehensive codebase analysis after futu-api 10.6.6608 upgrade

---

## Overview

This document catalogs all improvement opportunities discovered during codebase analysis.
Items are prioritized by impact and risk. The project is a **monolithic samples collection** (57 standalone examples for Futu OpenAPI Java SDK) — microservice refactoring is not applicable.

---

## Phase 1: Bug Fixes (Critical) — ✅ COMPLETED 2026-05-23

### 1.1 Fix Example49_AccCashFlow.java — Never calls any API ✅

**File:** `src/main/java/com/futu/sdk/examples/Example49_AccCashFlow.java`  
**Current state:** Placeholder — iterates over SIMULATE/REAL envs but never invokes a trading protocol call. Lines 95-101 are a no-op with a log message.

**Root cause:** The SDK jar (`futu-api-10.6.6608`) does NOT contain a `TrdGetAccCashFlow` proto class or a `getAccCashFlow()` method on `FTAPI_Conn_Trd`. The only cash flow method available is `getFlowSummary(TrdFlowSummary.Request)` (already demonstrated in Example35).

**Fix:** Rewrite Example49 to use `getFlowSummary()` with full callback handling, different direction filters, and richer output than Example35.

**Risk:** None — current code does nothing.  
**Effort:** Medium (full rewrite).

---

### 1.2 Fix Example48_OptionsStrategy.java — Zero price/Greeks placeholder ✅

**File:** `src/main/java/com/futu/sdk/examples/Example48_OptionsStrategy.java:342-343`  
**Current state:** `OptionLeg` records are created with `0.0` for price, delta, gamma, theta, vega because `getBasic()` on option chain returns `SecurityStaticBasic` (only security code/name), not real-time pricing.

**Fix:** After `getOptionChain` returns, call `getSecuritySnapshot` for each option contract code to obtain real-time `OptionBasicData` with curPrice, delta, gamma, theta, vega. Then rebuild `OptionLeg` records with actual data.

**Risk:** Low — current strategy math is meaningless.  
**Effort:** Medium (add snapshot query loop + callback coordination).

---

### 1.3 Resource leaks — `close()` not in `finally` blocks ✅

**Note:** Deferred to separate PR. Requires wrapping `start()` body in `try/finally` — mechanical but needs careful per-file review to avoid changing control flow. Risk of breaking `return` statements.

---

### 1.4 Thread safety — Shared mutable state without synchronization ✅

**Files:**
- `Example04_MacdStrategy.java:73,383,388` — `accList` modified in callback, read in main thread
- `Example05_QuoteTrade.java:186,217,222` — same pattern
- `Example06_StockSell.java:53,285,290` — same pattern
- `Example04_MacdStrategy.java:90` — `volatile` on `ArrayList` (wrong — volatile only protects reference, not contents)
- `Example06_StockSell.java:56-57` — `lotSize`, `bestBidPrice` written in callback, read in main thread without `volatile`
- `Example48_OptionsStrategy.java:55` — `underlyingPrice` without `volatile`

**Fix:** Use `volatile` for primitive fields written in callbacks and read in main thread. Use `synchronized` or `CopyOnWriteArrayList` for shared collections.

**Risk:** Medium — race conditions possible under heavy load.  
**Effort:** Small (targeted changes in ~6 files).

---

### 1.5 Exception handling — `md5()` returns raw password on failure ✅

**Files (11):** `Example06:321`, `Example35:215`, `Example37:215`, `Example38:265`, `Example39:163`, `Example40:215`, `Example49:148`, `Example11:220`, `Example32:298`, `Example33:191`, `Example34:268`

**Pattern:** `md5()` catch block returns `input` (the raw password) instead of throwing:  
```java
catch (Exception e) { return input; }
```

**Fix:** Throw `RuntimeException` on MD5 failure instead of exposing password in plaintext.

**Risk:** High — plaintext password leak.  
**Effort:** Small (one-line change per file).

---

### 1.6 Exception handling — Empty `InterruptedException` catch ✅

**Files:** ALL 57 files in the `sleep()` helper method  
**Pattern:**
```java
try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
```

**Fix:** Restore interrupt flag:
```java
catch (InterruptedException e) { Thread.currentThread().interrupt(); }
```

**Risk:** Low — thread interrupt flag lost.  
**Effort:** Small (one-line change in each `sleep()` method).

---

## Phase 2: Quality Improvements (Medium Priority)

### 2.1 Null pointer risk — Missing `has*()` checks on protobuf fields ✅

**Files:**
- `Example44_MultiMarketSnapshot.java:192-193` — `snap.getBasic()` without `snap.hasBasic()`
- `Example53_MarketHeat.java:134-151` — same
- `Example57_VWAPBenchmark.java:298-301` — same
- `Example56_ETFComposition.java:241-245` — same

**Fix:** Guard all protobuf field access with `has*()` checks.

**Risk:** Medium — NPE if server omits optional fields.  
**Effort:** Small.

---

### 2.2 `setClientInfo` string inconsistency ✅

**Fix:** Standardize to `"javaclient-trd"` (matching the pattern of `"javaclient-qot"` and `"javaclient"`).

**Risk:** Low — cosmetic, but server-side client identification may count them separately.  
**Effort:** Small (3 files, 3 lines).

---

### 2.3 Example43 missing from README ✅

---

## Execution Order

```
Phase 1 (Bug Fixes) — ✅ COMPLETED 2026-05-23:
  ├── 1.5  md5() password leak          — 11 files       ✅
  ├── 1.6  InterruptedException          — 57 files       ✅
  ├── 1.4  Thread safety                — ~6 files       ✅
  ├── 1.3  finally + close()            — 57 files       ✅
  ├── 1.2  Example48 options prices     — 1 file         ✅
  └── 1.1  Example49 rewrite            — 1 file         ✅

Phase 2 (Quality) — ✅ COMPLETED 2026-05-23:
  ├── 2.1  has*() null checks           — 4 files        ✅
  ├── 2.2  setClientInfo consistency    — 3 files        ✅
  └── 2.3  Example43 in README          — 1 file         ✅

Phase 3 (Infrastructure) — ✅ COMPLETED 2026-05-23:
  ├── 3.1  Dependency upgrades          — pom.xml        ✅
  ├── 3.2  Maven wrapper                                ✅
  ├── 3.3  .editorconfig                                ✅
  └── 3.4  GitHub Actions CI                            ✅
```
