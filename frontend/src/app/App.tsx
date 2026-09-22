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
          <Link className="brand" to="/">
            SkillPath
          </Link>
          <span>One clear next step.</span>
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
