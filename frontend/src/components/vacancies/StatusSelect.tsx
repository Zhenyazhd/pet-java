import { APPLICATION_STATUSES, type ApplicationStatus } from '../../api/types'
import { applicationStatusTone, formatApplicationStatus } from '../../lib/vacancies/applicationStatus'
import { classNames } from '../../lib/classNames'

type StatusSelectProps = {
  value: ApplicationStatus
  onChange: (status: ApplicationStatus) => void
  label: string
  disabled?: boolean
}

export function StatusSelect({ value, onChange, label, disabled }: StatusSelectProps) {
  const tone = applicationStatusTone(value)
  return (
    <select
      className={classNames('status-select', tone && `status-select--${tone}`)}
      value={value}
      onChange={(event) => onChange(event.target.value as ApplicationStatus)}
      aria-label={label}
      disabled={disabled}
    >
      {APPLICATION_STATUSES.map((status) => (
        <option key={status} value={status}>
          {formatApplicationStatus(status)}
        </option>
      ))}
    </select>
  )
}
