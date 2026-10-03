import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'

/** undefined while the stored session is being read; null when signed out. */
const SessionContext = createContext<Session | null | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, session) => setSession(session))
    return () => data.subscription.unsubscribe()
  }, [])

  return <SessionContext value={session}>{children}</SessionContext>
}

export function useSession() {
  return useContext(SessionContext)
}
