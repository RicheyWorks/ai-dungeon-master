# V2API

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createSessionV2**](V2API.md#createsessionv2) | **POST** /v2/session | Mint a guest player session and JWT.
[**disablePackV2**](V2API.md#disablepackv2) | **POST** /v2/catalog/packs/{id}/disable | Disable a content pack; returns the updated catalog.
[**enablePackV2**](V2API.md#enablepackv2) | **POST** /v2/catalog/packs/{id}/enable | Enable a content pack; returns the updated catalog.
[**getCatalogV2**](V2API.md#getcatalogv2) | **GET** /v2/catalog | Installed content packs and registered plugins (mod browser).
[**getHealthV2**](V2API.md#gethealthv2) | **GET** /v2/health | Health metrics envelope (public, no auth).
[**getSessionMeV2**](V2API.md#getsessionmev2) | **GET** /v2/session/me | Echo the authenticated session (no token reflected).
[**getStatusV2**](V2API.md#getstatusv2) | **GET** /v2/status | Current game status as a typed envelope.
[**listEntitlementsV2**](V2API.md#listentitlementsv2) | **GET** /v2/entitlements | List the caller&#39;s owned products.
[**loadGameV2**](V2API.md#loadgamev2) | **POST** /v2/load | Restore the caller&#39;s game engine from its save file.
[**narrateV2**](V2API.md#narratev2) | **POST** /v2/narrate | Generate a dungeon-master narration via the active LLM provider.
[**resetGameV2**](V2API.md#resetgamev2) | **POST** /v2/reset | Start a fresh engine for the caller (new party/quest).
[**saveGameV2**](V2API.md#savegamev2) | **POST** /v2/save | Persist the caller&#39;s game engine to a session-scoped save file.
[**submitActionV2**](V2API.md#submitactionv2) | **POST** /v2/action | Apply a choice; returns the updated game status envelope.
[**uploadPackV2**](V2API.md#uploadpackv2) | **POST** /v2/catalog/packs | Upload and install a content-pack zip at runtime; returns the updated catalog.
[**verifyReceiptV2**](V2API.md#verifyreceiptv2) | **POST** /v2/entitlements/verify | Validate a purchase receipt via its storefront and grant the entitlement.


# **createSessionV2**
```swift
    open class func createSessionV2(xRequestId: String? = nil, sessionRequest: SessionRequest? = nil, completion: @escaping (_ data: SessionEnvelope?, _ error: Error?) -> Void)
```

Mint a guest player session and JWT.

Public endpoint. Returns a session id plus a Bearer token used on all subsequent `/v2/_*` calls (and as a STOMP CONNECT header for WebSocket). When multi-player isolation is enabled on the server, each session gets its own game engine. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)
let sessionRequest = SessionRequest(displayName: "displayName_example") // SessionRequest |  (optional)

// Mint a guest player session and JWT.
V2API.createSessionV2(xRequestId: xRequestId, sessionRequest: sessionRequest) { (response, error) in
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
 **sessionRequest** | [**SessionRequest**](SessionRequest.md) |  | [optional] 

### Return type

[**SessionEnvelope**](SessionEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **disablePackV2**
```swift
    open class func disablePackV2(id: String, xRequestId: String? = nil, completion: @escaping (_ data: CatalogEnvelope?, _ error: Error?) -> Void)
```

Disable a content pack; returns the updated catalog.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let id = "id_example" // String | 
let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Disable a content pack; returns the updated catalog.
V2API.disablePackV2(id: id, xRequestId: xRequestId) { (response, error) in
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
 **id** | **String** |  | 
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **enablePackV2**
```swift
    open class func enablePackV2(id: String, xRequestId: String? = nil, completion: @escaping (_ data: CatalogEnvelope?, _ error: Error?) -> Void)
```

Enable a content pack; returns the updated catalog.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let id = "id_example" // String | 
let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Enable a content pack; returns the updated catalog.
V2API.enablePackV2(id: id, xRequestId: xRequestId) { (response, error) in
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
 **id** | **String** |  | 
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

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

# **getHealthV2**
```swift
    open class func getHealthV2(xRequestId: String? = nil, completion: @escaping (_ data: HealthEnvelope?, _ error: Error?) -> Void)
```

Health metrics envelope (public, no auth).

Versioned envelope with uptime, session/engine counts, memory, and the same dependency map as `/health/ready`. Excluded from JWT enforcement. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Health metrics envelope (public, no auth).
V2API.getHealthV2(xRequestId: xRequestId) { (response, error) in
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

[**HealthEnvelope**](HealthEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getSessionMeV2**
```swift
    open class func getSessionMeV2(xRequestId: String? = nil, completion: @escaping (_ data: SessionEnvelope?, _ error: Error?) -> Void)
```

Echo the authenticated session (no token reflected).

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Echo the authenticated session (no token reflected).
V2API.getSessionMeV2(xRequestId: xRequestId) { (response, error) in
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

[**SessionEnvelope**](SessionEnvelope.md)

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

# **loadGameV2**
```swift
    open class func loadGameV2(xRequestId: String? = nil, completion: @escaping (_ data: GameStatusEnvelope?, _ error: Error?) -> Void)
```

Restore the caller's game engine from its save file.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Restore the caller's game engine from its save file.
V2API.loadGameV2(xRequestId: xRequestId) { (response, error) in
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

# **resetGameV2**
```swift
    open class func resetGameV2(xRequestId: String? = nil, completion: @escaping (_ data: GameStatusEnvelope?, _ error: Error?) -> Void)
```

Start a fresh engine for the caller (new party/quest).

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Start a fresh engine for the caller (new party/quest).
V2API.resetGameV2(xRequestId: xRequestId) { (response, error) in
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

# **saveGameV2**
```swift
    open class func saveGameV2(xRequestId: String? = nil, completion: @escaping (_ data: GameSaveEnvelope?, _ error: Error?) -> Void)
```

Persist the caller's game engine to a session-scoped save file.

Authenticated callers save under `game.saves.dir/{sessionId}.json`. Unauthenticated callers share the process-default engine and save as `default.json`. Each authenticated session has its own engine instance. 

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)

// Persist the caller's game engine to a session-scoped save file.
V2API.saveGameV2(xRequestId: xRequestId) { (response, error) in
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

[**GameSaveEnvelope**](GameSaveEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
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

# **uploadPackV2**
```swift
    open class func uploadPackV2(file: URL, xRequestId: String? = nil, replace: Bool? = nil, completion: @escaping (_ data: CatalogEnvelope?, _ error: Error?) -> Void)
```

Upload and install a content-pack zip at runtime; returns the updated catalog.

### Example
```swift
// The following code samples are still beta. For any issue, please report via http://github.com/OpenAPITools/openapi-generator/issues/new
import AIDungeonMasterClient

let file = URL(string: "https://example.com")! // URL | Pack zip — pack.yaml plus optional items/, monsters/, strings/, quests/, campaigns/, npcs/, factions/. Pure data; code-bearing mods use the plugin loader instead.
let xRequestId = "xRequestId_example" // String | Optional correlation id echoed back in the response envelope's requestId. A server-generated UUID is used when omitted.  (optional)
let replace = true // Bool | Overwrite an already-installed pack with the same id. (optional) (default to false)

// Upload and install a content-pack zip at runtime; returns the updated catalog.
V2API.uploadPackV2(file: file, xRequestId: xRequestId, replace: replace) { (response, error) in
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
 **file** | **URL** | Pack zip — pack.yaml plus optional items/, monsters/, strings/, quests/, campaigns/, npcs/, factions/. Pure data; code-bearing mods use the plugin loader instead. | 
 **xRequestId** | **String** | Optional correlation id echoed back in the response envelope&#39;s requestId. A server-generated UUID is used when omitted.  | [optional] 
 **replace** | **Bool** | Overwrite an already-installed pack with the same id. | [optional] [default to false]

### Return type

[**CatalogEnvelope**](CatalogEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: multipart/form-data
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

