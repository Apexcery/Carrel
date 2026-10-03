import { supabase } from './supabase'

/** Calls the Carrel API, sending the signed-in user's access token. */
export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const { data } = await supabase.auth.getSession()
  const headers = new Headers(init?.headers)
  if (data.session) {
    headers.set('Authorization', `Bearer ${data.session.access_token}`)
  }
  return fetch(`${import.meta.env.VITE_API_URL}${path}`, { ...init, headers })
}
