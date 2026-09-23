import { useMutation, useQuery } from '@tanstack/react-query'
import { useRef } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  ApiError,
  getActiveLearningSession,
  listLearningSequences,
  startLearningSession,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

export function LearningCatalogPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const pending = useRef<{ sequenceKey: string; key: string } | null>(null)
  const sequences = useQuery({
    queryKey: ['learning-sequences'],
    queryFn: listLearningSequences,
    retry: false,
  })
  const active = useQuery({
    queryKey: ['learning-session-active'],
    queryFn: getActiveLearningSession,
    retry: false,
  })
  const start = useMutation({
    mutationFn: (sequenceKey: string) => {
      if (pending.current?.sequenceKey !== sequenceKey) {
        pending.current = { sequenceKey, key: crypto.randomUUID() }
      }
      return startLearningSession(sequenceKey, pending.current.key)
    },
    onSuccess: (result) => {
      pending.current = null
      void navigate(`/learning/session/${result.sessionId}`)
    },
  })

  if (sequences.isPending || active.isPending)
    return <Loading label={t('learning.loading')} />
  if (sequences.error instanceof ApiError && sequences.error.status === 401)
    return <Link to="/login">{t('common.signIn')}</Link>
  if (sequences.error instanceof ApiError && sequences.error.status === 404)
    return (
      <section className="panel">
        <h1>{t('learning.noGoal')}</h1>
        <Link to="/goals/new">{t('goal.chooseTrack')}</Link>
      </section>
    )
  if (sequences.error) return <ErrorNotice error={sequences.error} />
  if (active.error) return <ErrorNotice error={active.error} />

  return (
    <section className="panel learning-panel">
      <p className="eyebrow">{t('learning.eyebrow')}</p>
      <h1>{t('learning.title')}</h1>
      <p className="lede">{t('learning.intro')}</p>
      {active.data && (
        <p>
          <Link
            className="button-link"
            to={`/learning/session/${active.data.id}`}
          >
            {t('learning.continue')}
          </Link>
        </p>
      )}
      {!sequences.data?.length && <p>{t('learning.empty')}</p>}
      {sequences.data?.map((sequence) => (
        <article className="learning-sequence" key={sequence.key}>
          <h2>{sequence.title}</h2>
          <p>{sequence.description}</p>
          <ol>
            {sequence.steps.map((step) => (
              <li key={step.position}>
                <strong>{step.title}</strong> ·{' '}
                {t('learning.minutes', { minutes: step.minutes })}
              </li>
            ))}
          </ol>
          <button
            type="button"
            disabled={start.isPending || !!active.data}
            onClick={() => start.mutate(sequence.key)}
          >
            {t('learning.start')} ·{' '}
            {t('learning.minutes', { minutes: sequence.totalMinutes })}
          </button>
          {start.error && <ErrorNotice error={start.error} />}
        </article>
      ))}
      <p className="next-step-notice">{t('learning.boundary')}</p>
      <Link to="/goal">{t('common.returnGoal')}</Link>
    </section>
  )
}
