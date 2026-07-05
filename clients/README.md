# Generated client SDKs

Typed clients generated from [`docs/api/openapi.yaml`](../docs/api/openapi.yaml)
with [openapi-generator](https://openapi-generator.tech) **7.7.0**.
**Do not hand-edit** — regenerate instead (see commands below).

| Dir | Platform | Generator | Build |
|---|---|---|---|
| `typescript/` | Web / Node (fetch) | `typescript-fetch` | `npm install && npm run build` (type-checks clean under `tsc`) |
| `kotlin/` | Android / JVM | `kotlin` (jvm-okhttp4 + moshi) | `./gradlew build` |
| `swift/` | iOS / macOS | `swift5` (URLSession, async/await) | `swift build` or add the SwiftPM package |

All three expose the same surface: a `V2Api`/`V2API` (`getStatusV2`,
`submitActionV2`, `narrateV2`) and a `LegacyApi`/`LegacyAPI`, over the typed
`Envelope { type, version, payload, requestId }` models.

## Regenerate

    OG=openapi-generator-cli.jar   # 7.7.0

    java -jar $OG generate -i docs/api/openapi.yaml -g typescript-fetch -o clients/typescript
    java -jar $OG generate -i docs/api/openapi.yaml -g kotlin  -o clients/kotlin \
      --additional-properties=packageName=com.xai.dungeonmaster.client,library=jvm-okhttp4,serializationLibrary=moshi
    java -jar $OG generate -i docs/api/openapi.yaml -g swift5  -o clients/swift \
      --additional-properties=projectName=AIDungeonMasterClient,responseAs=AsyncAwait

The WebSocket message models come from `docs/api/asyncapi.yaml` via
`@asyncapi/cli generate models`. Keep both specs in sync with the `controller`
and `dto` classes whenever the contract changes, then regenerate.
