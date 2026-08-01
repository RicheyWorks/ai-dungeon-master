
# ReadinessResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **status** | [**inline**](#Status) |  |  |
| **probe** | [**inline**](#Probe) |  |  |
| **sessions** | **kotlin.Int** |  |  [optional] |
| **engines** | **kotlin.Int** |  |  [optional] |
| **dependencies** | [**kotlin.collections.Map&lt;kotlin.String, DependencyCheck&gt;**](DependencyCheck.md) |  |  [optional] |


<a id="Status"></a>
## Enum: status
| Name | Value |
| ---- | ----- |
| status | UP, DOWN |


<a id="Probe"></a>
## Enum: probe
| Name | Value |
| ---- | ----- |
| probe | readiness |



