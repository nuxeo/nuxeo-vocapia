/**
 * 
 */

package org.nuxeo.vocapia.service;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkManager.Scheduling;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Shedule the conversion of video and audio files and monitor progress.
 */
public class TranscriptionService extends DefaultComponent {

    protected static final String MEDIA_BLOB_PATH = "file:content";

    protected HttpClient httpClient;

    protected void initHttpClient() throws KeyManagementException,
            UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException {
        // Trust self signed certificates
        TrustStrategy blindTrust = new TrustStrategy() {

            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                return true;
            }
        };

        // Create and initialize a scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80,
                PlainSocketFactory.getSocketFactory()));

        SSLSocketFactory sslsf = new SSLSocketFactory(blindTrust);
        schemeRegistry.register(new Scheme("https", 443, sslsf));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
                schemeRegistry);
        httpClient = new DefaultHttpClient(cm);
    }

    @Override
    public void activate(ComponentContext context) throws Exception {
        initHttpClient();
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        if (workManager != null) {
            workManager.shutdownQueue(
                    workManager.getCategoryQueueId(TranscriptionWork.CATEGORY_SPEECH_TRANSCRIPTION),
                    10, TimeUnit.SECONDS);
        }
        httpClient = null;
    }

    
    public void launchTranscription(DocumentLocation docLoc) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        workManager.schedule(makeWork(docLoc), Scheduling.IF_NOT_RUNNING_OR_SCHEDULED);
    }

    private TranscriptionWork makeWork(DocumentLocation docLoc) {
        return new TranscriptionWork(docLoc, MEDIA_BLOB_PATH, httpClient);
    }
    
    public TranscriptionStatus getTranscriptionStatus(DocumentLocation docLoc) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        Work work = makeWork(docLoc);
        int[] pos = new int[1];
        work = workManager.find(work, null, true, pos);
        if (work == null) {
            return null;
        } else if (work.getState() == State.SCHEDULED) {
            String queueId = workManager.getCategoryQueueId(TranscriptionWork.CATEGORY_SPEECH_TRANSCRIPTION);
            int queueSize = workManager.listWork(queueId, State.SCHEDULED).size();
            return new TranscriptionStatus(
                    TranscriptionStatus.STATUS_TRANSCRIPTION_QUEUED,
                    pos[0] + 1, queueSize);
        } else { // RUNNING
            return new TranscriptionStatus(work.getStatus(), 0, 0);
        }
    }
}
