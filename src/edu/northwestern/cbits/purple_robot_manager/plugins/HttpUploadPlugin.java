package edu.northwestern.cbits.purple_robot_manager.plugins;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import edu.emory.mathcs.backport.java.util.Arrays;
import edu.emory.mathcs.backport.java.util.Collections;
import edu.northwestern.cbits.purple_robot_manager.EncryptionManager;
import edu.northwestern.cbits.purple_robot_manager.PurpleRobotApplication;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.WiFiHelper;
import edu.northwestern.cbits.purple_robot_manager.activities.StartActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.SettingsActivity;
import edu.northwestern.cbits.purple_robot_manager.logging.LiberalSSLSocketFactory;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class HttpUploadPlugin extends OutputPlugin
{
    private final static String CACHE_DIR = "http_pending_uploads";

    private final static String USER_HASH_KEY = "UserHash";
    private final static String OPERATION_KEY = "Operation";
    private final static String PAYLOAD_KEY = "Payload";
    private final static String CHECKSUM_KEY = "Checksum";
    private final static String CONTENT_LENGTH_KEY = "ContentLength";
    private final static String STATUS_KEY = "Status";

    private final static int WIFI_MULTIPLIER = 2;

    private final static long MAX_UPLOAD_PERIOD = 3600000;
    private final static long MIN_UPLOAD_PERIOD = 300000;

    private final static long MAX_RETRIES = 4;

    private final static long MAX_UPLOAD_SIZE = 262144; // 256KB
    private final static long MIN_UPLOAD_SIZE = 16384; // 16KB
    public static final String ENABLED = "config_enable_data_server";
    public static final boolean ENABLED_DEFAULT = false;
    public static final String LAST_UPLOAD_TIME = "http_last_upload";
    public static final String LAST_UPLOAD_SIZE = "http_last_upload_size";

    private final List<String> _pendingSaves = new ArrayList<>();
    private long _lastSave = 0;
    private long _lastUpload = 0;

    private double _throughput = 0.0;
    private double _accumulation = 0.0;

    private long _lastAccumulationMeasure = System.currentTimeMillis();
    private double _accumulationSum = 0.0;

    private long _uploadSize = MIN_UPLOAD_SIZE;
    private long _uploadPeriod = MIN_UPLOAD_PERIOD;

    private boolean _uploading = false;

    private int _failCount = 0;

    private static SharedPreferences _preferences = null;

    protected static SharedPreferences getPreferences(Context context)
    {
        if (HttpUploadPlugin._preferences == null)
            HttpUploadPlugin._preferences = PreferenceManager.getDefaultSharedPreferences(context
                    .getApplicationContext());

        return HttpUploadPlugin._preferences;
    }

    public double getRecentThroughput()
    {
        return this._throughput;
    }

    public double getRecentAccumulation()
    {
        return this._accumulation;
    }

    private void logSuccess(boolean success)
    {
        if (success)
        {
            this._uploadSize *= 2;
            this._uploadPeriod /= 2;
        }
        else
        {
            this._uploadSize /= 2;
            this._uploadPeriod *= 2;
        }

        if (this._uploadSize > MAX_UPLOAD_SIZE)
            this._uploadSize = MAX_UPLOAD_SIZE;
        else if (this._uploadSize < MIN_UPLOAD_SIZE)
            this._uploadSize = MIN_UPLOAD_SIZE;

        if (this._uploadPeriod > MAX_UPLOAD_PERIOD)
            this._uploadPeriod = MAX_UPLOAD_PERIOD;
        else if (this._uploadPeriod < MIN_UPLOAD_PERIOD)
            this._uploadPeriod = MIN_UPLOAD_PERIOD;
    }

    private long savePeriod()
    {
        return 10000;
    }

    private long uploadPeriod()
    {
        long period = this._uploadPeriod;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

        long prefPeriod = Long.parseLong(prefs.getString("config_http_upload_interval", "0"));

        if (prefPeriod != 0)
        {
            period = prefPeriod * 1000;

            this._uploadPeriod = period;
        }

        return period;
    }

    private long maxUploadSize()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

        long size = Long.parseLong(prefs.getString("config_http_upload_size", "0"));

        if (size == 0)
        {
            int multiplier = 1;

            if (WiFiHelper.wifiAvailable(this.getContext()))
                multiplier = WIFI_MULTIPLIER;

            size = this._uploadSize * multiplier;
        }

        if (size < MIN_UPLOAD_SIZE)
            size = MIN_UPLOAD_SIZE;

        return size;
    }

    public String[] respondsTo()
    {
        String[] activeActions =
        { Probe.PROBE_READING, OutputPlugin.FORCE_UPLOAD };

        return activeActions;
    }

    public void processIntent(Intent intent)
    {
        final SharedPreferences prefs = HttpUploadPlugin.getPreferences(this.getContext());

        if (this.enableDataServer(prefs) == false)
            return;

        PurpleRobotApplication.fixPreferences(this.getContext(), false);

        if (OutputPlugin.FORCE_UPLOAD.equals(intent.getAction()))
        {
            this._lastUpload = 0;
            this._lastSave = 0;
            this._failCount = 0;

            final HttpUploadPlugin me = this;

            Runnable r = new Runnable()
            {
                public void run()
                {
                    me.persistJSONObject(null);
                    me.uploadPendingObjects();
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
        else
        {
            try
            {
                Bundle extras = intent.getExtras();

                if (extras.containsKey("TRANSMIT") && extras.getBoolean("TRANSMIT") == false)
                    return;

                final JSONObject jsonObject = OutputPlugin.jsonForBundle(extras);

                if (jsonObject != null)
                {
                    synchronized (this._pendingSaves)
                    {
                        this._pendingSaves.add(jsonObject.toString());
                    }
                }
                else
                {
                    Log.e("PR-PERSIST", "NULL JSON FOR BUNDLE " + extras);
                }

                long now = System.currentTimeMillis();

                if (now - this._lastSave > this.savePeriod() || this._pendingSaves.size() > 128)
                {
                    this._failCount = 0;

                    final HttpUploadPlugin me = this;

                    Runnable r = new Runnable()
                    {
                        public void run()
                        {
                            me.persistJSONObject(jsonObject);
                            me.uploadPendingObjects();
                        }
                    };

                    Thread t = new Thread(r);
                    t.start();
                }
            }
            catch (JSONException e)
            {
                LogManager.getInstance(this.getContext()).logException(e);
            }
        }
    }

    @SuppressLint("NewApi")
    public void uploadPendingObjects()
    {
        if (this._uploading)
            return;

        final HttpUploadPlugin me = this;

        final long now = System.currentTimeMillis();

        if (now - me._lastUpload > me.uploadPeriod())
        {
            this._uploading = true;

            final SharedPreferences prefs = HttpUploadPlugin.getPreferences(this.getContext());

            PurpleRobotApplication.fixPreferences(this.getContext(), false);

            if (this.enableDataServer(prefs) == false)
            {
                this._uploading = false;
                return;
            }

            // TODO: Refactor out wifi/power restriction code to be handled solely by superclass...

            if (this.restrictToWifi(prefs))
            {
                if (WiFiHelper.wifiAvailable(this.getContext()) == false)
                {
                    this._throughput = 0.0;

                    this.broadcastMessage(R.string.message_wifi_pending, false);

                    this._lastUpload = now;
                    this._uploading = false;

                    return;
                }
            }

            final Resources resources = this.getContext().getResources();
            final long maxUploadSize = me.maxUploadSize();

            final Runnable r = new Runnable()
            {
                @SuppressWarnings("deprecation")
                public void run()
                {
                    long start = System.currentTimeMillis();

                    boolean wasSuccessful = false;

                    me._lastUpload = now;

                    File pendingFolder = me.getPendingFolder();

                    File archiveFolder = me.getArchiveFolder();

                    me.broadcastMessage(R.string.message_reading_files, false);

                    String[] filenames = pendingFolder.list(new FilenameFilter()
                    {
                        public boolean accept(File dir, String filename)
                        {
                            return filename.endsWith(".json");
                        }
                    });

                    if (filenames == null)
                        filenames = new String[0];

                    Collections.shuffle(Arrays.asList(filenames));

                    ArrayList<JSONObject> pendingObjects = new ArrayList<>();

                    int totalRead = 0;

                    for (String filename : filenames)
                    {
                        if (totalRead <= maxUploadSize)
                        {
                            File f = new File(pendingFolder, filename);

                            try
                            {
                                byte[] bytes = EncryptionManager.getInstance().readFromEncryptedStream(me.getContext(),
                                        new FileInputStream(f), me.encryptData(prefs));

                                JSONArray jsonArray = new JSONArray(new String(bytes, "UTF-8"));

                                totalRead += bytes.length;

                                for (int i = 0; i < jsonArray.length(); i++)
                                {
                                    JSONObject jsonObject = jsonArray.getJSONObject(i);

                                    pendingObjects.add(jsonObject);
                                }
                            }
                            catch (JSONException | IOException e)
                            {
                                LogManager.getInstance(me.getContext()).logException(e);
                            }

                            if (me.enableArchive(prefs))
                            {
                                long now = System.currentTimeMillis();

                                File archive = new File(archiveFolder, now + ".archive");

                                f.renameTo(archive);
                            }
                            else
                                f.delete();
                        }
                    }

                    if (pendingObjects.size() > 0)
                    {
                        me.broadcastMessage(R.string.message_package_upload, false);

                        long tally = 0;

                        List<JSONObject> toUpload = new ArrayList<>();

                        for (int i = 0; i < pendingObjects.size() && tally < maxUploadSize; i++)
                        {
                            try
                            {
                                JSONObject json = pendingObjects.get(i);

                                String jsonString = json.toString();

                                int jsonSize = jsonString.getBytes("UTF-8").length;

                                if (i > 0 && jsonSize > maxUploadSize)
                                {
                                    // Skip until connection is better...
                                }
                                else if (i == 0 || jsonSize + tally < maxUploadSize)
                                {
                                    tally += jsonSize;

                                    toUpload.add(json);
                                }
                            }
                            catch (UnsupportedEncodingException e)
                            {
                                LogManager.getInstance(me.getContext()).logException(e);
                            }
                        }

                        JSONArray uploadArray = new JSONArray();

                        for (int i = 0; i < toUpload.size(); i++)
                        {
                            uploadArray.put(toUpload.get(i));
                        }

                        int l = 0;
                        Random r = new Random(System.currentTimeMillis());

                        try
                        {
                            if (uploadArray.length() == 0)
                            {
                                while (pendingObjects.size() > 0)
                                {
                                    JSONArray toSave = new JSONArray();

                                    List<JSONObject> toRemove = new ArrayList<>();

                                    for (int i = 0; i < pendingObjects.size() && i < 100; i++)
                                    {
                                        toSave.put(pendingObjects.get(i));
                                        toRemove.add(pendingObjects.get(i));
                                    }

                                    File f = new File(pendingFolder, "pending_" + l + ".json");

                                    while (f.exists())
                                    {
                                        l += r.nextInt(10);

                                        f = new File(pendingFolder, "pending_" + l + ".json");
                                    }

                                    byte[] jsonBytes = toSave.toString().getBytes("UTF-8");

                                    EncryptionManager.getInstance().writeToEncryptedStream(me.getContext(),
                                            new FileOutputStream(f), jsonBytes, me.encryptData(prefs));

                                    pendingObjects.removeAll(toRemove);
                                }

                                throw new Exception(me.getContext().getString(R.string.error_empty_payload));
                            }

                            JSONObject jsonMessage = new JSONObject();

                            jsonMessage.put(OPERATION_KEY, "SubmitProbes");

                            String payload = uploadArray.toString();

                            // if (Build.VERSION.SDK_INT >=
                            // Build.VERSION_CODES.GINGERBREAD)
                            // payload = Normalizer.normalize(payload,
                            // Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]",
                            // "");

                            payload = payload.replaceAll("\r", "");
                            payload = payload.replaceAll("\n", "");

                            jsonMessage.put(PAYLOAD_KEY, payload);

                            String userHash = EncryptionManager.getInstance().getUserHash(me.getContext());

                            jsonMessage.put(USER_HASH_KEY, userHash);

                            MessageDigest md = MessageDigest.getInstance("MD5");

                            byte[] checksummed = (jsonMessage.get(USER_HASH_KEY).toString()
                                    + jsonMessage.get(OPERATION_KEY).toString() + jsonMessage.get(PAYLOAD_KEY)
                                    .toString()).getBytes("UTF-8");

                            byte[] digest = md.digest(checksummed);

                            String checksum = (new BigInteger(1, digest)).toString(16);

                            while (checksum.length() < 32)
                            {
                                checksum = "0" + checksum;
                            }

                            jsonMessage.put(CHECKSUM_KEY, checksum);
                            jsonMessage.put(CONTENT_LENGTH_KEY, checksummed.length);

                            AndroidHttpClient androidClient = AndroidHttpClient.newInstance("Purple Robot",
                                    me.getContext());

                            // Liberal HTTPS setup:
                            // http://stackoverflow.com/questions/2012497/accepting-a-certificate-for-https-on-android

                            HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

                            SchemeRegistry registry = new SchemeRegistry();
                            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

                            SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();

                            if (me.useLiberalSsl(prefs))
                            {
                                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                                trustStore.load(null, null);

                                socketFactory = new LiberalSSLSocketFactory(trustStore);
                            }

                            registry.register(new Scheme("https", socketFactory, 443));

                            HttpParams params = androidClient.getParams();
                            HttpConnectionParams.setConnectionTimeout(params, 180000);
                            HttpConnectionParams.setSoTimeout(params, 180000);

                            SingleClientConnManager mgr = new SingleClientConnManager(params, registry);
                            HttpClient httpClient = new DefaultHttpClient(mgr, params);

                            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

                            PendingIntent contentIntent = PendingIntent.getActivity(me.getContext(), 0, new Intent(me.getContext(), StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

                            String title = me.getContext().getString(R.string.notify_upload_data);

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(me.getContext());
                            builder.setContentTitle(title);
                            builder.setContentText(title);
                            builder.setContentIntent(contentIntent);
                            builder.setSmallIcon(R.drawable.ic_note_normal);
                            builder.setWhen(System.currentTimeMillis());
                            builder.setColor(0xff4e015c);

                            Notification note = builder.build();

                            note.flags = Notification.FLAG_ONGOING_EVENT;

                            String body = null;

                            long payloadSize = -1;

                            try
                            {
                                String uriString = prefs.getString("config_data_server_uri", me.getContext()
                                        .getResources().getString(R.string.sensor_upload_url));

                                URI siteUri = new URI(uriString);

                                HttpPost httpPost = new HttpPost(siteUri);

                                String jsonString = jsonMessage.toString();

                                List<NameValuePair> nameValuePairs = new ArrayList<>();
                                nameValuePairs.add(new BasicNameValuePair("json", jsonString));
                                HttpEntity entity = new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8);

                                httpPost.setEntity(entity);

                                String uploadMessage = String.format(resources
                                        .getString(R.string.message_transmit_bytes), (httpPost.getEntity()
                                        .getContentLength() / 1024));
                                me.broadcastMessage(uploadMessage, false);

                                // noteManager.notify(12345, note);

                                HttpResponse response = httpClient.execute(httpPost);

                                HttpEntity httpEntity = response.getEntity();

                                String contentHeader = null;

                                if (response.containsHeader("Content-Encoding"))
                                    contentHeader = response.getFirstHeader("Content-Encoding").getValue();

                                if (contentHeader != null && contentHeader.endsWith("gzip"))
                                {
                                    BufferedInputStream in = new BufferedInputStream(
                                            AndroidHttpClient.getUngzippedContent(httpEntity));

                                    ByteArrayOutputStream out = new ByteArrayOutputStream();

                                    int read = 0;
                                    byte[] buffer = new byte[1024];

                                    while ((read = in.read(buffer, 0, buffer.length)) != -1)
                                    {
                                        out.write(buffer, 0, read);
                                    }

                                    in.close();

                                    body = out.toString("UTF-8");
                                }
                                else
                                    body = EntityUtils.toString(httpEntity);

                                JSONObject json = new JSONObject(body);

                                String status = json.getString(STATUS_KEY);

                                String responsePayload = "";

                                if (json.has(PAYLOAD_KEY))
                                    responsePayload = json.getString(PAYLOAD_KEY);

                                if (status.equals("error") == false)
                                {
                                    byte[] responseDigest = md.digest((status + responsePayload).getBytes("UTF-8"));
                                    String responseChecksum = (new BigInteger(1, responseDigest)).toString(16);

                                    while (responseChecksum.length() < 32)
                                    {
                                        responseChecksum = "0" + responseChecksum;
                                    }

                                    if (responseChecksum.equals(json.getString(CHECKSUM_KEY)))
                                    {
                                        pendingObjects.removeAll(toUpload);

                                        wasSuccessful = true;

                                        String uploadedMessage = String.format(resources
                                                .getString(R.string.message_upload_successful), (httpPost.getEntity()
                                                .getContentLength() / 1024));

                                        me._failCount = 0;

                                        me.broadcastMessage(uploadedMessage, false);

                                        double elapsed = ((double) (System.currentTimeMillis() - start)) / 1000.0;

                                        payloadSize = httpPost.getEntity().getContentLength();

                                        me._throughput = ((double) payloadSize) / elapsed;
                                    }
                                    else
                                    {
                                        me.broadcastMessage(R.string.message_checksum_failed, true);
                                        me._failCount += 1;

                                        me._throughput = 0.0;
                                    }
                                }
                                else
                                {
                                    String errorMessage = String.format(resources.getString(R.string.message_server_error), status);

                                    me.broadcastMessage(errorMessage, true);

                                    me._failCount += 1;

                                    me._throughput = 0.0;
                                }
                            }
                            catch (HttpHostConnectException e)
                            {
                                me.broadcastMessage(R.string.message_http_connection_error, true);
                                LogManager.getInstance(me.getContext()).logException(e);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (SocketTimeoutException e)
                            {
                                me.broadcastMessage(R.string.message_socket_timeout_error, true);
                                LogManager.getInstance(me.getContext()).logException(e);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (SocketException e)
                            {
                                String errorMessage = String.format(resources.getString(R.string.message_socket_error), e.getMessage());
                                me.broadcastMessage(errorMessage, true);
                                LogManager.getInstance(me.getContext()).logException(e);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (UnknownHostException e)
                            {
                                me.broadcastMessage(R.string.message_unreachable_error, true);
                                LogManager.getInstance(me.getContext()).logException(e);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (JSONException e)
                            {
                                me.broadcastMessage(R.string.message_response_error, true);
                                LogManager.getInstance(me.getContext()).logException(e);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (SSLPeerUnverifiedException e)
                            {
                                LogManager.getInstance(me.getContext()).logException(e);
                                me.broadcastMessage(R.string.message_unverified_server, true);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            catch (Exception e)
                            {
                                LogManager.getInstance(me.getContext()).logException(e);
                                String errorMessage = String.format(
                                        resources.getString(R.string.message_general_error), e.toString());
                                me.broadcastMessage(errorMessage, true);

                                me._failCount += 1;
                                me._throughput = 0.0;
                            }
                            finally
                            {
                                androidClient.close();
                            }

                            while (pendingObjects.size() > 0)
                            {
                                JSONArray toSave = new JSONArray();

                                List<JSONObject> toRemove = new ArrayList<>();

                                for (int i = 0; i < pendingObjects.size() && i < 100; i++)
                                {
                                    toSave.put(pendingObjects.get(i));
                                    toRemove.add(pendingObjects.get(i));
                                }

                                File f = new File(pendingFolder, "pending_" + l + ".json");

                                while (f.exists())
                                {
                                    l += r.nextInt(10);

                                    f = new File(pendingFolder, "pending_" + l + ".json");
                                }

                                byte[] jsonBytes = toSave.toString().getBytes("UTF-8");

                                EncryptionManager.getInstance().writeToEncryptedStream(me.getContext(),
                                        new FileOutputStream(f), jsonBytes, me.encryptData(prefs));

                                pendingObjects.removeAll(toRemove);
                            }

                            if (wasSuccessful == false && me._failCount < MAX_RETRIES)
                            {

                            }
                            else
                            {
                                Editor e = prefs.edit();
                                e.putLong(HttpUploadPlugin.LAST_UPLOAD_TIME, System.currentTimeMillis());
                                e.putLong(HttpUploadPlugin.LAST_UPLOAD_SIZE, payloadSize);
                                e.apply();
                            }

                            String message = me.getContext().getString(R.string.notify_running);
                            String messageTitle = me.getContext().getString(R.string.notify_running_title);

                            builder = new NotificationCompat.Builder(me.getContext());
                            builder.setContentTitle(messageTitle);
                            builder.setContentText(message);
                            builder.setContentIntent(contentIntent);
                            builder.setSmallIcon(R.drawable.ic_note_normal);
                            builder.setWhen(System.currentTimeMillis());
                            builder.setColor(0xff4e015c);

                            note = builder.build();

                            note.flags = Notification.FLAG_ONGOING_EVENT;

                            // noteManager.notify(12345, note);
                        } catch (IOException e)
                        {
                            LogManager.getInstance(me.getContext()).logException(e);
                        }
                        catch (KeyStoreException e)
                        {
                            LogManager.getInstance(me.getContext()).logException(e);
                        }
                        catch (CertificateException e)
                        {
                            LogManager.getInstance(me.getContext()).logException(e);
                        }
                        catch (KeyManagementException e)
                        {
                            LogManager.getInstance(me.getContext()).logException(e);
                        } catch (Exception e)
                        {
                            LogManager.getInstance(me.getContext()).logException(e);
                        }
                        finally
                        {
                            me.logSuccess(wasSuccessful);
                        }
                    }

                    me._uploading = false;

                    filenames = pendingFolder.list(new FilenameFilter()
                    {
                        public boolean accept(File dir, String filename)
                        {
                            return filename.endsWith(".json");
                        }
                    });

                    if (filenames == null)
                        filenames = new String[0];

                    if (me._failCount < MAX_RETRIES && filenames.length > 0)
                    {
                        me._lastUpload = 0;

                        try
                        {
                            Thread.sleep(500);
                            me.uploadPendingObjects();
                        }
                        catch (InterruptedException e)
                        {

                        }
                    }
                    else if (me._failCount == 0)
                        me.broadcastMessage(R.string.message_reading_complete, false);
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    protected boolean enableArchive(SharedPreferences prefs)
    {
        return this.coerceBoolean(prefs, "config_http_archive", false);
    }

    protected boolean coerceBoolean(SharedPreferences prefs, String key, boolean defaultValue)
    {
        try
        {
            return prefs.getBoolean(key, defaultValue);
        }
        catch (ClassCastException e)
        {
            String enabled = prefs.getString(key, "" + defaultValue).toLowerCase(Locale.ENGLISH);

            boolean coerced = ("false".equals(enabled) == false);

            Editor edit = prefs.edit();
            edit.putBoolean(key, coerced);
            edit.apply();

            return coerced;
        }
    }

    protected boolean useLiberalSsl(SharedPreferences prefs)
    {
        return this.coerceBoolean(prefs, "config_http_liberal_ssl", true);
    }

    private boolean restrictToWifi(SharedPreferences prefs)
    {
        return this.coerceBoolean(prefs, "config_restrict_data_wifi", true);
    }

    private boolean encryptData(SharedPreferences prefs)
    {
        return this.coerceBoolean(prefs, "config_http_encrypt", true);
    }

    private boolean enableDataServer(SharedPreferences prefs)
    {
        return this.coerceBoolean(prefs, HttpUploadPlugin.ENABLED, HttpUploadPlugin.ENABLED_DEFAULT);
    }

    protected void broadcastMessage(int stringId, boolean log)
    {
        this.broadcastMessage(this.getContext().getResources().getString(stringId), log);
    }

    public File getPendingFolder()
    {
        SharedPreferences prefs = HttpUploadPlugin.getPreferences(this.getContext());

        File internalStorage = this.getContext().getFilesDir();

        if (this.useExternalStorage(prefs))
            internalStorage = this.getContext().getExternalFilesDir(null);

        if (internalStorage != null && !internalStorage.exists())
            internalStorage.mkdirs();

        File pendingFolder = new File(internalStorage, CACHE_DIR);

        if (pendingFolder != null && !pendingFolder.exists())
            pendingFolder.mkdirs();

        return pendingFolder;
    }

    private boolean useExternalStorage(SharedPreferences prefs)
    {
        return SettingsActivity.useExternalStorage(this.getContext());
    }

    public File getArchiveFolder()
    {
        File f = this.getPendingFolder();

        File archiveFolder = new File(f, "Archives");

        if (archiveFolder != null && !archiveFolder.exists())
            archiveFolder.mkdirs();

        return archiveFolder;
    }

    private void persistJSONObject(final JSONObject jsonObject)
    {
        long now = System.currentTimeMillis();

        this._lastSave = now;

        File pendingFolder = this.getPendingFolder();

        String filename = now + ".json";

        File f = new File(pendingFolder, filename);

        HashSet<String> toRemove = new HashSet<>();
        HashSet<String> invalidRemove = new HashSet<>();

        JSONArray saveArray = new JSONArray();

        synchronized (this._pendingSaves)
        {
            for (String jsonString : this._pendingSaves)
            {
                try
                {
                    JSONObject json = new JSONObject(jsonString);

                    if (saveArray.length() < 256)
                    {
                        saveArray.put(json);

                        toRemove.add(jsonString);
                    }
                }
                catch (JSONException | OutOfMemoryError e)
                {
                    LogManager.getInstance(this.getContext()).logException(e);
                    invalidRemove.add(jsonString);
                }
            }

            this._pendingSaves.removeAll(invalidRemove);
        }

        try
        {
            byte[] jsonBytes = saveArray.toString().getBytes("UTF-8");

            SharedPreferences prefs = HttpUploadPlugin.getPreferences(this.getContext());

            EncryptionManager.getInstance().writeToEncryptedStream(this.getContext(), new FileOutputStream(f),
                    jsonBytes, this.encryptData(prefs));

            this._accumulationSum += jsonBytes.length;

            if (now - this._lastAccumulationMeasure > 10000)
            {
                long duration = (now - this._lastAccumulationMeasure) / 1000;

                this._accumulation = this._accumulationSum / duration;

                this._accumulationSum = 0;
                this._lastAccumulationMeasure = now;
            }

            synchronized (this._pendingSaves)
            {
                this._pendingSaves.removeAll(toRemove);
            }
        } catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
        catch (OutOfMemoryError | IOException e)
        {
            LogManager.getInstance(this.getContext()).logException(e);
        }

        if (this._pendingSaves.size() > 128)
        {
            this._lastSave = 0;
            this._failCount = 0;

            final HttpUploadPlugin me = this;

            Runnable r = new Runnable()
            {
                public void run()
                {
                    me.persistJSONObject(null);
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    public void mailArchiveFiles(final Context context)
    {
        if (context instanceof Activity)
        {
            Activity activity = (Activity) context;

            activity.runOnUiThread(new Runnable()
            {
                public void run()
                {
                    Toast.makeText(context, "Packaging archive files for mailing...", Toast.LENGTH_LONG).show();
                }
            });

        }

        final HttpUploadPlugin me = this;

        Runnable r = new Runnable()
        {
            public void run()
            {
                File storage = context.getExternalCacheDir();

                if (!storage.exists())
                    storage.mkdirs();

                File pendingFolder = me.getArchiveFolder();

                final File[] pendingFiles = pendingFolder.listFiles(new FileFilter()
                {
                    public boolean accept(File file)
                    {
                        return file.getName().toLowerCase(Locale.getDefault()).endsWith(".archive");
                    }
                });

                final File zipfile = new File(storage, "archives.zip");

                final ArrayList<File> toDelete = new ArrayList<>();

                try
                {
                    ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipfile));

                    int totalWritten = 0;

                    for (int i = 0; i < pendingFiles.length && totalWritten < (MAX_UPLOAD_SIZE * 5 * 4); i++)
                    {
                        File f = pendingFiles[i];

                        String filename = f.getName();
                        FileInputStream fin = new FileInputStream(f);

                        ZipEntry entry = new ZipEntry(filename);
                        zout.putNextEntry(entry);

                        byte[] buffer = new byte[2048];
                        int read = 0;

                        while ((read = fin.read(buffer, 0, buffer.length)) != -1)
                        {
                            zout.write(buffer, 0, read);
                            totalWritten += read;
                        }

                        fin.close();

                        zout.closeEntry();

                        toDelete.add(f);
                    }

                    zout.close();
                } catch (IOException e)
                {
                    LogManager.getInstance(me.getContext()).logException(e);
                }

                if (context instanceof Activity) {
                    Activity activity = (Activity) context;

                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            AccountManager accountManager = AccountManager.get(context);

                            String email = null;

                            Account[] accounts = accountManager.getAccountsByType("com.google");

                            for (int i = 0; i < accounts.length && email == null; i++) {
                                Account account = accounts[i];

                                email = account.name;
                            }

                            Uri fileUri = Uri.fromFile(zipfile);

                            Intent sendIntent = new Intent(Intent.ACTION_SEND);
                            sendIntent.setType("application/zip");
                            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Purple Robot Archives");
                            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

                            if (email != null) {
                                String[] emails =
                                        {email};
                                sendIntent.putExtra(Intent.EXTRA_EMAIL, emails);
                            }

                            context.startActivity(sendIntent);

                            int remaining = pendingFiles.length - toDelete.size();

                            Toast.makeText(context,
                                    toDelete.size() + " archives packaged, " + remaining + " left in the device.",
                                    Toast.LENGTH_LONG).show();

                            for (File f : toDelete) {
                                f.delete();
                            }
                        }
                    });
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @SuppressLint("DefaultLocale")
    public void deleteArchiveFiles(final Context context)
    {
        final HttpUploadPlugin me = this;

        Runnable r = new Runnable()
        {
            public void run()
            {
                File pendingFolder = me.getArchiveFolder();

                if (context instanceof Activity)
                {
                    Activity activity = (Activity) context;

                    activity.runOnUiThread(new Runnable()
                    {
                        public void run()
                        {
                            Toast.makeText(context, context.getString(R.string.message_clearing_archive),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }


                try
                {
                    FileUtils.deleteDirectory(pendingFolder);
                }
                catch (IOException e)
                {
                    LogManager.getInstance(context).logException(e);
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    public int pendingFilesCount()
    {
        File pendingFolder = this.getPendingFolder();

        String[] filenames = pendingFolder.list(new FilenameFilter()
        {
            public boolean accept(File dir, String filename)
            {
                return filename.endsWith(".json");
            }
        });

        if (filenames == null)
            filenames = new String[0];

        return filenames.length;
    }

    public static void clearFiles(Context context)
    {
        try
        {
            File internalStorage = context.getFilesDir();
            File internalFolder = new File(internalStorage, CACHE_DIR);

            if (internalFolder.exists())
                FileUtils.deleteDirectory(internalFolder);

            File externalStorage = context.getExternalFilesDir(null);
            File externalFolder = new File(externalStorage, CACHE_DIR);

            if (externalFolder.exists())
                FileUtils.deleteDirectory(externalFolder);
        }
        catch (IOException e)
        {
            LogManager.getInstance(context).logException(e);
        }
    }

    public long pendingFilesSize()
    {
        File pendingFolder = this.getPendingFolder();

        String[] filenames = pendingFolder.list(new FilenameFilter()
        {
            public boolean accept(File dir, String filename)
            {
                return filename.endsWith(".json");
            }
        });

        if (filenames.length < 1024)
        {
            try
            {
                return FileUtils.sizeOf(pendingFolder);
            }
            catch (IllegalArgumentException e)
            {

            }
        }

        return 2L * 1024 * 1024 * 1024;
    }

    public boolean isEnabled(Context context)
    {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(HttpUploadPlugin.ENABLED, HttpUploadPlugin.ENABLED_DEFAULT);
    }
}
