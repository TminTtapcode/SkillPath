import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import {
  ApiError,
  getActiveGoal,
  getMe,
  logout,
  startDiagnostic,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

export function ActiveGoalPage() {
  const { t } = useI18n()
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
  const diagnosticMutation = useMutation({
    mutationFn: startDiagnostic,
    onSuccess: (session) => {
      queryClient.setQueryData(['assessment-session', session.id], session)
      void navigate(`/assessment/diagnostic?session=${session.id}`)
    },
  })

  if (profile.error instanceof ApiError && profile.error.status === 401) {
    return <AuthRequired />
  }
  if (profile.isPending || goal.isPending)
    return <Loading label={t('goal.loading')} />
  if (profile.error) return <ErrorNotice error={profile.error} />
  if (goal.error instanceof ApiError && goal.error.status === 404) {
    return (
      <section className="panel">
        <p className="eyebrow">{t('goal.noRoute')}</p>
        <h1>{t('goal.noGoal')}</h1>
        <p className="lede">{t('goal.noGoalDetail')}</p>
        <Link className="button-link" to="/goals/new">
          {t('goal.chooseTrack')}
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
          <p className="eyebrow">{t('goal.commandCenter')}</p>
          <h1>
            {t('goal.welcome', { name: profile.data?.displayName ?? '' })}
          </h1>
          <p className="lede" style={{ margin: 0 }}>
            {t('goal.routeReady')}
          </p>
        </div>
        <button
          className="button-secondary"
          onClick={() => logoutMutation.mutate()}
          disabled={logoutMutation.isPending}
        >
          {logoutMutation.isPending ? t('goal.signingOut') : t('goal.signOut')}
        </button>
      </div>

      <article className="today-task-card" aria-labelledby="today-task-title">
        <div className="task-card-header">
          <span className="task-category">{t('goal.category')}</span>
          <span
            className="task-duration"
            aria-label={t('goal.durationLabel', { minutes: dailyMinutes })}
          >
            {t('goal.allocated', { minutes: dailyMinutes })}
          </span>
        </div>
        <h2 id="today-task-title" className="task-title">
          {t('goal.diagnosticTitle')}
        </h2>
        <div className="task-rationale">
          <strong>{t('goal.why')} </strong>
          {t('goal.rationale')}
        </div>
        <div className="task-phases" aria-label={t('goal.category')}>
          <span className="phase-chip">{t('goal.questions')}</span>
          <span className="phase-chip">{t('goal.duration')}</span>
          <span className="phase-chip">{t('goal.resumeSafe')}</span>
        </div>
        {diagnosticMutation.error && (
          <ErrorNotice error={diagnosticMutation.error} />
        )}
        <div className="task-actions">
          <button
            type="button"
            onClick={() => diagnosticMutation.mutate()}
            disabled={diagnosticMutation.isPending}
          >
            <span>
              {diagnosticMutation.isPending
                ? t('goal.preparing')
                : t('goal.start')}
            </span>
          </button>
          <span
            style={{ fontSize: '0.8125rem', color: 'var(--sp-text-muted)' }}
          >
            {t('goal.retention')}
          </span>
          <Link className="button-link button-secondary" to="/knowledge">
            {t('knowledge.open')}
          </Link>
        </div>
      </article>

      <article className="learning-sequence" aria-labelledby="self-study-title">
        <p className="eyebrow">{t('learning.eyebrow')}</p>
        <h2 id="self-study-title">{t('learning.title')}</h2>
        <p>{t('learning.intro')}</p>
        <Link className="button-link button-secondary" to="/learning">
          {t('learning.open')}
        </Link>
      </article>

      <article
        className="learning-sequence"
        aria-labelledby="today-plan-link-title"
      >
        <p className="eyebrow">Today · Planner v1</p>
        <h2 id="today-plan-link-title">{t('planner.title')}</h2>
        <p>{t('planner.intro')}</p>
        <div className="planner-actions">
          <Link className="button-link" to="/today">
            {t('planner.open')}
          </Link>
          <Link className="button-link button-secondary" to="/roadmap">
            {t('roadmap.open')}
          </Link>
        </div>
      </article>

      <div>
        <h3 className="metadata-heading">{t('goal.specifications')}</h3>
        <dl className="metadata-grid">
          <div>
            <dt>{t('goal.targetDate')}</dt>
            <dd>{goal.data?.targetDate}</dd>
          </div>
          <div>
            <dt>{t('goal.dailyBudget')}</dt>
            <dd>
              {t('goal.perDay', {
                minutes: goal.data?.defaultDailyMinutes ?? dailyMinutes,
              })}
            </dd>
          </div>
          <div>
            <dt>{t('common.timezone')}</dt>
            <dd>{goal.data?.timezone}</dd>
          </div>
          <div>
            <dt>{t('goal.status')}</dt>
            <dd>
              <span className="status-badge">● {t('goal.active')}</span>
            </dd>
          </div>
        </dl>
      </div>

      <p className="next-step-notice">
        <strong>{t('goal.milestone')}</strong> {t('goal.milestoneDetail')}
      </p>
    </section>
  )
}

function AuthRequired() {
  const { t } = useI18n()
  return (
    <section className="panel auth-panel">
      <p className="eyebrow">{t('auth.session')}</p>
      <h1>{t('auth.ended')}</h1>
      <p className="lede">{t('auth.endedDetail')}</p>
      <Link className="button-link" to="/login">
        {t('common.signIn')}
      </Link>
    </section>
  )
}
