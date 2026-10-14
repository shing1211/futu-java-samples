# Proposal

## Why

The `futu-api.version` is pinned at `10.7.6708` (released ~2026-06-04). The latest FutuOpenD/Java SDK is **10.10.7008** (released 2026-08-13), available on Futu's Maven repository. Upgrading brings bug fixes, new market support (SG, MY, JP equities), crypto trading, combo options strategies, and improved rate limits.

## What Changes

- Update `futu-api.version` in `pom.xml` from `10.7.6708` → `10.10.7008`
- Add Futu Maven repository to `pom.xml` if `10.10.7008` is not on Maven Central (expected — `10.x` versions are not indexed by Maven Central search; they live on `https://maven.futunn.com`)
- Verify `mvn compile` passes with the new SDK
- Run at least one example end-to-end (e.g. `Example00_ConnectHA`) to confirm connectivity

## Capabilities

### New Capabilities

_(none — pure dependency upgrade)_

### Modified Capabilities

_(none)_

## Impact

- **Maven dependency**: `com.futunn.openapi:futu-api:10.10.7008` via Futu Maven repo
- **Build**: `mvn compile` must pass
- **Runtime**: Examples connect to FutuOpenD `:11111` — no OpenD upgrade is required by this change (SDK 10.10.7008 is backward-compatible with OpenD 10.7.x, but the latest OpenD 10.10.7008 is recommended)
- **Risk**: Low — this is a point-version bump with no API signature changes documented in the changelog; examples remain unchanged
