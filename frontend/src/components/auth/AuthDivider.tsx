export function AuthDivider({ children }: { children: string }) {
  return (
    <div className="auth-divider" role="separator">
      <span>{children}</span>
    </div>
  )
}
