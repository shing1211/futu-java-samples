# Design

## Context

The project pins `futu-api.version` at `10.7.6708` in `pom.xml`. The latest SDK/OpenD release is `10.10.7008` (2026-08-13). Maven Central search only returns up to `9.3.5308` for `com.futunn.openapi:futu-api` — `10.x` versions live on Futu's own Maven repository (`https://maven.futunn.com`). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Bump `futu-api.version` to `10.10.7008`
- Ensure `mvn compile` passes
- Verify at least one example runs successfully

**Non-Goals:**
- Upgrading OpenD itself (backward compatibility assumed; OpenD 10.10.7008 recommended but 10.7.x should work)
- Changing any example code
- Adding new examples or capabilities

## Decisions

### 1. Target version: `10.10.7008`

**Decision:** Bump directly from `10.7.6708` → `10.10.7008` (latest, released 2026-08-13).

**Rationale:** Changelog shows no breaking API changes between these versions — only additions (SG/MY/JP market support, crypto, combo options). The SDK minor version bump is backward-compatible for existing examples.

**Alternatives considered:**
- Bump to `10.8.6808` (mid-point): Not worth partial upgrade; `10.10.7008` is the target anyway.
- Wait for stable release: `10.10.7008` is already released and stable.

### 2. Maven repository configuration

**Decision:** Add Futu's Maven repository to `pom.xml` as a `repository` entry if `10.10.7008` is not on Maven Central.

**Rationale:** Maven Central search returns `9.3.5308` as the latest for `com.futunn.openapi:futu-api`. The `10.x` versions are not publicly indexed. Futu distributes them via `https://maven.futunn.com`.

**The repository entry to add (if needed):**
```xml
<repository>
    <id>futu-maven</id>
    <url>https://maven.futunn.com</url>
</repository>
```

**Verification:** After adding the repo, run `mvn dependency:resolve` and confirm `futu-api:10.10.7008` resolves. If Maven Central already has `10.10.7008`, the repository entry is unnecessary — verify first.

### 3. Verification approach

**Decision:** Run `mvn compile` then execute `Example00_ConnectHA` as a smoke test.

**Rationale:** The examples are self-contained and do not require a funded account — `Example00_ConnectHA` connects to OpenD and calls `getGlobalState`, exercising the SDK bootstrap path without placing any trades.

## Risks / Trade-offs

[Risk: SDK version not on Maven Central] → **Mitigation:** Add `maven.futunn.com` repository to `pom.xml` before upgrading version.

[Risk: API signature changed between 10.7 and 10.10] → **Mitigation:** If `mvn compile` fails, inspect the compilation error. The changelog shows only additive changes; any breakage is expected to be minor (e.g. new required fields in protobuf builders). Fix per error.

[Risk: OpenD version mismatch] → **Mitigation:** SDK 10.10.7008 is backward-compatible with OpenD 10.7.x. Recommend upgrading OpenD to 10.10.7008 separately if connectivity issues arise.

## Migration Plan

1. Edit `pom.xml`: update `<futu-api.version>` from `10.7.6708` to `10.10.7008`
2. Add `<repository>` for `maven.futunn.com` if `mvn dependency:resolve` fails
3. Run `mvn compile` — must pass
4. Run `Example00_ConnectHA` — must connect and report `qotLogined=true`
5. Commit with message: `upgrade futu-api: 10.7.6708 → 10.10.7008`

**Rollback:** Revert the `futu-api.version` line in `pom.xml` to `10.7.6708`.
