# V2Api

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**cancelMarketplaceInstallJobV2**](V2Api.md#cancelMarketplaceInstallJobV2) | **DELETE** /v2/marketplace/jobs/{jobId} | Cancel an async marketplace install. |
| [**createSessionV2**](V2Api.md#createSessionV2) | **POST** /v2/session | Mint a guest player session and JWT. |
| [**deleteSessionV2**](V2Api.md#deleteSessionV2) | **DELETE** /v2/session | Explicit logout — drop session, pack prefs, and live engine. |
| [**disablePackV2**](V2Api.md#disablePackV2) | **POST** /v2/catalog/packs/{id}/disable | Disable a content pack; returns the updated catalog. |
| [**enablePackV2**](V2Api.md#enablePackV2) | **POST** /v2/catalog/packs/{id}/enable | Enable a content pack; returns the updated catalog. |
| [**getCatalogV2**](V2Api.md#getCatalogV2) | **GET** /v2/catalog | Installed content packs and registered plugins (mod browser). |
| [**getHealthV2**](V2Api.md#getHealthV2) | **GET** /v2/health | Health envelope (lean public; detail with ops token). |
| [**getMarketplaceInstallJobV2**](V2Api.md#getMarketplaceInstallJobV2) | **GET** /v2/marketplace/jobs/{jobId} | Poll async marketplace install progress. |
| [**getMarketplacePackV2**](V2Api.md#getMarketplacePackV2) | **GET** /v2/marketplace/{id} | Marketplace pack detail. |
| [**getSessionMeV2**](V2Api.md#getSessionMeV2) | **GET** /v2/session/me | Echo the authenticated session (no token reflected). |
| [**getStatusV2**](V2Api.md#getStatusV2) | **GET** /v2/status | Current game status as a typed envelope. |
| [**installMarketplacePackV2**](V2Api.md#installMarketplacePackV2) | **POST** /v2/marketplace/{id}/install | Install a marketplace pack into the live catalog. |
| [**listEntitlementsV2**](V2Api.md#listEntitlementsV2) | **GET** /v2/entitlements | List the caller&#39;s owned products. |
| [**listMarketplaceV2**](V2Api.md#listMarketplaceV2) | **GET** /v2/marketplace | List local marketplace content packs. |
| [**listStorefrontsV2**](V2Api.md#listStorefrontsV2) | **GET** /v2/entitlements/storefronts | List registered storefronts and live/sandbox mode. |
| [**loadGameV2**](V2Api.md#loadGameV2) | **POST** /v2/load | Restore the caller&#39;s game engine from its save file. |
| [**narrateV2**](V2Api.md#narrateV2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider. |
| [**resetGameV2**](V2Api.md#resetGameV2) | **POST** /v2/reset | Start a fresh engine for the caller (new party/quest). |
| [**saveGameV2**](V2Api.md#saveGameV2) | **POST** /v2/save | Persist the caller&#39;s game engine to a session-scoped save file. |
| [**submitActionV2**](V2Api.md#submitActionV2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope. |
| [**uploadPackV2**](V2Api.md#uploadPackV2) | **POST** /v2/catalog/packs | Upload and install a content-pack zip at runtime; returns the updated catalog. |
| [**verifyReceiptV2**](V2Api.md#verifyReceiptV2) | **POST** /v2/entitlements/verify | Validate a purchase receipt via its storefront and grant the entitlement. |


<a id="cancelMarketplaceInstallJobV2"></a>
# **cancelMarketplaceInstallJobV2**
> MarketplaceInstallJobEnvelope cancelMarketplaceInstallJobV2(jobId, xRequestId)

Cancel an async marketplace install.

Same ownership ACL as poll — only the owning session may cancel. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val jobId : kotlin.String = jobId_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : MarketplaceInstallJobEnvelope = apiInstance.cancelMarketplaceInstallJobV2(jobId, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#cancelMarketplaceInstallJobV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#cancelMarketplaceInstallJobV2")
    e.printStackTrace()
}
```

### Parameters
| **jobId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**MarketplaceInstallJobEnvelope**](MarketplaceInstallJobEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="createSessionV2"></a>
# **createSessionV2**
> SessionEnvelope createSessionV2(xRequestId, sessionRequest)

Mint a guest player session and JWT.

Public endpoint. Returns a session id plus a Bearer token used on all subsequent &#x60;/v2/_*&#x60; calls (and as a STOMP CONNECT header for WebSocket). When multi-player isolation is enabled on the server, each session gets its own game engine. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val sessionRequest : SessionRequest =  // SessionRequest | 
try {
    val result : SessionEnvelope = apiInstance.createSessionV2(xRequestId, sessionRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#createSessionV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#createSessionV2")
    e.printStackTrace()
}
```

### Parameters
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **sessionRequest** | [**SessionRequest**](SessionRequest.md)|  | [optional] |

### Return type

[**SessionEnvelope**](SessionEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="deleteSessionV2"></a>
# **deleteSessionV2**
> DeleteSessionV2200Response deleteSessionV2(xRequestId)

Explicit logout — drop session, pack prefs, and live engine.

Requires a valid Bearer token. Clears session identity, session-scoped pack overrides, and the live game engine. Clients should discard the token afterward. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : DeleteSessionV2200Response = apiInstance.deleteSessionV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#deleteSessionV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#deleteSessionV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**DeleteSessionV2200Response**](DeleteSessionV2200Response.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="disablePackV2"></a>
# **disablePackV2**
> CatalogEnvelope disablePackV2(id, xRequestId)

Disable a content pack; returns the updated catalog.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val id : kotlin.String = id_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : CatalogEnvelope = apiInstance.disablePackV2(id, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#disablePackV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#disablePackV2")
    e.printStackTrace()
}
```

### Parameters
| **id** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="enablePackV2"></a>
# **enablePackV2**
> CatalogEnvelope enablePackV2(id, xRequestId)

Enable a content pack; returns the updated catalog.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val id : kotlin.String = id_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : CatalogEnvelope = apiInstance.enablePackV2(id, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#enablePackV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#enablePackV2")
    e.printStackTrace()
}
```

### Parameters
| **id** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getCatalogV2"></a>
# **getCatalogV2**
> CatalogEnvelope getCatalogV2(xRequestId)

Installed content packs and registered plugins (mod browser).

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : CatalogEnvelope = apiInstance.getCatalogV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getCatalogV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getCatalogV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getHealthV2"></a>
# **getHealthV2**
> HealthEnvelope getHealthV2(xRequestId, xMetricsToken, xAdminToken)

Health envelope (lean public; detail with ops token).

Versioned envelope. Unauthenticated callers get &#x60;status&#x60;, &#x60;uptimeSeconds&#x60;, and &#x60;detail: false&#x60;. With &#x60;X-Metrics-Token&#x60; or &#x60;X-Admin-Token&#x60;, includes sessions, engines, dependencies, memory, and &#x60;detail: true&#x60;. Excluded from JWT enforcement. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val xMetricsToken : kotlin.String = xMetricsToken_example // kotlin.String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields. 
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail. 
try {
    val result : HealthEnvelope = apiInstance.getHealthV2(xRequestId, xMetricsToken, xAdminToken)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getHealthV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getHealthV2")
    e.printStackTrace()
}
```

### Parameters
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| **xMetricsToken** | **kotlin.String**| Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xAdminToken** | **kotlin.String**| Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] |

### Return type

[**HealthEnvelope**](HealthEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getMarketplaceInstallJobV2"></a>
# **getMarketplaceInstallJobV2**
> MarketplaceInstallJobEnvelope getMarketplaceInstallJobV2(jobId, xRequestId)

Poll async marketplace install progress.

Jobs are bound to the session that started them (&#x60;POST …/install?async&#x3D;true&#x60;). Other sessions receive **403**. Legacy rows with no owner remain open. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val jobId : kotlin.String = jobId_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : MarketplaceInstallJobEnvelope = apiInstance.getMarketplaceInstallJobV2(jobId, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getMarketplaceInstallJobV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getMarketplaceInstallJobV2")
    e.printStackTrace()
}
```

### Parameters
| **jobId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**MarketplaceInstallJobEnvelope**](MarketplaceInstallJobEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getMarketplacePackV2"></a>
# **getMarketplacePackV2**
> MarketplacePackEnvelope getMarketplacePackV2(id, xRequestId)

Marketplace pack detail.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val id : kotlin.String = id_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : MarketplacePackEnvelope = apiInstance.getMarketplacePackV2(id, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getMarketplacePackV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getMarketplacePackV2")
    e.printStackTrace()
}
```

### Parameters
| **id** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**MarketplacePackEnvelope**](MarketplacePackEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getSessionMeV2"></a>
# **getSessionMeV2**
> SessionEnvelope getSessionMeV2(xRequestId)

Echo the authenticated session (no token reflected).

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : SessionEnvelope = apiInstance.getSessionMeV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getSessionMeV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getSessionMeV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**SessionEnvelope**](SessionEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getStatusV2"></a>
# **getStatusV2**
> GameStatusEnvelope getStatusV2(xRequestId)

Current game status as a typed envelope.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : GameStatusEnvelope = apiInstance.getStatusV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#getStatusV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#getStatusV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="installMarketplacePackV2"></a>
# **installMarketplacePackV2**
> installMarketplacePackV2(id, xRequestId, async)

Install a marketplace pack into the live catalog.

Sync by default. Pass &#x60;async&#x3D;true&#x60; for background download with progress (&#x60;GET /v2/marketplace/jobs/{jobId}&#x60;). Async jobs bind to the caller session for poll/cancel ACL. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val id : kotlin.String = id_example // kotlin.String | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val async : kotlin.Boolean = true // kotlin.Boolean | When true, returns 202 + job id for progress polling.
try {
    apiInstance.installMarketplacePackV2(id, xRequestId, async)
} catch (e: ClientException) {
    println("4xx response calling V2Api#installMarketplacePackV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#installMarketplacePackV2")
    e.printStackTrace()
}
```

### Parameters
| **id** | **kotlin.String**|  | |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **async** | **kotlin.Boolean**| When true, returns 202 + job id for progress polling. | [optional] [default to false] |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="listEntitlementsV2"></a>
# **listEntitlementsV2**
> EntitlementEnvelope listEntitlementsV2(xRequestId)

List the caller&#39;s owned products.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : EntitlementEnvelope = apiInstance.listEntitlementsV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#listEntitlementsV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#listEntitlementsV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**EntitlementEnvelope**](EntitlementEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="listMarketplaceV2"></a>
# **listMarketplaceV2**
> MarketplaceEnvelope listMarketplaceV2(xRequestId, q)

List local marketplace content packs.

Discovers local packs under &#x60;game.content.packs.dir&#x60; plus optional remote index (&#x60;game.marketplace.remote-url&#x60;) with install/enabled status from the live catalog. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val q : kotlin.String = q_example // kotlin.String | Filter by id, name, or description
try {
    val result : MarketplaceEnvelope = apiInstance.listMarketplaceV2(xRequestId, q)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#listMarketplaceV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#listMarketplaceV2")
    e.printStackTrace()
}
```

### Parameters
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **q** | **kotlin.String**| Filter by id, name, or description | [optional] |

### Return type

[**MarketplaceEnvelope**](MarketplaceEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="listStorefrontsV2"></a>
# **listStorefrontsV2**
> listStorefrontsV2()

List registered storefronts and live/sandbox mode.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
try {
    apiInstance.listStorefrontsV2()
} catch (e: ClientException) {
    println("4xx response calling V2Api#listStorefrontsV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#listStorefrontsV2")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="loadGameV2"></a>
# **loadGameV2**
> GameStatusEnvelope loadGameV2(xRequestId)

Restore the caller&#39;s game engine from its save file.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : GameStatusEnvelope = apiInstance.loadGameV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#loadGameV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#loadGameV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="narrateV2"></a>
# **narrateV2**
> NarrativeEnvelope narrateV2(xRequestId, narrateRequest)

Generate a dungeon-master narration via the active LLM provider.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val narrateRequest : NarrateRequest =  // NarrateRequest | 
try {
    val result : NarrativeEnvelope = apiInstance.narrateV2(xRequestId, narrateRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#narrateV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#narrateV2")
    e.printStackTrace()
}
```

### Parameters
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **narrateRequest** | [**NarrateRequest**](NarrateRequest.md)|  | [optional] |

### Return type

[**NarrativeEnvelope**](NarrativeEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="resetGameV2"></a>
# **resetGameV2**
> GameStatusEnvelope resetGameV2(xRequestId)

Start a fresh engine for the caller (new party/quest).

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : GameStatusEnvelope = apiInstance.resetGameV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#resetGameV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#resetGameV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="saveGameV2"></a>
# **saveGameV2**
> GameSaveEnvelope saveGameV2(xRequestId)

Persist the caller&#39;s game engine to a session-scoped save file.

Authenticated callers save under &#x60;game.saves.dir/{sessionId}.json&#x60;. Unauthenticated callers share the process-default engine and save as &#x60;default.json&#x60;. Each authenticated session has its own engine instance. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : GameSaveEnvelope = apiInstance.saveGameV2(xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#saveGameV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#saveGameV2")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**GameSaveEnvelope**](GameSaveEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="submitActionV2"></a>
# **submitActionV2**
> GameStatusEnvelope submitActionV2(actionRequest, xRequestId)

Apply a choice; returns the updated game status envelope.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val actionRequest : ActionRequest =  // ActionRequest | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : GameStatusEnvelope = apiInstance.submitActionV2(actionRequest, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#submitActionV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#submitActionV2")
    e.printStackTrace()
}
```

### Parameters
| **actionRequest** | [**ActionRequest**](ActionRequest.md)|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="uploadPackV2"></a>
# **uploadPackV2**
> CatalogEnvelope uploadPackV2(file, xRequestId, xAdminToken, replace)

Upload and install a content-pack zip at runtime; returns the updated catalog.

Multipart zip install. In production, &#x60;game.catalog.upload.require-admin&#x3D;true&#x60; so callers must send &#x60;X-Admin-Token&#x60; (current or previous during rotation). When &#x60;game.catalog.upload.enabled&#x3D;false&#x60;, always **403**. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val file : java.io.File = BINARY_DATA_HERE // java.io.File | Pack zip — pack.yaml plus optional items/, monsters/, strings/, quests/, campaigns/, npcs/, factions/. Pure data; code-bearing mods use the plugin loader instead.
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail. 
val replace : kotlin.Boolean = true // kotlin.Boolean | Overwrite an already-installed pack with the same id.
try {
    val result : CatalogEnvelope = apiInstance.uploadPackV2(file, xRequestId, xAdminToken, replace)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#uploadPackV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#uploadPackV2")
    e.printStackTrace()
}
```

### Parameters
| **file** | **java.io.File**| Pack zip — pack.yaml plus optional items/, monsters/, strings/, quests/, campaigns/, npcs/, factions/. Pure data; code-bearing mods use the plugin loader instead. | |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |
| **xAdminToken** | **kotlin.String**| Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **replace** | **kotlin.Boolean**| Overwrite an already-installed pack with the same id. | [optional] [default to false] |

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: multipart/form-data
 - **Accept**: application/json

<a id="verifyReceiptV2"></a>
# **verifyReceiptV2**
> EntitlementEnvelope verifyReceiptV2(verifyReceiptRequest, xRequestId)

Validate a purchase receipt via its storefront and grant the entitlement.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val verifyReceiptRequest : VerifyReceiptRequest =  // VerifyReceiptRequest | 
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : EntitlementEnvelope = apiInstance.verifyReceiptV2(verifyReceiptRequest, xRequestId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling V2Api#verifyReceiptV2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling V2Api#verifyReceiptV2")
    e.printStackTrace()
}
```

### Parameters
| **verifyReceiptRequest** | [**VerifyReceiptRequest**](VerifyReceiptRequest.md)|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**EntitlementEnvelope**](EntitlementEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

