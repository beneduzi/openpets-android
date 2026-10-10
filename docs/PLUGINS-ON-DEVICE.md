# OpenPets Android × Plugins nativos — Arquitetura **on-device**

> Status: **documento de arquitetura** (nada implementado). Baseado em consulta ao `@oracle` (2ª avaliação).
> Decisão: **rodar os plugins no próprio app Android**, sem host/daemon no PC.
> (A alternativa "host no PC + renderer remoto" foi rejeitada e está em `PLUGINS-REMOTE-RENDERER.md`.)

## 1. Objetivo e restrição
Fazer os **plugins nativos** funcionarem com o companion **Android**, com **zero dependência de PC**:
o JS do plugin roda **no celular** (offline-capable) e a UI é **nativa** no app.

## 2. Veredito
**Sim, é viável rodar o JS original dos plugins sem modificação no Android** — desde que o plugin use o
**SDK** (`ctx.*`) e não APIs específicas de desktop/browser. A peça que falta **não é um interpretador JS**:
é a **implementação do host** (o pacote `@open-pets/plugin-sdk` traz só tipos + test harness).

**Arquitetura recomendada: QuickJS + host de compatibilidade em Kotlin + UI nativa. Sem PC.**
```
Plugin ESM inalterado → runtime JS isolado → ponte de host com permissões → serviços Kotlin + renderer nativo
```
Escolha da engine:
- **QuickJS** — melhor encaixe: pequena, suporta **ES modules**, sem JIT, com **limites de memória** e **interrupção de execução** configuráveis.
- JavaScriptCore — viável, porém exige empacotar/manter você mesmo; sem vantagem aqui.
- V8/J2V8 — integração/binário/manutenção maiores; desnecessário para lembretes.

## 3. O que precisa ser construído (o "host")
- Validação de manifest/versão; defaults de config + **formulários de config nativos**.
- **Resolução de ESM** com escopo de pacote (imports relativos e deps realmente usadas).
- **Pumping de promises/microtasks**, dispatch de eventos e globals necessários
  (a engine **não** fornece `fetch`, timers, APIs de browser nem toda a i18n sozinha).
- **`ctx` compatível com o SDK**: assinaturas, retornos, eventos, cancelamento e erros.
- **Storage por plugin**, **scheduling durável**, **lookup/interpolação/fallback de traduções**.
- **Permissões e quotas** (CPU, memória, eventos, notificações, rede, storage).
- **Ciclo de vida**: start/stop, crashes, updates e **restauração de processo**.

**Sandbox/segurança**: para os plugins curados, exponha só capacidades estreitas. Antes de aceitar
plugins de terceiros, use um **processo Android isolado + IPC intermediado** — um runtime JS sozinho **não**
é fronteira de segurança de SO. **Não** expor reflexão Java/Kotlin, FS irrestrito, nem `std/os` do QuickJS.

## 4. Classificação por namespace de `ctx`
("agora" = o app **consegue** sustentar a API por trás; a ponte ainda precisa ser feita.)
| Namespace | Classificação |
| --- | --- |
| `pet.animate/react/speak/setScale`, status básico | **agora / ponte modesta** (já temos sprites + bubbles) |
| `pets.spawn`, `pet.move/wander/physics` | **esforço** (definir coordenadas/limites/lifecycle na superfície do app) |
| `pet.followCursor` | **degradado** (seguir toque/ponteiro disponível; cursor desktop global não existe) |
| `pet.onTick` | **esforço, fidelidade só em primeiro plano** (pausar/throttle fora de foco) |
| `ui.alert` | **esforço** (render nativo + semântica Done/Snooze; botões só-URL atuais não bastam) |
| `ui.bubble/update/dismiss/pin/subscribe` | **esforço** (IDs estáveis, lifecycle, actions, subscriptions) |
| `ui.delivery/menu/panel` | **esforço, escopo inicial restrito** (delivery nativo; menus/panels schema-driven) |
| `schedule.once/every/daily/cron/at/cancel/list` | **esforço substancial; timing pode degradar** (registros duráveis + AlarmManager; não timers JS em bg) |
| `storage` | **esforço** (KV JSON por plugin, atômico, limites, eventos; recriar subscriptions no restart) |
| `bus` | **esforço** (pub/sub local com escopo/lifecycle; entrega durável é separada) |
| `notify`, `audio` | **agora / ponte modesta** (respeitar permissão/canais/DND/audio focus) |
| `voice` | **TTS quase agora; STT exige esforço + consentimento** |
| `net`/`http.fetch` | **esforço; online-only degrada offline** (broker no Kotlin, allowlist + SSRF/DNS) |
| `ai`/`secrets` | **precisa config** (provedor do usuário + credencial no Keystore; offline exige modelo local) |
| `auth` | **esforço/config** (PKCE via browser; redirect do Android pode diferir do desktop) |
| `files` | **esforço via SAF** (URI + seleção do usuário; caminhos do desktop indisponíveis) |
| `system` | **misto**; acesso a processos/janelas do PC = **impossível** (não suportar) |
| `commands` | **misto** (comandos locais do app; shell/exec do desktop não suportado) |
| `status`, `log`, `t()` | **esforço modesto** |

