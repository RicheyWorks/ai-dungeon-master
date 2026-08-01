# V2Api

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**createSessionV2**](V2Api.md#createSessionV2) | **POST** /v2/session | Mint a guest player session and JWT. |
| [**disablePackV2**](V2Api.md#disablePackV2) | **POST** /v2/catalog/packs/{id}/disable | Disable a content pack; returns the updated catalog. |
| [**enablePackV2**](V2Api.md#enablePackV2) | **POST** /v2/catalog/packs/{id}/enable | Enable a content pack; returns the updated catalog. |
| [**getCatalogV2**](V2Api.md#getCatalogV2) | **GET** /v2/catalog | Installed content packs and registered plugins (mod browser). |
| [**getHealthV2**](V2Api.md#getHealthV2) | **GET** /v2/health | Health metrics envelope (public, no auth). |
| [**getSessionMeV2**](V2Api.md#getSessionMeV2) | **GET** /v2/session/me | Echo the authenticated session (no token reflected). |
| [**getStatusV2**](V2Api.md#getStatusV2) | **GET** /v2/status | Current game status as a typed envelope. |
| [**listEntitlementsV2**](V2Api.md#listEntitlementsV2) | **GET** /v2/entitlements | List the caller&#39;s owned products. |
| [**loadGameV2**](V2Api.md#loadGameV2) | **POST** /v2/load | Restore the caller&#39;s game engine from its save file. |
| [**narrateV2**](V2Api.md#narrateV2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider. |
| [**resetGameV2**](V2Api.md#resetGameV2) | **POST** /v2/reset | Start a fresh engine for the caller (new party/quest). |
| [**saveGameV2**](V2Api.md#saveGameV2) | **POST** /v2/save | Persist the caller&#39;s game engine to a session-scoped save file. |
| [**submitActionV2**](V2Api.md#submitActionV2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope. |
| [**uploadPackV2**](V2Api.md#uploadPackV2) | **POST** /v2/catalog/packs | Upload and install a content-pack zip at runtime; returns the updated catalog. |
| [**verifyReceiptV2**](V2Api.md#verifyReceiptV2) | **POST** /v2/entitlements/verify | Validate a purchase receipt via its storefront and grant the entitlement. |


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
> HealthEnvelope getHealthV2(xRequestId)

Health metrics envelope (public, no auth).

Versioned envelope with uptime, session/engine counts, memory, and the same dependency map as &#x60;/health/ready&#x60;. Excluded from JWT enforcement. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
try {
    val result : HealthEnvelope = apiInstance.getHealthV2(xRequestId)
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
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xRequestId** | **kotlin.String**| Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] |

### Return type

[**HealthEnvelope**](HealthEnvelope.md)

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
> CatalogEnvelope uploadPackV2(file, xRequestId, replace)

Upload and install a content-pack zip at runtime; returns the updated catalog.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = V2Api()
val file : java.io.File = BINARY_DATA_HERE // java.io.File | Pack zip — pack.yaml plus optional items/, monsters/, strings/, quests/, campaigns/, npcs/, factions/. Pure data; code-bearing mods use the plugin loader instead.
val xRequestId : kotlin.String = xRequestId_example // kotlin.String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted. 
val replace : kotlin.Boolean = true // kotlin.Boolean | Overwrite an already-installed pack with the same id.
try {
    val result : CatalogEnvelope = apiInstance.uploadPackV2(file, xRequestId, replace)
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

