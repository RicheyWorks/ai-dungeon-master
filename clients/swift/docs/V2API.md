# V2API

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getCatalogV2**](V2API.md#getcatalogv2) | **GET** /v2/catalog | Installed content packs and registered plugins (mod browser).
[**getStatusV2**](V2API.md#getstatusv2) | **GET** /v2/status | Current game status as a typed envelope.
[**listEntitlementsV2**](V2API.md#listentitlementsv2) | **GET** /v2/entitlements | List the caller&#39;s owned products.
[**narrateV2**](V2API.md#narratev2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider.
[**submitActionV2**](V2API.md#submitactionv2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope.
[**verifyReceiptV2**](V2API.md#verifyreceiptv2) | **POST** /v2/entitlements/verify | Validate a purchase receipt via its storefront and grant the entitlement.


# **getCatalogV2**
```swift
    open class func getCatalogV2(xRequestId: String? = nil, completion: @escaping (_ data: CatalogEnvelope?, _ error: Error?) -> Void)
```

Installed content packs and registered plugins (mod browser).

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Installed content packs and registered plugins (mod browser).
V2API.getCatalogV2(xRequestId: xRequestId) { (response, error) in
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
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getStatusV2**
```swift
    open class func getStatusV2(xRequestId: String? = nil, completion: @escaping (_ data: GameStatusEnvelope?, _ error: Error?) -> Void)
```

Current game status as a typed envelope.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Current game status as a typed envelope.
V2API.getStatusV2(xRequestId: xRequestId) { (response, error) in
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
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listEntitlementsV2**
```swift
    open class func listEntitlementsV2(xRequestId: String? = nil, completion: @escaping (_ data: EntitlementEnvelope?, _ error: Error?) -> Void)
```

List the caller's owned products.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// List the caller's owned products.
V2API.listEntitlementsV2(xRequestId: xRequestId) { (response, error) in
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
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**EntitlementEnvelope**](EntitlementEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **narrateV2**
```swift
    open class func narrateV2(xRequestId: String? = nil, narrateRequest: NarrateRequest? = nil, completion: @escaping (_ data: NarrativeEnvelope?, _ error: Error?) -> Void)
```

Generate a dungeon-master narration via the active LLM provider.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)
let narrateRequest = NarrateRequest(prompt: "prompt_example") // NarrateRequest |  (optional)

// Generate a dungeon-master narration via the active LLM provider.
V2API.narrateV2(xRequestId: xRequestId, narrateRequest: narrateRequest) { (response, error) in
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
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 
 **narrateRequest** | [**NarrateRequest**](NarrateRequest.md) |  | [optional] 

### Return type

[**NarrativeEnvelope**](NarrativeEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **submitActionV2**
```swift
    open class func submitActionV2(actionRequest: ActionRequest, xRequestId: String? = nil, completion: @escaping (_ data: GameStatusEnvelope?, _ error: Error?) -> Void)
```

Apply a choice; returns the updated game status envelope.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let actionRequest = ActionRequest(choiceLabel: "choiceLabel_example") // ActionRequest | 
let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Apply a choice; returns the updated game status envelope.
V2API.submitActionV2(actionRequest: actionRequest, xRequestId: xRequestId) { (response, error) in
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
 **actionRequest** | [**ActionRequest**](ActionRequest.md) |  | 
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**GameStatusEnvelope**](GameStatusEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **verifyReceiptV2**
```swift
    open class func verifyReceiptV2(verifyReceiptRequest: VerifyReceiptRequest, xRequestId: String? = nil, completion: @escaping (_ data: EntitlementEnvelope?, _ error: Error?) -> Void)
```

Validate a purchase receipt via its storefront and grant the entitlement.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let verifyReceiptRequest = VerifyReceiptRequest(storefront: "storefront_example", productId: "productId_example", receipt: "receipt_example") // VerifyReceiptRequest | 
let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Validate a purchase receipt via its storefront and grant the entitlement.
V2API.verifyReceiptV2(verifyReceiptRequest: verifyReceiptRequest, xRequestId: xRequestId) { (response, error) in
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
 **verifyReceiptRequest** | [**VerifyReceiptRequest**](VerifyReceiptRequest.md) |  | 
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**EntitlementEnvelope**](EntitlementEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

