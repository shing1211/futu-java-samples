# Phase: Cover 27 Uncovered API Methods — 9 New Examples

**Date:** 2026-05-23
**Status:** In Progress

## Overview

The Futu OpenAPI Java SDK exposes 82 API methods (67 Qot + 15 Trd). After the initial 57 examples,
27 Qot API methods remained without dedicated examples. This phase creates 9 new example files.

## Example Plan

| # | File | APIs Covered |
|---|------|-------------|
| 54 | Example54_OptionAnalytics | getOptionVolatility, getOptionExerciseProbability |
| 58 | Example58_FinancialStatements | getFinancialsStatements, getFinancialsRevenueBreakdown, getFinancialsEarningsPriceMove, getFinancialsEarningsPriceHistory |
| 59 | Example59_ResearchRatings | getResearchAnalystConsensus, getResearchRatingSummary, getResearchMorningstarReport |
| 60 | Example60_CompanyFundamentals | getCompanyProfile, getCompanyExecutives, getCompanyExecutiveBackground, getCompanyOperationalEfficiency |
| 61 | Example61_ShareholdersInsiders | getShareholdersOverview, getShareholdersHoldingChanges, getShareholdersHolderDetail, getShareholdersInstitutional, getInsiderHolderList, getInsiderTradeList |
| 62 | Example62_CorporateActions | getCorporateActionsDividends, getCorporateActionsBuybacks, getCorporateActionsStockSplits |
| 63 | Example63_ShortVolumeInterest | getDailyShortVolume, getShortInterest |
| 64 | Example64_ValuationScreener | getValuationDetail, getValuationPlateStockList |
| 65 | Example65_TopTenBrokers | getTopTenBuySellBrokers |

## Not in SDK 10.6.6608 (deferred):
getStockScreen, getOptionScreen, getWarrantScreen, getTechnicalUnusual, getFinancialUnusual, getDerivativeUnusual

## ✅ Resolved in SDK 10.7.6708 (2026-06-05):
All 6 deferred APIs are now present in `futu-api-10.7.6708` and covered by:
- `Example66_StockScreen` (getStockScreen)
- `Example67_OptionScreen` (getOptionScreen)
- `Example68_WarrantScreen` (getWarrantScreen)
- `Example69_UnusualActivity` (getTechnicalUnusual, getFinancialUnusual, getDerivativeUnusual)

See `plan/UPGRADE-10.7.6708.md` for details.

## Conventions (follow Example41_rehab.java style):
- Package: com.futu.sdk.examples
- Pattern: `implements FTSPI_Qot, FTSPI_Conn`
- Main method calls start()
- try/finally wrapping with close() in finally
- InterruptedException restores interrupt flag
- Log all response fields
- Max 20 items displayed with "... (N more)" truncation

## Updates needed after creation:
- ARCHITECTURE.md: Add files to directory listing
- README.md: Add to examples tables
- plan/FUTURE-IMPROVEMENTS.md: Mark phase complete
