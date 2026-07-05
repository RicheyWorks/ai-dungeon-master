# LegacyAPI

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getChoicesLegacy**](LegacyAPI.md#getchoiceslegacy) | **GET** /api/game/choices | Available choice labels.
[**getStatusLegacy**](LegacyAPI.md#getstatuslegacy) | **GET** /api/game/status | Legacy game status (flat pipe-delimited party summary string).
[**loadGameLegacy**](LegacyAPI.md#loadgamelegacy) | **POST** /api/game/load | 
[**saveGameLegacy**](LegacyAPI.md#savegamelegacy) | **POST** /api/game/save | 
[**startQuestLegacy**](LegacyAPI.md#startquestlegacy) | **POST** /api/game/start | (Re)start the quest and return the opening banner.
[**submitActionLegacy**](LegacyAPI.md#submitactionlegacy) | **POST** /api/game/action | 


# **getChoicesLegacy**
```swift
    open class func getChoicesLegacy(completion: @escaping (_ data: [String]?, _ error: Error?) -> Void)
```

Available choice labels.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// Available choice labels.
LegacyAPI.getChoicesLegacy() { (response, error) in
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

**[String]**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getStatusLegacy**
```swift
    open class func getStatusLegacy(completion: @escaping (_ data: GameStatusResponse?, _ error: Error?) -> Void)
```

Legacy game status (flat pipe-delimited party summary string).

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// Legacy game status (flat pipe-delimited party summary string).
LegacyAPI.getStatusLegacy() { (response, error) in
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

[**GameStatusResponse**](GameStatusResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **loadGameLegacy**
```swift
    open class func loadGameLegacy(completion: @escaping (_ data: String?, _ error: Error?) -> Void)
```



### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


LegacyAPI.loadGameLegacy() { (response, error) in
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

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **saveGameLegacy**
```swift
    open class func saveGameLegacy(completion: @escaping (_ data: String?, _ error: Error?) -> Void)
```



### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


LegacyAPI.saveGameLegacy() { (response, error) in
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

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **startQuestLegacy**
```swift
    open class func startQuestLegacy(completion: @escaping (_ data: String?, _ error: Error?) -> Void)
```

(Re)start the quest and return the opening banner.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient


// (Re)start the quest and return the opening banner.
LegacyAPI.startQuestLegacy() { (response, error) in
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

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **submitActionLegacy**
```swift
    open class func submitActionLegacy(actionRequest: ActionRequest, completion: @escaping (_ data: String?, _ error: Error?) -> Void)
```



### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let actionRequest = ActionRequest(choiceLabel: "choiceLabel_example") // ActionRequest | 

LegacyAPI.submitActionLegacy(actionRequest: actionRequest) { (response, error) in
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

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

