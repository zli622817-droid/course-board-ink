package com.eego.courseboardbeta;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MainActivity extends Activity {
    private WebView wv;
    private ConfigServer server;
    private String currentView = "board";

    public class JsBridge {
        @JavascriptInterface
        public String startConfig() {
            String ip = getLocalIpAddress();
            if (ip == null) return null;
            stopConfig();
            server = new ConfigServer(9092, MainActivity.this, wv);
            server.start();
            return "http://" + ip + ":9092/";
        }
        @JavascriptInterface
        public void stopConfig() {
            if (server != null) {
                server.stopServer();
                server = null;
            }
        }
        @JavascriptInterface
        public void saveCourses(String json) {
            getSharedPreferences("courseboard", MODE_PRIVATE).edit().putString("courses_json", json).commit();
        }
        @JavascriptInterface
        public void setView(String v) {
            currentView = v;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(R.layout.activity_main);
        wv = (WebView) findViewById(R.id.webview);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        wv.setPadding(0, 0, 0, 0);
        wv.addJavascriptInterface(new JsBridge(), "Android");
        wv.loadUrl("file:///android_asset/courses.html");
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                Enumeration<InetAddress> en2 = ni.getInetAddresses();
                while (en2.hasMoreElements()) {
                    InetAddress addr = en2.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stopServer();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            wv.evaluateJavascript("prevDay()", null);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            wv.evaluateJavascript("nextDay()", null);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!"board".equals(currentView)) {
                wv.evaluateJavascript("showBoard()", null);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
