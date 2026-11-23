package com.example.soalpintar;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class AppConfig {
    private static final String[] ALLOWED_DOMAINS = {"solusiedu.id"};
    private static final String PREFS = "soalpintar_config";
    private static final String PRODUCTION_API_BASE = "https://soalpintar.solusiedu.id/api/";
    private static final String TAG = "AppConfig";
    public final String announcement;
    public final String apiBase;
    public final int heartbeatSeconds;
    public final boolean maintenance;
    public final String maintenanceMessage;
    public final int minVersionCode;
    public final String updateMessage;
    public final String updateUrl;

    public interface Callback {
        void onConfig(AppConfig appConfig);
    }

    private AppConfig(JSONObject jSONObject, String str) {
        String optString = jSONObject.optString("api_base", "");
        this.apiBase = isAllowedApiBase(optString) ? optString : str;
        JSONObject optJSONObject = jSONObject.optJSONObject("maintenance");
        this.maintenance = optJSONObject != null && optJSONObject.optBoolean("enabled", false);
        this.maintenanceMessage = optJSONObject != null ? optJSONObject.optString("message", "") : "";
        this.minVersionCode = jSONObject.optInt("min_version_code", 0);
        this.updateUrl = jSONObject.optString("update_url", "");
        this.updateMessage = jSONObject.optString("update_message", "");
        this.announcement = jSONObject.optString("announcement", "");
        this.heartbeatSeconds = Math.max(60, Math.min(600, jSONObject.optInt("heartbeat_seconds", 180)));
    }

    public static String apiBase(Context context) {
        return isCustomTestBuild() ? "https://soalpintar.solusiedu.id/api/" : cached(context).apiBase;
    }

    public static AppConfig cached(Context context) {
        try {
            return new AppConfig(new JSONObject(prefs(context).getString("config_json", "{}")), "https://soalpintar.solusiedu.id/api/");
        } catch (Exception unused) {
            return new AppConfig(new JSONObject(), "https://soalpintar.solusiedu.id/api/");
        }
    }

    private static void fetch(final Context context, final String str, final boolean z, final Callback callback) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(0, str + "app-config", null, new Response.Listener() { // from class: com.example.soalpintar.AppConfig$$ExternalSyntheticLambda0
            @Override // com.android.volley.Response.Listener
            public final void onResponse(Object obj) {
                AppConfig.lambda$fetch$0(context, callback, (JSONObject) obj);
            }
        }, new Response.ErrorListener() { // from class: com.example.soalpintar.AppConfig$$ExternalSyntheticLambda1
            @Override // com.android.volley.Response.ErrorListener
            public final void onErrorResponse(VolleyError volleyError) {
                AppConfig.lambda$fetch$1(str, z, context, callback, volleyError);
            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(7000, 1, 1.5f));
        jsonObjectRequest.setShouldCache(false);
        ExamApi.queue(context).add(jsonObjectRequest);
    }

    static boolean isAllowedApiBase(String str) {
        if (str != null && !str.isEmpty()) {
            Uri parse = Uri.parse(str);
            if ("https".equals(parse.getScheme()) && parse.getHost() != null && str.endsWith("/api/")) {
                String lowerCase = parse.getHost().toLowerCase();
                for (String str2 : ALLOWED_DOMAINS) {
                    if (lowerCase.equals(str2) || lowerCase.endsWith("." + str2)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isCustomTestBuild() {
        return false;
    }

    static /* synthetic */ void lambda$fetch$0(Context context, Callback callback, JSONObject jSONObject) {
        prefs(context).edit().putString("config_json", jSONObject.toString()).apply();
        callback.onConfig(new AppConfig(jSONObject, "https://soalpintar.solusiedu.id/api/"));
    }

    static /* synthetic */ void lambda$fetch$1(String str, boolean z, Context context, Callback callback, VolleyError volleyError) {
        Log.w(TAG, "app-config gagal dari " + str + ": " + volleyError);
        if (!z || str.equals("https://soalpintar.solusiedu.id/api/")) {
            callback.onConfig(cached(context));
        } else {
            fetch(context, "https://soalpintar.solusiedu.id/api/", false, callback);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static void refresh(Context context, Callback callback) {
        fetch(context, apiBase(context), true, callback);
    }

    public boolean needsUpdate() {
        int i = this.minVersionCode;
        return i > 0 && 4 < i;
    }
}
