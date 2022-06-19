package com.simpleblog;

import android.content.res.Configuration;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.ReactContext;
import com.simpleblog.react.modules.LocaleManagerModule;

public class MainActivity extends ReactActivity {

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "SimpleBlog";
    }

    /**
     * Returns the instance of the {@link ReactActivityDelegate}. There the RootView is created and
     * you can specify the rendered you wish to use (Fabric or the older renderer).
     */
    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        return new MainActivityDelegate(this, getMainComponentName());
    }

    public static class MainActivityDelegate extends ReactActivityDelegate {
        public MainActivityDelegate(ReactActivity activity, String mainComponentName) {
            super(activity, mainComponentName);
        }

        @Override
        protected ReactRootView createRootView() {
            ReactRootView reactRootView = new ReactRootView(getContext());
            // If you opted-in for the New Architecture, we enable the Fabric Renderer.
            reactRootView.setIsFabric(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED);
            return reactRootView;
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);

            if (getReactNativeHost().hasInstance()) {
                final ReactContext context = getReactInstanceManager().getCurrentReactContext();
                if (context != null) {
                    final LocaleManagerModule localeManagerModule = context.getNativeModule(LocaleManagerModule.class);
                    if (localeManagerModule != null) {
                        localeManagerModule.onConfigurationChanged(newConfig);
                    }
                }
            }
        }
    }
}
