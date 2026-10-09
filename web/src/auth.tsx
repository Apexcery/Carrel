import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { Session } from '@supabase/supabase-js'
import { saveQueries } from './savedQueries'
import { supabase } from './supabase'

/** undefined while the stored session is being read; null when signed out. */
const SessionContext = createContext<Session | null | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined)
  const queryClient = useQueryClient()
  /** Whose data is in the cache: undefined until the stored session has been read. */
  const userId = useRef<string | null | undefined>(undefined)
  const saving = useRef<ReturnType<typeof saveQueries>>(undefined)

  useEffect(() => {
    async function changed(next: Session | null) {
      // A different reader (or none) means none of the cached data is theirs. Bring back what was saved for them.
      const nextId = next?.user.id ?? null
      if (nextId !== userId.current) {
        saving.current?.stop()
        queryClient.clear()
        userId.current = nextId
        saving.current = saveQueries(queryClient, nextId)
      }
      // Pages start once it's back, so they show it rather than loading it again.
      await saving.current?.restored
      setSession(next)
    }
    supabase.auth.getSession().then(({ data }) => changed(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, session) => changed(session))
    return () => {
      data.subscription.unsubscribe()
      saving.current?.stop()
      saving.current = undefined
      userId.current = undefined
    }
  }, [queryClient])

  return <SessionContext value={session}>{children}</SessionContext>
}

export function useSession() {
  return useContext(SessionContext)
}
