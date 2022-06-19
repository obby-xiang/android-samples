package com.simpleblog.react.modules;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Locale;
import java.util.Objects;

@ReactModule(name = LocaleManagerModule.NAME)
public class LocaleManagerModule extends ReactContextBaseJavaModule {
    public static final String NAME = "LocaleManager";

    private static final String LOCALE_CHANGED_EVENT_NAME = "localeChanged";

    @Nullable
    private Locale mLocale;

    public LocaleManagerModule(@Nullable ReactApplicationContext reactContext) {
        super(reactContext);
        mLocale = getLocaleFromContext(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void getLocale(@NonNull final Promise promise) {
        mLocale = getLocaleFromContext(getReactApplicationContext());
        promise.resolve(stringifyLocale(mLocale));
    }

    @ReactMethod
    public void addListener(final String eventName) {
    }

    @ReactMethod
    public void removeListeners(final double count) {
    }

    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        final Locale newLocale = getLocaleFromConfiguration(newConfig);
        if (!Objects.equals(mLocale, newLocale)) {
            mLocale = newLocale;
            emitLocaleChanged(mLocale);
        }
    }

    public void emitLocaleChanged(@Nullable final Locale locale) {
        final WritableMap localePreferences = Arguments.createMap();
        localePreferences.putString("locale", stringifyLocale(locale));

        final ReactApplicationContext reactApplicationContext = getReactApplicationContextIfActiveOrWarn();
        if (reactApplicationContext != null) {
            reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(LOCALE_CHANGED_EVENT_NAME, localePreferences);
        }
    }

    @Nullable
    private Locale getLocaleFromContext(@Nullable final Context context) {
        if (context == null) {
            return null;
        }

        final Resources resources = context.getResources();
        if (resources == null) {
            return null;
        }

        return getLocaleFromConfiguration(resources.getConfiguration());
    }

    @Nullable
    private Locale getLocaleFromConfiguration(@Nullable final Configuration configuration) {
        return configuration == null ? null : ConfigurationCompat.getLocales(configuration).get(0);
    }

    @Nullable
    private String stringifyLocale(@Nullable final Locale locale) {
        if (locale == null) {
            return null;
        }

        final String language = locale.getLanguage();
        final String country = locale.getCountry();

        if (country.isEmpty()) {
            return language;
        }

        return language + "_" + country;
    }
}
