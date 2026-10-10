# OpenPets Android — companion

[![Android CI](https://github.com/beneduzi/openpets-android/actions/workflows/android.yml/badge.svg)](https://github.com/beneduzi/openpets-android/actions/workflows/android.yml)

App Android que funciona como **companion OpenPets**: exibe um pet (spritesheet) em tela sempre ligada e recebe
reações/falas/mídia dos agentes (OpenCode, MCP, etc.) por TCP, para uso como companion dedicado em um device antigo.

Construído do zero com base no protocolo do [OpenPets](https://github.com/OpenPetsHQ/openpets) (MIT).

## Screenshots

| Pet (Hoot) | Balão rico (markdown + HUD + botões) | Mídia | Formulário |
| :-: | :-: | :-: | :-: |
| ![hoot](docs/screenshots/pet-hoot.png) | ![rich](docs/screenshots/rich-bubble.png) | ![media](docs/screenshots/media.png) | ![form](docs/screenshots/form.png) |

| Capivara | Ações |
| :-: | :-: |
| ![capy](docs/screenshots/pet-capybara.png) | ![actions](docs/screenshots/actions.png) |

## Recursos

- **Pet** por spritesheet (8×9, 192×208) com animações por reação (idle/thinking/working/editing/running/testing/
  waiting/waving/success/error/celebrating) e **passeio** (anda para os lados quando ocioso).
- **Balões ricos**: texto com **markdown**, **HUD** (barras de progresso), **até 4 botões** de ação
  (`url` abre / `callbackUrl` faz POST), **pinado** + `id` (`pet.bubbleDismiss`), e **formulários** (inputs → POST em `submitUrl`).
- **Mídia** (`pet.showMedia`): imagem/GIF **empurrada em base64** (sem rede) ou por URL; o pet vai para a direita e a
  imagem preenche a área à esquerda.
- **Cores + rótulo por projeto** (o plugin do OpenCode manda `source`).
- **Som** (bip ou **som personalizado** via seletor), **haptics**, **voz (TTS)** e **notificação de sistema** (toggles).
- **Arrastar/seguir o dedo**, toque = wave, toque longo = Configurações.
- **Pets embutidos** (Professor Hoot, Tux, Melon Capybara, Lazy Raccoon, Mellow Duck, American Eagle),
  **importar .zip/pasta**, **catálogo online** (codex-pets.net), **`pets.install`/`install-local`**.
- **Lease** opcional (`status.leases`, "Exigir lease") e **mapeamento reação→animação** + duração do `waiting`.
- Always-on: foreground service, tela ligada, modo imersivo, auto-start no boot, ícone oficial OpenPets.

## Estrutura

```
app/        # UI (Canvas pet + balões ricos + mídia/form), service de rede, settings, always-on
protocol/   # Kotlin puro (JVM-testável): protocolo IPC/remote, validadores, servidores TCP, mapping
tools/      # helpers (discovery, send-reaction, send-media)
docs/       # integração e arquitetura (inclui plano de plugins on-device)
```

## Build

```bash
./gradlew :protocol:test        # testes (protocolo + integração dos servidores)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17, Android SDK (platform 34, build-tools 34). `local.properties` com `sdk.dir`.

## Usar no device

1. Instale o APK (`adb install -r app-debug.apk`).
2. Abra o app: o pet aparece; o foreground service sobe (IPC `18420` + Remote `18421`).
3. **Toque e segure** → Configurações (bind/portas/tokens, pet, animações, som/voz/vibração/notificação, lease).
4. Anote o **IP do device** e os **tokens** (IPC/Remote).

## Integrar com o OpenCode

Veja [`docs/AGENT-INTEGRATION.md`](docs/AGENT-INTEGRATION.md). Resumo:
- **IPC (recomendado)**: gere um discovery com `tools/write-discovery.sh` e defina `OPENPETS_DISCOVERY_FILE`
  (cobre o **plugin** do OpenCode e o MCP).
- **Remote**: `OPENPETS_REMOTE_ENDPOINT` / `OPENPETS_REMOTE_TOKEN` (MCP).

```bash
node tools/send-reaction.mjs <ip> 18420 <ipcToken> say "Rodando testes" testing
node tools/send-media.mjs    <ip> 18420 <ipcToken> ./print.png "print" 8000
```

## Protocolo (resumo)

- **IPC `openpets-ipc` v1** sobre TCP (token): `hello`, `status` (inclui `leases`), `pets.list`, `pets.install`,
  `pets.install-local`, `lease.acquire/heartbeat/release`, `pet.react`, `pet.say` (com `hud`/`inputs`/`submitUrl`/`id`/`pinned`),
  `pet.showMedia` (com `data`/`url`), `pet.bubbleDismiss`.
- **Remote `openpets-remote` v1** sobre TCP (token): `status`, `pet.react`, `pet.say`.

## Plugins nativos do desktop

Os plugins do OpenPets desktop rodam num host (Electron) que **renderiza a UI**. Rodá-los **no próprio app**
(QuickJS + host de compatibilidade + UI nativa) é viável, mas grande. Documentado em
[`docs/PLUGINS-ON-DEVICE.md`](docs/PLUGINS-ON-DEVICE.md) (e a alternativa descartada em `docs/PLUGINS-REMOTE-RENDERER.md`).

## Licença

MIT — veja [LICENSE](LICENSE). Marca/arte do OpenPets: projeto MIT do OpenPetsHQ (atribuição mantida).
