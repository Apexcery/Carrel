import { Fragment } from 'react'
import { Link } from 'react-router'
import type { Contributor } from '../types'

/** Names as listNames writes them ("A, B, and C"), each linking to its author page when Hardcover knows the author. */
export function AuthorNames({ authors }: { authors: Contributor[] }) {
  return authors.map((author, i) => (
    <Fragment key={author.id}>
      {i > 0 && (authors.length === 2 ? ' and ' : i === authors.length - 1 ? ', and ' : ', ')}
      {author.hardcoverId ? (
        <Link to={`/authors/hardcover/${author.hardcoverId}`} className="series-link">
          {author.name}
        </Link>
      ) : (
        author.name
      )}
    </Fragment>
  ))
}
