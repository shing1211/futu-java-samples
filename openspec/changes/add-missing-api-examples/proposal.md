# Proposal

## Why

The SDK upgrade to v10.10.7008 brought 163 API surfaces, but the 70 existing examples (00–69, missing 29) cover only 79 distinct protobuf imports — leaving **84 APIs (~52%) with no Java example**. This gaps hurts discoverability, increases time-to-first-call for those APIs, and makes it impossible for developers to see working patterns for high-value features like ARK fund tracking, combo/spread order placement, zero-DTE options, and institutional investor data.

## What Changes

- Add **5 new example files** (70–74) covering the highest-value missing APIs, selected by retail demand and gap severity
- Each example follows the existing naming convention (`ExampleN_DescriptiveName.java`)
- All examples use the SDK's async SPI callback pattern, RSA auth via `Config.java`, and the HA connection helpers already established in the codebase

### New Examples

| # | File | APIs Demonstrated | Gap |
|---|------|-------------------|-----|
| 70 | `Example70_ArkFundTracking.java` | `QotGetArkFundHolding`, `QotGetArkActiveTransaction`, `QotGetArkStockDynamic` | Zero coverage — ARK is major retail interest |
| 71 | `Example71_ComboOrderPlacement.java` | `TrdPlaceComboOrder`, `TrdReconfirmOrder` | Example48 only computes legs client-side; this submits real combo orders |
| 72 | `Example72_ZeroDTEOptions.java` | `QotGetOptionZeroDteContract`, `QotGetOptionZeroDteScreener`, `QotGetOptionExpirationDate` | Zero-DTE is a growing retail strategy |
| 73 | `Example73_OptionAnalytics.java` | `QotGetOptionQuote`, `QotGetOptionRank`, `QotGetOptionSellerScreener`, `QotGetOptionMarketStatistic` | Full options analytics beyond basic chain |
| 74 | `Example74_InsiderInstitutional.java` | `QotGetInstitutionList`, `QotGetInstitutionProfile`, `QotGetInsiderHolderList`, `QotGetInsiderTradeList` | Institutional/insider data — thin existing coverage |

### Impact

- **New files**: 5 Java files in `src/main/java/com/futu/sdk/examples/`
- **New imports**: 15+ new protobuf classes not currently imported anywhere
- **Dependencies**: None — pure additive
- **Existing examples**: Unchanged
- **SDK version**: `10.10.7008` (already upgraded)
