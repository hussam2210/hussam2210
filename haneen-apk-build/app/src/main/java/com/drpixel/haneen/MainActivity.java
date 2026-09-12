package com.drpixel.haneen;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String PREFS = "haneen_settings";
    private static final String KEY_URL = "gateway_url";
    private static final String DEFAULT_URL = "http://192.168.10.8:8787";
    private static final int DISCOVERY_PORT = 8788;
    private static final String DISCOVERY_MAGIC = "DRPIXEL_SYNC_DISCOVER_V1";

    private WebView web;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        web.addJavascriptInterface(new Bridge(), "HaneenBridge");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, 42);
                    }
                    request.grant(request.getResources());
                });
            }
        });
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 42);
        }
        web.loadUrl("file:///android_asset/index.html");
    }

    private String normalize(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        if (v.isEmpty()) v = DEFAULT_URL;
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://" + v;
        String hostPart = v.substring(v.indexOf("://") + 3);
        if (!hostPart.matches(".*:\\d+$")) v = v + ":8787";
        return v;
    }

    private void saveBaseUrl(String value) {
        prefs.edit().putString(KEY_URL, normalize(value)).apply();
    }

    private InetAddress directedBroadcastAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wifi == null ? null : wifi.getDhcpInfo();
            if (dhcp == null) return null;
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) quads[k] = (byte) ((broadcast >> (k * 8)) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            return null;
        }
    }

    private String discoverGatewayNow() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(2200);
            byte[] payload = DISCOVERY_MAGIC.getBytes(StandardCharsets.UTF_8);

            InetAddress global = InetAddress.getByName("255.255.255.255");
            socket.send(new DatagramPacket(payload, payload.length, global, DISCOVERY_PORT));

            InetAddress directed = directedBroadcastAddress();
            if (directed != null && !directed.equals(global)) {
                socket.send(new DatagramPacket(payload, payload.length, directed, DISCOVERY_PORT));
            }

            byte[] buf = new byte[2048];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            String text = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(text);
            if (!"DRPixelSyncGateway".equals(json.optString("service"))) return null;
            String url = normalize(json.optString("url"));
            return url.isEmpty() ? null : url;
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    public class Bridge {
        @JavascriptInterface public String getBaseUrl() {
            return normalize(prefs.getString(KEY_URL, DEFAULT_URL));
        }

        @JavascriptInterface public void saveBaseUrl(String value) {
            MainActivity.this.saveBaseUrl(value);
        }

        @JavascriptInterface public void discoverGateway() {
            new Thread(() -> {
                String found = discoverGatewayNow();
                if (found != null) saveBaseUrl(found);
                final String value = found == null ? "" : found;
                runOnUiThread(() -> web.evaluateJavascript("window.haneenDiscoveryResult(" + JSONObject.quote(value) + ")", null));
            }).start();
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
