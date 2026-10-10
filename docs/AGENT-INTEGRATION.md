# Integrando agentes com o OpenPets Android

O app Android é o **destino** OpenPets. Os agentes (OpenCode plugin, MCP `openpets`,
CLI `@open-pets/cli`) precisam apontar para o IP do device em vez do app desktop.

## 1. IPC (recomendado — cobre plugin e MCP)

O cliente OpenPets localiza o destino por um arquivo de *discovery*. Basta substituí-lo
por um que aponte via TCP para o Android:

```json
{
  "protocol": "openpets-ipc",
  "protocolVersion": 1,
  "endpoint": "tcp://192.168.0.14:18420",
  "token": "<IPC token mostrado nas configurações do app>",
  "appVersion": "0.1.0",
  "pid": 12345,
  "platform": "linux"
}
```

Regras (validadas pelo próprio cliente):
- `endpoint` TCP só aceita IPv4 **privado/local** (192.168.x, 10.x, 172.16-31.x, 169.254.x, 127.x).
- `token` com 16..256 caracteres.
- `platform` deve ser igual ao do processo consumidor (`linux` aqui; `darwin`/`win32` conforme o SO).
- `pid` inteiro > 0.

Gere o arquivo com o helper:

```bash
tools/write-discovery.sh <DEVICE_IP> <IPC_TOKEN> [IPC_PORT] [OUT_PATH]
# padrão OUT_PATH: ~/.config/opencode/openpets-discovery.json
```

### 1a. MCP `openpets` (via opencode.jsonc)

Adicione o env ao servidor MCP em `~/.config/opencode/opencode.jsonc`:

```jsonc
"openpets": {
  "type": "local",
  "command": ["npx", "-y", "@open-pets/cli@4.0.0", "mcp", "--pet", "default"],
  "environment": {
    "OPENPETS_DISCOVERY_FILE": "/home/gustavo/.config/opencode/openpets-discovery.json"
  },
  "enabled": true
}
```

### 1b. Plugin do OpenCode (`@open-pets/opencode`)

O plugin roda dentro do processo do OpenCode e usa o mesmo discovery. Exporte a variável
antes de subir o OpenCode (shell profile, `.env`, ou wrapper):

```bash
export OPENPETS_DISCOVERY_FILE="$HOME/.config/opencode/openpets-discovery.json"
```

> Observação: o plugin OpenPets usa somente o modo *discovery/local* (o desktop não faz
> push para viewers). Por isso o caminho via IPC acima é o único que cobre o plugin.

## 2. Remote (MCP/CLI)

O cliente também suporta modo remote por env (o **plugin não** suporta):

```bash
export OPENPETS_REMOTE_ENDPOINT="tcp://192.168.0.14:18421"
export OPENPETS_REMOTE_TOKEN="<Remote token mostrado nas configurações>"
export OPENPETS_REMOTE_CLIENT_ID="android-companion"   # opcional
```

## 3. Teste

```bash
# status via CLI (usa discovery do path padrão OU o modo remote por env)
OPENPETS_DISCOVERY_FILE=~/.config/opencode/openpets-discovery.json \
  npx -y @open-pets/cli@4.0.0 status

# reação direta (sem OpenCode)
node tools/send-reaction.mjs 192.168.0.14 18420 <IPC_TOKEN> react editing
node tools/send-reaction.mjs 192.168.0.14 18420 <IPC_TOKEN> say "Rodando testes" testing

# mídia: o app NAO baixa de URL; os bytes são empurrados (base64) pelo cliente
node tools/send-media.mjs 192.168.0.14 18420 <IPC_TOKEN> ./print.png "print do app" 8000
```

### Mídia (`pet.showMedia`)
- Params: `data` (imagem em **base64**, empurrada — preferido) OU `url` (http/https, o app baixa),
  `message` (legenda), `reaction`, `durationMs` (1–30 s, padrão 8 s), `clickUrl`.
- Tipos: png/jpg/jpeg/webp/gif. Limite ~10 MB. O app só precisa da conexão IPC (não precisa de rede).
- `clickUrl` (https/URL absoluta) abre no navegador ao tocar na imagem.

## Mapa de reações → animação

`idle, thinking, working, editing, running, testing, waiting, waving, success, error, celebrating`

| reação | animação |
| --- | --- |
| idle | idle |
| thinking | review |
| working / editing / running | running |
| testing / waiting | waiting |
| waving | waving |
| success / celebrating | jumping |
| error | failed |
