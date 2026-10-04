import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/react'

/**
 * A dropdown styled to match the site (native <select> menus use the operating system's highlight colours,
 * which pages can't change). Label it with aria-label, or wrap it in a Headless UI Field with a Label.
 */
export function Select<T extends string>({
  value,
  onChange,
  options,
  className,
  'aria-label': ariaLabel,
}: {
  value: T
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  className?: string
  'aria-label'?: string
}) {
  return (
    <Listbox value={value} onChange={onChange}>
      <ListboxButton className={className ? `select-button ${className}` : 'select-button'} aria-label={ariaLabel}>
        <WidestLabel labels={options.map((o) => o.label)} current={options.find((o) => o.value === value)?.label} />
        <span className="select-caret" aria-hidden="true">
          ▾
        </span>
      </ListboxButton>
      <ListboxOptions anchor="bottom start" className="select-options">
        {options.map((option) => (
          <ListboxOption key={option.value} value={option.value} className="select-option">
            {option.label}
          </ListboxOption>
        ))}
      </ListboxOptions>
    </Listbox>
  )
}

/**
 * Shows the current label at the width of the longest one, so a control doesn't resize as its value changes.
 * Every label sits in the same grid cell; all but the current one are invisible (and hidden from screen readers).
 */
export function WidestLabel({ labels, current }: { labels: string[]; current: string | undefined }) {
  return (
    <span className="widest-label">
      {[...new Set(labels)].map((label) => (
        <span key={label} className={label === current ? undefined : 'widest-label-hidden'}>
          {label}
        </span>
      ))}
    </span>
  )
}
