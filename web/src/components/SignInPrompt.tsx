import { Link } from 'react-router'
import { signInPath } from '../signIn'

/** Shown where a signed-out visitor would see their library: what signing in is for, and how. */
export function SignInPrompt({ title, panel = false, children }: { title: string; panel?: boolean; children: string }) {
  return (
    // On a book page it takes the shelf panel's place, so it borrows that look.
    <section className={panel ? 'shelf-panel sign-in-prompt' : 'sign-in-prompt'}>
      <p className="kicker">{title}</p>
      <p>{children}</p>
      <p className="sign-in-prompt-actions">
        <Link to={signInPath()} className="primary-button">
          Sign in
        </Link>
        <Link to={signInPath(undefined, 'sign-up')} className="link-button">
          Create an account
        </Link>
      </p>
    </section>
  )
}
