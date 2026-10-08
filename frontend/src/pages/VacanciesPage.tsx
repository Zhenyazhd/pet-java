import { useEffect } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import type { Vacancy } from '../api/types'
import { statusOf } from '../lib/vacancies/vacancy'
import { AddVacancyForm } from '../components/vacancies/AddVacancyForm'
import { VacancyDialog } from '../components/vacancies/VacancyDialog'
import { VacancyList } from '../components/vacancies/VacancyList'
import { PageHeader } from '../components/ui/PageHeader'
import { useDocumentTitle } from '../lib/useDocumentTitle'
import { useVacancies } from '../lib/vacancies/useVacancies'

export function VacanciesPage() {
  useDocumentTitle('Vacancies')
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { vacancies, loading, error, reload, updateApplication, remove } = useVacancies()

  const selectedId = id === undefined ? null : Number(id)
  const selected = vacancies.find((vacancy) => vacancy.id === selectedId) ?? null

  useEffect(() => {
    if (selectedId !== null && !loading && !error && !selected) {
      navigate('/vacancies', { replace: true })
    }
  }, [selectedId, loading, error, selected, navigate])

  async function onSaved(vacancyId: number) {
    await reload()
    navigate(`/vacancies/${vacancyId}`)
  }

  const findSavedByUrl = (url: string) => vacancies.find((vacancy) => vacancy.url === url)?.id ?? null
  const closeDialog = () =>
    location.key === 'default' ? navigate('/vacancies', { replace: true }) : navigate(-1)

  async function deleteSelected(vacancy: Vacancy) {
    await remove(vacancy.id)
    closeDialog()
  }

  return (
    <section className="page" aria-labelledby="vacancies-title">
      <PageHeader
        eyebrow="Pipeline"
        title="Vacancies"
        titleId="vacancies-title"
        lead="Save a posting and AI will keep what matters. Track where each application stands."
      />

      <div className="vacancies">
        <section aria-labelledby="add-title">
          <h2 id="add-title" className="section-title">
            Add a vacancy
          </h2>
          <AddVacancyForm onSaved={onSaved} findSavedByUrl={findSavedByUrl} />
        </section>

        <hr className="rule" />

        <section aria-labelledby="saved-title">
          <h2 id="saved-title" className="section-title">
            Saved
          </h2>
          <VacancyList
            vacancies={vacancies}
            loading={loading}
            error={error}
            onRetry={reload}
            onStatusChange={updateApplication}
          />
        </section>
      </div>

      {selected && (
        <VacancyDialog
          key={selected.id}
          vacancy={selected}
          onClose={closeDialog}
          onStatusChange={(status) => updateApplication(selected, status)}
          onNotesSave={(notes) =>
            updateApplication(selected, statusOf(selected), notes)
          }
          onDelete={() => deleteSelected(selected)}
        />
      )}
    </section>
  )
}
