package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceInstallJob;

import java.util.Collection;
import java.util.Optional;

/**
 * Durable store for marketplace install job snapshots (progress / multi-node poll).
 * In-process download threads remain local; this stores the last known phase so
 * restarts and other nodes can report status.
 */
public interface MarketplaceJobStore {

    void save(JobRecord record);

    Optional<JobRecord> load(String jobId);

    /** All known job ids (best-effort). */
    Collection<String> ids();

    void delete(String jobId);

    /**
     * Serializable job row. {@code cancelRequested} is sticky once true.
     * {@code ownerSessionId} binds poll/cancel to the session that started the job
     * (null for legacy rows / unauthenticated installs).
     */
    record JobRecord(
            String jobId,
            String packId,
            String phase,
            long bytesRead,
            long bytesTotal,
            String message,
            boolean cancelRequested,
            String error,
            long updatedAtMs,
            String ownerSessionId
    ) {
        public MarketplaceInstallJob toDto() {
            return MarketplaceInstallJob.of(
                    jobId, packId, phase, bytesRead, bytesTotal, message, cancelRequested, error);
        }

        public boolean terminal() {
            return "DONE".equals(phase) || "FAILED".equals(phase) || "CANCELLED".equals(phase);
        }

        /**
         * Fail-closed ownership: only the starting session may poll/cancel.
         * Ownerless (legacy) jobs are not world-accessible — use admin tooling.
         */
        public boolean ownedBy(String sessionId) {
            if (ownerSessionId == null || ownerSessionId.isBlank()) return false;
            return sessionId != null && ownerSessionId.equals(sessionId);
        }
    }
}
