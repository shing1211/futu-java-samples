# Design

## Context

See proposal.md — motivation is filling 84 API gaps (~52% coverage). This design covers the technical approach for the 5 new example files. All examples target SDK v10.10.7008, use the async SPI callback model, and must compile cleanly against the existing codebase.

## Goals / Non-Goals

**Goals:**
- Each new example compiles and runs (modulo OpenD availability) following existing patterns
- New examples mirror the structure and style of adjacent existing examples (e.g., Example25 for options, Example11 for trading)
- Each example is self-contained, runnable via `mvn exec:java -Dexec.mainClass=...`
- No modification to existing examples or the SDK wrapper layer

**Non-Goals:**
- Runtime verification (no live OpenD in this environment) — compile-only check
- No changes to Config.java, Helpers.java, or any shared infrastructure
- No new multi-market HA example (Example70-74 are single-connection)

## Decisions

### Pattern: Single-SPI vs Multi-SPI
**Decision:** Use the minimal SPI interface needed for each example.

| Example | Interfaces | Rationale |
|---------|-----------|-----------|
| 70 (ARK Fund) | `FTSPI_Qot, FTSPI_Conn` | Quote-only, no trading |
| 71 (Combo Order) | `FTSPI_Qot, FTSPI_Conn, FTSPI_Trd` | Needs both qot (price lookup) + trd (order) |
| 72 (Zero-DTE) | `FTSPI_Qot, FTSPI_Conn` | Quote-only |
| 73 (Option Analytics) | `FTSPI_Qot, FTSPI_Conn` | Quote-only |
| 74 (Insider/Institutional) | `FTSPI_Qot, FTSPI_Conn` | Quote-only |

### Pattern: Connection establishment
All examples follow the same connect-and-wait pattern from existing examples:
```
qot.setClientInfo("javaclient", 1);
qot.setConnSpi(this);
qot.setQotSpi(this);
if (Config.FUTU_RSA_ENABLED && !Config.RSA_KEY_CONTENT.isEmpty()) {
    qot.setRSAPrivateKey(Config.RSA_KEY_CONTENT);
}
qot.initConnect(host, port, rsaEnabled);
// Poll connected flag, 8-10s timeout
```

### Pattern: CountDownLatch for async responses
All examples use `CountDownLatch` + `AtomicInteger` for pending query tracking, matching Example48's established pattern:
```
private final CountDownLatch queryLatch = new CountDownLatch(1);
private final AtomicInteger pendingQueries = new AtomicInteger(0);
```
Each API call increments `pendingQueries`; each callback decrements it and counts down the latch.

### Pattern: Connection reuse
Examples 70, 72, 73, 74 are quote-only and reuse the `FTAPI_Conn_Qot` pattern.
Example 71 (combo orders) is trading and reuses the dual `FTAPI_Conn_Qot` + `FTAPI_Conn_Trd` pattern from Example05.

### Example 71 — Combo Order Placement
`TrdPlaceComboOrder` takes a `TrdCommon.TrdHeader` + `comboLegs` list. The example will:
1. Use `QotGetOptionChain` + `QotGetSecuritySnapshot` to pick 2 legs (e.g., Bull Call Spread: buy lower-strike call + sell higher-strike call on the same underlying)
2. Build `TrdPlaceComboOrder.C2S` with the `comboLegs` list
3. Call `trd.placeComboOrder(req)` (NOT `placeOrder`)
4. Handle `onReply_TrdPlaceComboOrder` callback

`TrdReconfirmOrder` is called after `modifyOrder` when the risk check requires re-confirmation — document this in the example.

### Example 70 — ARK Fund Tracking
Use US stock market (`QotCommon.QotMarket.QotMarket_US_Security`) with ARK Innovation ETF (`ARKK`) as the primary example. `QotGetArkFundHolding` returns a list of holdings with weight/count. `QotGetArkActiveTransaction` returns recent fund activity. No trading needed.

### Example 72 — Zero-DTE Options
`QotGetOptionZeroDteContract` and `QotGetOptionZeroDteScreener` filter for 0DTE contracts. Use `QotGetOptionExpirationDate` to find today's expiry first. Combine with `QotGetOptionQuote` for live quotes on qualifying contracts.

### Example 73 — Option Analytics
`QotGetOptionRank` ranks options by premium/discount vs theoretical. `QotGetOptionSellerScreener` finds short-interest-rich options. `QotGetOptionMarketStatistic` provides IV/HV data. These complement Example25 (basic chain) and Example54 (basic analytics).

### Example 74 — Insider/Institutional Data
`QotGetInstitutionList` + `QotGetInstitutionProfile` for institutional investors. `QotGetInsiderHolderList` + `QotGetInsiderTradeList` for insider activity. Use HK or US market securities as test cases.

## Risks / Trade-offs

[Risk] `TrdPlaceComboOrder` API surface is unverified against live OpenD — the exact field names for combo legs may differ from the protobuf generated code. **Mitigation:** Use `jar tf` on the compiled JAR to inspect `TrdPlaceComboOrder$C2S` inner class fields before writing the example.

[Risk] ARK Fund APIs (`QotGetArkFundHolding`) may require US market subscription. **Mitigation:** Use `getSecuritySnapshot` (no subscription needed) as a fallback for price data.

[Risk] Zero-DTE contracts only exist on expiry days — example may return empty results mid-week. **Mitigation:** Handle empty results gracefully and log a warning with the next expiry date.

## Open Questions

1. **Combo leg order side**: For a Bull Call Spread, does the API expect `BUY` for the long call and `BUY` or `SELL` for the short call (both sides are BUY from the user's perspective)? Check `TrdCommon.TrdSide` enum values and `TrdPlaceComboOrder` proto definition. → Defer to implementation phase, verify against JAR.
2. **ARK API auth**: Does `QotGetArkFundHolding` require any special data permissions beyond normal US quote subscription? → Assume standard US quote subscription is sufficient; document if 403 is returned.
3. **Example numbering**: Confirm no existing Example70-74 files already exist in the repo. → Verify before writing tasks.

## Migration Plan

N/A — pure additive. No deployment, no rollback needed.

## Dependencies

- `pom.xml` already has SDK v10.10.7008
- `Config.java` already handles RSA key loading
- `Helpers.java` already provides `sleep()` and `toHex()`
- No new dependencies
