# AI Dungeon Master — API specs

Machine-readable contracts for the API. Both validate cleanly (OpenAPI via
`openapi-spec-validator`; AsyncAPI against the official 2.6.0 JSON Schema).

- `openapi.yaml` — REST (OpenAPI 3.0.3): `/v2/status`, `/v2/action`,
  `/v2/narrate`, plus the legacy `/api/game/*` endpoints. Every v2 response is
  the typed `Envelope { type, version, payload, requestId }`.
- `asyncapi.yaml` — STOMP-over-WebSocket (AsyncAPI 2.6.0): the
  `/topic/narrative` stream and the `/app/action` command channel on `/ws`.

## View

- OpenAPI: paste into https://editor.swagger.io, or run
  `npx @redocly/cli preview-docs docs/api/openapi.yaml`.
- AsyncAPI: paste into https://studio.asyncapi.com.

## Generate client SDKs (TS / Kotlin / Swift)

REST models + clients:

    npx @openapitools/openapi-generator-cli generate \
      -i docs/api/openapi.yaml -g typescript-fetch -o clients/ts
    # swap -g for kotlin (Android) or swift5 (iOS)

WebSocket message models:

    npx @asyncapi/cli generate models typescript docs/api/asyncapi.yaml -o clients/ts-ws

Keep both specs in sync with the `controller` and `dto` classes whenever the
contract changes.
