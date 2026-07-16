# AI Dungeon Master — API specs

Machine-readable contracts for the API. Both validate cleanly (OpenAPI via
`openapi-spec-validator`; AsyncAPI against the official 2.6.0 JSON Schema).

- `openapi.yaml` — REST (OpenAPI 3.0.3). Every v2 response is the typed
  `Envelope { type, version, payload, requestId }`. Endpoint groups: game
  (`/v2/status`, `/v2/action`, `/v2/narrate`), sessions (`/v2/session`,
  `/v2/session/me`), catalog + mod browser (`/v2/catalog`,
  `/v2/catalog/packs/{id}/enable|disable`), entitlements (`/v2/entitlements`,
  `/v2/entitlements/verify`), plus the legacy `/api/game/*` API.
- `asyncapi.yaml` — STOMP-over-WebSocket (AsyncAPI 2.6.0): the
  `/topic/narrative` stream and the `/app/action` command channel on `/ws`.

## View

- OpenAPI: paste into https://editor.swagger.io, or run
  `npx @redocly/cli preview-docs docs/api/openapi.yaml`.
- AsyncAPI: paste into https://studio.asyncapi.com.

## Generate client SDKs

The TypeScript, Kotlin, and Swift REST clients are generated from `openapi.yaml`
with openapi-generator 7.7.0 — see [`clients/README.md`](../../clients/README.md)
for the exact commands. WebSocket message models come from `asyncapi.yaml` via
`@asyncapi/cli generate models`.

Keep both specs in sync with the `controller` and `dto` classes whenever the
contract changes, then regenerate.
