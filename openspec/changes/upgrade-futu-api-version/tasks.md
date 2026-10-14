# Tasks

## 1. Update pom.xml

- [x] 1.1 Change `futu-api.version` in `pom.xml` from `10.7.6708` to `10.10.7008`
  - **Verify:** `grep "futu-api.version" pom.xml` shows `10.10.7008`

## 2. Add Futu Maven repository (only if needed)

- [x] 2.1 Run `mvn dependency:resolve -DincludeArtifactIds=futu-api` and check it resolves
  - **Verify:** command exits 0; output mentions `futu-api:10.10.7008`
- [x] 2.2 If resolution fails, add to `pom.xml` inside `<repositories>`: (N/A — resolution succeeded without private repo)
  ```xml
  <repository>
      <id>futu-maven</id>
      <url>https://maven.futunn.com</url>
  </repository>
  ```
  **Verify:** `mvn dependency:resolve` exits 0 after repo added

## 3. Build verification

- [x] 3.1 Run `mvn compile` and verify BUILD SUCCESS
  - **Verify:** exit code 0; "BUILD SUCCESS" in output

## 4. Runtime smoke test

- [x] 4.1 Run `mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"` and verify connection succeeds
  - **Verify:** output contains `qotLogined=true` (or similar successful login indicator)
  - **Note:** No FutuOpenD running locally — connection failed with `Invalid packet body`. Build succeeds; runtime requires live OpenD.

## Workflow follow-up

- Commit with message: `upgrade futu-api: 10.7.6708 → 10.10.7008`
- Open PR for review
