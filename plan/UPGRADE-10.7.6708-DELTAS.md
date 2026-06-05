# Upgrade Deltas: futu-api 10.6.6608 → 10.7.6708

**Generated:** 2026-06-05
**Method:** Bytecode inspection of `futu-api-10.7.6708.jar` via `javap` + `jar tf`

---

## 1. API Surface Delta

| Category | 10.5.6508 | 10.7.6708 | New | Removed |
|----------|-----------|-----------|-----|---------|
| Qot methods (`FTAPI_Conn_Qot`) | 27 | **73** | +46 | 0 |
| Trd methods (`FTAPI_Conn_Trd`) | 11 | **17** | +6 | 0 |
| Trd read-only (get*) | 11 | 12 | +1 (`getComboMaxTrdQtys`) | 0 |
| Trd write (place/modify/unlock/sub) | 0 | 5 | +5 (place, placeCombo, modify, unlock, subAccPush) | 0 |

**No method removals** — fully backward-compatible.

### 1.1 New Qot Methods (46)

| Group | Methods |
|-------|---------|
| Fundamentals (financials) | `getFinancialsStatements`, `getFinancialsRevenueBreakdown`, `getFinancialsEarningsPriceMove`, `getFinancialsEarningsPriceHistory` |
| Research | `getResearchAnalystConsensus`, `getResearchRatingSummary`, `getResearchMorningstarReport` |
| Valuation | `getValuationDetail`, `getValuationPlateStockList` |
| Corporate actions | `getCorporateActionsDividends`, `getCorporateActionsBuybacks`, `getCorporateActionsStockSplits` |
| Shareholders | `getShareholdersOverview`, `getShareholdersHoldingChanges`, `getShareholdersHolderDetail`, `getShareholdersInstitutional` |
| Insiders | `getInsiderHolderList`, `getInsiderTradeList` |
| Company info | `getCompanyProfile`, `getCompanyExecutives`, `getCompanyExecutiveBackground`, `getCompanyOperationalEfficiency` |
| Top brokers | `getTopTenBuySellBrokers` |
| Short interest | `getDailyShortVolume`, `getShortInterest` |
| Option analytics | `getOptionVolatility`, `getOptionExerciseProbability` |
| Option quotes | `getOptionQuote` |
| Option strategy | `getOptionStrategy`, `getOptionStrategyAnalysis`, `getOptionStrategySpread` |
| **Screening** (the 6 new ones!) | `getStockScreen`, `getOptionScreen`, `getWarrantScreen`, `getTechnicalUnusual`, `getFinancialUnusual`, `getDerivativeUnusual` |

### 1.2 New Trd Methods (6)

| Method | Purpose |
|--------|---------|
| `placeOrder(TrdPlaceOrder.Request)` | Submit single-leg order |
| `placeComboOrder(TrdPlaceComboOrder.Request)` | Submit combo/conditional order |
| `modifyOrder(TrdModifyOrder.Request)` | Modify existing order |
| `unlockTrade(TrdUnlockTrade.Request)` | Unlock trade (e.g. password/biometric) |
| `subAccPush(TrdSubAccPush.Request)` | Subscribe to account push notifications |
| `getComboMaxTrdQtys(TrdGetComboMaxTrdQtys.Request)` | Max tradable quantity for combo orders |

---

## 2. New Proto Packages

### 2.1 `QotStockScreen` (new class family)

| Inner class | Purpose |
|-------------|---------|
| `Request` / `C2S` / `S2C` / `Response` | Standard envelope |
| `ScreenQuery` | OneOf filter container (simple, plate, financial, indicator, broker, option, kline-shape, featured) |
| `QuerySimpleField`, `QueryPlate`, `QueryPropertyFinancial`, `QueryPropertyBroker`, `QueryPropertyOption`, `QueryIndicatorPositional`, `QueryIndicatorPattern`, `QueryPropertyFeatured`, `QueryKlineShape` | Filter sub-queries |
| `PropertyBasic`, `PropertySimple`, `PropertyFeatured`, `PropertyIndicator` | Sort/retrieve properties |
| `Sort` | Sort spec with `Direction` + OneOf property |
| `RetrieveQuery` | Which fields to return |
| `Boundary` | Min/max for numeric filters |
| `Plate` | Plate filter |
| `StockScreenItem` | Result row: `stockId` + `resultsList` (of `RspItemResult`) |

### 2.2 `QotOptionScreen`

