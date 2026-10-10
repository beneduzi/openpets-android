# OpenPets Android × Plugins nativos — Arquitetura (Opção D: renderer remoto)

> ⚠️ **SUPERSEDIDO** por [`PLUGINS-ON-DEVICE.md`](PLUGINS-ON-DEVICE.md). O usuário rejeitou depender de
> host no PC ("proposta muito ruim"); a decisão passou a ser **rodar os plugins no próprio app**.
> Este documento fica como registro histórico da alternativa descartada.

> Status: **documento de arquitetura** (nada implementado). Decisão tomada a partir de consulta ao `@oracle`
> e da inspeção do upstream. Serve para dimensionar e decidir quando codar.

## 1. Objetivo
Fazer os **plugins nativos do OpenPets desktop** funcionarem com o celular Android como companion:
o plugin roda **sem modificação**; o celular é a **tela** (pet, balões, botões) e as **ações** (Done/Snooze/etc.)
voltam para o plugin, que executa no PC.

## 2. Restrição que define tudo
No OpenPets, plugins rodam num **sandbox (BrowserWindow)** e **o HOST renderiza a UI** — o plugin só
*descreve* bubbles/alerts/HUD/menus/panels; quem desenha é o host. Além disso, **não existe package de host
publicado** (`@open-pets/plugin-sdk` traz só tipos + test harness), e o IPC do desktop é **somente controle**
(não observa eventos de plugin nem injeta callbacks de ação).

Consequência: plugins só "funcionam de verdade" onde o **host** roda → **no PC**. Portar o host para o
Android ou reescrevê-lo headless é muito maior.

## 3. Decisão
**Opção D — manter o host Electron real no PC e adicionar o Android como renderer remoto** (fork pequeno do
`apps/desktop`). Preserva plugins **sem modificação** para um **conjunto limitado** de capacidades.
Não existe "todos os plugins + pouco esforço".

## 4. Topologia
```
PC
  Plugin JS original (sandbox Electron existente)
      ↕ bridge do SDK (permissões, quotas, callbacks)
  Serviços do host + arbitragem de bubbles
      ↕ novo adaptador de renderer remoto
  Endpoint de renderer autenticado (WSS)
      ⇅ conexão bidirecional persistente
Celular (Android)
  Renderer nativo: pet + bubbles + botões/inputs
```
- **Celular conecta (outbound) ao PC** por **WSS**. PC escuta em interface LAN/VPN; sem listener público por padrão.
- Pareamento com **credencial revogável** própria + **TLS**. Acesso fora da LAN: via VPN (relay público é fase futura).
- **Manter** `openpets-ipc` e `openpets-remote` intactos. Introduzir um **contrato de renderer novo e versionado**
  (não fingir que o IPC atual suporta UI de plugin).
- **Dono da tela / arbitragem**: política para o tráfego de agentes (reações/falas) não sobrescrever bubbles
  interativas de plugin.

## 5. Contrato do renderer (v1) — mensagens
Envelope JSON por linha sobre WSS; `type`, `version`, `id`, `ts`; ack + heartbeat.

**Renderer → Host**
- `hello` (token/pairing, device info, protocolos suportados)
- `bubble.ack` { bubbleId, revision, epoch }
- `ui.action` { bubbleId, revision, epoch, actionId, eventId }
- `ui.submit` { bubbleId, revision, epoch, formValues, eventId }
- `ui.dismissed` { bubbleId, revision, epoch, reason }
- `snapshot.request`, `ping`

**Host → Renderer**
- `ui.bubble` (upsert) { bubbleId, revision, epoch, kind: alert|transient|pinned, text, actions[], inputs[],
  hud?, media?, pin?, ttlMs, clickUrl? }
- `ui.bubble.update` / `ui.bubble.dismiss`
- `ui.alert` (variante de alerta com ações)
- `pet.*` (react/say/showMedia — reaproveita o vocabulário já implementado)
- `notify`, `audio`, `snapshot`, `pong`

## 6. Semântica de ações (o ponto crítico)
1. O PC **mantém** o handle vivo da bubble e os callbacks.
2. O celular recebe **descritores + IDs opacos** (nunca funções).
3. O celular manda `ui.action`/`ui.submit` com **identidade da bubble, actionId/valores, epoch, eventId**.
4. O PC **valida**: dono, vivacidade (bubble ainda ativa), ação anunciada, schema do input, **duplicatas**.
5. O PC roteia pelo **mesmo caminho do host** até o `onAction`/`onSubmit` original do plugin.

Regras: preservar a **ordem de registro** (ex.: Water Reminder registra `onAction` **e** `onDismissed` — trocar a
ordem muda o scheduling). Usar **ack + dedup**; **não** prometer exatamente-uma-vez entre crashes. Descartar
eventos **obsoletos** após reload do plugin/reinício do host. Ao reconectar, enviar **snapshot do estado atual**
(não replay de bubbles expiradas). **Preferir a conexão persistente a webhooks** (webhook sozinho não invoca o
callback do plugin). Distinguir **ação de plugin** (invoca o handler do plugin) de **ação de agente** (endpoint
do PC registrado) — nenhuma permite execução arbitrária de SDK/shell.

