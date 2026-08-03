package com.xai.dungeonmaster.android

import com.xai.dungeonmaster.client.models.MarketplaceInstallJob as SdkInstallJob
import com.xai.dungeonmaster.client.models.MarketplaceListing as SdkListing
import com.xai.dungeonmaster.client.models.MarketplacePayload as SdkPayload

/** Soft JSON models for marketplace UI (mapped from generated SDK types). */
data class MarketplaceListing(
    val id: String,
    val displayName: String? = null,
    val version: String? = null,
    val minEngineVersion: String? = null,
    val description: String? = null,
    val installed: Boolean? = null,
    val enabled: Boolean? = null,
    val requiredProductIds: List<String>? = null,
    val locked: Boolean? = null,
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

/** Map generated SDK marketplace payload → UI model. */
fun SdkPayload.toUi(): MarketplacePayload = MarketplacePayload(
    root = root,
    remoteIndexUrl = remoteIndexUrl,
    remoteOk = remoteOk,
    remoteError = remoteError,
    available = available,
    installed = installed,
    packs = packs?.map { it.toUi() },
)

fun SdkListing.toUi(): MarketplaceListing = MarketplaceListing(
    id = id,
    displayName = displayName,
    version = version,
    minEngineVersion = minEngineVersion,
    description = description,
    installed = installed,
    enabled = enabled,
    sourcePath = sourcePath,
    source = source.value,
    downloadUrl = downloadUrl,
    sha256 = sha256,
)

fun SdkInstallJob.toUi(): MarketplaceInstallJob = MarketplaceInstallJob(
    jobId = jobId,
    packId = packId,
    phase = phase.value,
    bytesRead = bytesRead,
    bytesTotal = bytesTotal,
    percent = percent,
    message = message,
    cancelRequested = cancelRequested,
    error = error,
)