| Inner class | Purpose |
|-------------|---------|
| `Request` / `C2S` / `S2C` | Standard envelope |
| `C2S` fields | `marketCategoryList`, `filterList` (of `FilterGroup`), `sortList`, `pageFrom`, `pageCount`, `optionRetrieveList`, `underlyingRetrieveList` |
| `FilterGroup`, `Sort` | Filter and sort specs |

### 2.3 `QotWarrantScreen`

| Inner class | Purpose |
|-------------|---------|
| `Request` / `C2S` / `S2C` | Standard envelope |
| `C2S` fields | `marketType`, `isDelay`, `filterList` (of `ScreenGroup`), `sortList`, `onlyCount`, `pageFrom`, `pageCount` |
| `ScreenGroup`, `Sort` | Filter and sort specs |

### 2.4 `SkillWrapAPI` (new package, used for unusual activities)

| Inner class | Purpose |
|-------------|---------|
| `TechnicalUnusualReq` | `stockSymbol`, `timeRange`, `indicatorFilters` (list of strings), `languageId` |
| `TechnicalUnusualRsp` | `retType`, `retMsg`, `errCode`, `timeRange`, `content` (HTML/text blob) |
| `FinancialUnusualReq` / `FinancialUnusualRsp` | Same structure for financial metrics |
| `DerivativeUnusualReq` / `DerivativeUnusualRsp` | Same structure for derivatives |

**Note:** `*Unusual*` responses return content as a string blob (HTML/markdown), not structured data. Examples should display the content string.

### 2.5 `QotStockFilter` — UNCHANGED

Method still `stockFilter(QotStockFilter.Request)` — same class name. No renames.

### 2.6 `TrdGetAccCashFlow` — **STILL ABSENT**

Confirmed via `jar tf` and `grep` over extracted bytecode:
- ❌ No `TrdGetAccCashFlow` class in 10.7.6708
- ❌ No `getAccCashFlow` method on `FTAPI_Conn_Trd`
- ✅ `TrdCommon$AccCashInfo` data class exists (used by `TrdGetFunds`)

**Implication:** `Example49_AccCashFlow.java` cannot be rewritten to use a real method. It remains a no-op stub. This is now confirmed for **two consecutive SDK versions** (10.6.6608 and 10.7.6708).

---

## 3. Verified Compatibilities (spot checks)

| Proto class | 10.5.6508 | 10.7.6708 | Compatible |
|-------------|-----------|-----------|------------|
| `QotGetBasicQot.Request` | c2S only | c2S only | ✅ |
| `QotGetBasicQot.C2S` | `securityList`, `header` | `securityList`, `header` | ✅ |
| `QotGetSecuritySnapshot.Request` | c2S only | c2S only | ✅ |
| `FTAPI_Conn_Qot.stockFilter()` | `QotStockFilter.Request` | `QotStockFilter.Request` | ✅ |
| `FTAPI_Conn_Qot.setPriceReminder()` | n/a | `QotSetPriceReminder.Request` | ✅ (new) |
| `FTAPI_Conn_Qot.modifyUserSecurity()` | n/a | `QotModifyUserSecurity.Request` | ✅ (new) |

All Request wrappers follow the same `c2S_` field pattern → no compile breaks expected for the existing 65 examples.

---

## 4. OpenD Status (live)

| Field | Value |
|-------|-------|
| Server address | `172.18.208.88:11111` (RSA enabled) |
| **OpenD `serverVer`** | **`1007`** (i.e. OpenD 10.7.x) — captured 2026-06-05 via `Example00_ConnectHA` |
| User confirmed | running "latest" (10.7.x) as of 2026-06-05 |
| `getSecuritySnapshot` | ✅ returns HK.00700 TENCENT @ 456.6, US.AAPL @ 311.23 |
| Connection status | ✅ `qotLogined=true, trdLogined=true, connStatus=CONNECTED` |

---

## 5. Decision: `Example49_AccCashFlow`

**Cannot rewrite** — the method does not exist in 10.7.6708 (or 10.6.6608, or 10.5.6508). Options:

1. **Keep as-is** — current no-op stub, no functional change
2. **Replace with `getFlowSummary` walkthrough** — duplicates `Example35_CashFlow`
3. **Replace with documentation example** — shows protobuf request structure, doesn't call any API

**Chosen:** Option 1 (keep as-is). Update `plan/FUTURE-IMPROVEMENTS.md` §1.1 to mark "deferred indefinitely — method not present in SDK 10.5/10.6/10.7".