## 7. Onde cada capacidade roda
| Capacidade | Onde |
| --- | --- |
| schedule/storage/bus/config/i18n/log | **PC** (inalterado) |
| permissões/quotas/validação de pacote/SSRF | **PC** (autoritativo) |
| AI/secrets/OAuth/files/system | **PC** (rotular que afetam o PC) |
| pet speak/react/animação/scale | **telefone** (identidade lógica no PC) |
| bubbles/alerts (arbitragem, handles, lifecycle) | desenho no **telefone**; dono no **PC** |
| commands/menus/status | registro no **PC**; listas/forms no telefone (progressivo) |
| audio/notificações/TTS | **telefone** (companion); evitar playback duplicado no PC |
| movimento/física/cursor/multi-pet | **adiar / desktop-only** |
| STT (push-to-talk) | **adiar** (precisa consentimento/captura no Android) |
| panels (HTML/CSS/JS do plugin) | **desktop-only** inicialmente |

Assets validados trafegam como **bytes limitados ou referência opaca** (caminhos do PC não servem no telefone).
Publicar uma **matriz de compatibilidade** testada; plugins não suportados: bloquear ou rotular — **não** fingir sucesso.

## 8. Onde “plugar” no desktop (upstream)
Commit de referência inspecionado: `cd25bf60e2a128723c5f7bdb877b83ff28a138da`.
- **Melhor costura**: `apps/desktop/src/plugin-bubble-arbiter.ts` — interface mínima `PetBubbleSink.present(...)`
  e já expõe `handleAction` / `handleSubmit` / `handleDismissed`. **Preservar a arbitragem e trocar o sink** por um
  de rede.
- Injeção do host: `plugin-runtime.ts` (JS host, pet api, scheduler, storage, capabilities).
- Ligação SDK↔bubbles: `plugin-host-capabilities.ts`.
- **Provavelmente reutilizável**: validação de manifest/instalação, config/state/storage, scheduling, permissões/quotas,
  plumbing de callbacks, i18n, arbitragem de bubbles.
- **Acoplado ao Electron** (reimplementar/adaptar): `plugin-js-host.ts`, preload/IPC do SDK, registry de pet-windows,
  diálogos nativos, panels, adaptadores de notificação/áudio/voz, bootstrap da app.
- “Headless” aqui = **sem janela visível**, ainda Electron (hidden BrowserWindow roda Chromium; auditar dependências).
- **Correção**: a versão atual aceita **panels HTML/CSS/JS** de plugin (`plugin-panels.ts`) — nem tudo é declarativo.

## 9. Plano por fases (1 engenheiro, MVP enxuto)
| Fase | Objetivo / critério de aceite | Esforço |
| --- | --- | --- |
| 0. Spike | Fixar versões; provar que o arbiter aceita um **sink de rede** (gravação) e que uma ação volta ao **Water Reminder inalterado** | 3–5 dias |
| 1. Slice vertical | Android conecta com segurança; renderiza 1 alert sticky; **Done/Later + dismiss** chegam aos handlers originais; mudanças de storage/schedule verificadas | 1–2 sem |
| 2. MVP 2 plugins | Reminders + Water Reminder; snooze/done; assets; áudio/notificação no celular; snapshot na reconexão; rejeição de ações obsoletas; teardown do plugin | 2–3 sem |
| 3. Ampliação | menus/forms de comando, inputs/HUD/pin/update, compatibilidade, recuperação em background no Android, empacotamento e testes de segurança | 4–8 sem |

**~4–6 semanas-eng para o MVP enxuto.** Fora do MVP: criação de lembrete é **command form** (não bubble input) →
só na fase 3.

## 10. Riscos e gates de verificação
1. **Ciclo de vida**: testar ação-antes-de-dismiss, timing de subscriptions, taps duplicados, updates, expiração,
   reconexão e reload de plugin.
2. **Disponibilidade**: PC dormindo/offline = sem execução ao vivo; definir comportamento de lembrete perdido.
3. **Segurança**: preservar o sandbox e permissões por chamada; **validar eventos remotos independentemente**
   (handler interno atrás do IPC não é automaticamente seguro na rede).
4. **Limites do renderer**: 4 botões não cobrem todo o SDK (forms/pinned/HUD/panels) — rastrear, não descartar em silêncio.
5. **Manutenção do fork/versão**: fixar versões de plugin com a revisão do host testada; buscar extensão upstream
   (sink remoto) para reduzir o custo do fork.
6. **Licença**: manter avisos MIT/copyright; auditar dependências de plugins, artes, sons e marca.

## 11. Não-objetivos (inicial)
Rodar plugins no celular offline; reescrever o host headless “Electron-free”; suportar panels/STT/movimento;
acesso fora da LAN sem VPN.

## 12. Questões abertas (verificar antes de investir)
- O upstream aceitaria uma extensão oficial de “sink remoto” (evita fork)? Há issue/PR?
- Requisitos exatos de sandbox/token entre main e plugin host no runtime atual.
- Qual versão do desktop/plugin-sdk fixar (compatibilidade de manifestVersion 3).
- Estratégia de pareamento/TLS (certificado auto-assinado + fingerprint vs mTLS).
- Política de arbitragem quando agente e plugin querem a tela ao mesmo tempo.
