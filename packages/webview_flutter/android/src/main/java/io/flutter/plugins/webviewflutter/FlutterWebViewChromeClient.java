package io.flutter.plugins.webviewflutter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.HashMap;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

class FlutterWebViewChromeClient extends WebChromeClient implements PluginRegistry.ActivityResultListener {

    private final ActivityWrapper activityWrapper;
    private final MethodChannel methodChannel;

    private FileChooserResultListener activeRequest;

    FlutterWebViewChromeClient(ActivityWrapper activityWrapper, MethodChannel methodChannel) {
        this.activityWrapper = activityWrapper;
        this.methodChannel = methodChannel;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("message", message);
        methodChannel.invokeMethod("javascriptAlert", arguments);
        result.confirm();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(
        WebView webView,
        final ValueCallback<Uri[]> filePathCallback,
        final FileChooserParams fileChooserParams
    ) {
        cancelCurrentRequest();

        Activity activity = activityWrapper.activity();

        if (activity == null) return false;

        activeRequest = new FileChooserResultListener() {
            @Override
            public void accept(int resultCode, @NonNull Intent data) {
                filePathCallback.onReceiveValue(FileChooserParams.parseResult(resultCode, data));
            }

            @Override
            public void cancel() {
                filePathCallback.onReceiveValue(null);
            }

        };

        Intent intent = fileChooserParams.createIntent();
        try {
            activity.startActivityForResult(intent, 1001);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
        FileChooserResultListener resultListener = activeRequest;

        if (resultListener == null)
            return false;

        resultListener.accept(resultCode, intent);
        activeRequest = null;

        return true;
    }

    private void cancelCurrentRequest() {
        if (activeRequest != null) {
            activeRequest.cancel();
            activeRequest = null;
        }
    }

    private interface FileChooserResultListener {

        void accept(int resultCode, @NonNull Intent data);

        void cancel();

    }

}
