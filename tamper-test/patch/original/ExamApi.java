package com.example.soalpintar;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.soalpintar.ExamApi;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class ExamApi {
    private static final String PREFS = "soalpintar_prefs";
    private static final String TAG = "ExamApi";
    private static RequestQueue queue;

    public static final class Identity {
        public final String absen;
        public final String kelas;
        public final String name;

        public Identity(String str, String str2, String str3) {
            this.name = str == null ? "" : str.trim();
            this.kelas = str2 == null ? "" : str2.trim();
            this.absen = str3 != null ? str3.trim() : "";
        }

        public boolean isComplete() {
            return (this.name.isEmpty() || this.kelas.isEmpty() || this.absen.isEmpty()) ? false : true;
        }
    }

    public interface StatusCallback {
        void onResult(Boolean bool);
    }

    private ExamApi() {
    }

    public static String baseUrl(Context context) {
        return AppConfig.apiBase(context);
    }

    public static String deviceId(Context context) {
        String string = Settings.Secure.getString(context.getContentResolver(), "android_id");
        if (string != null && !string.isEmpty() && !"9774d56d682e549c".equals(string)) {
            return string;
        }
        SharedPreferences prefs = prefs(context);
        String string2 = prefs.getString("fallback_device_id", null);
        if (string2 != null) {
            return string2;
        }
        String uuid = UUID.randomUUID().toString();
        prefs.edit().putString("fallback_device_id", uuid).apply();
        return uuid;
    }

    public static void deviceStatus(Context context, String str, final StatusCallback statusCallback) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(0, Uri.parse(baseUrl(context) + "exam/" + Uri.encode(str) + "/device-status").buildUpon().appendQueryParameter("device_id", deviceId(context)).build().toString(), null, new Response.Listener() { // from class: com.example.soalpintar.ExamApi$$ExternalSyntheticLambda2
            @Override // com.android.volley.Response.Listener
            public final void onResponse(Object obj) {
                ExamApi.StatusCallback.this.onResult(Boolean.valueOf(((JSONObject) obj).optBoolean("blocked", false)));
            }
        }, new Response.ErrorListener() { // from class: com.example.soalpintar.ExamApi$$ExternalSyntheticLambda3
            @Override // com.android.volley.Response.ErrorListener
            public final void onErrorResponse(VolleyError volleyError) {
                ExamApi.StatusCallback.this.onResult(null);
            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1.5f));
        jsonObjectRequest.setShouldCache(false);
        queue(context).add(jsonObjectRequest);
    }

    public static String errorCode(VolleyError volleyError) {
        if (volleyError.networkResponse == null) {
            return "network";
        }
        try {
            return new JSONObject(new String(volleyError.networkResponse.data, StandardCharsets.UTF_8)).optString("code", String.valueOf(volleyError.networkResponse.statusCode));
        } catch (Exception unused) {
            return String.valueOf(volleyError.networkResponse.statusCode);
        }
    }

    public static String errorMessage(VolleyError volleyError, String str) {
        NetworkResponse networkResponse = volleyError.networkResponse;
        if (networkResponse == null) {
            return "Tidak dapat terhubung ke server. Periksa koneksi internet lalu coba lagi.";
        }
        try {
            String optString = new JSONObject(new String(networkResponse.data, StandardCharsets.UTF_8)).optString("message", "");
            if (!optString.isEmpty()) {
                return optString;
            }
        } catch (Exception unused) {
        }
        return networkResponse.statusCode == 404 ? "Token tidak ditemukan. Periksa kembali token dari guru." : networkResponse.statusCode == 429 ? "Kuota peserta ujian sedang penuh. Tunggu sebentar lalu coba lagi." : str;
    }

    static /* synthetic */ void lambda$sendEvent$0(String str, Runnable runnable, JSONObject jSONObject) {
        Log.d(TAG, str + " terkirim");
        if (runnable != null) {
            runnable.run();
        }
    }

    public static Identity loadIdentity(Context context) {
        SharedPreferences prefs = prefs(context);
        return new Identity(prefs.getString("student_name", ""), prefs.getString("student_class", ""), prefs.getString("student_absen", ""));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static RequestQueue queue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context.getApplicationContext());
        }
        return queue;
    }

    public static void saveIdentity(Context context, Identity identity) {
        prefs(context).edit().putString("student_name", identity.name).putString("student_class", identity.kelas).putString("student_absen", identity.absen).apply();
    }

    public static void sendEvent(Context context, String str, String str2, String str3, int i) {
        sendEvent(context, str, str2, str3, i, null);
    }

    public static void sendEvent(Context context, String str, final String str2, String str3, int i, final Runnable runnable) {
        int i2;
        try {
            Identity loadIdentity = loadIdentity(context);
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("device_id", deviceId(context));
            jSONObject.put("name", loadIdentity.name);
            jSONObject.put("class", loadIdentity.kelas);
            jSONObject.put("absen", loadIdentity.absen);
            if (str3 != null) {
                jSONObject.put("reason", str3);
            }
            if (i > 0) {
                jSONObject.put("count", i);
            }
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(1, baseUrl(context) + "exam/" + Uri.encode(str) + "/" + str2, jSONObject, new Response.Listener() { // from class: com.example.soalpintar.ExamApi$$ExternalSyntheticLambda0
                @Override // com.android.volley.Response.Listener
                public final void onResponse(Object obj) {
                    ExamApi.lambda$sendEvent$0(str2, runnable, (JSONObject) obj);
                }
            }, new Response.ErrorListener() { // from class: com.example.soalpintar.ExamApi$$ExternalSyntheticLambda1
                @Override // com.android.volley.Response.ErrorListener
                public final void onErrorResponse(VolleyError volleyError) {
                    Log.w(ExamApi.TAG, str2 + " gagal: " + volleyError);
                }
            });
            if (!"block".equals(str2) && !"complete".equals(str2)) {
                i2 = 2;
                jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(8000, i2, 1.5f));
                jsonObjectRequest.setShouldCache(false);
                queue(context).add(jsonObjectRequest);
            }
            i2 = 4;
            jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(8000, i2, 1.5f));
            jsonObjectRequest.setShouldCache(false);
            queue(context).add(jsonObjectRequest);
        } catch (JSONException e) {
            Log.e(TAG, "sendEvent JSON error: " + e.getMessage());
        }
    }

    public static String validateUrl(Context context, String str, Identity identity) {
        return Uri.parse(baseUrl(context) + "validate-token/" + Uri.encode(str)).buildUpon().appendQueryParameter("device_id", deviceId(context)).appendQueryParameter("name", identity.name).appendQueryParameter("class", identity.kelas).appendQueryParameter("absen", identity.absen).build().toString();
    }
}
