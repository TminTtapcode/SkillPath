import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { register as registerAccount } from '../../shared/api/client'
import { ErrorNotice } from '../../shared/components/AsyncState'

const schema = z.object({
  email: z.email('Enter a valid email address.'),
  password: z.string().min(12, 'Use at least 12 characters.').max(128),
  displayName: z.string().trim().min(1, 'Enter your name.').max(100),
  timezone: z.string().min(1, 'Timezone is required.'),
})

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      email: '',
      password: '',
      displayName: '',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    },
  })
  const mutation = useMutation({
    mutationFn: registerAccount,
    onSuccess: (profile) => {
      queryClient.setQueryData(['me'], profile)
      void navigate('/goals/new')
    },
  })

  return (
    <section className="panel auth-panel">
      <p className="eyebrow">Start your route</p>
      <h1>Create account</h1>
      <p className="lede">Tell us who you are. Your first goal comes next.</p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <div className="form-group">
          <label htmlFor="displayName">Display name</label>
          <input
            id="displayName"
            autoComplete="name"
            aria-invalid={Boolean(form.formState.errors.displayName)}
            aria-describedby={
              form.formState.errors.displayName ? 'displayName-error' : undefined
            }
            {...form.register('displayName')}
          />
          <FieldError
            id="displayName-error"
            message={form.formState.errors.displayName?.message}
          />
        </div>

        <div className="form-group">
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={Boolean(form.formState.errors.email)}
            aria-describedby={
              form.formState.errors.email ? 'email-error' : undefined
            }
            {...form.register('email')}
          />
          <FieldError
            id="email-error"
            message={form.formState.errors.email?.message}
          />
        </div>

        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(form.formState.errors.password)}
            aria-describedby={
              form.formState.errors.password ? 'password-error' : undefined
            }
            {...form.register('password')}
          />
          <FieldError
            id="password-error"
            message={form.formState.errors.password?.message}
          />
        </div>

        <div className="form-group">
          <label htmlFor="timezone">Timezone</label>
          <input
            id="timezone"
            autoComplete="off"
            aria-invalid={Boolean(form.formState.errors.timezone)}
            aria-describedby={
              form.formState.errors.timezone ? 'timezone-error' : undefined
            }
            {...form.register('timezone')}
          />
          <FieldError
            id="timezone-error"
            message={form.formState.errors.timezone?.message}
          />
        </div>

        <button
          type="submit"
          disabled={mutation.isPending}
          className={mutation.isPending ? 'pending' : ''}
        >
          {mutation.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.875rem' }}>
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} className="field-error" role="alert">
      {message}
    </p>
  ) : null
}
