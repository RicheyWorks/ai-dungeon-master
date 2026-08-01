# HealthApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getLiveness**](HealthApi.md#getLiveness) | **GET** /health | Liveness probe — process is accepting HTTP. |
| [**getReadiness**](HealthApi.md#getReadiness) | **GET** /health/ready | Readiness probe — configured auth backends reachable. |
| [**getReadinessAlias**](HealthApi.md#getReadinessAlias) | **GET** /ready | Alias for &#x60;/health/ready&#x60;. |


<a id="getLiveness"></a>
# **getLiveness**
> LivenessResponse getLiveness()

Liveness probe — process is accepting HTTP.

Always returns 200 while the JVM is up. Does **not** check JDBC/Redis. Use &#x60;/health/ready&#x60; for dependency-aware readiness. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
try {
    val result : LivenessResponse = apiInstance.getLiveness()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling HealthApi#getLiveness")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling HealthApi#getLiveness")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**LivenessResponse**](LivenessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getReadiness"></a>
# **getReadiness**
> ReadinessResponse getReadiness()

Readiness probe — configured auth backends reachable.

Probes JDBC, Redis, and/or file stores only when selected via &#x60;game.auth.*.store&#x60;. Returns **503** when a required dependency is DOWN. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
try {
    val result : ReadinessResponse = apiInstance.getReadiness()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling HealthApi#getReadiness")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling HealthApi#getReadiness")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getReadinessAlias"></a>
# **getReadinessAlias**
> ReadinessResponse getReadinessAlias()

Alias for &#x60;/health/ready&#x60;.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
try {
    val result : ReadinessResponse = apiInstance.getReadinessAlias()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling HealthApi#getReadinessAlias")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling HealthApi#getReadinessAlias")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

