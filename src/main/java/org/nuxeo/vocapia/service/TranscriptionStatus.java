package org.nuxeo.vocapia.service;

/**
 * Data transfer object to report on transcription progress.
 */
public class TranscriptionStatus {
    
    public static final String STATUS_TRANSCRIPTION_QUEUED = "queued";

    public final int position;
    
    public final int queueSize;
    
    public final String status;
    
    public TranscriptionStatus(String status, int position, int queueSize) {
        this.status = status;
        this.position = position;
        this.queueSize = queueSize;
    }

}
