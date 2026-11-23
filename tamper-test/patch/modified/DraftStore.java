package com.example.soalpintar;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class DraftStore {
    private static final long MAX_AGE_MS = 259200000;
    private static final int MAX_URL_CHARS = 7500;
    private static final String PREFS = "soalpintar_draft";

    private DraftStore() {
    }

    private static JSONArray clean(JSONArray jSONArray) {
        JSONArray jSONArray2 = new JSONArray();
        if (jSONArray != null) {
            for (int i = 0; i < jSONArray.length(); i++) {
                String optString = jSONArray.optString(i, "");
                if (!optString.isEmpty()) {
                    jSONArray2.put(optString);
                }
            }
        }
        return jSONArray2;
    }

    public static void clear(Context context, String str) {
        prefs(context).edit().remove("draft|" + norm(str)).apply();
    }

    public static boolean has(Context context, String str) {
        return load(context, str).length() > 0;
    }

    public static JSONObject load(Context context, String str) {
        JSONObject optJSONObject;
        try {
            String string = prefs(context).getString("draft|" + norm(str), null);
            if (string == null) {
                return new JSONObject();
            }
            JSONObject jSONObject = new JSONObject(string);
            return (System.currentTimeMillis() - jSONObject.optLong("at", 0L) <= MAX_AGE_MS && (optJSONObject = jSONObject.optJSONObject("entries")) != null) ? optJSONObject : new JSONObject();
        } catch (JSONException unused) {
            return new JSONObject();
        }
    }

    public static void merge(Context context, String str, String str2) {
        if (str == null || str2 == null) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(str2);
            JSONObject load = load(context, str);
            Iterator<String> keys = jSONObject.keys();
            while (keys.hasNext()) {
                String next = keys.next();
                if (next.startsWith("entry.") && !next.endsWith("_sentinel")) {
                    JSONArray clean = clean(jSONObject.optJSONArray(next));
                    if (clean.length() == 0) {
                        load.remove(next);
                    } else {
                        load.put(next, clean);
                    }
                }
            }
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("at", System.currentTimeMillis());
            jSONObject2.put("entries", load);
            prefs(context).edit().putString("draft|" + norm(str), jSONObject2.toString()).apply();
        } catch (JSONException unused) {
        }
    }

    private static String norm(String str) {
        return str == null ? "" : str.trim().toUpperCase();
    }

    public static String prefillUrl(Context context, String str, String str2) {
        int indexOf;
        if (str2 == null || (indexOf = str2.indexOf("/viewform")) < 0) {
            return null;
        }
        JSONObject load = load(context, str);
        if (load.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(str2.substring(0, indexOf + "/viewform".length()));
        sb.append("?usp=pp_url");
        Iterator<String> keys = load.keys();
        while (keys.hasNext()) {
            String next = keys.next();
            JSONArray optJSONArray = load.optJSONArray(next);
            if (optJSONArray != null) {
                for (int i = 0; i < optJSONArray.length(); i++) {
                    String str3 = "&" + Uri.encode(next) + "=" + Uri.encode(optJSONArray.optString(i, ""));
                    if (sb.length() + str3.length() <= MAX_URL_CHARS) {
                        sb.append(str3);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static void setViolations(Context context, String str, int i) {
        prefs(context).edit().putInt("viol|" + norm(str), Math.max(0, i)).apply();
    }

    public static int violations(Context context, String str) {
        return prefs(context).getInt("viol|" + norm(str), 0);
    }
}
