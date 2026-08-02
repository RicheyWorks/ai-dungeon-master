# HealthApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getLiveness**](HealthApi.md#getLiveness) | **GET** /health | Liveness probe — process is accepting HTTP. |
| [**getPrometheusMetrics**](HealthApi.md#getPrometheusMetrics) | **GET** /metrics | Prometheus text exposition for scrapers. |
| [**getReadiness**](HealthApi.md#getReadiness) | **GET** /health/ready | Readiness probe — configured auth backends reachable. |
| [**getReadinessAlias**](HealthApi.md#getReadinessAlias) | **GET** /ready | Alias for &#x60;/health/ready&#x60;. |


<a id="getLiveness"></a>
# **getLiveness**
> LivenessResponse getLiveness()

Liveness probe — process is accepting HTTP.

Always returns 200 while the JVM is up. Does **not** check JDBC/Redis. Use &#x60;/health/ready&#x60; for dependency-aware readiness. Always lean (no recon fields). 

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

<a id="getPrometheusMetrics"></a>
# **getPrometheusMetrics**
> kotlin.String getPrometheusMetrics(xMetricsToken, authorization)

Prometheus text exposition for scrapers.

Plaintext metrics (&#x60;text/plain&#x60;). When &#x60;game.metrics.scrape-token&#x60; is set (required in production), scrapers must send &#x60;X-Metrics-Token&#x60; or &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Prefer private-network scrape. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
val xMetricsToken : kotlin.String = xMetricsToken_example // kotlin.String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields. 
val authorization : kotlin.String = authorization_example // kotlin.String | Bearer scrape token (alternative to X-Metrics-Token).
try {
    val result : kotlin.String = apiInstance.getPrometheusMetrics(xMetricsToken, authorization)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling HealthApi#getPrometheusMetrics")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling HealthApi#getPrometheusMetrics")
    e.printStackTrace()
}
```

### Parameters
| **xMetricsToken** | **kotlin.String**| Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **authorization** | **kotlin.String**| Bearer scrape token (alternative to X-Metrics-Token). | [optional] |

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="getReadiness"></a>
# **getReadiness**
> ReadinessResponse getReadiness(xMetricsToken, xAdminToken)

Readiness probe — configured auth backends reachable.

Probes JDBC, Redis, and/or file stores only when selected via &#x60;game.auth.*.store&#x60;. Returns **503** when a required dependency is DOWN. Public responses are lean (&#x60;status&#x60; + &#x60;probe&#x60;). Session/engine counts and the dependency map require &#x60;X-Metrics-Token&#x60; or &#x60;X-Admin-Token&#x60;. 

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
val xMetricsToken : kotlin.String = xMetricsToken_example // kotlin.String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields. 
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail. 
try {
    val result : ReadinessResponse = apiInstance.getReadiness(xMetricsToken, xAdminToken)
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
| **xMetricsToken** | **kotlin.String**| Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xAdminToken** | **kotlin.String**| Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] |

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getReadinessAlias"></a>
# **getReadinessAlias**
> ReadinessResponse getReadinessAlias(xMetricsToken, xAdminToken)

Alias for &#x60;/health/ready&#x60;.

### Example
```kotlin
// Import classes:
//import com.xai.dungeonmaster.client.infrastructure.*
//import com.xai.dungeonmaster.client.models.*

val apiInstance = HealthApi()
val xMetricsToken : kotlin.String = xMetricsToken_example // kotlin.String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields. 
val xAdminToken : kotlin.String = xAdminToken_example // kotlin.String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail. 
try {
    val result : ReadinessResponse = apiInstance.getReadinessAlias(xMetricsToken, xAdminToken)
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
| **xMetricsToken** | **kotlin.String**| Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **xAdminToken** | **kotlin.String**| Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] |

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

