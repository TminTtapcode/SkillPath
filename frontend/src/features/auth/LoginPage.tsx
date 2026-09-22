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
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          {...form.register('email')}
        />
        {form.formState.errors.email && (
          <p className="field-error">{form.formState.errors.email.message}</p>
        )}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          {...form.register('password')}
        />
        {form.formState.errors.password && (
          <p className="field-error">
            {form.formState.errors.password.message}
          </p>
        )}

        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p>
        New here? <Link to="/register">Create an account</Link>
      </p>
    </section>
  )
}
