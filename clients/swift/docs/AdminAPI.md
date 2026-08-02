# AdminAPI

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**v2AdminReceiptsGet**](AdminAPI.md#v2adminreceiptsget) | **GET** /v2/admin/receipts | List recent redeemed receipts (ops)
[**v2AdminSessionPacksGet**](AdminAPI.md#v2adminsessionpacksget) | **GET** /v2/admin/session-packs | Session pack overrides (ops)


# **v2AdminReceiptsGet**
```swift
    open class func v2AdminReceiptsGet(xAdminToken: String, limit: Int? = nil, productId: String? = nil, storefront: String? = nil, sessionId: String? = nil, since: Int64? = nil, until: Int64? = nil, completion: @escaping (_ data: Void?, _ error: Error?) -> Void)
```

List recent redeemed receipts (ops)

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xAdminToken = "xAdminToken_example" // String | 
let limit = 987 // Int |  (optional) (default to 50)
let productId = "productId_example" // String |  (optional)
let storefront = "storefront_example" // String |  (optional)
let sessionId = "sessionId_example" // String |  (optional)
let since = 987 // Int64 | Epoch milliseconds (inclusive lower bound) (optional)
let until = 987 // Int64 | Epoch milliseconds (inclusive upper bound) (optional)

// List recent redeemed receipts (ops)
AdminAPI.v2AdminReceiptsGet(xAdminToken: xAdminToken, limit: limit, productId: productId, storefront: storefront, sessionId: sessionId, since: since, until: until) { (response, error) in
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
 **xAdminToken** | **String** |  | 
 **limit** | **Int** |  | [optional] [default to 50]
 **productId** | **String** |  | [optional] 
 **storefront** | **String** |  | [optional] 
 **sessionId** | **String** |  | [optional] 
 **since** | **Int64** | Epoch milliseconds (inclusive lower bound) | [optional] 
 **until** | **Int64** | Epoch milliseconds (inclusive upper bound) | [optional] 

### Return type

Void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **v2AdminSessionPacksGet**
```swift
    open class func v2AdminSessionPacksGet(sessionId: String, xAdminToken: String, completion: @escaping (_ data: Void?, _ error: Error?) -> Void)
```

Session pack overrides (ops)

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let sessionId = "sessionId_example" // String | 
let xAdminToken = "xAdminToken_example" // String | 

// Session pack overrides (ops)
AdminAPI.v2AdminSessionPacksGet(sessionId: sessionId, xAdminToken: xAdminToken) { (response, error) in
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
 **sessionId** | **String** |  | 
 **xAdminToken** | **String** |  | 

### Return type

Void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

