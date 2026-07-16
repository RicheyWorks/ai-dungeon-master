
# GameStatusV2

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **party** | [**kotlin.collections.List&lt;MemberState&gt;**](MemberState.md) |  |  [optional] |
| **chaosLevel** | **kotlin.Int** |  |  [optional] |
| **combatActive** | **kotlin.Boolean** |  |  [optional] |
| **availableChoices** | **kotlin.collections.List&lt;kotlin.String&gt;** |  |  [optional] |
| **recentHistory** | **kotlin.collections.List&lt;kotlin.String&gt;** |  |  [optional] |
| **quest** | [**QuestInfo**](QuestInfo.md) |  |  [optional] |
| **recentEvents** | **kotlin.collections.List&lt;kotlin.String&gt;** | Compact story-memory facts from the engine&#39;s Chronicle (newest last), e.g. \&quot;Quest completed: The Weeping Tree\&quot;. Bounded server-side. |  [optional] |



