# futu-java-samples

Java samples for [Futu OpenAPI](https://openapi.futunn.com/) — `com.futunn.openapi:futu-api:10.5.6508` Maven SDK.

## Overview

Mirrors the structure and examples of [futu-python-samples](https://github.com/shing1211/futu-python-samples) in Java.
Each example is a standalone Java file that can be run independently via Maven.

**Note:** The official Futu Java SDK uses a callback-driven async model which differs significantly from the synchronous Python SDK.
This project ports the examples while adapting to the Java SDK's native patterns.

## Project Structure

```
futu-java-samples/
├── pom.xml                          # Maven build (Java 25, UTF-8)
├── .env.example                     # Environment config template
├── .gitignore
├── LICENSE
└── src/
    └── main/
        └── java/
            └── com/futu/sdk/examples/
                ├── Config.java               # .env configuration loader
                ├── BaseExample.java           # Base class for examples (connection management)
                ├── Example00_ConnectHA.java   # HA gateway selection + connection demo
                ├── Example01_MarketSnapshot.java  # Market snapshot (get_market_snapshot)
                └── ... (more examples coming)
```

## Quick Start

### 1. Prerequisites
- Java 25 (OpenJDK)
- Maven 3.6+
- [FutuOpenD](https://openapi.futunn.com/futu-api-doc/quick/opend-base.html) running on `127.0.0.1:11111`

### 2. Clone and Build

```bash
git clone https://github.com/shing1211/futu-java-samples
cd futu-java-samples
mvn compile
```

### 3. Run Examples

```bash
# Example 00 - HA Gateway Selection & Connect
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example00_ConnectHA"

# Example 01 - Market Snapshot
mvn compile exec:java -Dexec.mainClass="com.futu.sdk.examples.Example01_MarketSnapshot"
```

### 4. Configure Connection

Copy `.env.example` to `.env` and adjust:

```bash
cp .env.example .env
# Edit .env with your OpenD host/port/RSA settings
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FUTU_OPEND_HOST` | `127.0.0.1` | OpenD host |
| `FUTU_OPEND_PORT` | `11111` | OpenD port |
| `FUTU_RSA_ENABLED` | `false` | Enable RSA encryption |
| `FUTU_RSA_KEY_PATH` | `/etc/futu/keys/private_key.pem` | Path to RSA private key |
| `FUTU_SECURITY_FIRM` | `FUTU HK` | Security firm identifier |
| `FUTU_OPEND_HOSTS` | (empty) | Comma-separated `host:port:is_rsa` for HA mode |

## Examples

| # | Example | Description |
|---|---------|-------------|
| 00 | `Example00_ConnectHA` | HA gateway selection, parallel TCP probe, auto-fallback |
| 01 | `Example01_MarketSnapshot` | Market snapshot for all stocks (HK, US, SZ, SH) |

More examples coming — matching the numbering of futu-python-samples.

## Maven Dependency

```xml
<dependency>
    <groupId>com.futunn.openapi</groupId>
    <artifactId>futu-api</artifactId>
    <version>10.5.6508</version>
</dependency>
```

## Contributing

1. Fork the repo
2. Create a branch (`git checkout -b example-XX-description`)
3. Run tests (`mvn test`)
4. Commit (`git commit -m 'Add example-XX: description'`)
5. Push (`git push origin example-XX`)
6. Open a Pull Request

## License

MIT — see [LICENSE](LICENSE)