import { ApiError } from '../api'

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Try again.'
  const canRetry = onRetry && !(error instanceof ApiError && error.status === 404)
  return (
    <div className="notice" role="alert">
      <p>{message}</p>
      {canRetry && (
        <button type="button" className="link-button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
