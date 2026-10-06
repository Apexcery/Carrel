import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Area } from 'react-easy-crop'
import { Link, useNavigate } from 'react-router'
import { ApiError, apiSend, apiUpload } from '../api'
import { useSession } from '../auth'
import { Avatar } from '../components/Avatar'
import { PictureCropper } from '../components/PictureCropper'
import { usePageTitle } from '../pageTitle'
import { passwordProblem } from '../passwords'
import { cropPicture } from '../pictures'
import { PROFILE_KEY, useProfile } from '../profile'
import { clearRecentSearches } from '../recentSearches'
import { profilePath } from '../shelves'
import { supabase } from '../supabase'
import type { Profile } from '../types'
import { useUsernameStatus } from '../username'

/** One row per detail: label, current value, and a link that opens a small form in place. */
export function AccountSettings() {
  usePageTitle('Account')
  return (
    <>
      <PictureSetting />
      <div className="setting-rows">
        <UsernameRow />
        <EmailRow />
        <PasswordRow />
      </div>
      <section className="settings-section" aria-labelledby="privacy-title">
        <h2 id="privacy-title" className="section-title">
          Privacy
        </h2>
        <div className="setting-rows">
          <ProfileRow />
        </div>
      </section>
      {/* Set apart, so nobody reaches them while changing their details. */}
      <section className="settings-section danger-zone" aria-labelledby="danger-zone-title">
        <h2 id="danger-zone-title" className="section-title">
          Danger zone
        </h2>
        <div className="setting-rows">
          <DeleteLibraryRow />
          <DeleteAccountRow />
        </div>
      </section>
    </>
  )
}

function SettingRow({
  label,
  value,
  hint,
  action = 'Change',
  editing,
  onEdit,
  children,
}: {
  label: string
  value: ReactNode
  hint?: ReactNode
  action?: string
  editing: boolean
  onEdit: () => void
  children: ReactNode
}) {
  return (
    <div className="setting-row">
      <span className="setting-label">{label}</span>
      {editing ? (
        <div className="setting-edit">{children}</div>
      ) : (
        <>
          <span className="setting-value">{value}</span>
          <button type="button" className="link-button" onClick={onEdit}>
            {action}
          </button>
          {hint && <p className="setting-hint">{hint}</p>}
        </>
      )}
    </div>
  )
}

function FormActions({
  saving,
  disabled = false,
  label,
  savingLabel,
  onCancel,
}: {
  saving: boolean
  disabled?: boolean
  label: string
  savingLabel: string
  onCancel: () => void
}) {
  return (
    <div className="settings-actions">
      <button type="submit" className="primary-button" disabled={saving || disabled}>
        {saving ? savingLabel : label}
      </button>
      <button type="button" className="link-button" onClick={onCancel}>
        Cancel
      </button>
    </div>
  )
}

