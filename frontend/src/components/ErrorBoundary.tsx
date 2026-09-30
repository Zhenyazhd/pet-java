import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from './ui/Button'

type ErrorBoundaryProps = {
  children: ReactNode
  /** Shown under the heading; keep short. */
  hint?: string
  /** Top-level boundary: also offers a hard page reload, not just a local retry. */
  fatal?: boolean
}

type ErrorBoundaryState = {
  error: Error | null
}

/**
 * Catches render/lifecycle exceptions in the subtree below it so one broken page (an
 * unexpected API shape, a rendering bug) can't white-screen the whole app. React only
 * supports this via a class component — there is no hook equivalent.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack)
  }

  private reset = () => {
    this.setState({ error: null })
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }
    return (
      <section className="page error-boundary">
        <h1>Something went wrong</h1>
        <p className="muted">
          {this.props.hint ?? 'This part of the app hit an unexpected error.'}
        </p>
        <div className="form-actions">
          <Button onClick={this.reset}>Try again</Button>
          {this.props.fatal && (
            <Button variant="ghost" onClick={() => window.location.reload()}>
              Reload page
            </Button>
          )}
        </div>
      </section>
    )
  }
}
