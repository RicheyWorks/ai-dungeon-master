
# ReadinessResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **status** | [**inline**](#Status) |  |  |
| **probe** | [**inline**](#Probe) |  |  |
| **sessions** | **kotlin.Int** | Present only with metrics/admin token. |  [optional] |
| **engines** | **kotlin.Int** | Present only with metrics/admin token. |  [optional] |
| **dependencies** | [**kotlin.collections.Map&lt;kotlin.String, DependencyCheck&gt;**](DependencyCheck.md) | Present only with metrics/admin token. |  [optional] |


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



