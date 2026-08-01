
# HealthPayload

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **status** | [**inline**](#Status) |  |  |
| **uptimeSeconds** | **kotlin.Long** |  |  [optional] |
| **sessions** | **kotlin.Int** |  |  [optional] |
| **engines** | **kotlin.Int** |  |  [optional] |
| **dependencies** | [**kotlin.collections.Map&lt;kotlin.String, DependencyCheck&gt;**](DependencyCheck.md) |  |  [optional] |
| **memory** | [**HealthPayloadMemory**](HealthPayloadMemory.md) |  |  [optional] |


<a id="Status"></a>
## Enum: status
| Name | Value |
| ---- | ----- |
| status | UP, DOWN |



