# DroidLottery Miner — Guia de Implementação do App Android

## 1. Objetivo

Criar um aplicativo Android 7.0+ chamado **DroidLottery Miner**, capaz de operar em dois modos:

- **Pool Mining**: conexão direta com Binance Pool ou outra pool configurável, envio de shares e recebimento proporcional conforme a pool.
- **Lottery / Solo Mining**: conexão com uma solo pool, uso de endereço público de carteira para identificação/payout quando suportado, acompanhamento de hashrate e best difficulty e recompensa somente se um bloco válido for encontrado conforme as regras da pool.

O projeto inicial não terá backend próprio.

## 2. Stack

- Android Native
- Kotlin recomendado
- Java interoperável quando necessário
- Android NDK
- C/C++
- JNI
- CMake
- Coroutines
- Room / SQLite
- Foreground Service
- Android Keystore
- NetworkCallback
- Thermal APIs quando disponíveis

```text
minSdk = 24
Android 7.0+
```

## 3. Arquitetura

```text
Android UI
   ↓
MiningController
   ├── StratumClient
   ├── ThermalManager
   ├── RewardManager
   └── NativeMiningBridge
          ↓ JNI
       C/C++ Engines
       ├── SHA-256d
       ├── Scrypt
       ├── KAWPOW
       └── ETChash
```

## 4. Módulos

```text
droidlottery/
├── app/
├── core/
│   ├── model/
│   ├── config/
│   ├── security/
│   └── logging/
├── mining/
│   ├── controller/
│   ├── service/
│   ├── stats/
│   └── benchmark/
├── stratum/
│   ├── client/
│   ├── protocol/
│   ├── parser/
│   └── reconnect/
├── thermal/
├── native/
│   ├── sha256d/
│   ├── scrypt/
│   ├── kawpow/
│   └── etchash/
└── ui/
```

## 5. Enums principais

```kotlin
enum class MiningMode { POOL, LOTTERY_SOLO }
enum class PoolProvider { BINANCE, PUBLIC_POOL, CUSTOM }
enum class MiningAlgorithm { SHA256D, SCRYPT, KAWPOW, ETCHASH }
enum class Coin { BTC, BCH, LTC, RVN, ETC }
enum class RewardDestination { BINANCE_POOL, EXTERNAL_WALLET }
```

## 6. Modelo de profile

```kotlin
data class MiningProfile(
    val id: String,
    val name: String,
    val coin: Coin,
    val algorithm: MiningAlgorithm,
    val mode: MiningMode,
    val provider: PoolProvider,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val usernameTemplate: String,
    val password: String?,
    val rewardDestination: RewardDestination,
    val walletAddress: String?,
    val enabled: Boolean
)
```

## 7. Exemplo Binance BTC

```json
{
  "id": "binance-btc",
  "name": "Binance BTC",
  "coin": "BTC",
  "algorithm": "SHA256D",
  "mode": "POOL",
  "provider": "BINANCE",
  "host": "btc.poolbinance.com",
  "port": 1800,
  "tls": false,
  "usernameTemplate": "{miningAccount}.{worker}",
  "password": "x",
  "rewardDestination": "BINANCE_POOL",
  "walletAddress": null,
  "enabled": true
}
```

## 8. Exemplo Lottery BTC

```json
{
  "id": "publicpool-btc-solo",
  "name": "Public Pool BTC Solo",
  "coin": "BTC",
  "algorithm": "SHA256D",
  "mode": "LOTTERY_SOLO",
  "provider": "PUBLIC_POOL",
  "host": "public-pool.io",
  "port": 3333,
  "tls": false,
  "usernameTemplate": "{wallet}.{worker}",
  "password": "x",
  "rewardDestination": "EXTERNAL_WALLET",
  "walletAddress": "bc1q...",
  "enabled": true
}
```

## 9. Telas principais

### Pool

```text
DroidLottery

Device: droid01
Mode: Pool
Coin: BTC
Profile: Binance BTC

Hashrate: 28.4 MH/s
Accepted: 128
Rejected: 2
Temperature: 54 C
Battery: 81%

[ START / STOP ]
```

### Lottery

