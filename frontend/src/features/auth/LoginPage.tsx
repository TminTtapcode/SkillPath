import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { login } from '../../shared/api/client'
import { ErrorNotice } from '../../shared/components/AsyncState'

const schema = z.object({
  email: z.email('Enter a valid email address.'),
  password: z.string().min(1, 'Enter your password.').max(128),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: { email: '', password: '' },
  })
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (profile) => {
      queryClient.setQueryData(['me'], profile)
      void navigate('/goal')
    },
  })

  return (
    <section className="panel auth-panel">
      <p className="eyebrow">Welcome back</p>
      <h1>Sign in</h1>
      <p className="lede">Continue your personalized learning route.</p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
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
          {form.formState.errors.email && (
            <p id="email-error" className="field-error" role="alert">
              {form.formState.errors.email.message}
            </p>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(form.formState.errors.password)}
            aria-describedby={
              form.formState.errors.password ? 'password-error' : undefined
            }
            {...form.register('password')}
          />
          {form.formState.errors.password && (
            <p id="password-error" className="field-error" role="alert">
              {form.formState.errors.password.message}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={mutation.isPending}
          className={mutation.isPending ? 'pending' : ''}
        >
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.875rem' }}>
        New to SkillPath? <Link to="/register">Create an account</Link>
      </p>
    </section>
  )
}
