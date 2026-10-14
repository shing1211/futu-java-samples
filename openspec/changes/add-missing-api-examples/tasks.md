# Tasks

## 1. Scaffold and verify build baseline

- [ ] 1.1 Confirm `mvn compile` succeeds with current codebase — verify BUILD SUCCESS
- [ ] 1.2 Verify no Example70–74 files exist in `src/main/java/com/futu/sdk/examples/`
- [ ] 1.3 Confirm JAR at `~/.m2/repository/com/futunn/openapi/futu-api/10.10.7008/futu-api-10.10.7008.jar` contains all target protobuf classes

## 2. Example70 — ARK Fund Tracking

- [ ] 2.1 Create `Example70_ArkFundTracking.java` implementing `QotGetArkFundHolding`, `QotGetArkActiveTransaction`, `QotGetArkStockDynamic`
- [ ] 2.2 Use `ARKK` (ARK Innovation ETF) as primary security; US market (`market=2`)
- [ ] 2.3 Import all 3 new protobuf classes: `QotGetArkFundHolding`, `QotGetArkActiveTransaction`, `QotGetArkStockDynamic`
- [ ] 2.4 Follow `FTSPI_Qot + FTSPI_Conn` pattern from Example25; log each holding with weight and shares
- [ ] 2.5 Verify `mvn compile` succeeds — confirm no missing imports or type errors
- [ ] 2.6 Add entry to README.md examples table: `# 70 | Example70_ArkFundTracking | QotGetArkFundHolding, QotGetArkActiveTransaction, QotGetArkStockDynamic`

## 3. Example71 — Combo Order Placement

- [ ] 3.1 Create `Example71_ComboOrderPlacement.java` implementing `TrdPlaceComboOrder` + `TrdReconfirmOrder`
- [ ] 3.2 Use dual `FTAPI_Conn_Qot` + `FTAPI_Conn_Trd` pattern from Example05
- [ ] 3.3 Build a Bull Call Spread on US.NDX (buy lower-strike call, sell higher-strike call) using `QotGetOptionChain` to find legs
- [ ] 3.4 Import new protobuf classes: `TrdPlaceComboOrder`, `TrdReconfirmOrder`
- [ ] 3.5 Log full `TrdPlaceComboOrder.C2S` structure for each combo leg; confirm `comboLegs` field is accessible via reflection-free protobuf getter
- [ ] 3.6 If `TrdPlaceComboOrder$C2S` lacks a `comboLegs` setter chain matching the Python SDK's `combo_legs=[...]` pattern, document the actual field name in the example comments
- [ ] 3.7 Use SIMULATE trading environment (`TrdCommon.TrdEnv.SIMULATE`); log a warning if running in REAL
- [ ] 3.8 Verify `mvn compile` succeeds
- [ ] 3.9 Add entry to README.md: `# 71 | Example71_ComboOrderPlacement | TrdPlaceComboOrder, TrdReconfirmOrder`

## 4. Example72 — Zero-DTE Options

- [ ] 4.1 Create `Example72_ZeroDTEOptions.java` implementing `QotGetOptionZeroDteContract`, `QotGetOptionZeroDteScreener`, `QotGetOptionExpirationDate`
- [ ] 4.2 Use US options market; call `getOptionExpirationDate` first to find today's expiry
- [ ] 4.3 Import new protobuf classes: `QotGetOptionZeroDteContract`, `QotGetOptionZeroDteScreener`
- [ ] 4.4 Handle empty result case (non-expiry day) gracefully with a warning
- [ ] 4.5 Verify `mvn compile` succeeds
- [ ] 4.6 Add entry to README.md: `# 72 | Example72_ZeroDTEOptions | QotGetOptionZeroDteContract, QotGetOptionZeroDteScreener, QotGetOptionExpirationDate`

## 5. Example73 — Option Analytics

- [ ] 5.1 Create `Example73_OptionAnalytics.java` implementing `QotGetOptionQuote`, `QotGetOptionRank`, `QotGetOptionSellerScreener`, `QotGetOptionMarketStatistic`
- [ ] 5.2 Use US market index options (e.g., NDX or SPX) as test underlying
- [ ] 5.3 Import new protobuf classes: `QotGetOptionQuote`, `QotGetOptionRank`, `QotGetOptionSellerScreener`, `QotGetOptionMarketStatistic`
- [ ] 5.4 Follow `FTSPI_Qot + FTSPI_Conn` pattern from Example25
- [ ] 5.5 Verify `mvn compile` succeeds
- [ ] 5.6 Add entry to README.md: `# 73 | Example73_OptionAnalytics | QotGetOptionQuote, QotGetOptionRank, QotGetOptionSellerScreener, QotGetOptionMarketStatistic`

## 6. Example74 — Insider & Institutional Data

- [ ] 6.1 Create `Example74_InsiderInstitutional.java` implementing `QotGetInstitutionList`, `QotGetInstitutionProfile`, `QotGetInsiderHolderList`, `QotGetInsiderTradeList`
- [ ] 6.2 Use HK market (`market=1`) or US market — pick a well-known stock with active insider trading (e.g., HK.00700 or US.AAPL)
- [ ] 6.3 Import new protobuf classes: `QotGetInstitutionList`, `QotGetInstitutionProfile`, `QotGetInsiderHolderList`, `QotGetInsiderTradeList`
- [ ] 6.4 Follow `FTSPI_Qot + FTSPI_Conn` pattern from Example25
- [ ] 6.5 Verify `mvn compile` succeeds
- [ ] 6.6 Add entry to README.md: `# 74 | Example74_InsiderInstitutional | QotGetInstitutionList, QotGetInstitutionProfile, QotGetInsiderHolderList, QotGetInsiderTradeList`

## 7. GitHub Issues and PR

- [ ] 7.1 Run `mvn compile` and confirm BUILD SUCCESS with all 5 new files
- [ ] 7.2 Create GitHub issue describing the coverage gap analysis findings (84 missing APIs, 5 new examples planned) — label `enhancement`
- [ ] 7.3 Create a branch `add-missing-api-examples-70-74` off `main`
- [ ] 7.4 Commit all 5 new files + README.md updates with conventional commit message: `feat: add examples 70-74 for missing API coverage`
- [ ] 7.5 Push branch and create PR to `main` — link to the issue as `Fixes #<issue>`
- [ ] 7.6 Request review per repo's CODEOWNERS or maintainer

## 8. Archive and sync

- [ ] 8.1 After PR is merged, run `openspec archive add-missing-api-examples`
- [ ] 8.2 Run `openspec sync-specs` if any spec-level artifacts were created
