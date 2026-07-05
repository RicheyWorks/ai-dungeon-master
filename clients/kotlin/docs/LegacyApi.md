# LegacyApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getChoicesLegacy**](LegacyApi.md#getChoicesLegacy) | **GET** /api/game/choices | Available choice labels. |
| [**getStatusLegacy**](LegacyApi.md#getStatusLegacy) | **GET** /api/game/status | Legacy game status (flat pipe-delimited party summary string). |
| [**loadGameLegacy**](LegacyApi.md#loadGameLegacy) | **POST** /api/game/load |  |
| [**saveGameLegacy**](LegacyApi.md#saveGameLegacy) | **POST** /api/game/save |  |
| [**startQuestLegacy**](LegacyApi.md#startQuestLegacy) | **POST** /api/game/start | (Re)start the quest and return the opening banner. |
| [**submitActionLegacy**](LegacyApi.md#submitActionLegacy) | **POST** /api/game/action |  |


<a id="getChoicesLegacy"></a>
# **getChoicesLegacy**
> kotlin.collections.List&lt;kotlin.String&gt; getChoicesLegacy()

Available choice labels.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
try {
    val result : kotlin.collections.List<kotlin.String> = apiInstance.getChoicesLegacy()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#getChoicesLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#getChoicesLegacy")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**kotlin.collections.List&lt;kotlin.String&gt;**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getStatusLegacy"></a>
# **getStatusLegacy**
> GameStatusResponse getStatusLegacy()

Legacy game status (flat pipe-delimited party summary string).

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
try {
    val result : GameStatusResponse = apiInstance.getStatusLegacy()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#getStatusLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#getStatusLegacy")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**GameStatusResponse**](GameStatusResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="loadGameLegacy"></a>
# **loadGameLegacy**
> kotlin.String loadGameLegacy()



### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
try {
    val result : kotlin.String = apiInstance.loadGameLegacy()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#loadGameLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#loadGameLegacy")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="saveGameLegacy"></a>
# **saveGameLegacy**
> kotlin.String saveGameLegacy()



### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
try {
    val result : kotlin.String = apiInstance.saveGameLegacy()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#saveGameLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#saveGameLegacy")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="startQuestLegacy"></a>
# **startQuestLegacy**
> kotlin.String startQuestLegacy()

(Re)start the quest and return the opening banner.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
try {
    val result : kotlin.String = apiInstance.startQuestLegacy()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#startQuestLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#startQuestLegacy")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="submitActionLegacy"></a>
# **submitActionLegacy**
> kotlin.String submitActionLegacy(actionRequest)



### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = LegacyApi()
val actionRequest : ActionRequest =  // ActionRequest | 
try {
    val result : kotlin.String = apiInstance.submitActionLegacy(actionRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LegacyApi#submitActionLegacy")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LegacyApi#submitActionLegacy")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **actionRequest** | [**ActionRequest**](ActionRequest.md)|  | |

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined

