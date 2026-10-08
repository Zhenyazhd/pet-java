import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = { children: ReactNode; inline?: boolean }
type State = { failed: boolean }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    const Wrapper = this.props.inline ? 'div' : 'main'
    return (
      <Wrapper className="fatal" role="alert">
        <p className="small-caps">Something went wrong</p>
        <h1>The page hit an unexpected error</h1>
        <p>Reloading usually fixes it.</p>
        <button type="button" className="btn btn--outline" onClick={() => window.location.reload()}>
          Reload
        </button>
      </Wrapper>
    )
  }
}
