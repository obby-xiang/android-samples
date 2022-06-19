import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import en_US from './locales/en_US';
import zh_CN from './locales/zh_CN';

const resources = {
  en_US: {
    translation: en_US,
  },
  zh_CN: {
    translation: zh_CN,
  },
};

i18n
  .use(initReactI18next)
  .init({
    resources,
    compatibilityJSON: 'v3',
    fallbackLng: 'en_US',
    debug: __DEV__,
    interpolation: {
      escapeValue: false,
    },
  })
  .then(() => {
    console.log('Successfully to init i18n');
  })
  .catch(error => {
    console.error('Failed to init i18n');
  });

export default i18n;
