export const DEFAULT_TIMEZONE = 'Asia/Ho_Chi_Minh'

export const SUPPORTED_LOCALES = ['vi-VN', 'en'] as const
export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: AppLocale = 'vi-VN'
export const LOCALE_STORAGE_KEY = 'skillpath.locale'

export function isAppLocale(value: unknown): value is AppLocale {
  return SUPPORTED_LOCALES.includes(value as AppLocale)
}

export function getPreferredLocale(): AppLocale {
  if (typeof window === 'undefined') return DEFAULT_LOCALE
  const saved = window.localStorage.getItem(LOCALE_STORAGE_KEY)
  return isAppLocale(saved) ? saved : DEFAULT_LOCALE
}