```text
LOTTERY MODE

Coin: BTC
Pool: Public Pool
Worker: droid01
Wallet: bc1q...123

Hashrate: 28.4 MH/s
Total hashes: 38,947,239,123
Best difficulty: 1.29 M
Accepted shares: 87
Block candidate: NO
```

## 10. MiningController

Responsabilidades:

```text
start(profile)
stop()
pause()
resume()
switchProfile()
handleNewJob()
handleDifficulty()
handleShareResult()
handleNetworkLoss()
handleThermalState()
```

Fluxo:

```text
start
→ validate profile
→ start foreground service
→ connect stratum
→ subscribe
→ authorize
→ receive job
→ start native engine
```

## 11. Foreground Service

Criar `MiningForegroundService`.

Notificação:

```text
DroidLottery
BTC • Pool
28.4 MH/s
54 C
```

Ações: `PAUSE` e `STOP`.

## 12. Stratum V1

O MVP deve implementar primeiro Stratum V1.

Mensagens essenciais:

```text
mining.subscribe
mining.authorize
mining.notify
mining.set_difficulty
mining.submit
```

O parser deve suportar mensagens fragmentadas, múltiplas mensagens no buffer, reconexão, invalidar jobs antigos e diferenciar accepted/rejected.

## 13. Reconexão

Backoff:

```text
2s → 5s → 10s → 20s → 30s → 60s
```

Ao reconectar:

```text
stop native workers
invalidate old job
connect
subscribe
authorize
receive fresh job
restart native workers
```

## 14. Engine SHA-256d

Prioridade máxima.

Usado por BTC e BCH.

Implementar em C/C++ via JNI.

Interface esperada:

```cpp
startSha256dJob(...)
stopMining()
getHashrate()
getTotalHashes()
getBestDifficulty()
setThreadCount(...)
setThrottle(...)
```

Pipeline:

```text
Stratum job
→ coinbase
→ merkle root
→ block header
→ nonce iteration
→ SHA256
→ SHA256
→ target comparison
```

Se `hash <= share target`, enviar share.

Se `hash <= network target`, marcar `BLOCK CANDIDATE` e submeter imediatamente.

## 15. Multithreading

Default:

```text
availableProcessors / 2
```

Distribuir nonces entre threads.

## 16. ARM

Fases:

```text
V1 C++ portátil
V2 ARM64
V3 NEON
V4 benchmark por ABI
```

ABIs:

```text
armeabi-v7a
arm64-v8a
```

## 17. Outros algoritmos

- **Scrypt**: LTC, depois do SHA-256d estar estável.
- **KAWPOW**: experimental; validar GPU/Vulkan e memória.
- **ETChash**: experimental; validar memória e compatibilidade.

Se incompatível:

```text
UNSUPPORTED ON THIS DEVICE
```

## 18. Benchmark

Persistir:

```text
device model
ABI
algorithm
hashrate
temperature
duration
```

## 19. Controle térmico

Estados:

```text
NORMAL
WARM
HOT
CRITICAL
```

Política inicial:

```text
NORMAL   → 100% do limite
WARM     → 80%
HOT      → 50%
CRITICAL → STOP
```

Defaults:

```text
mineOnlyCharging = true
minimumBattery = 40%
cpuLimit = 50%
```

## 20. Worker IDs

```text
droid01
droid02
...
droid10
```

Não usar IMEI, telefone, e-mail ou serial físico.

## 21. Segurança

O app nunca deve armazenar:

```text
seed phrase
private key
Binance password
Binance API secret
2FA secret
```

Somente endereço público quando necessário.

## 22. RewardWallet

```kotlin
data class RewardWallet(
    val label: String,
    val coin: Coin,
    val network: String,
    val address: String,
    val memo: String? = null
)
```

Validar formato, prefixo, checksum quando aplicável e rede.

## 23. Persistência

Usar:

```text
Room
+
Android Keystore
```

Tabelas:

```text
MiningProfile
MiningSession
BenchmarkResult
MinerEvent
RewardWallet
Settings
```

## 24. Importação de pools

Carregar presets via:

```text
assets/pools.json
```

Schema:

```json
{
  "version": 1,
  "pools": []
}
```

Exemplo:

