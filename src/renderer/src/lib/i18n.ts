import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import ru from "@/locales/ru.json";
import en from "@/locales/en.json";
import uk from "@/locales/uk.json";

export const SUPPORTED_LOCALES = ["ru", "en", "uk"] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

const STORAGE_KEY = "hibiki-language";

function isLocale(value: string): value is Locale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

function detectInitialLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored && isLocale(stored)) return stored;
  const browserLang = navigator.language.slice(0, 2).toLowerCase();
  if (isLocale(browserLang)) return browserLang;
  return "ru";
}

i18n.use(initReactI18next).init({
  resources: {
    ru: { translation: ru },
    en: { translation: en },
    uk: { translation: uk },
  },
  lng: detectInitialLocale(),
  fallbackLng: "ru",
  interpolation: { escapeValue: false },
});

export function setLocale(locale: Locale): void {
  i18n.changeLanguage(locale);
  localStorage.setItem(STORAGE_KEY, locale);
}

export default i18n;
