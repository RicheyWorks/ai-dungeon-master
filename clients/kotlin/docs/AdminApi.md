# AdminApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getAdminSessionPacks**](AdminApi.md#getAdminSessionPacks) | **GET** /v2/admin/session-packs | Session pack overrides (ops) |
| [**listAdminReceipts**](AdminApi.md#listAdminReceipts) | **GET** /v2/admin/receipts | List recent redeemed receipts (ops) |


<a id="getAdminSessionPacks"></a>
# **getAdminSessionPacks**
> getAdminSessionPacks(sessionId, xAdminToken)

Session pack overrides (ops)

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = AdminApi()
val sessionId : kotlin.String = sessionId_example // kotlin.String | 
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | 
try {
    apiInstance.getAdminSessionPacks(sessionId, xAdminToken)
} catch (e: ClientException) {
    println("4xx response calling AdminApi#getAdminSessionPacks")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling AdminApi#getAdminSessionPacks")
    e.printStackTrace()
}
```

### Parameters
| **sessionId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xAdminToken** | **kotlin.String**|  | |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="listAdminReceipts"></a>
# **listAdminReceipts**
> listAdminReceipts(xAdminToken, limit, productId, storefront, sessionId, since, until)

List recent redeemed receipts (ops)

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = AdminApi()
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | 
val limit : kotlin.Int = 56 // kotlin.Int | 
val productId : kotlin.String = productId_example // kotlin.String | 
val storefront : kotlin.String = storefront_example // kotlin.String | 
val sessionId : kotlin.String = sessionId_example // kotlin.String | 
val since : kotlin.Long = 789 // kotlin.Long | Epoch milliseconds (inclusive lower bound)
val until : kotlin.Long = 789 // kotlin.Long | Epoch milliseconds (inclusive upper bound)
try {
    apiInstance.listAdminReceipts(xAdminToken, limit, productId, storefront, sessionId, since, until)
} catch (e: ClientException) {
    println("4xx response calling AdminApi#listAdminReceipts")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling AdminApi#listAdminReceipts")
    e.printStackTrace()
}
```

### Parameters
| **xAdminToken** | **kotlin.String**|  | |
| **limit** | **kotlin.Int**|  | [optional] [default to 50] |
| **productId** | **kotlin.String**|  | [optional] |
| **storefront** | **kotlin.String**|  | [optional] |
| **sessionId** | **kotlin.String**|  | [optional] |
| **since** | **kotlin.Long**| Epoch milliseconds (inclusive lower bound) | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **until** | **kotlin.Long**| Epoch milliseconds (inclusive upper bound) | [optional] |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

