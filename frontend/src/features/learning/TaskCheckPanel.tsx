import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import {
  ApiError,
  getTaskCheck,
  submitTaskCheck,
  type TaskCheckAnswer,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

export function TaskCheckPanel({
  taskId,
  onComplete,
}: {
  taskId: string
  onComplete: () => void
}) {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string[]>([])
  const [minutes, setMinutes] = useState('')
  const [result, setResult] = useState<number | null>(null)
  const pending = useRef<{
    signature: string
    key: string
    answer: TaskCheckAnswer
  } | null>(null)
  const check = useQuery({
    queryKey: ['task-check', taskId],
    queryFn: () => getTaskCheck(taskId),
    retry: false,
  })
  const submit = useMutation({
    mutationFn: (input: { key: string; answer: TaskCheckAnswer }) =>
      submitTaskCheck(taskId, input.answer, input.key),
    onSuccess: (attempt) => {
      setResult(attempt.score)
      pending.current = null
      void queryClient.invalidateQueries({ queryKey: ['today'] })
      void queryClient.invalidateQueries({ queryKey: ['roadmap'] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: ['learning-session'] })
      }
    },
  })
  if (check.isPending) return <Loading label={t('learning.check.loading')} />
  if (check.error) return <ErrorNotice error={check.error} />
  const question = check.data!
  if (result !== null)
    return (
      <section aria-labelledby="task-check-title" className="task-check">
        <h3 id="task-check-title">{t('learning.check.title')}</h3>
        <p role="status">
          {t('learning.check.observed', { score: Math.round(result * 100) })}
        </p>
        <button onClick={onComplete}>{t('learning.check.continue')}</button>
      </section>
    )
  const validMinutes =
    minutes.trim() !== '' &&
    Number.isInteger(Number(minutes)) &&
    Number(minutes) >= 0 &&
    Number(minutes) <= 360
  function send() {
    const answer: TaskCheckAnswer = {
      selectedOptionIds: [...selected].sort(),
      timeSpentSeconds: 0,
      actualMinutes: Number(minutes),
    }
    const signature = JSON.stringify(answer)
    if (pending.current?.signature !== signature)
      pending.current = { signature, key: crypto.randomUUID(), answer }
    submit.mutate(pending.current)
  }
  return (
    <section aria-labelledby="task-check-title" className="task-check">
      <h3 id="task-check-title">{t('learning.check.title')}</h3>
      <p>{t('learning.check.boundary')}</p>
      <fieldset disabled={submit.isPending}>
        <legend>{question.prompt}</legend>
        <p>
          {t(
            question.type === 'MULTIPLE_CHOICE'
              ? 'learning.check.many'
              : 'learning.check.one',
          )}
        </p>
        {question.options.map((option) => (
          <label key={option.id} className="task-check-option">
            <input
              type={question.type === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
              name={`task-check-${taskId}`}
              checked={selected.includes(option.id)}
              onChange={() =>
                setSelected((before) =>
                  question.type === 'MULTIPLE_CHOICE'
                    ? before.includes(option.id)
                      ? before.filter((id) => id !== option.id)
                      : [...before, option.id]
                    : [option.id],
                )
              }
            />
            {option.label}
          </label>
        ))}
      </fieldset>
      <label className="learning-minutes">
        {t('learning.actualMinutes')}
        <input
          type="number"
          min="0"
          max="360"
          step="1"
          value={minutes}
          aria-invalid={minutes !== '' && !validMinutes}
          onChange={(event) => setMinutes(event.target.value)}
          disabled={submit.isPending}
        />
      </label>
      <button
        disabled={submit.isPending || selected.length === 0 || !validMinutes}
        onClick={send}
      >
        {t('learning.check.submit')}
      </button>
      {submit.error && (
        <>
          <ErrorNotice error={submit.error} />
          {pending.current && (
            <button
              className="button-secondary"
              disabled={submit.isPending}
              onClick={() => submit.mutate(pending.current!)}
            >
              {t('learning.retry')}
            </button>
          )}
        </>
      )}
    </section>
  )
}
