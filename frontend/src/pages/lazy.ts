import { lazy } from 'react'

// Pages load on demand so the sign-in screen does not ship the resume editor.
export const JobMatchesPage = lazy(() => import('./JobMatchesPage'))
export const JobsLayout = lazy(() => import('./JobsLayout'))
export const ProfilePage = lazy(() => import('./ProfilePage'))
export const ResumePage = lazy(() => import('./ResumePage'))
export const SearchProfilePage = lazy(() => import('./SearchProfilePage'))
export const VacanciesPage = lazy(() => import('./VacanciesPage'))

/** Fetches every page chunk, so moving between pages never shows a gap. Called once someone is signed in. */
export function preloadPages() {
  const quiet = () => {}
  void import('./JobMatchesPage').catch(quiet)
  void import('./JobsLayout').catch(quiet)
  void import('./ProfilePage').catch(quiet)
  void import('./ResumePage').catch(quiet)
  void import('./SearchProfilePage').catch(quiet)
  void import('./VacanciesPage').catch(quiet)
}
