# DroidLottery Miner

**DroidLottery Miner** is a native Android Bitcoin SHA-256d miner focused on configurable Stratum V1 pool connections, a local-first dashboard, and a Solo/Lottery workflow. It is designed for Android 7.0+ devices and does not require a project-owned backend.

> **Experimental software:** mobile CPU mining is generally far less efficient than dedicated hardware. This project makes no promise of profitability, payout, accepted shares, or block discovery.

## Donate

If this project is useful, BTC donations are optional and appreciated.

```text
bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq
```

The app includes a **Donate BTC** screen with a copy button. Send BTC only, verify the full address before sending, and never share a seed phrase or private key.

## Features

- Configurable Stratum V1 TCP/TLS connection.
- `mining.subscribe`, `mining.authorize`, difficulty, job, share, and reconnect handling.
- CPU SHA-256d reference engine with configurable thread count.
- Foreground service, persistent notification, and partial wake lock for processing with the screen locked.
- Dashboard with live hashrate, graph, job/share counters, candidates, rewards status, and mining history.
- Compact **Mining Console** optimized for portrait and landscape.
- Local profile and performance settings; no project backend.
- Public BTC payout address and worker configuration for Solo/Lottery mining.

## Default profile

The first launch uses this Solo BTC profile:

| Setting | Value |
| --- | --- |
| Pool | Public Pool BTC Solo |
| Host | `public-pool.io` |
| Port | `3333` |
| Algorithm | SHA-256d |
| Mode | Lottery Solo |
| TLS | Disabled |
| Worker | `bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq.droid01` |

Check the pool's official documentation before using any endpoint. Pool availability, ports, authorization rules, and payout rules can change.

## Installation

1. Open this repository in Android Studio.
2. Use the Gradle Wrapper and JDK 17 for this project.
3. Allow Gradle to download dependencies on the first sync.
4. Connect an Android 7.0+ device, then run the `app` configuration.

Commands:

```sh
./gradlew test
./gradlew assembleDebug
```

Requirements: compile SDK 35, min SDK 24, Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, NDK 27.1, and CMake 3.22.1.

## Configure and start

1. Open **Configure Profile**.
2. Confirm the pool host and port.
3. Enter a public BTC payout address and worker name in the format required by the pool, such as `{wallet}.droid01`.
4. Set CPU threads. The default uses all processors; reduce it when heat or battery use is excessive.
5. Save, return to the dashboard, and select **Start**.

Expected connection states are `CONNECTING`, `SUBSCRIBING`, `AUTHORIZED`, and `MINING`. The hashrate graph begins after a valid job is received. Accepted and rejected counts are based on pool responses.

An accepted share is not a BTC payout. In Solo mode, a reward depends on finding a valid block and on the pool's payout policy.

## Dashboard and console

The main dashboard displays current hashrate, historical graph, jobs, accepted shares, candidate blocks, status, and thermal state. **Open Mining Console** provides a denser view that automatically switches between portrait and landscape layouts. **View Mining History** lists connection, authorization, job, and share events stored locally.

Android may still stop an app due to force-stop, critical battery/thermal conditions, OS resource management, or manufacturer battery policies. For longer sessions, set the app battery policy to **Unrestricted** in Android settings. The user can always stop mining from the app.

## Security and privacy

- Never enter a seed phrase, private key, exchange password, API secret, or 2FA secret.
- Store only public payout addresses and non-sensitive local configuration.
- Validate every pool host, port, TLS option, and payout address before mining.
- The donation address is public; it cannot spend funds or recover a wallet.

## Project layout

```text
app/
├── src/main/java/com/droidlottery/miner/
│   ├── core/       # profiles and performance settings
│   ├── mining/     # controller, service, engine, runtime stats
│   ├── stratum/    # V1 client, parser, protocol, job model
│   ├── thermal/    # thermal policy
│   ├── ui/         # dashboard, console, history, guide, donate screens
│   └── nativebridge/ # JNI benchmark bridge
├── src/main/cpp/   # CMake and native benchmark source
└── src/test/       # unit tests
```

## Testing

Run the JVM test suite with `./gradlew test`. Tests cover thermal-policy behavior, controller lifecycle, and fragmented/multiple Stratum V1 messages. Test against pools only with a public worker identity you control; never commit production secrets.

## Roadmap

- NDK-optimized SHA-256d engine and ARM/NEON benchmarking.
- Full thermal monitoring and automatic throttle/stop policy.
- Configurable pool presets and endpoint failover.
- Optional fleet view based on pool worker statistics.
- Additional algorithms only after device compatibility validation.

## Contributing

Read [AGENTS.md](AGENTS.md) before contributing. Keep protocol, engine, UI, and persistence changes separated; add tests for parsing, targets, endianness, nonce handling, and lifecycle behavior.

## License

No license has been selected yet. Do not assume permission to redistribute or commercially use the project until a license is added.
