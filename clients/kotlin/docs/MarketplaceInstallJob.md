
# MarketplaceInstallJob

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **jobId** | **kotlin.String** |  |  |
| **packId** | **kotlin.String** |  |  |
| **phase** | [**inline**](#Phase) |  |  |
| **percent** | **kotlin.Int** |  |  |
| **bytesRead** | **kotlin.Long** |  |  [optional] |
| **bytesTotal** | **kotlin.Long** |  |  [optional] |
| **message** | **kotlin.String** |  |  [optional] |
| **cancelRequested** | **kotlin.Boolean** |  |  [optional] |
| **error** | **kotlin.String** |  |  [optional] |


<a id="Phase"></a>
## Enum: phase
| Name | Value |
| ---- | ----- |
| phase | QUEUED, DOWNLOADING, VERIFYING, INSTALLING, DONE, FAILED, CANCELLED |



