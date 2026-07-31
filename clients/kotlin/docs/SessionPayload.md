
# SessionPayload

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **sessionId** | **kotlin.String** | Stable player session id (JWT subject). |  |
| **displayName** | **kotlin.String** |  |  |
| **token** | **kotlin.String** | JWT (only set on create; null on /session/me). |  [optional] |
| **expiresAtEpochSeconds** | **kotlin.Long** |  |  [optional] |
| **createdAtEpochSeconds** | **kotlin.Long** |  |  [optional] |



