import {NativeEventEmitter, NativeModules} from 'react-native';
import type {EventSubscription} from 'react-native/Libraries/vendor/emitter/EventEmitter';

interface NativeLocaleManagerSpec {
  +getLocale: () => Promise<string>;
}

export const NativeLocaleManager: ?NativeLocaleManagerSpec =
  NativeModules.LocaleManager;

type LocalePreferences = {|
  locale: ?string,
|};

type LocaleChangeListener = (preferences: LocalePreferences) => void;

const nativeEventEmitter = NativeLocaleManager
  ? new NativeEventEmitter(NativeLocaleManager)
  : null;

export default {
  getLocale(): Promise<string> {
    return NativeLocaleManager
      ? NativeLocaleManager.getLocale()
      : new Promise((resolve, reject) => {
          reject('NativeLocaleManager is null');
        });
  },

  addChangeListener(listener: LocaleChangeListener): ?EventSubscription {
    if (nativeEventEmitter) {
      return nativeEventEmitter.addListener('localeChanged', listener);
    }
  },
};
