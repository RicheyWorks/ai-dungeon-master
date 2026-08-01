package com.xai.dungeonmaster.dto;

/**
 * Async marketplace install progress ({@code GET /v2/marketplace/jobs/{jobId}}).
 */
public record MarketplaceInstallJob(
        String jobId,
        String packId,
        String phase,
        long bytesRead,
        long bytesTotal,
        int percent,
        String message,
        boolean cancelRequested,
        String error
) {
    public static MarketplaceInstallJob of(
            String jobId,
            String packId,
            String phase,
            long bytesRead,
            long bytesTotal,
            String message,
            boolean cancelRequested,
            String error) {
        int pct = 0;
        if (bytesTotal > 0) {
            pct = (int) Math.min(100, Math.max(0, (bytesRead * 100) / bytesTotal));
        } else if ("DONE".equals(phase)) {
            pct = 100;
        }
        return new MarketplaceInstallJob(
                jobId, packId, phase, bytesRead, bytesTotal, pct, message, cancelRequested, error);
    }
}
