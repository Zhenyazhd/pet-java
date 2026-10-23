import { APPLICATION_STATUSES, type ApplicationStatus } from '../../api/types'
import { applicationStatusTone, formatApplicationStatus } from '../../lib/vacancies/applicationStatus'
import { classNames } from '../../lib/classNames'
import { Select } from '../ui/Select'

type StatusSelectProps = {
  value: ApplicationStatus
  onChange: (status: ApplicationStatus) => void
  label: string
  disabled?: boolean
}

export function StatusSelect({ value, onChange, label, disabled }: StatusSelectProps) {
  const tone = applicationStatusTone(value)
  return (
    <Select
      className={classNames('status-select', tone && `status-select--${tone}`)}
      value={value}
      onChange={(next) => onChange(next as ApplicationStatus)}
      options={APPLICATION_STATUSES.map((status) => ({ value: status, label: formatApplicationStatus(status) }))}
      aria-label={label}
      disabled={disabled}
    />
  )
}
