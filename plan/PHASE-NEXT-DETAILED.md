# Phase 4–9: Detailed Design & Implementation Plan

**Date:** 2026-05-24
**Status:** Planned
**Prerequisite:** Phase 1–3 completed (bug fixes, quality, infrastructure)
**New examples:** 54, 58–65 created (callback handlers simplified to `s2c={}`)

---

## Executive Summary

This plan addresses 7 areas of improvement discovered through comprehensive codebase analysis:

| Phase | Focus | Files Changed | Risk | Effort |
|-------|-------|---------------|------|--------|
| 4 | Critical Bug Fixes | ~25 | HIGH | Medium |
| 5 | Shared Utility Extraction | ~70 | LOW | Large |
| 6 | Test Infrastructure | ~10 new | LOW | Medium |
| 7 | CI/CD & Quality Gates | ~5 | LOW | Small |
| 8 | Documentation & New API Polish | ~5 | LOW | Small |
| 9 | Advanced Features | ~10 | MEDIUM | Large |

**Microservice refactoring:** NOT APPLICABLE. This project is a **sample/demo collection** of 66 standalone Java files. Each example is intentionally self-contained and runnable independently. Decomposing into microservices would defeat the educational purpose.

---

## Phase 4: Critical Bug Fixes

### 4.1 SLF4J Format Specifier Mismatch — 92 occurrences across 20+ files

**Severity:** CRITICAL — garbled output or `IllegalArgumentException` at runtime
**Root Cause:** Python/printf-style format specifiers (`{:.2f}`, `{:.4f}`, `{:+.2f}`, `{:<12}`, `{:>10.2f}`) used in SLF4J logger calls. SLF4J only supports `{}` as a simple placeholder.

**Fix Pattern:**
```java
// BEFORE (broken):
logger.info("price={:.2f} vol={}", price, vol);

// AFTER (correct):
logger.info("price={} vol={}", String.format("%.2f", price), vol);
```

**Alternative:** Create a `Fmt` utility class with common formatters:
```java
public final class Fmt {
    public static String f2(double v) { return String.format("%.2f", v); }
    public static String f4(double v) { return String.format("%.4f", v); }
    public static String f6(double v) { return String.format("%.6f", v); }
    public static String pct(double v) { return String.format("%.2f%%", v); }
    public static String signed(double v) { return String.format("%+.2f", v); }
}
```
This keeps logger calls readable: `logger.info("price={} vol={}", Fmt.f2(price), vol);`

**Affected Files (with occurrence count):**

| File | Count | Key Lines |
|------|-------|-----------|
| Example04_MacdStrategy.java | 10 | 208,209,237,270,271,275,295,351,370,408 |
| Example05_QuoteTrade.java | 5 | 150,171,251,289 |
| Example06_StockSell.java | 2 | 203,274 |
| Example07_Kline.java | 2 | 183,200 |
| Example08_RtTicker.java | 2 | 129,148 |
| Example10_OrderBook.java | 5 | 146,151,159,166 |
| Example14_CurKline.java | 1 | 131 |
| Example16_StockQuote.java | 5 | 146,149,154,159 |
| Example19_CapitalFlow.java | 7 | 137,154,157,164 |
| Example20_IpoList.java | 2 | 123,127 |
| Example23_PriceReminder.java | 1 | 189 |
| Example28_Warrant.java | 1 | 130 |
| Example31_Misc.java | 2 | 152,174 |
| Example32_OrderQuery.java | 3 | 233,236,255 |
| Example33_TradingInfo.java | 4 | 154,176,178,180 |
| Example34_CancelAll.java | 1 | 190 |
| Example35_CashFlow.java | 1 | 190 |
| Example37_MarginRatio.java | 1 | 200 |
| Example38_OrderFee.java | 3 | 193,236,241 |
| Example42_CapitalDistribution.java | 2 | 139,141 |
| Example44_MultiMarketSnapshot.java | 2 | 156,202 |
| Example47_WarrantFilter.java | 3 | 190,192,194 |
| Example48_OptionsStrategy.java | 15 | 117,262-273,304-315,355,360 |
| Example50_HistoryOrderDeal.java | 4 | 260,266,276,280 |
| Example53_MarketHeat.java | 2 | 153,174 |
| Example55_EMA.java | 2 | 160,186 |
| Example56_ETFComposition.java | 3 | 271,272,297 |
| Example57_VWAPBenchmark.java | 7 | 209,218,219,225,324,344,386 |
| Example02_QuotePush.java | 2 | 203,219 |

