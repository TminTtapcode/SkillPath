import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { ActiveGoalPage } from '../features/goals/ActiveGoalPage'
import { GoalSetupPage } from '../features/goals/GoalSetupPage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <header className="site-header">
          <div className="site-header-inner">
            <div className="brand-group">
              <Link className="brand" to="/">
                <svg
                  aria-hidden="true"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="var(--sp-primary)"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <circle cx="12" cy="12" r="10" />
                  <polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76" />
                </svg>
                <span>SkillPath</span>
              </Link>
              <span className="brand-badge">Adaptive Planner</span>
            </div>
            <div className="header-status">
              <span>One clear next step</span>
            </div>
          </div>
        </header>
        <main className="app-shell">
          <Routes>
            <Route path="/" element={<Navigate to="/goal" replace />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/goals/new" element={<GoalSetupPage />} />
            <Route path="/goal" element={<ActiveGoalPage />} />
            <Route path="*" element={<Navigate to="/goal" replace />} />
          </Routes>
        </main>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
