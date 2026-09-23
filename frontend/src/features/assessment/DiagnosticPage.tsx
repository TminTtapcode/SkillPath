import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  ApiError,
  type AssessmentQuestion,
  type AssessmentResult,
  getDiagnosticResult,
  getNextDiagnosticQuestion,
  submitDiagnosticAttempt,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n, type TranslationKey } from '../../shared/i18n/I18n'

export function DiagnosticPage() {
  const { t } = useI18n()
  const [searchParams] = useSearchParams()
  const sessionId = searchParams.get('session') ?? ''
  const question = useQuery({
    queryKey: ['diagnostic-question', sessionId],
    queryFn: () => getNextDiagnosticQuestion(sessionId),
    enabled: Boolean(sessionId),
    retry: false,
  })
  const completed = question.isSuccess && question.data === null
  const result = useQuery({
    queryKey: ['diagnostic-result', sessionId],
    queryFn: () => getDiagnosticResult(sessionId),
    enabled: Boolean(sessionId) && completed,
    retry: false,
  })

  if (!sessionId) {
    return (
      <section className="panel diagnostic-panel">
        <p className="eyebrow">{t('diagnostic.eyebrow')}</p>
        <h1>{t('diagnostic.noSession')}</h1>
        <p className="lede">{t('diagnostic.noSessionDetail')}</p>
        <Link className="button-link" to="/goal">
          {t('common.returnGoal')}
        </Link>
      </section>
    )
  }
  if (question.isPending)
    return <Loading label={t('diagnostic.loadingQuestion')} />
  if (question.error instanceof ApiError && question.error.status === 410) {
    return <ExpiredDiagnostic />
  }
  if (question.error) return <ErrorNotice error={question.error} />
  if (question.data) {
    return (
      <QuestionCard
        key={question.data.sessionQuestionId}
        sessionId={sessionId}
        question={question.data}
      />
    )
  }
  if (result.isPending) return <Loading label={t('diagnostic.loadingResult')} />
  if (result.error) return <ErrorNotice error={result.error} />
  if (!result.data) return <p className="notice">{t('diagnostic.noResult')}</p>
  return <DiagnosticResultView result={result.data} />
}

