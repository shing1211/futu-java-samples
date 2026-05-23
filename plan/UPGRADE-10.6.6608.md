# Upgrade Plan: futu-api 10.5.6508 → 10.6.6608

**Date:** 2026-05-23  
**Target:** `com.futunn.openapi:futu-api:10.6.6608` (Maven Central)  
**OpenD Requirement:** FutuOpenD ≥ 10.6.x  

---

## 1. Summary

Upgrade the Java samples project from futu-api **10.5.6508** to **10.6.6608**.  
The change is limited to the version property in `pom.xml`; no API signatures are expected to break within the 10.x major line.

---

## 2. Changes Required

### 2.1 pom.xml (line 20)

```xml
<futu-api.version>10.6.6608</futu-api.version>
```

### 2.2 Existing MD files to update

| File | What to update |
|------|----------------|
| `README.md` | SDK version badge, troubleshooting section (lines 7, 376, 381) |
| `ARCHITECTURE.md` | Overview section version reference (line 5, 11) |

### 2.3 Version-dependent source comments (to verify)

| File | Note |
|------|------|
| `Example49_AccCashFlow.java` | `getAccCashFlow` may return "Unknown protocol id" |
| `Example48_OptionsStrategy.java` | `getBasic()` does not have `getCurPrice()` |

These may be resolved in 10.6 — verify after compilation.

---

## 3. Execution Steps

1. Update version in `pom.xml`
2. Run `mvn clean compile` — verify 57 examples compile
3. Run `mvn test` — verify tests pass
4. Check for deprecation warnings
5. Verify Example48/Example49 comments against new SDK
6. Update `README.md` and `ARCHITECTURE.md`
7. Commit and push

---

## 4. Risk Assessment

- **Protocol compatibility:** 10.5 → 10.6 is a minor version bump within the 10.x major line — protobuf field additions but no breaking changes expected
- **SDK Java API:** Core classes (`FTAPI_Conn_Qot`, `FTAPI_Conn_Trd`, `FTSPI_*` interfaces) unchanged
- **Dependencies:** Transitive deps (protobuf 3.5.1, bouncycastle 1.68) are identical
- **Risk level:** LOW
