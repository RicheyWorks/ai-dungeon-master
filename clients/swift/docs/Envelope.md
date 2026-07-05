# Envelope

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**type** | **String** | Payload discriminator (game_status, narrative_update, error). | 
**version** | **Int** | Schema version of the payload contract. | 
**payload** | **AnyCodable** | Typed body; shape depends on &#x60;type&#x60;. | 
**requestId** | **String** | Correlation id (echoed from X-Request-Id or generated). | 

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


