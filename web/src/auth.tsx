import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'

/** undefined while the stored session is being read; null when signed out. */
const SessionContext = createContext<Session | null | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined)
  const queryClient = useQueryClient()
  const userId = useRef<string | null>(null)

  useEffect(() => {
    function changed(next: Session | null) {
      // A different reader (or none) means none of the cached data is theirs.
      const nextId = next?.user.id ?? null
      if (nextId !== userId.current) {
        queryClient.clear()
        userId.current = nextId
      }
      setSession(next)
    }
    supabase.auth.getSession().then(({ data }) => changed(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, session) => changed(session))
    return () => data.subscription.unsubscribe()
  }, [queryClient])

  return <SessionContext value={session}>{children}</SessionContext>
}

export function useSession() {
  return useContext(SessionContext)
}
