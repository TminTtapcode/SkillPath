import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
} from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { DiagnosticPage } from '../features/assessment/DiagnosticPage'
import { ActiveGoalPage } from '../features/goals/ActiveGoalPage'
import { GoalSetupPage } from '../features/goals/GoalSetupPage'
import { KnowledgeStatePage } from '../features/progress/KnowledgeStatePage'
import { LearningCatalogPage } from '../features/learning/LearningCatalogPage'
import { LearningSessionPage } from '../features/learning/LearningSessionPage'
import { TodayPage } from '../features/planner/TodayPage'
import { RoadmapPage } from '../features/planner/RoadmapPage'
import { SystemArchitectureGraph } from '../features/onboarding/SystemArchitectureGraph'
import { I18nProvider, LanguageSelector, useI18n } from '../shared/i18n/I18n'
import { Navbar } from '../shared/components/Navbar'

export function App() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
      }),
  )
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <BrowserRouter>
          <AppContent />
        </BrowserRouter>
      </I18nProvider>
    </QueryClientProvider>
  )
}

function AppContent() {
  const { t } = useI18n()
  const location = useLocation()
  const isAuthRoute =
    location.pathname === '/login' || location.pathname === '/register'

  return (
    <div className={`app-root ${isAuthRoute ? 'auth-mode' : 'learner-mode'}`}>
      {!isAuthRoute && <Navbar />}
      <div className="app-main-area">
        <header
          className={`site-header ${!isAuthRoute ? 'mobile-only-header' : ''}`}
        >
          <div className="site-header-inner">
            <div className="brand-group">
              <Link className="brand" to="/today">
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
              <span className="brand-badge">{t('app.badge')}</span>
            </div>
            <div className="header-status">
              <span>{t('app.tagline')}</span>
              <LanguageSelector />
            </div>
          </div>
        </header>

        <main className="app-shell">
          <Routes>
            <Route path="/" element={<Navigate to="/today" replace />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/goals/new" element={<GoalSetupPage />} />
            <Route path="/onboarding/architecture" element={<SystemArchitectureGraph />} />
            <Route path="/goal" element={<ActiveGoalPage />} />
            <Route path="/assessment/diagnostic" element={<DiagnosticPage />} />
            <Route path="/knowledge" element={<KnowledgeStatePage />} />
            <Route path="/learning" element={<LearningCatalogPage />} />
            <Route path="/today" element={<TodayPage />} />
            <Route path="/roadmap" element={<RoadmapPage />} />
            <Route
              path="/learning/session/:id"
              element={<LearningSessionPage />}
            />
            <Route path="*" element={<Navigate to="/today" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}
