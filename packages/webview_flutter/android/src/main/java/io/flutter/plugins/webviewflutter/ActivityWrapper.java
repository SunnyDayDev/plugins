package io.flutter.plugins.webviewflutter;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

import io.flutter.plugin.common.PluginRegistry;

class ActivityWrapper implements PluginRegistry.ActivityResultListener {

    private final ActivityProvider provider;

    private final Set<PluginRegistry.ActivityResultListener> resultListeners = new HashSet<>();

    ActivityWrapper(ActivityProvider registar) {
        this.provider = registar;
    }

    Activity activity() {
        return provider.getActivity();
    }

    void addActivityResultListener(PluginRegistry.ActivityResultListener listener) {
        resultListeners.add(listener);
    }

    void removeActivityResultListener(PluginRegistry.ActivityResultListener listener) {
        resultListeners.remove(listener);
    }

    @Override
    public boolean onActivityResult(int i, int i1, Intent intent) {
        boolean handled = false;
        for (PluginRegistry.ActivityResultListener resultListener: resultListeners) {
            handled = handled || resultListener.onActivityResult(i, i1, intent);
        }
        return handled;
    }

    interface ActivityProvider {

        @Nullable Activity getActivity();

    }

}
