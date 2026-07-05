# V2Api

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**disablePackV2**](V2Api.md#disablePackV2) | **POST** /v2/catalog/packs/{id}/disable | Disable a content pack; returns the updated catalog. |
| [**enablePackV2**](V2Api.md#enablePackV2) | **POST** /v2/catalog/packs/{id}/enable | Enable a content pack; returns the updated catalog. |
| [**getCatalogV2**](V2Api.md#getCatalogV2) | **GET** /v2/catalog | Installed content packs and registered plugins (mod browser). |
| [**getStatusV2**](V2Api.md#getStatusV2) | **GET** /v2/status | Current game status as a typed envelope. |
| [**listEntitlementsV2**](V2Api.md#listEntitlementsV2) | **GET** /v2/entitlements | List the caller&#39;s owned products. |
| [**narrateV2**](V2Api.md#narrateV2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider. |
| [**submitActionV2**](V2Api.md#submitActionV2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope. |
| [**verifyReceiptV2**](V2Api.md#verifyReceiptV2) | **POST** /v2/entitlements/verify | Validate a purchase receipt via its storefront and grant the entitlement. |


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

