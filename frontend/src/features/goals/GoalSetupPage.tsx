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

export function GoalSetupPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const templates = useQuery({
    queryKey: ['goal-templates'],
    queryFn: listGoalTemplates,
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
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
        We will use your deadline and daily budget to shape the route.
      </p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <label htmlFor="goalTemplateId">Learning path</label>
        <select id="goalTemplateId" {...form.register('goalTemplateId')}>
          <option value="">Select a path</option>
          {templates.data.map((template) => (
            <option key={template.id} value={template.id}>
              {template.displayName}
            </option>
          ))}
        </select>
        {form.formState.errors.goalTemplateId && (
          <p className="field-error">
            {form.formState.errors.goalTemplateId.message}
          </p>
        )}

        <label htmlFor="targetDate">Target date</label>
        <input
          id="targetDate"
          type="date"
          min={futureDate(1)}
          {...form.register('targetDate')}
        />
        {form.formState.errors.targetDate && (
          <p className="field-error">
            {form.formState.errors.targetDate.message}
          </p>
        )}

        <label htmlFor="defaultDailyMinutes">Daily study time</label>
        <select
          id="defaultDailyMinutes"
          {...form.register('defaultDailyMinutes', { valueAsNumber: true })}
        >
          {[30, 45, 60, 90, 120, 180].map((minutes) => (
            <option key={minutes} value={minutes}>
              {minutes} minutes
            </option>
          ))}
        </select>

        <label htmlFor="timezone">Timezone</label>
        <input id="timezone" {...form.register('timezone')} />
        {form.formState.errors.timezone && (
          <p className="field-error">
            {form.formState.errors.timezone.message}
          </p>
        )}

        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating goal…' : 'Create my route'}
        </button>
      </form>
    </section>
  )
}
