import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { createGoal, listGoalTemplates } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'

const schema = z.object({
  goalTemplateId: z.string().min(1, 'Choose a goal.'),
  targetDate: z.string().min(1, 'Choose a target date.'),
  timezone: z.string().min(1, 'Enter a timezone.'),
  defaultDailyMinutes: z.number().int().min(30).max(180),
})

type FormValues = z.infer<typeof schema>

function futureDate(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

const MINUTE_OPTIONS = [30, 45, 60, 90, 120, 180]

export function GoalSetupPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const templates = useQuery({
    queryKey: ['goal-templates'],
    queryFn: listGoalTemplates,
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      goalTemplateId: '',
      targetDate: futureDate(90),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
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

  if (templates.isPending) return <Loading label="Loading learning paths…" />
  if (templates.error) return <ErrorNotice error={templates.error} />
  if (!templates.data?.length) {
    return <p className="notice">No learning paths are available yet.</p>
  }

  return (
    <section className="panel goal-panel">
      <p className="eyebrow">Choose the destination</p>
      <h1>Create your first goal</h1>
      <p className="lede">
        We will use your deadline and daily budget to calculate the shortest defensible learning route.
      </p>

      {mutation.error && <ErrorNotice error={mutation.error} />}

      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <div className="form-group">
          <label htmlFor="goalTemplateId">Curated Career Track</label>
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
            <option value="">Select a path</option>
            {templates.data.map((template) => (
              <option key={template.id} value={template.id}>
                {template.displayName}
              </option>
            ))}
          </select>
          {form.formState.errors.goalTemplateId && (
            <p
              id="goalTemplateId-error"
              className="field-error"
              role="alert"
            >
              {form.formState.errors.goalTemplateId.message}
            </p>
          )}
        </div>

        {selectedTemplateId && (
          <div className="track-selection-card">
            <h3>Java Backend Internship Readiness</h3>
            <p>
              Curated track covering Java 21 OOP, Collections, Multithreading,
              Spring Boot 3, and MySQL database fundamentals.
            </p>
            <div className="track-tags">
              <span className="track-tag">Java 21</span>
              <span className="track-tag">Spring Boot 3</span>
              <span className="track-tag">MySQL 8.4</span>
              <span className="track-tag">REST API</span>
              <span className="track-tag">Internship Ready</span>
            </div>
          </div>
        )}

        <div className="form-group">
          <label htmlFor="targetDate">Target readiness date</label>
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
          <label htmlFor="defaultDailyMinutes">Daily study budget</label>
          <div className="minute-pill-group" role="group" aria-label="Quick daily minutes selection">
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
                {minutes} minutes
              </option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="timezone">Timezone</label>
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
          {mutation.isPending ? 'Calculating route…' : 'Generate learning route'}
        </button>
      </form>
    </section>
  )
}
