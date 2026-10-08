import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'
import type { ApplicationStatus, Vacancy } from '../../api/types'

type State = {
  vacancies: Vacancy[]
  loading: boolean
  error: string | null
}

export function useVacancies() {
  const [state, setState] = useState<State>({ vacancies: [], loading: true, error: null })
  const saving = useRef(new Set<number>())
  const latestReload = useRef(0)

  const reload = useCallback(async () => {
    const seq = ++latestReload.current
    setState((prev) => ({ ...prev, loading: true, error: null }))
    try {
      const vacancies = await api.listVacancies()
      if (seq !== latestReload.current) return
      setState({ vacancies, loading: false, error: null })
    } catch (err) {
      if (seq !== latestReload.current) return
      const error = err instanceof Error ? err.message : 'Failed to load vacancies'
      setState((prev) => ({ ...prev, loading: false, error }))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const patch = useCallback((id: number, change: (vacancy: Vacancy) => Vacancy) => {
    setState((prev) => ({
      ...prev,
      vacancies: prev.vacancies.map((vacancy) => (vacancy.id === id ? change(vacancy) : vacancy)),
    }))
  }, [])

  const updateApplication = useCallback(
    async (vacancy: Vacancy, status: ApplicationStatus, notes?: string) => {
      if (saving.current.has(vacancy.id)) {
        throw new Error('Still saving the previous change. Try again in a moment.')
      }
      saving.current.add(vacancy.id)
      const previous = vacancy.application
      const optimistic = (current: Vacancy): Vacancy => ({
        ...current,
        application: {
          id: previous?.id ?? 0,
          status,
          notes: notes ?? previous?.notes ?? null,
        },
      })
      patch(vacancy.id, optimistic)
      try {
        const saved = previous
          ? await api.updateApplication(previous.id, { status, notes })
          : await api.createApplication(vacancy.id, status)
        patch(vacancy.id, (current) => ({
          ...current,
          application: {
            id: saved.id,
            status: saved.status,
            notes: saved.notes,
          },
        }))
      } catch (err) {
        patch(vacancy.id, (current) => ({ ...current, application: previous }))
        throw err
      } finally {
        saving.current.delete(vacancy.id)
      }
    },
    [patch],
  )

  const remove = useCallback(async (id: number) => {
    await api.deleteVacancy(id)
    setState((prev) => ({ ...prev, vacancies: prev.vacancies.filter((v) => v.id !== id) }))
  }, [])

  return { ...state, reload, updateApplication, remove }
}
