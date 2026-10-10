# OpenPets Android — companion

App Android que funciona como o **destino OpenPets**: exibe um pet (spritesheet)
em tela sempre ligada e recebe reações/falas dos agentes (OpenCode, MCP, etc.)
via TCP, para uso como companion em device antigo.

- Kotlin nativo, `minSdk 24` (Android 7+), render via `Canvas` (sem Compose/WebView).
- Implementa os protocolos do OpenPets sobre TCP:
  - **IPC** `openpets-ipc` v1 (porta padrão `18420`) — drop-in para o cliente/plugin OpenPets.
  - **Remote** `openpets-remote` v1 (porta padrão `18421`) — token `[A-Za-z0-9_-]`, 30 req/min.
- Foreground service + tela ligada + reinício no boot.

## Estrutura

```
openpets-android/
├── app/        # UI (Canvas pet + balão), service de rede, settings, always-on
├── protocol/   # Kotlin puro (JVM-testável): wire protocol, validators, servers TCP, mapping
└── tools/      # helpers de integração (discovery JSON, envio de reações)
```

Módulos:
- `:protocol` — sem dependências Android. Contém `IpcServer`, `RemoteServer`,
  `LineServer`, validadores, `PetReactionStateMachine`, `ReactionMapping`, `PetManifest`.
- `:app` — `PetView`/`SpeechBubbleView`/`PetStageView`, `OpenPetsService`, `BootReceiver`,
  `AppSettings`, `MainActivity`.

## Build

```bash
cd openpets-android
./gradlew :protocol:test          # testes do protocolo + integração dos servidores
./gradlew :app:assembleDebug      # gera app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17, Android SDK (platform 34, build-tools 34), `local.properties` com `sdk.dir`.

## Uso no device

1. Instale o APK (`adb install -r app-debug.apk`).
2. Abra o app: o pet aparece e o foreground service sobe (IPC 18420 + Remote 18421).
3. **Toque** no pet → reação `waving`. **Pressione e segure** → configurações
   (bind address, portas, tokens, auto-start no boot, IP do device).
4. Anote o **IP do device** e o **IPC token** / **Remote token** (botões "Copiar").

### Tela sempre ligada
O app usa `FLAG_KEEP_SCREEN_ON` e modo imersivo. Para um companion dedicado:
- Desative a otimização de bateria para o app (MIUI: "Sem restrições" / autostart).
- Opcional: fixe o app (screen pinning) ou use como "daydream"/quiosque.

## Apontando os agentes para o device

Veja [`docs/AGENT-INTEGRATION.md`](docs/AGENT-INTEGRATION.md). Resumo:

- **IPC (recomendado)**: gere um discovery JSON com `tools/write-discovery.sh` e defina
  `OPENPETS_DISCOVERY_FILE` no processo do OpenCode. Cobre o **plugin** do OpenCode e o MCP.
- **Remote**: defina `OPENPETS_REMOTE_ENDPOINT`/`OPENPETS_REMOTE_TOKEN` (suportado pelo MCP).

Teste rápido sem OpenCode:

```bash
node tools/send-reaction.mjs 192.168.0.14 18420 <ipcToken> react thinking
node tools/send-reaction.mjs 192.168.0.14 18420 <ipcToken> say "Trabalhando nisso" editing
```

## Status

- ✅ Fase 0 — scaffold + `:protocol` (validado por testes)
- ✅ Fase 1 — render Canvas (spritesheet + balão + máquina de estados)
- ✅ Fase 2 — servidores TCP IPC + Remote (testes de integração verdes)
- ✅ Fase 3 — service always-on, boot, configurações, docs
- ⏳ Verificação end-to-end no device físico (aguarda habilitar "Instalar via USB" no MIUI)
