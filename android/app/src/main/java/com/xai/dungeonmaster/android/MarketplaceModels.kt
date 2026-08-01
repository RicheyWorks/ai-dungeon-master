package com.xai.dungeonmaster.android

/** Soft JSON models for /v2/marketplace (Moshi reflect). */
data class MarketplaceListing(
    val id: String,
    val displayName: String? = null,
    val version: String? = null,
    val minEngineVersion: String? = null,
    val description: String? = null,
    val installed: Boolean? = null,
    val enabled: Boolean? = null,
    val sourcePath: String? = null,
    /** `local` or `remote` */
    val source: String? = null,
    val downloadUrl: String? = null,
    val sha256: String? = null,
)

data class MarketplacePayload(
    val root: String? = null,
    val remoteIndexUrl: String? = null,
    val remoteOk: Boolean? = null,
    val remoteError: String? = null,
    val available: Int? = null,
    val installed: Int? = null,
    val packs: List<MarketplaceListing>? = null,
)

data class MarketplaceEnvelope(
    val type: String? = null,
    val version: Int? = null,
    val requestId: String? = null,
    val payload: MarketplacePayload? = null,
)

data class MarketplaceInstallPayload(
    val packId: String? = null,
    val alreadyInstalled: Boolean? = null,
    val message: String? = null,
)

data class MarketplaceInstallEnvelope(
    val type: String? = null,
    val payload: MarketplaceInstallPayload? = null,
)

data class ErrorEnvelopePayload(
    val message: String? = null,
)

data class ErrorEnvelope(
    val payload: ErrorEnvelopePayload? = null,
)

data class MarketplaceInstallJob(
    val jobId: String,
    val packId: String? = null,
    val phase: String? = null,
    val bytesRead: Long? = null,
    val bytesTotal: Long? = null,
    val percent: Int? = null,
    val message: String? = null,
    val cancelRequested: Boolean? = null,
    val error: String? = null,
)

data class MarketplaceInstallJobEnvelope(
    val type: String? = null,
    val payload: MarketplaceInstallJob? = null,
)