function UsernameRow() {
  const queryClient = useQueryClient()
  const current = useProfile().data?.username ?? null
  const [editing, setEditing] = useState(false)
  const [username, setUsername] = useState('')
  const [saved, setSaved] = useState(false)
  const status = useUsernameStatus(username, '3 to 20 letters, numbers, underscores, or hyphens.', current)

  const save = useMutation({
    mutationFn: () => apiSend<Profile>('PUT', '/profile', { username: username.trim() }),
    onSuccess: (profile) => {
      queryClient.setQueryData(PROFILE_KEY, profile)
      setSaved(true)
      setEditing(false)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (status.canSave) {
      save.mutate()
    }
  }

  const message = save.error ? { text: save.error.message, tone: 'error' } : status

  return (
    <SettingRow
      label="Username"
      value={current}
      hint={saved ? 'Username saved.' : undefined}
      editing={editing}
      onEdit={() => {
        setUsername(current ?? '')
        setSaved(false)
        save.reset()
        setEditing(true)
      }}
    >
      <form className="settings-form" onSubmit={submit}>
        <label>
          <span className="visually-hidden">Username</span>
          <input
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={20}
            required
            autoFocus
            value={username}
            aria-describedby="username-status"
            aria-invalid={message.tone === 'error'}
            onChange={(e) => {
              setUsername(e.target.value)
              save.reset()
            }}
          />
        </label>
        <p id="username-status" className={`form-message ${message.tone}`} aria-live="polite">
          {message.text}
        </p>
        <FormActions saving={save.isPending} disabled={!status.canSave} label="Save" savingLabel="Saving…" onCancel={() => setEditing(false)} />
      </form>
    </SettingRow>
  )
}

/** Pictures bigger than this aren't opened for cropping; only the cropped square is uploaded. */
const MAX_PICTURE_BYTES = 20 * 1024 * 1024

/**
 * The reader's picture, above their other details. Add or Change opens the file picker straight away; a chosen picture
 * opens for cropping to a circle, then Save uploads it.
 */
function PictureSetting() {
  const queryClient = useQueryClient()
  const current = useProfile().data?.avatarUrl ?? null
  // The chosen file, opened for cropping, and the square the reader has picked out of it.
  const [src, setSrc] = useState<string | null>(null)
  const [area, setArea] = useState<Area | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [hint, setHint] = useState<string>()

  // Let go of each chosen file once it's replaced or cropping ends.
  useEffect(() => () => {
    if (src) {
      URL.revokeObjectURL(src)
    }
  }, [src])

  function stopCropping() {
    setSrc(null)
    setArea(null)
  }

  const saved = (message: string) => (profile: Profile) => {
    queryClient.setQueryData(PROFILE_KEY, profile)
    stopCropping()
    setHint(message)
  }
  const save = useMutation({
    mutationFn: async () => apiUpload<Profile>('/profile/picture', await cropPicture(src!, area!), 'image/png'),
    onSuccess: saved('Picture saved.'),
  })
  const remove = useMutation({
    mutationFn: () => apiSend<Profile>('DELETE', '/profile/picture'),
    onSuccess: saved('Picture removed.'),
  })
  const busy = save.isPending || remove.isPending
  const error = problem ?? save.error?.message ?? remove.error?.message

  // Opened first, so a file the browser can't show (an iPhone's HEIC, say) is turned away before cropping.
  function choose(file: File | undefined) {
    setProblem(null)
    setHint(undefined)
    save.reset()
    remove.reset()
    if (!file) {
      return
    }
    if (file.size > MAX_PICTURE_BYTES) {
      setProblem('That picture is too large. Choose one of up to 20 MB.')
      return
    }
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      setArea(null)
      setSrc(url)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      setProblem('Carrel can’t read that picture. Try a JPEG, PNG, or WebP.')
    }
    image.src = url
  }

  // The browser's own file button doesn't match the site, so the input is hidden inside a label styled as a link;
  // clicking the label opens the picker.
  const chooser = (text: string) => (
    <label className="link-button">
      <input
        type="file"
        className="visually-hidden"
        accept="image/jpeg,image/png,image/webp,image/gif"
        disabled={busy}
        onChange={(e) => {
          choose(e.target.files?.[0])
          // So choosing the same file again still counts as a change.
          e.target.value = ''
        }}
      />
      {text}
    </label>
  )

  return (
    <section className="picture-setting" aria-labelledby="picture-setting-title">
      <h2 id="picture-setting-title" className="setting-label">
        Profile picture
      </h2>
      {src ? (
        <div className="settings-form">
          <PictureCropper src={src} onCropped={setArea} />
          {error && <p className="form-message error">{error}</p>}
          <div className="settings-actions">
            <button type="button" className="primary-button" disabled={!area || busy} onClick={() => save.mutate()}>
              {save.isPending ? 'Saving…' : 'Save'}
            </button>
            {chooser('Choose another')}
            <button type="button" className="link-button" disabled={busy} onClick={stopCropping}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="picture-current">
          <Avatar url={current} size="large" />
          <div className="picture-actions">
            <div className="settings-actions">
              {chooser(current ? 'Change' : 'Add')}
              {current && (
                <button type="button" className="link-button" disabled={busy} onClick={() => remove.mutate()}>
                  {remove.isPending ? 'Removing…' : 'Remove'}
                </button>
              )}
            </div>
            {error ? <p className="form-message error">{error}</p> : hint && <p className="setting-hint">{hint}</p>}
          </div>
        </div>
      )}
    </section>
  )
}

/** Public or private, switched straight away: there's nothing to type. */
function ProfileRow() {
  const queryClient = useQueryClient()
  const profile = useProfile().data
  const save = useMutation({
    mutationFn: () => apiSend<Profile>('PUT', '/profile', { username: profile!.username, isPublic: !profile!.isPublic }),
    onSuccess: (saved) => queryClient.setQueryData(PROFILE_KEY, saved),
  })
  if (!profile?.username) {
    return null
  }
  return (
    <SettingRow
      label="Profile"
      value={
        <>
          {profile.isPublic ? 'Public: anyone can see ' : 'Private: only you can see '}
          <Link to={profilePath(profile.username)} className="series-link">
            your profile
          </Link>{' '}
          and shelves.
        </>
      }
      hint={save.error?.message}
      action={save.isPending ? 'Saving…' : profile.isPublic ? 'Make private' : 'Make public'}
      editing={false}
      onEdit={() => {
        if (!save.isPending) {
          save.mutate()
        }
      }}
    >
      {null}
    </SettingRow>
  )
}

function EmailRow() {
  const user = useSession()?.user
  const [editing, setEditing] = useState(false)
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)

  const change = useMutation({
    mutationFn: async () => {
      const { error } = await supabase.auth.updateUser(
        { email: email.trim() },
        { emailRedirectTo: `${window.location.origin}/settings/account` },
      )
      if (error) {
        throw error
      }
    },
    onSuccess: () => {
      setSent(true)
      setEditing(false)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    change.mutate()
  }

  const hint = sent
    ? 'We’ve sent a confirmation link to both addresses (check spam folders too). Your email changes once you’ve confirmed both.'
    : user?.new_email
      ? `Waiting for you to confirm the change to ${user.new_email}.`
      : undefined

  return (
    <SettingRow
      label="Email"
      value={user?.email}
      hint={hint}
      editing={editing}
      onEdit={() => {
        setEmail('')
        setSent(false)
        change.reset()
        setEditing(true)
      }}
    >
      <form className="settings-form" onSubmit={submit}>
        <label>
          <span className="visually-hidden">New email</span>
          <input
            type="email"
            autoComplete="email"
            placeholder="New email"
            required
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <p className={`form-message${change.error ? ' error' : ''}`} role={change.error ? 'alert' : undefined}>
          {change.error ? change.error.message : 'We’ll email both addresses to confirm the change.'}
        </p>
        <FormActions saving={change.isPending} label="Change email" savingLabel="Sending…" onCancel={() => setEditing(false)} />
      </form>
    </SettingRow>
  )
}

function PasswordRow() {
  const email = useSession()?.user.email ?? ''
  const [editing, setEditing] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeated, setRepeated] = useState('')
  const [changed, setChanged] = useState(false)

  const change = useMutation({
    mutationFn: async () => {
      const problem = passwordProblem(newPassword)
      if (problem) {
        throw new Error(problem)
      }
      if (newPassword !== repeated) {
        throw new Error('The new passwords don’t match.')
      }
      // Supabase doesn't ask for the current password, so check it here before changing anything.
      const check = await supabase.auth.signInWithPassword({ email, password: currentPassword })
      if (check.error) {
        throw new Error(check.error.code === 'invalid_credentials' ? 'Your current password isn’t right.' : check.error.message)
      }
      const { error } = await supabase.auth.updateUser({ password: newPassword })
      if (error) {
        throw error
      }
    },
    onSuccess: () => {
      setChanged(true)
      setEditing(false)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    change.mutate()
  }

  return (
    <SettingRow
      label="Password"
      value="••••••••"
      hint={changed ? 'Password changed.' : undefined}
      editing={editing}
      onEdit={() => {
        setCurrentPassword('')
        setNewPassword('')
        setRepeated('')
        setChanged(false)
        change.reset()
        setEditing(true)
      }}
    >
      <form className="settings-form" onSubmit={submit}>
        {/* Lets password managers match the change to the right account. */}
        <input type="email" autoComplete="username" value={email} readOnly hidden />
        <label>
          <span>Current password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            autoFocus
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
        </label>
        <label>
          <span>New password (at least 8 characters)</span>
          <input
            type="password"
            autoComplete="new-password"
            required
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </label>
        <label>
          <span>Repeat new password</span>
          <input
            type="password"
            autoComplete="new-password"
            required
            value={repeated}
            onChange={(e) => setRepeated(e.target.value)}
          />
        </label>
        {change.error && (
          <p className="form-message error" role="alert">
            {change.error.message}
          </p>
        )}
        <FormActions saving={change.isPending} label="Change password" savingLabel="Saving…" onCancel={() => setEditing(false)} />
      </form>
    </SettingRow>
  )
}

function DeleteLibraryRow() {
  const queryClient = useQueryClient()
  const user = useSession()?.user
  const [editing, setEditing] = useState(false)
  const [password, setPassword] = useState('')
  const [deleted, setDeleted] = useState(false)

  const remove = useMutation({
    mutationFn: () => apiSend<void>('POST', '/account/delete-library', { password }),
    onSuccess: () => {
      // Shelves, suggestions, stats, and imports were all built from the library.
      queryClient.invalidateQueries()
      setDeleted(true)
      setEditing(false)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    remove.mutate()
  }

  const error =
    remove.error instanceof ApiError && remove.error.status === 503
      ? 'Deleting book data is unavailable right now. Try again later.'
      : remove.error?.message

  return (
    <SettingRow
      label="Delete book data"
      value={<span className="muted">Empty your library but keep your account.</span>}
      hint={deleted ? 'Your book data has been deleted.' : undefined}
      action="Delete"
      editing={editing}
      onEdit={() => {
        setPassword('')
        setDeleted(false)
        remove.reset()
        setEditing(true)
      }}
    >
      <form className="settings-form" onSubmit={submit}>
        <p className="setting-warning">
          This deletes every book in your library, along with your ratings, progress, reading history, and imports. Your
          account and username stay. It can’t be undone.
        </p>
        <input type="email" autoComplete="username" value={user?.email ?? ''} readOnly hidden />
        <label>
          <span>Your password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            autoFocus
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && (
          <p className="form-message error" role="alert">
            {error}
          </p>
        )}
        <FormActions saving={remove.isPending} label="Delete my book data" savingLabel="Deleting…" onCancel={() => setEditing(false)} />
      </form>
    </SettingRow>
  )
}

function DeleteAccountRow() {
  const navigate = useNavigate()
  const user = useSession()?.user
  const [editing, setEditing] = useState(false)
  const [password, setPassword] = useState('')

  const remove = useMutation({
    mutationFn: () => apiSend<void>('POST', '/account/delete', { password }),
    onSuccess: () => {
      // Leave nothing of theirs in this browser, and don't land the next person to sign in on these settings.
      clearRecentSearches(user?.id ?? '')
      navigate('/', { replace: true })
      // The account no longer exists, so only clear this browser's session.
      return supabase.auth.signOut({ scope: 'local' })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    remove.mutate()
  }

  const error =
    remove.error instanceof ApiError && remove.error.status === 503
      ? 'Account deletion is unavailable right now. Try again later.'
      : remove.error?.message

  return (
    <SettingRow
      label="Delete account"
      value={<span className="muted">Permanently delete your account.</span>}
      action="Delete"
      editing={editing}
      onEdit={() => {
        setPassword('')
        remove.reset()
        setEditing(true)
      }}
    >
      <form className="settings-form" onSubmit={submit}>
        <p className="setting-warning">
          This deletes your account, along with your library, ratings, progress, and reading history. It can’t be undone.
        </p>
        <input type="email" autoComplete="username" value={user?.email ?? ''} readOnly hidden />
        <label>
          <span>Your password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            autoFocus
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && (
          <p className="form-message error" role="alert">
            {error}
          </p>
        )}
        <FormActions saving={remove.isPending} label="Delete my account" savingLabel="Deleting…" onCancel={() => setEditing(false)} />
      </form>
    </SettingRow>
  )
}
