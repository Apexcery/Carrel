import { CONTACT_EMAIL } from '../legal'

/** The contact address for the privacy and copyright pages, or a note that it's on its way. */
export function Contact({ subject }: { subject: string }) {
  if (!CONTACT_EMAIL) {
    return (
      <p className="legal-contact">
        A contact address for {subject} is being set up and will appear here before Carrel opens to new members.
      </p>
    )
  }
  return (
    <p className="legal-contact">
      Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
    </p>
  )
}
