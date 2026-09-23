import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ApiError, getActiveGoal, getMe, logout } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'

export function ActiveGoalPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['me'], queryFn: getMe, retry: false })
  const goal = useQuery({
    queryKey: ['active-goal'],
    queryFn: getActiveGoal,
    retry: false,
  })
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear()
      void navigate('/login')
    },
  })

  if (profile.error instanceof ApiError && profile.error.status === 401) {
    return <AuthRequired />
  }
  if (profile.isPending || goal.isPending)
    return <Loading label="Calculating your optimal learning route…" />
  if (profile.error) return <ErrorNotice error={profile.error} />
  if (goal.error instanceof ApiError && goal.error.status === 404) {
    return (
      <section className="panel">
        <p className="eyebrow">No active route</p>
        <h1>No active goal yet</h1>
        <p className="lede">
          Your route starts after you set a destination and daily study budget.
        </p>
        <Link className="button-link" to="/goals/new">
          Choose learning track
        </Link>
      </section>
    )
  }
  if (goal.error) return <ErrorNotice error={goal.error} />

  const dailyMinutes = goal.data?.defaultDailyMinutes ?? 60

  return (
    <section className="panel today-panel goal-summary">
      <div className="summary-heading">
        <div className="summary-heading-left">
          <p className="eyebrow">Today Command Center</p>
          <h1>Welcome, {profile.data?.displayName}</h1>
          <p className="lede" style={{ margin: 0 }}>
            Your route for Java Backend Internship readiness is calibrated.
          </p>
        </div>
        <button
          className="button-secondary"
          onClick={() => logoutMutation.mutate()}
          disabled={logoutMutation.isPending}
        >
          {logoutMutation.isPending ? 'Signing out…' : 'Sign out'}
        </button>
      </div>

      {/* Today's High-Value Task Card */}
      <article className="today-task-card" aria-labelledby="today-task-title">
        <div className="task-card-header">
          <span className="task-category">Phase 2 Preview • Core Milestone</span>
          <span className="task-duration" aria-label={`Estimated duration ${dailyMinutes} minutes`}>
            ⏱️ {dailyMinutes} mins allocated today
          </span>
        </div>
        <h2 id="today-task-title" className="task-title">
          Java Backend: Knowledge Graph Diagnostic & Initial Assessment
        </h2>
        <div className="task-rationale">
          <strong>Why this task: </strong>
          Initial diagnostic will produce concept-level evidence across Java syntax,
          OOP polymorphism, and basic collection frameworks to tailor your daily plan.
        </div>
        <div className="task-phases" aria-label="Task phases">
          <span className="phase-chip">1. Diagnostic Quiz</span>
          <span className="phase-chip">2. Knowledge Graph Mapping</span>
          <span className="phase-chip">3. Adaptive Task Scheduler</span>
        </div>
        <div className="task-actions">
          <button type="button" disabled style={{ opacity: 0.85, cursor: 'default' }}>
            <span>Next Phase Starting Soon</span>
          </button>
          <span style={{ fontSize: '0.8125rem', color: 'var(--sp-text-muted)' }}>
            Phase 2 Knowledge Graph & Diagnostic are next in roadmap.
          </span>
        </div>
      </article>

      {/* Goal Metadata */}
      <div>
        <h3 style={{ fontSize: '0.875rem', color: 'var(--sp-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.75rem' }}>
          Active Goal Specifications
        </h3>
        <dl className="metadata-grid">
          <div>
            <dt>Target Date</dt>
            <dd>{goal.data?.targetDate}</dd>
          </div>
          <div>
            <dt>Daily Budget</dt>
            <dd>{goal.data?.defaultDailyMinutes} min / day</dd>
          </div>
          <div>
            <dt>Timezone</dt>
            <dd>{goal.data?.timezone}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>
              <span className="status-badge">
                ● {goal.data?.status ?? 'ACTIVE'}
              </span>
            </dd>
          </div>
        </dl>
      </div>

      <p className="next-step-notice">
        <strong>Next in Sequence:</strong> Phase 2 Knowledge Graph entities and relation validator will construct your visual prerequisite map.
      </p>
    </section>
  )
}

function AuthRequired() {
  return (
    <section className="panel auth-panel">
      <p className="eyebrow">Session</p>
      <h1>Session ended</h1>
      <p className="lede">Sign in again to continue with your learning route.</p>
      <Link className="button-link" to="/login">
        Sign in
      </Link>
    </section>
  )
}
