# HealthPayload

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**status** | **String** |  | 
**uptimeSeconds** | **Int64** |  | [optional] 
**detail** | **Bool** | false for unauthenticated lean responses; true when sessions/engines/ dependencies/memory are included.  | 
**sessions** | **Int** |  | [optional] 
**engines** | **Int** |  | [optional] 
**dependencies** | [String: DependencyCheck] |  | [optional] 
**memory** | [**HealthPayloadMemory**](HealthPayloadMemory.md) |  | [optional] 

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