**Execution Order:** Fix `Fmt.java` first, then fix each file top-to-bottom.

---

### 4.2 Example64 Typo: `FUTU_RESA_ENABLED` → `FUTU_RSA_ENABLED`

**Severity:** HIGH — compilation error or runtime misconfiguration
**File:** `Example64_ValuationScreener.java:45`
**Fix:** One-line change.

---

### 4.3 Example65 Wrong Market ID: `setMarket(2)` → `setMarket(11)`

**Severity:** HIGH — US securities use market ID 11 in `QotCommon.QotMarket`, not 2
**File:** `Example65_TopTenBrokers.java:52`
**Fix:** Replace `setMarket(2)` with `setMarket(MARKET_US)` where `MARKET_US = 11`.

---

### 4.4 Example02 Magic Numbers for Market

**Severity:** MEDIUM — inconsistent with all other examples
**File:** `Example02_QuotePush.java:86-87`
**Fix:** Replace `setMarket(1)` with `setMarket(MARKET_HK)` using a named constant.

---

### 4.5 Example50: Multiple Bugs

**Severity:** HIGH
**File:** `Example50_HistoryOrderDeal.java`

**4.5.1 Missing connection wait loop**
The code immediately awaits on latches without waiting for `connected == true`. The `onInitConnect` callback sets the flag but it's never checked.

**Fix:** Add standard connection wait loop before latch await:
```java
int waited = 0;
while (!connected && waited < 8000) { sleep(50); waited += 50; }
if (!connected) { logger.error("Connection timed out"); return; }
```

**4.5.2 Unused `qot` connection**
`FTAPI_Conn_Qot qot` is created (line 36), closed (line 87), but never connected. The class implements `FTSPI_Qot` with a single empty `onReply_GetGlobalState` stub.

**Fix:** Remove the `qot` field, remove `qot.close()` from finally, remove `FTSPI_Qot` from implements, remove empty `onReply_GetGlobalState`.

**4.5.3 `System.exit(0)` on line 97**
Prevents cleanup hooks from running. No other example (except Example00 for error cases) uses `System.exit()`.

**Fix:** Remove `System.exit(0)`.

**4.5.4 `onDisconnect` doesn't reset `connected` flag**
**Fix:** Add `connected = false;` in `onDisconnect`.

---

### 4.6 Example34: Race Condition with `trd.close()`

**Severity:** MEDIUM
**File:** `Example34_CancelAll.java`
**Issue:** Business logic (getAccList → queryOrders → cancelOrders) runs entirely in callback chain on SDK thread, but `finally { trd.close() }` runs on main thread after connection wait. No synchronization ensures callback chain completes before close.

**Fix:** Add a `CountDownLatch` that is counted down after the final cancel callback, and await it before the finally block:
```java
private final CountDownLatch done = new CountDownLatch(1);

// In final callback:
done.countDown();

// In start():
done.await(30, TimeUnit.SECONDS);
```

---

### 4.7 MD5 `getBytes()` Without Charset — 11 files

**Severity:** MEDIUM — platform-dependent encoding
**Files:** Example06, 11, 32, 33, 34, 35, 37, 38, 39, 40, 49
**Fix:** Replace `input.getBytes()` with `input.getBytes(StandardCharsets.UTF_8)`.

---

### 4.8 Inconsistent `onDisconnect` Flag Reset in Multi-Connection Examples

