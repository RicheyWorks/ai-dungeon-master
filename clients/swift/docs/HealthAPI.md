# HealthAPI

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getLiveness**](HealthAPI.md#getliveness) | **GET** /health | Liveness probe — process is accepting HTTP.
[**getPrometheusMetrics**](HealthAPI.md#getprometheusmetrics) | **GET** /metrics | Prometheus text exposition for scrapers.
[**getReadiness**](HealthAPI.md#getreadiness) | **GET** /health/ready | Readiness probe — configured auth backends reachable.
[**getReadinessAlias**](HealthAPI.md#getreadinessalias) | **GET** /ready | Alias for &#x60;/health/ready&#x60;.


# **getLiveness**
```swift
    open class func getLiveness(completion: @escaping (_ data: LivenessResponse?, _ error: Error?) -> Void)
```

Liveness probe — process is accepting HTTP.

Always returns 200 while the JVM is up. Does **not** check JDBC/Redis. Use `/health/ready` for dependency-aware readiness. Always lean (no recon fields). 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// Liveness probe — process is accepting HTTP.
HealthAPI.getLiveness() { (response, error) in
    guard error == nil else {
        print(error)
        return
    }

    if (response) {
        dump(response)
    }
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getPrometheusMetrics**
```swift
    open class func getPrometheusMetrics(xMetricsToken: String? = nil, authorization: String? = nil, completion: @escaping (_ data: String?, _ error: Error?) -> Void)
```

Prometheus text exposition for scrapers.

Plaintext metrics (`text/plain`). When `game.metrics.scrape-token` is set (required in production), scrapers must send `X-Metrics-Token` or `Authorization: Bearer <token>`. Prefer private-network scrape. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xMetricsToken = "xMetricsToken_example" // String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields.  (optional)
let authorization = "authorization_example" // String | Bearer scrape token (alternative to X-Metrics-Token). (optional)

// Prometheus text exposition for scrapers.
HealthAPI.getPrometheusMetrics(xMetricsToken: xMetricsToken, authorization: authorization) { (response, error) in
    guard error == nil else {
        print(error)
        return
    }

    if (response) {
        dump(response)
    }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xMetricsToken** | **String** | Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] 
 **authorization** | **String** | Bearer scrape token (alternative to X-Metrics-Token). | [optional] 

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getReadiness**
```swift
    open class func getReadiness(xMetricsToken: String? = nil, xAdminToken: String? = nil, completion: @escaping (_ data: ReadinessResponse?, _ error: Error?) -> Void)
```

Readiness probe — configured auth backends reachable.

Probes JDBC, Redis, and/or file stores only when selected via `game.auth.*.store`. Returns **503** when a required dependency is DOWN. Public responses are lean (`status` + `probe`). Session/engine counts and the dependency map require `X-Metrics-Token` or `X-Admin-Token`. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xMetricsToken = "xMetricsToken_example" // String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields.  (optional)
let xAdminToken = "xAdminToken_example" // String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  (optional)

// Readiness probe — configured auth backends reachable.
HealthAPI.getReadiness(xMetricsToken: xMetricsToken, xAdminToken: xAdminToken) { (response, error) in
    guard error == nil else {
        print(error)
        return
    }

    if (response) {
        dump(response)
    }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xMetricsToken** | **String** | Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] 
 **xAdminToken** | **String** | Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] 

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getReadinessAlias**
```swift
    open class func getReadinessAlias(xMetricsToken: String? = nil, xAdminToken: String? = nil, completion: @escaping (_ data: ReadinessResponse?, _ error: Error?) -> Void)
```

Alias for `/health/ready`.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xMetricsToken = "xMetricsToken_example" // String | Metrics scrape token (`game.metrics.scrape-token`). Alternative to `Authorization: Bearer <token>`. Unlocks readiness/v2 health detail fields.  (optional)
let xAdminToken = "xAdminToken_example" // String | Ops shared secret (`game.admin.token`). During rotation, `game.admin.token.previous` is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  (optional)

// Alias for `/health/ready`.
HealthAPI.getReadinessAlias(xMetricsToken: xMetricsToken, xAdminToken: xAdminToken) { (response, error) in
    guard error == nil else {
        print(error)
        return
    }

    if (response) {
        dump(response)
    }
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xMetricsToken** | **String** | Metrics scrape token (&#x60;game.metrics.scrape-token&#x60;). Alternative to &#x60;Authorization: Bearer &lt;token&gt;&#x60;. Unlocks readiness/v2 health detail fields.  | [optional] 
 **xAdminToken** | **String** | Ops shared secret (&#x60;game.admin.token&#x60;). During rotation, &#x60;game.admin.token.previous&#x60; is also accepted. Required for admin routes and (in prod) catalog pack upload; unlocks health recon detail.  | [optional] 

### Return type

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

