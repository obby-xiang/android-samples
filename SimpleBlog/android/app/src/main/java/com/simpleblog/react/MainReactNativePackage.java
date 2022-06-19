package com.simpleblog.react;

import androidx.annotation.NonNull;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.annotations.ReactModuleList;
import com.facebook.react.uimanager.ViewManager;
import com.simpleblog.react.modules.LocaleManagerModule;

import java.util.Collections;
import java.util.List;

@ReactModuleList(nativeModules = {LocaleManagerModule.class})
public class MainReactNativePackage implements ReactPackage {
    @NonNull
    @Override
    public List<NativeModule> createNativeModules(@NonNull ReactApplicationContext reactContext) {
        return Collections.<NativeModule>singletonList(new LocaleManagerModule(reactContext));
    }

    @NonNull
    @Override
    public List<ViewManager> createViewManagers(@NonNull ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}
