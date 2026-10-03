import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { apiGet } from '../api'
import { ErrorNotice } from '../components/ErrorNotice'
import type { BookDetail } from '../types'
import { BookSkeleton } from './BookPage'

/**
 * Opens a book by its Hardcover or Open Library id (as search results link), then swaps the URL for
 * Carrel's own /books/:id. Opening it stores the book on the server if it wasn't already.
 */
export function BookResolver({ source }: { source: 'hardcover' | 'openlibrary' }) {
  const { sourceId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const book = useQuery({
    queryKey: ['book', source, sourceId],
    queryFn: () => apiGet<BookDetail>(`/books/${source}/${encodeURIComponent(sourceId ?? '')}`),
  })

  useEffect(() => {
    if (book.data) {
      queryClient.setQueryData(['book', String(book.data.id)], book.data)
      navigate(`/books/${book.data.id}`, { replace: true })
    }
  }, [book.data, navigate, queryClient])

  return book.isError ? <ErrorNotice error={book.error} onRetry={() => book.refetch()} /> : <BookSkeleton />
}
