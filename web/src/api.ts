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
  const response = await send(path)
  return response.json() as Promise<T>
}

/** Like apiGet, but resolves to null when the API answers 404. */
export async function apiGetOrNull<T>(path: string): Promise<T | null> {
  try {
    return await apiGet<T>(path)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

/** Sends a JSON request; resolves to the parsed response, or undefined for an empty one. */
export async function apiSend<T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const response = await send(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return (response.status === 204 ? undefined : await response.json()) as T
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await apiFetch(path, init)
  } catch {
    throw new ApiError(0, 'Can’t reach Carrel right now. Check your connection and try again.')
  }
  if (!response.ok) {
    throw new ApiError(response.status, await messageFor(response))
  }
  return response
}

async function messageFor(response: Response): Promise<string> {
  switch (response.status) {
    case 400: {
      // Validation problems carry a readable message per field.
      const problem = await response.json().catch(() => null)
      const first = Object.values<string[]>(problem?.errors ?? {})[0]?.[0]
      return first ?? 'That wasn’t accepted. Check what you entered.'
    }
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
