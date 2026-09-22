import { ApiError } from '../api/client'

export function ErrorNotice({ error }: { error: unknown }) {
  const message =
    error instanceof ApiError
      ? error.message
      : 'Something went wrong. Please try again.'
  return (
    <div className="notice notice-error" role="alert">
      {message}
    </div>
  )
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="notice" role="status">
      {label}
    </p>
  )
}
