# HealthAPI

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getLiveness**](HealthAPI.md#getliveness) | **GET** /health | Liveness probe — process is accepting HTTP.
[**getReadiness**](HealthAPI.md#getreadiness) | **GET** /health/ready | Readiness probe — configured auth backends reachable.
[**getReadinessAlias**](HealthAPI.md#getreadinessalias) | **GET** /ready | Alias for &#x60;/health/ready&#x60;.


# **getLiveness**
```swift
    open class func getLiveness(completion: @escaping (_ data: LivenessResponse?, _ error: Error?) -> Void)
```

Liveness probe — process is accepting HTTP.

Always returns 200 while the JVM is up. Does **not** check JDBC/Redis. Use `/health/ready` for dependency-aware readiness. 

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

# **getReadiness**
```swift
    open class func getReadiness(completion: @escaping (_ data: ReadinessResponse?, _ error: Error?) -> Void)
```

Readiness probe — configured auth backends reachable.

Probes JDBC, Redis, and/or file stores only when selected via `game.auth.*.store`. Returns **503** when a required dependency is DOWN. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// Readiness probe — configured auth backends reachable.
HealthAPI.getReadiness() { (response, error) in
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

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getReadinessAlias**
```swift
    open class func getReadinessAlias(completion: @escaping (_ data: ReadinessResponse?, _ error: Error?) -> Void)
```

Alias for `/health/ready`.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// Alias for `/health/ready`.
HealthAPI.getReadinessAlias() { (response, error) in
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

[**ReadinessResponse**](ReadinessResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

