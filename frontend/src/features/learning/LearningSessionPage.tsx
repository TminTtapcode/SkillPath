import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  ApiError,
  commandLearningTask,
  getLearningSession,
  type LearningOperation,
  type LearningTask,
} from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'
import { TaskCheckPanel } from './TaskCheckPanel'

type PendingCommand = {
  taskId: string
  operation: LearningOperation
  body?:
    | { actualMinutes: number; completedStepIds: string[] }
    | { reasonCode: 'TIME' | 'DIFFICULT' | 'OTHER' }
  key: string
  signature: string
}

const activityKeys = {
  LEARN: 'learning.activity.LEARN',
  PRACTICE: 'learning.activity.PRACTICE',
  RECALL: 'learning.activity.RECALL',
} as const

export function LearningSessionPage() {
  const { id } = useParams()
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [checked, setChecked] = useState<string[]>([])
  const [actualMinutes, setActualMinutes] = useState('')
  const [reason, setReason] = useState<'TIME' | 'DIFFICULT' | 'OTHER'>(
    'DIFFICULT',
  )
  const pending = useRef<PendingCommand | null>(null)
  const session = useQuery({
    queryKey: ['learning-session', id],
    queryFn: () => getLearningSession(id!),
    enabled: !!id,
    retry: false,
  })
  const task =
    session.data?.status === 'ACTIVE'
      ? session.data.tasks.find((item) => item.status !== 'COMPLETED')
      : undefined
  useEffect(() => {
    setChecked([])
    setActualMinutes('')
    pending.current = null
  }, [task?.id])
  const command = useMutation({
    mutationFn: (input: PendingCommand) =>
      commandLearningTask(input.taskId, input.operation, input.key, input.body),
    onSuccess: () => {
      pending.current = null
      void queryClient.invalidateQueries({ queryKey: ['learning-session', id] })
      void queryClient.invalidateQueries({
        queryKey: ['learning-session-active'],
      })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        void queryClient.invalidateQueries({
          queryKey: ['learning-session', id],
        })
      }
    },
  })

  function send(operation: LearningOperation, current: LearningTask) {
    const body =
      operation === 'complete'
        ? {
            actualMinutes: Number(actualMinutes),
            completedStepIds: [...checked].sort(),
          }
        : ['skip', 'blocked', 'abandon'].includes(operation)
          ? { reasonCode: reason }
          : undefined
    const signature = JSON.stringify({ taskId: current.id, operation, body })
    if (pending.current?.signature !== signature) {
      pending.current = {
        taskId: current.id,
        operation,
        body,
        signature,
        key: crypto.randomUUID(),
      }
    }
    command.mutate(pending.current)
  }

  if (!id) return <p>{t('learning.missing')}</p>
  if (session.isPending) return <Loading label={t('learning.loading')} />
  if (session.error instanceof ApiError && session.error.status === 401)
    return <Link to="/login">{t('common.signIn')}</Link>
  if (session.error instanceof ApiError && session.error.status === 404)
    return <p role="alert">{t('learning.missing')}</p>
  if (session.error) return <ErrorNotice error={session.error} />
  const data = session.data!
  const validMinutes =
    actualMinutes.trim() !== '' &&
    Number.isInteger(Number(actualMinutes)) &&
    Number(actualMinutes) >= 0 &&
    Number(actualMinutes) <= 360
  const canComplete =
    task &&
    validMinutes &&
    checked.length === task.checklist.length &&
    task.checklist.every((item) => checked.includes(item.id))

  return (
    <section className="panel learning-panel">
      <p className="eyebrow">{t('learning.eyebrow')}</p>
      <h1>{data.title}</h1>
      {data.status === 'COMPLETED' && <h2>{t('learning.completed')}</h2>}
      {data.status === 'STOPPED' && <h2>{t('learning.stopped')}</h2>}
      {task && (
        <article
          className="learning-task"
          aria-labelledby="learning-task-title"
        >
          <p className="eyebrow">
            {t('learning.step', {
              position: task.position,
              total: data.tasks.length,
            })}
          </p>
          <h2 id="learning-task-title">{task.title}</h2>
          <p className="task-category">{t(activityKeys[task.activityType])}</p>
          <p>{task.instructions}</p>
          <div className="learning-resource">
            <h3>{task.resourceTitle}</h3>
            <p>{task.resourceBody}</p>
          </div>
          {task.status === 'IN_PROGRESS' &&
            task.evaluationMode !== 'OBJECTIVE' && (
              <>
                <fieldset className="learning-checklist">
                  <legend>{t('learning.checklist')}</legend>
                  {task.checklist.map((item) => (
                    <label key={item.id}>
                      <input
                        type="checkbox"
                        checked={checked.includes(item.id)}
                        onChange={(event) =>
                          setChecked(
                            event.target.checked
                              ? [...checked, item.id]
                              : checked.filter((id) => id !== item.id),
                          )
                        }
                        disabled={command.isPending}
                      />
                      {item.label}
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
                    aria-describedby="learning-completion-hint"
                    aria-invalid={actualMinutes !== '' && !validMinutes}
                    value={actualMinutes}
                    onChange={(event) => setActualMinutes(event.target.value)}
                    disabled={command.isPending}
                  />
                </label>
                <p className="form-hint" id="learning-completion-hint">
                  {t('learning.validation')}
                </p>
              </>
            )}
          {['ASSIGNED', 'IN_PROGRESS'].includes(task.status) && (
            <label className="learning-reason">
              {t('learning.reason')}
              <select
                value={reason}
                onChange={(event) =>
                  setReason(
                    event.target.value as 'TIME' | 'DIFFICULT' | 'OTHER',
                  )
                }
                disabled={command.isPending}
              >
                <option value="DIFFICULT">
                  {t('learning.reason.DIFFICULT')}
                </option>
                <option value="TIME">{t('learning.reason.TIME')}</option>
                <option value="OTHER">{t('learning.reason.OTHER')}</option>
              </select>
            </label>
          )}
          <div className="task-actions learning-actions">
            {task.status === 'ASSIGNED' && (
              <>
                <button
                  disabled={command.isPending}
                  onClick={() => send('start', task)}
                >
                  {t('learning.beginTask')}
                </button>
                <button
                  className="button-secondary"
                  disabled={command.isPending}
                  onClick={() => send('skip', task)}
                >
                  {t('learning.skipTask')}
                </button>
              </>
            )}
            {task.status === 'IN_PROGRESS' && (
              <>
                {task.evaluationMode !== 'OBJECTIVE' && (
                  <button
                    disabled={command.isPending || !canComplete}
                    onClick={() => send('complete', task)}
                  >
                    {t('learning.completeTask')}
                  </button>
                )}
                <button
                  className="button-secondary"
                  disabled={command.isPending}
                  onClick={() => send('blocked', task)}
                >
                  {t('learning.blockTask')}
                </button>
                <button
                  className="button-secondary"
                  disabled={command.isPending}
                  onClick={() => send('abandon', task)}
                >
                  {t('learning.abandonTask')}
                </button>
              </>
            )}
            {task.status === 'BLOCKED' && (
              <button
                disabled={command.isPending}
                onClick={() => send('resume', task)}
              >
                {t('learning.resumeTask')}
              </button>
            )}
          </div>
          {task.status === 'IN_PROGRESS' &&
            task.evaluationMode === 'OBJECTIVE' && (
              <TaskCheckPanel
                taskId={task.id}
                onComplete={() => void session.refetch()}
              />
            )}
          {command.error && (
            <>
              <ErrorNotice error={command.error} />
              {pending.current && (
                <button
                  className="button-secondary"
                  disabled={command.isPending}
                  onClick={() => command.mutate(pending.current!)}
                >
                  {t('learning.retry')}
                </button>
              )}
              <button
                className="button-secondary"
                onClick={() => void session.refetch()}
              >
                {t('learning.sync')}
              </button>
            </>
          )}
        </article>
      )}
      <p className="next-step-notice">
        {t(
          data.tasks.some((item) => item.evaluationMode === 'OBJECTIVE')
            ? 'learning.check.sessionBoundary'
            : 'learning.boundary',
        )}
      </p>
      <Link to="/learning">{t('learning.goCatalog')}</Link>
      <Link to="/goal">{t('common.returnGoal')}</Link>
    </section>
  )
}
