# Generated client SDKs

Typed clients generated from [`docs/api/openapi.yaml`](../docs/api/openapi.yaml).
**Do not hand-edit** — regenerate instead:

    java -jar openapi-generator-cli.jar generate \
      -i docs/api/openapi.yaml -g typescript-fetch -o clients/typescript

- `typescript/` — TypeScript (fetch) client. Type-checks clean under `tsc`.
  Usage: `cd clients/typescript && npm install && npm run build`.

Android (Kotlin) and iOS (Swift) clients come from the same spec with
`-g kotlin` / `-g swift5`. The WebSocket message models come from
`docs/api/asyncapi.yaml` via `@asyncapi/cli generate models`.