**Não** transformar API não suportada em "no-op silencioso": rejeitar o plugin incompatível ou **falhar explícito**.
Compatibility matrix versionada (não inferir compatibilidade só por `sdkVersion`).

## 5. Realidade de background (Doze)
Foreground service **não é** um daemon de desktop. Mínimo robusto:
1. **Persistir schedules e interações pendentes ANTES de dar ack.**
2. **AlarmManager** para deadlines visíveis ao usuário (calcular a próxima ocorrência).
3. **WorkManager** para reconciliação/trabalho adiável (mínimo 15 min; pode atrasar).
4. Na entrega do alarme: restaurar estado do plugin, notificar, finalizar rápido.
5. **Reconciliar** após reboot, mudança de hora/timezone, permissões e updates.
6. Foreground service só para função contínua **visível e compliant**.
Limitações: `SCHEDULE_EXACT_ALARM` exige acesso especial (com fallback inexato); `USE_EXACT_ALARM` não é assumível;
alarmes allow-while-idle têm rate limit (não reproduzem `every()` de alta frequência); isenção de bateria não é garantida;
**app force-stopped não entrega lembrete até ser reaberto**.
**Armadilha crítica**: schedules persistidos **não preservam closures JS** → é preciso provar que **reiniciar o plugin
inalterado reconstrói os handlers sem duplicar schedules nem perder ações**. Sem isso, compatibilidade de `schedule` não existe só guardando timestamps.

## 6. Menor caminho viável (MVP on-device)
Suportar **apenas Reminders + Water Reminder**, com os pacotes **originais fixados**. Implementar só o que eles usam:
ESM + ciclo de vida + config; `storage`; traduções; os métodos de `schedule` que usam; `ui.alert`/notificações com Done/Snooze;
`audio` e bubbles/pet que invocam; **roteamento de ações mesmo após morte do processo**.
Não implementar panels/OAuth/AI/STT/rede ampla/cron amplo antes de um plugin exigir.

### Esforço
| Marco | sem-eng |
| --- | ---: |
| Auditoria de fonte/contrato + spike de runtime com o plugin real | 1–2 |
| Host de compatibilidade estreito + fluxos em primeiro plano | 2–3 |
| Scheduling durável + restauração + ações de notificação | 3–4 |
| Permissões + testes em device + hardening | 2–3 |
| **Total** | **8–12** |
SDK amplo e produção: **~25–50+** sem-eng. Paridade literal com o desktop é **impossível**.

## 7. Híbridos que mantêm o PC fora
- **Pacotes de compatibilidade curados**: empacotar só plugins auditados que caibam no host nativo (JS original preservado).
- **Webhook opcional** só quando um plugin **precisar** falar com um agente externo (execução/agendamento continuam no celular).
- **AI em nuvem ou modelo local** opcionais (nada disso obrigatório para lembretes).
- **Matriz de compatibilidade** versionada (versão do plugin, features, offline, permissões, limitações).

## 8. De-risking (prototipar o difícil, não a animação)
Primeiro: (1) rodar o **Reminders original** no QuickJS; (2) config + traduções + scheduling + Done/Snooze;
(3) **matar o processo** entre agendar e entregar; (4) entregar/responder **travado e offline**;
(5) repetir com **exact-alarm negado**, reboot e mudança de timezone.
Depois: ship dos 2 plugins; auditar os demais um a um; só então instalação de terceiros (com isolamento).

## 9. Riscos / questões abertas
Ciclo de vida e reconstrução de handlers; Doze/exact-alarm; sandbox real (processo isolado); ESM/globals exatos
que os plugins usam; `pet.move/physics` na superfície do app; panels; manutenção da engine; licença MIT/atribuição.
Decidir a versão do `plugin-sdk`/plugins a fixar.
