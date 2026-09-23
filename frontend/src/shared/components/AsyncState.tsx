import { ApiError } from '../api/client'
import { useI18n, type TranslationKey } from '../i18n/I18n'

const ERROR_KEYS: Partial<Record<string, TranslationKey>> = {
  UNAUTHENTICATED: 'error.UNAUTHENTICATED',
  ACTIVE_GOAL_NOT_FOUND: 'error.ACTIVE_GOAL_NOT_FOUND',
  DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION:
    'error.DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION',
  ASSESSMENT_SESSION_NOT_FOUND: 'error.ASSESSMENT_SESSION_NOT_FOUND',
  ASSESSMENT_SESSION_EXPIRED: 'error.ASSESSMENT_SESSION_EXPIRED',
  ASSESSMENT_NOT_COMPLETED: 'error.ASSESSMENT_NOT_COMPLETED',
  QUESTION_ALREADY_ANSWERED: 'error.QUESTION_ALREADY_ANSWERED',
  INVALID_ANSWER_SELECTION: 'error.INVALID_ANSWER_SELECTION',
}

export function ErrorNotice({ error }: { error: unknown }) {
  const { t } = useI18n()
  const errorKey =
    error instanceof ApiError && error.problem?.code
      ? ERROR_KEYS[error.problem.code]
      : undefined
  const message = errorKey ? t(errorKey) : t('error.generic')
  const correlationId =
    error instanceof ApiError ? error.problem?.correlationId : undefined

  return (
    <div className="notice notice-error" role="alert">
      <svg
        aria-hidden="true"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ flexShrink: 0 }}
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <span>
        {message}
        {!errorKey && correlationId
          ? ` ${t('error.reference', { id: correlationId })}`
          : null}
      </span>
    </div>
  )
}

export function Loading({ label }: { label?: string }) {
  const { t } = useI18n()
  return (
    <div className="notice" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <span>{label ?? t('common.loading')}</span>
    </div>
  )
}
