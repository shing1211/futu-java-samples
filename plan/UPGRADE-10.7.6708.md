# Upgrade Plan: futu-api 10.6.6608 → 10.7.6708

**Date:** 2026-06-05  
**Target:** `com.futunn.openapi:futu-api:10.7.6708` (Maven Central)  
**OpenD Requirement:** FutuOpenD ≥ 10.7.x (verified live: `serverVer=1007`)  
**Companion doc:** `plan/UPGRADE-10.7.6708-DELTAS.md` (full proto diff)

---

## 1. Summary

Upgrade the Java samples project from futu-api **10.6.6608** to **10.7.6708**, AND add 4 new example files for the 6 newly-available screen APIs. The SDK published on 2026-06-04 to Maven Central, and is the largest non-breaking minor bump in the 10.x series (added 46 Qot + 6 Trd methods).

This upgrade is **fully backward-compatible** — no method removals, no proto field removals. The only behavioral change is the existence of new APIs.

---

## 2. Changes Required

### 2.1 `pom.xml` (1 line, line 20)

```xml
<futu-api.version>10.7.6708</futu-api.version>
```

### 2.2 New source files (4)

| File | APIs demonstrated |
|------|-------------------|
| `Example66_StockScreen.java` | `getStockScreen` |
| `Example67_OptionScreen.java` | `getOptionScreen` |
| `Example68_WarrantScreen.java` | `getWarrantScreen` |
| `Example69_UnusualActivity.java` | `getTechnicalUnusual`, `getFinancialUnusual`, `getDerivativeUnusual` |

Each follows the established pattern (`implements FTSPI_Qot, FTSPI_Conn`, builder-based C2S, async latch, log first 10 items).

### 2.3 Documentation updates

| File | Sites |
|------|-------|
| `README.md` | Line 7 (SDK version), line 10 (count), line 372 (retCode convention), line 386 (OpenD version compatibility), line 390 (downgrade example), lines 158-160 (examples table — 4 new rows) |
| `ARCHITECTURE.md` | Line 5 (SDK version), line 11 (retCode version), lines 360-363 (directory listing — 4 new files) |
| `plan/UPGRADE-10.7.6708-DELTAS.md` | **New** — full API surface diff |
| `plan/FUTURE-IMPROVEMENTS.md` | Re-open §1.1 (`getAccCashFlow` still missing in 10.7); add Phase 5 (6 screen APIs now covered) |

### 2.4 `Example49_AccCashFlow.java` — UNCHANGED

The deferred "rewrite using real `getAccCashFlow`" is **NOT possible in this upgrade** — confirmed via `javap` and `jar tf` that the method does not exist in 10.7.6708 (nor 10.6.6608 nor 10.5.6508). `TrdGetAccCashFlow` proto class also absent. The example remains a documented no-op stub. See `FUTURE-IMPROVEMENTS.md` §1.1 update.

---

## 3. Execution Steps

1. ✅ Download 10.7.6708 jar to local Maven cache
2. ✅ Inspect API surface: 73 Qot + 17 Trd methods (vs 27+11 in 10.5.6508)
3. ✅ Confirm 6 screen APIs all present, `getAccCashFlow` still absent
4. ✅ Bump `pom.xml` to 10.7.6708
5. ✅ `mvn clean compile` — 71 source files compile clean
6. ✅ `mvn test` — passes
7. ✅ Live smoke test: `Example00_ConnectHA` returns `serverVer=1007`, `qotLogined=true trdLogined=true`
8. ✅ Live smoke test: `Example01_MarketSnapshot` returns HK.00700 and US.AAPL
9. ✅ Live test: `Example66_StockScreen` callback wired correctly (server rejects no-filter request with retCode=2 — expected, demonstrates the call works end-to-end)
10. ✅ Update README.md, ARCHITECTURE.md, FUTURE-IMPROVEMENTS.md
11. ⏳ `mvn package -DskipTests` (shaded jar build)
12. ⏳ `git commit && git push`

---

## 4. Risk Assessment

- **Protocol compatibility:** 10.6 → 10.7 is a minor bump — fully backward-compatible. Verified via proto byte-compile and live test against OpenD 10.7.
- **SDK Java API:** All existing classes and methods unchanged. New methods are purely additive.
- **Dependencies:** Transitive deps (protobuf 3.5.1, bouncycastle 1.68) are identical.
- **OpenD:** User confirmed running "latest" (10.7.x). Live test confirmed `serverVer=1007`.
- **Risk level:** **LOW**

---

## 5. New APIs Now Covered (post-upgrade)

| API | Example | Status |
|-----|---------|--------|
| `getStockScreen` | Example66 | ✅ Newly covered |
| `getOptionScreen` | Example67 | ✅ Newly covered |
| `getWarrantScreen` | Example68 | ✅ Newly covered |
| `getTechnicalUnusual` | Example69 | ✅ Newly covered |
| `getFinancialUnusual` | Example69 | ✅ Newly covered |
| `getDerivativeUnusual` | Example69 | ✅ Newly covered |
| `getAccCashFlow` | (n/a) | ❌ Still absent in 10.7.6708 — Example49 remains a no-op stub |

---

## 6. Future Opportunities (not in this PR)

The following 46 new Qot methods and 6 new Trd methods are now available in the SDK but are not yet covered by dedicated examples:

- **Financials/research/valuation/company/shareholders/corporate actions/insiders/short interest/top brokers** — already covered collectively by Examples 58-65 in the 10.6 era
- **Option strategy family** (`getOptionQuote`, `getOptionStrategy`, `getOptionStrategyAnalysis`, `getOptionStrategySpread`) — could become Example70+
- **Trading write methods** (`placeOrder`, `placeComboOrder`, `modifyOrder`, `unlockTrade`, `subAccPush`) — already partially covered in Example34 (modifyOrder) and Example40 (push); could be expanded into dedicated examples
- **`getComboMaxTrdQtys`** — for combo orders (covered by Example33 for single orders; could be added)

These are deferred to follow-up PRs to keep this change focused.
