import { Link } from 'react-router'
import { usePageTitle } from '../pageTitle'

export const NOT_FOUND_TITLE = 'Page not found'

export function NotFoundPage() {
  usePageTitle(NOT_FOUND_TITLE)
  return (
    <section className="home">
      <p className="kicker">Not on the shelf</p>
      <h1 className="home-title">This page doesn’t exist.</h1>
      <p className="home-lede">
        <Link to="/">Back to the start</Link>
      </p>
    </section>
  )
}
