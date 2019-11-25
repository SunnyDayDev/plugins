// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.webkit.WebViewClientCompat;
import io.flutter.plugin.common.MethodChannel;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

// We need to use WebViewClientCompat to get
// shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
// invoked by the webview on older Android devices, without it pages that use iframes will
// be broken when a navigationDelegate is set on Android version earlier than N.
class FlutterWebViewClient {
  private static final String TAG = "FlutterWebViewClient";
  private final MethodChannel methodChannel;
  private boolean hasNavigationDelegate;
  private boolean hasInterceptRequestDelegate;

  FlutterWebViewClient(MethodChannel methodChannel) {
    this.methodChannel = methodChannel;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    if (!hasNavigationDelegate) {
      return false;
    }
    notifyOnNavigationRequest(
        request.getUrl().toString(), request.getRequestHeaders(), view, request.isForMainFrame());
    // We must make a synchronous decision here whether to allow the navigation or not,
    // if the Dart code has set a navigation delegate we want that delegate to decide whether
    // to navigate or not, and as we cannot get a response from the Dart delegate synchronously we
    // return true here to block the navigation, if the Dart delegate decides to allow the
    // navigation the plugin will later make an addition loadUrl call for this url.
    //
    // Since we cannot call loadUrl for a subframe, we currently only allow the delegate to stop
    // navigations that target the main frame, if the request is not for the main frame
    // we just return false to allow the navigation.
    //
    // For more details see: https://github.com/flutter/flutter/issues/25329#issuecomment-464863209
    return request.isForMainFrame();
  }

  private boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (!hasNavigationDelegate) {
      return false;
    }
    // This version of shouldOverrideUrlLoading is only invoked by the webview on devices with
    // webview versions  earlier than 67(it is also invoked when hasNavigationDelegate is false).
    // On these devices we cannot tell whether the navigation is targeted to the main frame or not.
    // We proceed assuming that the navigation is targeted to the main frame. If the page had any
    // frames they will be loaded in the main frame instead.
    Log.w(
        TAG,
        "Using a navigationDelegate with an old webview implementation, pages with frames or iframes will not work");
    notifyOnNavigationRequest(url, null, view, true);
    return true;
  }
  
  
  @Nullable
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private WebResourceResponse shouldInterceptRequest(
      WebView view, final WebResourceRequest request) {
    if (!hasInterceptRequestDelegate) {
      return null;
    }
  
    final HashMap<String, Object> args = new HashMap<>();
    args.put("url", request.getUrl().toString());
    args.put("method", request.getMethod());
    args.put("headers", request.getRequestHeaders());

    final AtomicReference<WebResourceResponse> responseReference = new AtomicReference<>(null);
    final Semaphore sema = new Semaphore(0);
    
    view.post(new Runnable() {
      @Override
      public void run() {
        
        methodChannel.invokeMethod("interceptRequest", args, new MethodChannel.Result() {
          @Override
          public void success(Object result) {
            if (result != null) {
              WebResourceResponse response = parseWebResourceResponse((Map<String, Object>) result);
              responseReference.set(response);
            }
      
            sema.release();
          }
    
          @Override
          public void error(String errorCode, String errorMessage, Object errorDetails) {
            sema.release();
          }
    
          @Override
          public void notImplemented() {
            sema.release();
          }
        });

      }
    });
    
    try {
      sema.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return responseReference.get();
  }
  
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private WebResourceResponse parseWebResourceResponse(Map<String, Object> result) {
    int statusCode = (int) result.get("statusCode");
    String reasonPhrase = (String) result.get("reasonPhrase");
    if (reasonPhrase == null) {
      reasonPhrase = "Unspecified";
    }
    Map<String, String> headers = (Map<String, String>) result.get("headers");
    if (headers == null) {
      headers = Collections.emptyMap();
    }
    byte[] body = (byte[]) result.get("body");
    if (body == null) {
      body = new byte[0];
    }
  
    String mimeType = "*/*";
    String encoding = "utf-8";
    String contentTypeHeader = headers.get("Content-Type");
    if (contentTypeHeader == null) {
      contentTypeHeader = headers.get("content-type");
    }
    if (contentTypeHeader != null) {
      String[] contentTypeItems = contentTypeHeader.split(";");
      mimeType  = contentTypeItems[0];
      for (String item :contentTypeItems) {
        String[] itemValueParts = item.trim().split("=");
        if (itemValueParts[0].toLowerCase().equals("charset")) {
          encoding = itemValueParts[1];
        }
      }
    }
  
    return new WebResourceResponse(
        mimeType, encoding, statusCode, reasonPhrase, headers,
        new ByteArrayInputStream(body)
    );
  }

  private void onPageFinished(WebView view, String url) {
    Map<String, Object> args = new HashMap<>();
    args.put("url", url);
    methodChannel.invokeMethod("onPageFinished", args);
  }

  private void notifyOnNavigationRequest(
      String url, Map<String, String> headers, WebView webview, boolean isMainFrame) {
    HashMap<String, Object> args = new HashMap<>();
    args.put("url", url);
    args.put("isForMainFrame", isMainFrame);
    if (isMainFrame) {
      methodChannel.invokeMethod(
          "navigationRequest", args, new OnNavigationRequestResult(url, headers, webview));
    } else {
      methodChannel.invokeMethod("navigationRequest", args);
    }
  }

  // This method attempts to avoid using WebViewClientCompat due to bug
  // https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
  // https://github.com/flutter/flutter/issues/29446.
  WebViewClient createWebViewClient(
      boolean hasNavigationDelegate,
      boolean hasInterceptRequestDelegate
  ) {
    this.hasNavigationDelegate = hasNavigationDelegate;
    this.hasInterceptRequestDelegate  = hasInterceptRequestDelegate;

    if (!hasNavigationDelegate || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return internalCreateWebViewClient();
    }

    return internalCreateWebViewClientCompat();
  }

  private WebViewClient internalCreateWebViewClient() {
    return new WebViewClient() {
      @TargetApi(Build.VERSION_CODES.N)
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }
  
      @Nullable
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldInterceptRequest(view, request);
      }
    };
  }

  private WebViewClientCompat internalCreateWebViewClientCompat() {
    return new WebViewClientCompat() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, url);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }
    };
  }

  private static class OnNavigationRequestResult implements MethodChannel.Result {
    private final String url;
    private final Map<String, String> headers;
    private final WebView webView;

    private OnNavigationRequestResult(String url, Map<String, String> headers, WebView webView) {
      this.url = url;
      this.headers = headers;
      this.webView = webView;
    }

    @Override
    public void success(Object shouldLoad) {
      Boolean typedShouldLoad = (Boolean) shouldLoad;
      if (typedShouldLoad) {
        loadUrl();
      }
    }

    @Override
    public void error(String errorCode, String s1, Object o) {
      throw new IllegalStateException("navigationRequest calls must succeed");
    }

    @Override
    public void notImplemented() {
      throw new IllegalStateException(
          "navigationRequest must be implemented by the webview method channel");
    }

    private void loadUrl() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.loadUrl(url, headers);
      } else {
        webView.loadUrl(url);
      }
    }
  }
}