function QuestionCard({
  sessionId,
  question,
}: {
  sessionId: string
  question: AssessmentQuestion
}) {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string[]>([])
  const [confidence, setConfidence] = useState('0.6')
  const idempotencyKey = useRef(crypto.randomUUID())
  const startedAt = useRef(Date.now())
  const mutation = useMutation({
    mutationFn: () =>
      submitDiagnosticAttempt(
        sessionId,
        {
          sessionQuestionId: question.sessionQuestionId,
          selectedOptionIds: selected,
          selfConfidence: Number(confidence),
          timeSpentSeconds: Math.min(
            3600,
            Math.max(0, Math.round((Date.now() - startedAt.current) / 1000)),
          ),
        },
        idempotencyKey.current,
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['diagnostic-question', sessionId],
      })
    },
    onError: async (error) => {
      if (
        error instanceof ApiError &&
        error.problem?.code === 'QUESTION_ALREADY_ANSWERED'
      ) {
        await queryClient.invalidateQueries({
          queryKey: ['diagnostic-question', sessionId],
        })
      }
    },
  })
  const multiple = question.type === 'MULTIPLE_CHOICE'

  function toggle(optionId: string) {
    if (!multiple) {
      setSelected([optionId])
      return
    }
    setSelected((current) =>
      current.includes(optionId)
        ? current.filter((id) => id !== optionId)
        : [...current, optionId],
    )
  }

  return (
    <section className="panel diagnostic-panel">
      <div className="diagnostic-progress-row">
        <div>
          <p className="eyebrow">{t('diagnostic.title')}</p>
          <h1>
            {t('diagnostic.progress', {
              position: question.position,
              total: question.totalQuestions,
            })}
          </h1>
        </div>
        <span className="diagnostic-time">
          {t('diagnostic.aboutMinutes', {
            minutes: Math.max(1, Math.round(question.estimatedSeconds / 60)),
          })}
        </span>
      </div>
      <div
        className="diagnostic-progress-track"
        role="progressbar"
        aria-label={t('diagnostic.progressLabel')}
        aria-valuemin={0}
        aria-valuemax={question.totalQuestions}
        aria-valuenow={question.position - 1}
      >
        <span
          style={{
            width: `${((question.position - 1) / question.totalQuestions) * 100}%`,
          }}
        />
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (selected.length) mutation.mutate()
        }}
      >
        <fieldset className="diagnostic-fieldset" disabled={mutation.isPending}>
          <legend>{question.prompt}</legend>
          <p className="diagnostic-instruction">
            {multiple ? t('diagnostic.selectMany') : t('diagnostic.selectOne')}
          </p>
          <div className="diagnostic-options">
            {question.options.map((option) => {
              const checked = selected.includes(option.id)
              return (
                <label
                  className={`diagnostic-option ${checked ? 'selected' : ''}`}
                  key={option.id}
                >
                  <input
                    type={multiple ? 'checkbox' : 'radio'}
                    name="diagnostic-answer"
                    value={option.id}
                    checked={checked}
                    onChange={() => toggle(option.id)}
                  />
                  <span>{option.label}</span>
                </label>
              )
            })}
          </div>
        </fieldset>

        <label className="diagnostic-confidence" htmlFor="self-confidence">
          {t('diagnostic.confidence')}
          <select
            id="self-confidence"
            value={confidence}
            onChange={(event) => setConfidence(event.target.value)}
            disabled={mutation.isPending}
          >
            <option value="0.3">{t('diagnostic.confidenceLow')}</option>
            <option value="0.6">{t('diagnostic.confidenceMedium')}</option>
            <option value="0.9">{t('diagnostic.confidenceHigh')}</option>
          </select>
        </label>

        {mutation.error && <ErrorNotice error={mutation.error} />}
        <div className="diagnostic-actions">
          <Link className="button-link button-secondary" to="/goal">
            {t('diagnostic.leave')}
          </Link>
          <button
            type="submit"
            disabled={!selected.length || mutation.isPending}
          >
            {mutation.isPending
              ? t('diagnostic.saving')
              : t('diagnostic.submit')}
          </button>
        </div>
      </form>
      <p className="diagnostic-boundary-note">{t('diagnostic.boundary')}</p>
    </section>
  )
}

function DiagnosticResultView({ result }: { result: AssessmentResult }) {
  const { t } = useI18n()
  return (
    <section className="panel diagnostic-panel">
      <p className="eyebrow">{t('result.complete')}</p>
      <h1>{t('result.title')}</h1>
      <p className="lede">{result.interpretation}</p>

      <div className="diagnostic-score" aria-label={t('result.scoreLabel')}>
        <span>{t('result.score')}</span>
        <strong>{Math.round(result.overallObjectiveScore * 100)}%</strong>
        <small>{t('result.notMastery')}</small>
      </div>

      <h2 className="diagnostic-evidence-heading">
        {t('result.demonstrated')}
      </h2>
      <ul className="diagnostic-evidence-list">
        {result.evidence.map((item) => (
          <li key={item.evidenceId}>
            <div>
              <strong>{item.knowledgeNodeName}</strong>
              <span>
                {t(`result.dimension.${item.dimension}` as TranslationKey)}
              </span>
            </div>
            <span className="diagnostic-observation">
              {t('result.observed', { score: Math.round(item.score * 100) })}
            </span>
          </li>
        ))}
      </ul>

      <div className="diagnostic-actions">
        <Link className="button-link" to="/knowledge">
          {t('knowledge.open')}
        </Link>
        <Link className="button-link" to="/goal">
          {t('common.returnGoal')}
        </Link>
      </div>
    </section>
  )
}

function ExpiredDiagnostic() {
  const { t } = useI18n()
  return (
    <section className="panel diagnostic-panel">
      <p className="eyebrow">{t('diagnostic.expired')}</p>
      <h1>{t('diagnostic.expiredTitle')}</h1>
      <p className="lede">{t('diagnostic.expiredDetail')}</p>
      <Link className="button-link" to="/goal">
        {t('common.returnGoal')}
      </Link>
    </section>
  )
}
