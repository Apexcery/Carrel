import { supabase } from './supabase'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** Calls the Carrel API, sending the signed-in user's access token. */
export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const { data } = await supabase.auth.getSession()
  const headers = new Headers(init?.headers)
  if (data.session) {
    headers.set('Authorization', `Bearer ${data.session.access_token}`)
  }
  return fetch(`${import.meta.env.VITE_API_URL}${path}`, { ...init, headers })
}

/** GETs JSON from the API, throwing ApiError with a readable message on failure. */
export async function apiGet<T>(path: string): Promise<T> {
  let response: Response
  try {
    response = await apiFetch(path)
  } catch {
    throw new ApiError(0, 'Can’t reach Carrel right now. Check your connection and try again.')
  }
  if (!response.ok) {
    throw new ApiError(response.status, messageFor(response.status))
  }
  return response.json() as Promise<T>
}

function messageFor(status: number): string {
  switch (status) {
    case 401:
      return 'Your session has expired. Sign in again.'
    case 404:
      return 'We couldn’t find that.'
    case 429:
      return 'That’s a lot of requests in a short time. Wait a minute and try again.'
    case 503:
      return 'Book data is temporarily unavailable. Try again in a few minutes.'
    default:
      return 'Something went wrong. Try again.'
  }
}
