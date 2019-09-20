package io.flutter.plugins.webviewflutter;

import android.app.Activity;

import java.lang.ref.WeakReference;

class WeakActivityProvider implements ActivityRegistar.ActivityProvider {

  private final WeakReference<Activity> weakActivity;

  WeakActivityProvider(Activity activity) {
    this.weakActivity = new WeakReference<>(activity);
  }

  @Override
  public Activity activity() {
    return weakActivity.get();
  }

}
