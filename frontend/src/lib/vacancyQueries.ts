import type { QueryClient } from '@tanstack/react-query'

/** Refresh both the vacancy list and one vacancy's detail cache, in parallel. */
export function invalidateVacancy(queryClient: QueryClient, vacancyId: number): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['vacancies'] }),
    queryClient.invalidateQueries({ queryKey: ['vacancies', vacancyId] }),
  ])
}
