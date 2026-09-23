import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { createGoal, listGoalTemplates } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { DEFAULT_TIMEZONE } from '../../shared/config/localization'
import { useI18n } from '../../shared/i18n/I18n'

type FormValues = {
  goalTemplateId: string
  targetDate: string
  timezone: string
  defaultDailyMinutes: number
}

function futureDate(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

const MINUTE_OPTIONS = [30, 45, 60, 90, 120, 180]

export function GoalSetupPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const templates = useQuery({
    queryKey: ['goal-templates'],
    queryFn: listGoalTemplates,
  })
  const schema = useMemo(
    () =>
      z.object({
        goalTemplateId: z.string().min(1, t('validation.goal')),
        targetDate: z.string().min(1, t('validation.targetDate')),
        timezone: z.string().min(1, t('validation.timezoneRequired')),
        defaultDailyMinutes: z.number().int().min(30).max(180),
      }),
    [t],
  )
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      goalTemplateId: '',
      targetDate: futureDate(90),
      timezone: DEFAULT_TIMEZONE,
      defaultDailyMinutes: 60,
    },
  })
  const mutation = useMutation({
    mutationFn: (values: FormValues) => createGoal(values, crypto.randomUUID()),
    onSuccess: (goal) => {
      queryClient.setQueryData(['active-goal'], goal)
      void navigate('/goal')
    },
  })

  const selectedTemplateId = form.watch('goalTemplateId')
  const selectedMinutes = form.watch('defaultDailyMinutes')
  const selectedTemplate = templates.data?.find(
    (template) => template.id === selectedTemplateId,
  )

  if (templates.isPending) return <Loading label={t('goalSetup.loading')} />
  if (templates.error) return <ErrorNotice error={templates.error} />
  if (!templates.data?.length) {
    return <p className="notice">{t('goalSetup.empty')}</p>
  }

  return (
    <section className="panel goal-panel">
      <p className="eyebrow">{t('goalSetup.eyebrow')}</p>
      <h1>{t('goalSetup.title')}</h1>
      <p className="lede">{t('goalSetup.lede')}</p>

      {mutation.error && <ErrorNotice error={mutation.error} />}

      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <div className="form-group">
          <label htmlFor="goalTemplateId">{t('goalSetup.track')}</label>
          <select
            id="goalTemplateId"
            aria-invalid={Boolean(form.formState.errors.goalTemplateId)}
            aria-describedby={
              form.formState.errors.goalTemplateId
                ? 'goalTemplateId-error'
                : undefined
            }
            {...form.register('goalTemplateId')}
          >
            <option value="">{t('goalSetup.select')}</option>
            {templates.data.map((template) => (
              <option key={template.id} value={template.id}>
                {template.displayName}
              </option>
            ))}
          </select>
          {form.formState.errors.goalTemplateId && (
            <p id="goalTemplateId-error" className="field-error" role="alert">
              {form.formState.errors.goalTemplateId.message}
            </p>
          )}
        </div>

        {selectedTemplate && (
          <div className="track-selection-card">
            <h3>{selectedTemplate.displayName}</h3>
            <p>{selectedTemplate.description}</p>
            <div className="track-tags">
              <span className="track-tag">Java 21</span>
              <span className="track-tag">Spring Boot 3</span>
              <span className="track-tag">MySQL 8.4</span>
              <span className="track-tag">REST API</span>
            </div>
          </div>
        )}

        <div className="form-group">
          <label htmlFor="targetDate">{t('goalSetup.date')}</label>
          <input
            id="targetDate"
            type="date"
            min={futureDate(1)}
            aria-invalid={Boolean(form.formState.errors.targetDate)}
            aria-describedby={
              form.formState.errors.targetDate ? 'targetDate-error' : undefined
            }
            {...form.register('targetDate')}
          />
          {form.formState.errors.targetDate && (
            <p id="targetDate-error" className="field-error" role="alert">
              {form.formState.errors.targetDate.message}
            </p>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="defaultDailyMinutes">{t('goalSetup.budget')}</label>
          <div
            className="minute-pill-group"
            role="group"
            aria-label={t('goalSetup.quickMinutes')}
          >
            {MINUTE_OPTIONS.map((minutes) => (
              <button
                type="button"
                key={minutes}
                className={`minute-pill ${selectedMinutes === minutes ? 'active' : ''}`}
                onClick={() =>
                  form.setValue('defaultDailyMinutes', minutes, {
                    shouldValidate: true,
                  })
                }
              >
                {minutes}m
              </button>
            ))}
          </div>
          <select
            id="defaultDailyMinutes"
            style={{ display: 'none' }}
            {...form.register('defaultDailyMinutes', { valueAsNumber: true })}
          >
            {MINUTE_OPTIONS.map((minutes) => (
              <option key={minutes} value={minutes}>
                {t('goalSetup.minutes', { minutes })}
              </option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="timezone">{t('common.timezone')}</label>
          <input
            id="timezone"
            aria-invalid={Boolean(form.formState.errors.timezone)}
            aria-describedby={
              form.formState.errors.timezone ? 'timezone-error' : undefined
            }
            {...form.register('timezone')}
          />
          {form.formState.errors.timezone && (
            <p id="timezone-error" className="field-error" role="alert">
              {form.formState.errors.timezone.message}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={mutation.isPending}
          className={mutation.isPending ? 'pending' : ''}
          style={{ marginTop: '0.5rem' }}
        >
          {mutation.isPending ? t('goalSetup.pending') : t('goalSetup.submit')}
        </button>
      </form>
    </section>
  )
}
