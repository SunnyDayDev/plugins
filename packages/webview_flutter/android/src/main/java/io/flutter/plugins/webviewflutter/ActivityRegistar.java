package io.flutter.plugins.webviewflutter;

import android.app.Activity;
import android.content.Intent;

import java.util.HashSet;
import java.util.Set;

import io.flutter.plugin.common.PluginRegistry;

class ActivityRegistar implements PluginRegistry.ActivityResultListener {

    private final ActivityProvider activityProvider;

    private final Set<PluginRegistry.ActivityResultListener> resultListeners = new HashSet<>();

    ActivityRegistar(ActivityProvider activityProvider) {
        this.activityProvider = activityProvider;
    }

    Activity activity() {
        return activityProvider.activity();
    }

    public void addActivityResultListener(PluginRegistry.ActivityResultListener listener) {
        resultListeners.add(listener);
    }

    public void removeActivityResultListener(PluginRegistry.ActivityResultListener listener) {
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

        Activity activity();

    }

}