**Severity:** LOW
**Files:** Example04, 05, 06 (multi-connection examples that don't reset flags)
**Fix:** Add `qotConnected = false;` / `trdConnected = false;` in `onDisconnect`.

---

### 4.9 Example43: `queryQuota()` Always Returns -1

**Severity:** LOW — all delta calculations output 0
**File:** `Example43_SubscribeLifecycle.java:202-212`
**Fix:** Use an `AtomicInteger` to capture the quota value from the callback, or restructure to use a synchronous wrapper.

---

### 4.10 Dead Code: `bytesToHex()` in Example00

**Severity:** LOW
**File:** `Example00_ConnectHA.java:243-249`
**Fix:** Remove unused method.

---

## Phase 5: Shared Utility Extraction

### 5.1 Create `Helpers.java` — Centralized Utilities

**New File:** `src/main/java/com/futu/sdk/examples/Helpers.java`

```java
public final class Helpers {

    // ── Market Constants (from QotCommon.QotMarket) ──────────────
    public static final int MARKET_HK = 1;   // QotMarket_HK_Security
    public static final int MARKET_US = 11;  // QotMarket_US_Security
    public static final int MARKET_SH = 4;   // QotMarket_SH_Security
    public static final int MARKET_SZ = 5;   // QotMarket_SZ_Security
    public static final int MARKET_HK_FUTURE = 7;

    // ── Trading Constants ────────────────────────────────────────
    public static final int TRD_ENV_SIMULATE = 1;
    public static final int TRD_ENV_REAL = 2;
    public static final int TRD_MARKET_HK = 1;
    public static final int TRD_MARKET_US = 2;

    // ── Sleep ───────────────────────────────────────────────────
    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── MD5 (with UTF-8 charset) ───────────────────────────────
    public static String md5(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    // ── Number Formatting ───────────────────────────────────────
    public static String f2(double v) { return String.format("%.2f", v); }
    public static String f3(double v) { return String.format("%.3f", v); }
    public static String f4(double v) { return String.format("%.4f", v); }
    public static String f6(double v) { return String.format("%.6f", v); }
    public static String pct(double v) { return String.format("%.2f%%", v); }
    public static String signed2(double v) { return String.format("%+.2f", v); }
    public static String signed4(double v) { return String.format("%+.4f", v); }

    // ── Order Status Labels ─────────────────────────────────────
    public static String orderStatusLabel(int status) {
        return switch (status) {
            case 0 -> "SUBMITTING";
            case 1 -> "SUBMITTED";
            case 2 -> "SUBMIT_FAILED";
            case 3 -> "QUEUED";
            case 4 -> "PART_FILLED";
            case 5 -> "FILLED";
            case 6 -> "CANCELLED";
            case 7 -> "CANCELLING";
            case 8 -> "SUBMITTED_PARTIAL";
            case 9 -> "CANCEL_PARTIAL";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    // ── Trading Side Labels ─────────────────────────────────────
    public static String trdSideLabel(int side) {
        return switch (side) {
            case 1 -> "BUY";
            case 2 -> "SELL";
            default -> "SIDE(" + side + ")";
        };
    }

    // ── Turnover Formatting ─────────────────────────────────────
    public static String formatTurnover(double t) {
        if (t >= 1_000_000_000) return String.format("%.2fB", t / 1_000_000_000);
        if (t >= 1_000_000) return String.format("%.2fM", t / 1_000_000);
        if (t >= 1_000) return String.format("%.2fK", t / 1_000);
        return String.format("%.2f", t);
    }

    private Helpers() {} // prevent instantiation
}
```

### 5.2 Refactor All Examples to Use `Helpers`

**Changes per file:**
1. Add `import static com.futu.sdk.examples.Helpers.*;`
2. Remove local `sleep()` method → use static import
3. Remove local `md5()` method → use static import (11 files)
4. Replace local `MARKET_*` constants → use `Helpers.MARKET_*`
5. Replace local `TRD_*` constants → use `Helpers.TRD_*`
6. Replace local `orderStatusLabel()` → use `Helpers.orderStatusLabel()`
7. Replace local `trdSideLabel()` → use `Helpers.trdSideLabel()`
8. Replace local `formatTurnover()` → use `Helpers.formatTurnover()` (3 files)
9. Replace `{:.2f}` patterns → use `Helpers.f2()` / `Fmt.f2()`

**Files affected:** All 66 Java source files (65 examples + Config.java unchanged)

**Risk:** LOW — mechanical refactoring, no logic changes. Compile after each batch of 10 files.

---

## Phase 6: Test Infrastructure

### 6.1 Create `src/test/java/` Directory Structure

```
src/test/java/com/futu/sdk/examples/
├── ConfigTest.java          # Unit tests for Config.java
├── HelpersTest.java         # Unit tests for Helpers.java
├── FmtTest.java             # Unit tests for Fmt.java
└── integration/
    └── ExampleSmokeTest.java  # Smoke tests (instantiation only)
```

### 6.2 `ConfigTest.java` — Test Configuration Parsing

```java
class ConfigTest {
    @Test void testDefaultHost() { ... }
    @Test void testDefaultPort() { ... }
    @Test void testHaHostParsing() { ... }
    @Test void testRsaKeyDetection_Pkcs8() { ... }
    @Test void testRsaKeyDetection_Pkcs1() { ... }
    @Test void testGetBoolEnv_trueValues() { ... }
    @Test void testGetBoolEnv_falseValues() { ... }
    @Test void testGetIntEnv_valid() { ... }
    @Test void testGetIntEnv_invalid() { ... }
}
```

### 6.3 `HelpersTest.java` — Test Utility Methods

```java
class HelpersTest {
    @Test void testMd5_knownValue() { ... }
    @Test void testMd5_emptyString() { ... }
    @Test void testOrderStatusLabel_allCodes() { ... }
    @Test void testTrdSideLabel() { ... }
    @Test void testFormatTurnover_billions() { ... }
    @Test void testFormatTurnover_millions() { ... }
    @Test void testFormatTurnover_thousands() { ... }
    @Test void testF2() { ... }
    @Test void testPct() { ... }
}
```

### 6.4 `ExampleSmokeTest.java` — Verify Instantiation

```java
class ExampleSmokeTest {
    @Test void example00_instantiates() { assertNotNull(new Example00_ConnectHA()); }
    @Test void example01_instantiates() { ... }
    // ... one per example — catches import errors, missing fields, etc.
}
```

### 6.5 Update `pom.xml` — Add `maven-surefire-plugin` Test Scope

Ensure surefire picks up test classes. Add `src/test/resources/` if needed for test `.env` files.

---

## Phase 7: CI/CD & Quality Gates

### 7.1 Upgrade GitHub Actions Workflow

**File:** `.github/workflows/maven.yml`

**Current:** Only `mvn -B compile`
**Target:**
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven
      - name: Compile
        run: mvn -B compile
      - name: Test
        run: mvn -B test
      - name: Package
        run: mvn -B package -DskipTests
      - name: Upload JAR
        uses: actions/upload-artifact@v4
        with:
          name: futu-java-samples
          path: target/*.jar
```

### 7.2 Add Checkstyle Plugin

**File:** `pom.xml`
**Purpose:** Enforce code style consistency (indentation, imports, naming)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.4.0</version>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
    <configuration>
        <configLocation>google_checks.xml</configLocation>
        <failOnViolation>false</failOnViolation>  <!-- warn only initially -->
    </configuration>
</plugin>
```

### 7.3 Add SpotBugs Plugin (Optional)

Static analysis for null pointer dereferences, resource leaks, thread safety issues.

### 7.4 Add Logging Configuration

**New File:** `src/main/resources/simplelogger.properties`

```properties
org.slf4j.simpleLogger.defaultLogLevel=info
org.slf4j.simpleLogger.log.com.futu=debug
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS
```

---

## Phase 8: Documentation & New API Polish

### 8.1 Update README.md

**Current:** Lists 57 examples (00–57)
**Target:** List all 66 examples (00–65)

Add these entries to the Quote/Market Data table:

| # | Class | Description |
|---|-------|-------------|
| 54 | `Example54_OptionAnalytics` | `getOptionVolatility`, `getOptionExerciseProbability` — option analytics |
| 58 | `Example58_FinancialStatements` | `getFinancialsStatements`, `getFinancialsRevenueBreakdown`, `getFinancialsEarningsPriceMove`, `getFinancialsEarningsPriceHistory` |
| 59 | `Example59_ResearchRatings` | `getResearchAnalystConsensus`, `getResearchRatingSummary` |
| 60 | `Example60_CompanyFundamentals` | `getCompanyProfile`, `getCompanyExecutives`, `getCompanyOperationalEfficiency` |
| 61 | `Example61_ShareholdersInsiders` | `getShareholdersOverview`, `getShareholdersHoldingChanges`, `getShareholdersInstitutional`, `getInsiderHolderList`, `getInsiderTradeList` |
| 62 | `Example62_CorporateActions` | `getCorporateActionsDividends`, `getCorporateActionsBuybacks`, `getCorporateActionsStockSplits` |
| 63 | `Example63_ShortVolumeInterest` | `getDailyShortVolume`, `getShortInterest` |
| 64 | `Example64_ValuationScreener` | `getValuationDetail`, `getValuationPlateStockList` |
| 65 | `Example65_TopTenBrokers` | `getTopTenBuySellBrokers` |

Update header: `**Examples:** 66 (00–65, missing 29)`

### 8.2 Update ARCHITECTURE.md

Add new files to the directory listing section. Update the component diagram to include new examples.

### 8.3 Update `plan/FUTURE-IMPROVEMENTS.md`

Mark Phase 1–3 as completed. Add Phase 4–9 summary.

### 8.4 Polish New Example Callback Handlers

The 9 new examples (54, 58–65) currently log raw `s2c={}` which is functional but not user-friendly. After discovering correct proto field names (via `javap` on the SDK jar), enhance callbacks to log individual fields with proper formatting.

**Approach:** Extract proto class definitions from the SDK jar:
```bash
jar xf ~/.m2/repository/com/futunn/openapi/futu-api/10.7.6708/futu-api-10.7.6708.jar
javap -p com/futu/openapi/pb/QotGetOptionVolatility\$S2C.class
```

Then update each callback to use correct getter methods with `Helpers.f2()` / `Helpers.f4()` formatting.

---

## Phase 9: Advanced Features

### 9.1 Connection Helper — Reduce Boilerplate

**New Class:** `src/main/java/com/futu/sdk/examples/ConnectionHelper.java`

Encapsulates the repeated connection setup pattern:
```java
public class ConnectionHelper {
    private final FTAPI_Conn_Qot qot;
    private final FTAPI_Conn_Trd trd;  // nullable
    private volatile boolean connected = false;

    public ConnectionHelper(FTSPI_Qot qotSpi, FTSPI_Conn connSpi) { ... }
    public ConnectionHelper(FTSPI_Trd trdSpi, FTSPI_Conn connSpi) { ... }

    public boolean connect(long timeoutMs) {
        // setClientInfo, setConnSpi, setRSAPrivateKey, initConnect, wait loop
    }

    public void close() { qot.close(); if (trd != null) trd.close(); }
    public boolean isConnected() { return connected; }
}
```

**Impact:** Reduces 15–20 lines of boilerplate per example to 3–5 lines.

### 9.2 Synchronous Request Wrapper

**New Class:** `src/main/java/com/futu/sdk/examples/SyncRequest.java`

Wraps the async fire-and-forget pattern into a blocking call with timeout:
```java
public class SyncRequest {
    public static <T> T await(Consumer<CountDownLatch> sender, Function<CountDownLatch, T> extractor, long timeoutMs) {
        var latch = new CountDownLatch(1);
        var ref = new AtomicReference<T>();
        sender.accept(latch);
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        return extractor.apply(latch);
    }
}
```

**Impact:** Makes examples easier to follow for users coming from the synchronous Python SDK.

### 9.3 Example Runner CLI

**New Class:** `src/main/java/com/futu/sdk/examples/ExampleRunner.java`

A unified entry point that lists and runs examples by number:
```bash
java -jar futu-java-samples.jar --list     # list all examples
java -jar futu-java-samples.jar 1          # run Example01
java -jar futu-java-samples.jar 54         # run Example54
```

### 9.4 .gitignore Cleanup

Add entries:
```
META-INF/
com/
```
These are extracted SDK jar contents that should not be in the repo.

### 9.5 Consistent Connection Timeout

Standardize all examples to use `8000ms` connection wait timeout (currently mixed 8000/10000ms). Define as `Helpers.CONNECTION_TIMEOUT_MS = 8000`.

---

## Execution Order

```
Phase 4 (Critical Bug Fixes):
  ├── 4.1  SLF4J format specifiers     — 92 occurrences, 20+ files   [HIGH]
  ├── 4.2  Example64 FUTU_RESA typo    — 1 line                       [HIGH]
  ├── 4.3  Example65 market ID         — 1 line                       [HIGH]
  ├── 4.4  Example02 magic numbers     — 2 lines                      [MEDIUM]
  ├── 4.5  Example50 multiple bugs     — ~15 lines                    [HIGH]
  ├── 4.6  Example34 race condition    — ~10 lines                    [MEDIUM]
  ├── 4.7  MD5 getBytes() charset      — 11 files                     [MEDIUM]
  ├── 4.8  onDisconnect flag reset     — 3 files                      [LOW]
  ├── 4.9  Example43 queryQuota()      — ~15 lines                    [LOW]
  └── 4.10 Example00 dead code         — 7 lines                      [LOW]

Phase 5 (Shared Utility Extraction):
  ├── 5.1  Create Helpers.java + Fmt.java  — new files
  └── 5.2  Refactor all 66 examples        — mechanical, batch of 10

Phase 6 (Test Infrastructure):
  ├── 6.1  Create test directory structure
  ├── 6.2  ConfigTest.java
  ├── 6.3  HelpersTest.java
  ├── 6.4  ExampleSmokeTest.java
  └── 6.5  Update pom.xml surefire config

Phase 7 (CI/CD & Quality Gates):
  ├── 7.1  Upgrade GitHub Actions workflow
  ├── 7.2  Add Checkstyle plugin
  ├── 7.3  Add SpotBugs plugin (optional)
  └── 7.4  Add logging configuration

Phase 8 (Documentation & Polish):
  ├── 8.1  Update README.md (66 examples)
  ├── 8.2  Update ARCHITECTURE.md
  ├── 8.3  Update FUTURE-IMPROVEMENTS.md
  └── 8.4  Polish new example callbacks (54, 58-65)

Phase 9 (Advanced Features):
  ├── 9.1  ConnectionHelper class
  ├── 9.2  SyncRequest wrapper
  ├── 9.3  ExampleRunner CLI
  ├── 9.4  .gitignore cleanup
  └── 9.5  Consistent connection timeout
```

---

## Risk Assessment

| Phase | Risk | Mitigation |
|-------|------|------------|
| 4 | HIGH — SLF4J fix touches 20+ files | Compile after each file; run `mvn test` after batch |
| 5 | LOW — mechanical refactoring | Compile after each batch of 10 files |
| 6 | LOW — new test files only | No existing code modified |
| 7 | LOW — CI config only | Test on a branch before merging |
| 8 | LOW — documentation only | Review diff carefully |
| 9 | MEDIUM — new abstractions | Optional; each feature is independent |

---

## Verification Checklist

After each phase:
- [ ] `mvn compile` succeeds with zero errors
- [ ] `mvn test` passes (once Phase 6 is complete)
- [ ] No SLF4J format specifier warnings in test output
- [ ] README example count matches actual file count
- [ ] All examples can be instantiated (smoke test)
- [ ] GitNexus `detect_changes()` shows only expected symbols affected