```json
{
  "id": "binance-btc",
  "name": "Binance BTC",
  "coin": "BTC",
  "algorithm": "SHA256D",
  "mode": "POOL",
  "host": "btc.poolbinance.com",
  "ports": [1800, 443, 3333, 8888],
  "tls": false,
  "usernamePattern": "{miningAccount}.{worker}",
  "passwordDefault": "x",
  "rewardType": "ACCOUNT"
}
```

## 25. Custom Pool

Tela:

```text
Name
Coin
Algorithm
Mode
Host
Port
TLS
Username Pattern
Password
Reward Type

[ TEST CONNECTION ]
[ SAVE ]
```

O teste deve validar:

```text
DNS
TCP
TLS se usado
Stratum subscribe
Authorize
Latency
```

## 26. Pool failover

Cada profile pode ter endpoints `primary`, `secondary` e `tertiary`.

Ao falhar, tentar o próximo.

## 27. Presets iniciais

```text
Binance BTC
Binance BCH
Binance LTC
Binance RVN
Binance ETC
Public Pool BTC Solo
Public Pool BTC PPLNS
Custom Pool
```

## 28. Public Pool BTC Solo

```text
host = public-pool.io
port = 3333
mode = LOTTERY_SOLO
username = {wallet}.{worker}
password = x
```

TLS opcional:

```text
public-pool.io:4333
```

## 29. Public Pool BTC PPLNS

```text
host = public-pool.io
port = 13333
mode = POOL
username = {wallet}.{worker}
```

TLS:

```text
14333
```

## 30. Binance endpoints iniciais

### BTC

```text
btc.poolbinance.com:1800
sha256.poolbinance.com:443
```

### BCH

```text
bch.poolbinance.com:8888
bch.poolbinance.com:3333
bch.poolbinance.com:1800
bch.poolbinance.com:443
```

### LTC

```text
ltc.poolbinance.com:3333
ltc.poolbinance.com:443
ltc.poolbinance.com:25
```

### RVN

```text
rvn.poolbinance.com:9000
rvn.poolbinance.com:9100
rvn.poolbinance.com:9200
```

### ETC

```text
etc.poolbinance.com:1800
etc.poolbinance.com:25
etc.poolbinance.com:443
etc.poolbinance.com:3333
etc.poolbinance.com:8888
```

## 31. Roadmap

### Sprint 1
Android project, UI, Foreground Service, Room, ThermalManager e Settings.

### Sprint 2
Stratum V1, TCP, parser, reconnect e logs.

### Sprint 3
C++ SHA-256d, JNI, threads e hashrate.

### Sprint 4
Binance BTC, worker auth, shares e dashboard.

### Sprint 5
Public Pool Solo, wallet username, best difficulty e Lottery dashboard.

### Sprint 6
Custom Pool, `pools.json`, connection tester.

### Sprint 7
BCH.

### Sprint 8
Scrypt/LTC.

## 32. Testes obrigatórios

```text
unit:
SHA256
SHA256d
target
difficulty
endianness
nonce
stratum parser

integration:
subscribe
authorize
notify
submit
reconnect

android:
foreground service
battery events
network loss
thermal events
room persistence
```

## 33. Critério de sucesso V0.1

```text
[ ] instala Android 7+
[ ] inicia foreground service
[ ] conecta Binance BTC
[ ] autentica worker
[ ] recebe job
[ ] calcula SHA-256d
[ ] envia share
[ ] mostra accepted/rejected
[ ] mostra hashrate
[ ] protege temperatura
[ ] troca para Public Pool Solo
[ ] usa endereço BTC como username
[ ] recebe jobs solo
[ ] calcula best difficulty
[ ] não exige backend próprio
```

## 34. Regras para o agente de código

1. Não criar servidor.
2. Implementar Stratum V1 corretamente.
3. Manter pools configuráveis.
4. Nunca hardcodar wallet do desenvolvedor.
5. Nunca coletar seed/private key.
6. Separar engine, protocolo e UI.
7. Priorizar SHA-256d.
8. Garantir Android 7+.
9. Não habilitar algoritmo incompatível sem benchmark.
10. Toda troca de profile invalida o job anterior.
11. Todo endpoint deve ser configurável.
12. Criar testes antes de otimização ARM.
13. Validar shares reais antes de otimizar.
14. Não prometer rentabilidade.
