# V2Api

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getStatusV2**](V2Api.md#getStatusV2) | **GET** /v2/status | Current game status as a typed envelope. |
| [**narrateV2**](V2Api.md#narrateV2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider. |
| [**submitActionV2**](V2Api.md#submitActionV2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope. |


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

