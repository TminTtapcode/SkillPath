import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { Link } from 'react-router'
import {
  ApiError,
  generateTodayPlan,
  getTodayPlan,
  reviseTodayPlan,
  type TodayPlan,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n, type TranslationKey } from '../../shared/i18n/I18n'

const reasonKeys: Record<string, TranslationKey> = {
  GOAL_RELEVANT_GAP: 'planner.reason.GOAL_RELEVANT_GAP',
  UNLOCKS_DEPENDENCIES: 'planner.reason.UNLOCKS_DEPENDENCIES',
  REVIEW_DUE: 'planner.reason.REVIEW_DUE',
  TIME_FIT: 'planner.reason.TIME_FIT',
}

const statusKeys: Record<string, TranslationKey> = {
  ASSIGNED: 'planner.status.ASSIGNED',
  IN_PROGRESS: 'planner.status.IN_PROGRESS',
  COMPLETED: 'planner.status.COMPLETED',
}

export function TodayPage() {
  const { locale, t } = useI18n()
  const queryClient = useQueryClient()
  const retryKey = useRef<string | null>(null)
  const query = useQuery({
    queryKey: ['today', locale],
    queryFn: getTodayPlan,
    retry: false,
  })
  const command = useMutation({
    mutationFn: (action: 'generate' | 'revise') => {
      retryKey.current ??= crypto.randomUUID()
      return action === 'generate'
        ? generateTodayPlan(retryKey.current)
        : reviseTodayPlan(retryKey.current)
    },
    onSuccess: (plan) => {
      retryKey.current = null
      queryClient.setQueryData(['today', locale], plan)
      void queryClient.invalidateQueries({ queryKey: ['roadmap'] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        retryKey.current = null
        void query.refetch()
      }
    },
  })

  if (query.isPending) return <Loading label={t('planner.pending')} />
  if (query.error instanceof ApiError && query.error.status === 401)
    return (
      <p className="notice">
        {t('error.UNAUTHENTICATED')}{' '}
        <Link to="/login">{t('common.signIn')}</Link>
      </p>
    )
  if (query.error instanceof ApiError && query.error.status === 404)
    return (
      <p className="notice">
        {t('error.ACTIVE_GOAL_NOT_FOUND')}{' '}
        <Link to="/goals/new">{t('goal.chooseTrack')}</Link>
      </p>
    )
  if (query.error) return <ErrorNotice error={query.error} />

  const plan = query.data as TodayPlan
  const notGenerated = plan.outcome === 'NOT_GENERATED'
  const canRevise =
    Boolean(plan.planId) &&
    plan.items.every((item) => item.status === 'ASSIGNED')
  const errorCode =
    command.error instanceof ApiError ? command.error.problem?.code : undefined
  const totalPlannedMinutes = plan.items.reduce(
    (sum, item) => sum + item.minutes,
    0,
  )
  const budgetPercent =
    plan.budgetMinutes > 0
      ? Math.min(
          100,
          Math.round((totalPlannedMinutes / plan.budgetMinutes) * 100),
        )
      : 0

  return (
    <section className="panel planner-page">
      <div className="page-header-block">
        <p className="eyebrow">SkillPath · Today</p>
        <h1>{t('planner.title')}</h1>
        <p className="lede">{t('planner.intro')}</p>
      </div>

      <div className="budget-progress-card">
        <div className="budget-progress-header">
          <div className="budget-stats-left">
            <span className="status-badge">
              {t('planner.budget', { minutes: plan.budgetMinutes })}
            </span>
          </div>
          <div className="budget-actions-right">
            <Link className="button-link button-secondary" to="/roadmap">
              {t('roadmap.open')}
            </Link>
          </div>
        </div>
        {plan.budgetMinutes > 0 && (
          <div className="budget-meter-wrap">
            <div
              className="budget-meter-track"
              role="progressbar"
              aria-valuenow={totalPlannedMinutes}
              aria-valuemin={0}
              aria-valuemax={plan.budgetMinutes}
              aria-label={t('planner.budget', { minutes: plan.budgetMinutes })}
            >
              <div
                className="budget-meter-fill"
                style={{ width: `${budgetPercent}%` }}
              />
            </div>
            <div className="budget-meter-caption">
              <span>{totalPlannedMinutes} min allocated</span>
              <span>
                {plan.budgetMinutes - totalPlannedMinutes} min remaining
              </span>
            </div>
          </div>
        )}
      </div>

      {notGenerated && (
        <div className="today-empty-prompt">
          <p className="notice">{t('planner.empty')}</p>
        </div>
      )}

      {plan.activeManualSessionId && (
        <p className="notice">
          {t('planner.manual')}{' '}
          <Link to={`/learning/session/${plan.activeManualSessionId}`}>
            {t('planner.start')}
          </Link>
        </p>
      )}
      {plan.outcome === 'GOAL_COMPLETION_CANDIDATE' && (
        <p className="notice">{t('planner.candidate')}</p>
      )}
      {plan.outcome === 'NO_TIME_FIT_VARIANT' && (
        <p className="notice">{t('planner.timeFit')}</p>
      )}
      {plan.outcome === 'NO_SAFE_RECOMMENDATION' && (
        <p className="notice">
          {t(
            plan.reasonCode === 'NO_CONTENT'
              ? 'planner.noContent'
              : 'planner.noSafe',
          )}
        </p>
      )}

      {plan.items.length > 0 && (
        <div className="today-tasks-container">
          <ol className="planner-task-list">
            {plan.items.map((item) => (
              <li className="planner-task" key={item.taskId}>
                <div className="task-top-meta">
                  <span className="eyebrow">
                    {item.nodeName} · {item.minutes} min
                  </span>
                  <span className="status-badge">
                    {statusKeys[item.status]
                      ? t(statusKeys[item.status])
                      : item.status}
                  </span>
                </div>
                <h2>{item.title}</h2>
                <div className="task-reason-box">
                  <p>
                    <strong>{t('planner.reason')}: </strong>
                    {item.reasons
                      .map((reason) =>
                        reasonKeys[reason] ? t(reasonKeys[reason]) : reason,
                      )
                      .join(' · ')}
                  </p>
                </div>
              </li>
            ))}
          </ol>

          <div className="today-summary-footer">
            <p>
              {t('planner.remaining', {
                minutes:
                  plan.budgetMinutes -
                  plan.items.reduce((sum, item) => sum + item.minutes, 0),
              })}
            </p>
            {plan.alternatives.length > 0 && (
              <p>
                {t('planner.alternatives')}: {plan.alternatives.join(', ')}
              </p>
            )}
            {plan.sessionId && (
              <div className="today-primary-cta">
                <Link
                  className="button-link today-start-btn"
                  to={`/learning/session/${plan.sessionId}`}
                >
                  <svg
                    aria-hidden="true"
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="currentColor"
                  >
                    <polygon points="5 3 19 12 5 21 5 3" />
                  </svg>
                  <span>{t('planner.start')}</span>
                </Link>
              </div>
            )}
            <p className="planner-boundary">{t('planner.selfReport')}</p>
          </div>
        </div>
      )}

      {command.error &&
        (errorCode === 'ACTIVE_LEARNING_SESSION' ||
        errorCode === 'ACTIVE_PLANNER_SESSION' ? (
          <p className="notice notice-error" role="alert">
            {t('planner.manual')}
          </p>
        ) : (
          <ErrorNotice error={command.error} />
        ))}

      <div className="planner-actions">
        {notGenerated ? (
          <button
            type="button"
            className="today-generate-btn"
            disabled={command.isPending}
            onClick={() => command.mutate('generate')}
          >
            <svg
              aria-hidden="true"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
            >
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            <span>{t('planner.generate')}</span>
          </button>
        ) : (
          canRevise &&
          !plan.activeManualSessionId && (
            <button
              type="button"
              className="button-secondary"
              disabled={command.isPending}
              onClick={() => command.mutate('revise')}
            >
              {t('planner.revise')}
            </button>
          )
        )}
        {command.isPending && <span role="status">{t('planner.pending')}</span>}
      </div>
      <div className="page-footer-nav">
        <Link to="/goal">{t('common.returnGoal')}</Link>
      </div>
    </section>
  )
}
