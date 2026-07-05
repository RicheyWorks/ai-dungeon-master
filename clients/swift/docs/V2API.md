# V2API

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getStatusV2**](V2API.md#getstatusv2) | **GET** /v2/status | Current game status as a typed envelope.
[**narrateV2**](V2API.md#narratev2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider.
[**submitActionV2**](V2API.md#submitactionv2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope.


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

