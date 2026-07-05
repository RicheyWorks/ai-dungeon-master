
# Envelope

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **type** | **kotlin.String** | Payload discriminator (game_status, narrative_update, error). |  |
| **version** | **kotlin.Int** | Schema version of the payload contract. |  |
| **payload** | [**kotlin.Any**](.md) | Typed body; shape depends on &#x60;type&#x60;. |  |
| **requestId** | **kotlin.String** | Correlation id (echoed from X-Request-Id or generated). |  |



