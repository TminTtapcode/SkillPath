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
  timezone: z.string().min(1),
})

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
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
      <h1>Create your SkillPath account</h1>
      <p className="lede">Tell us who you are. Your first goal comes next.</p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <label htmlFor="displayName">Display name</label>
        <input
          id="displayName"
          autoComplete="name"
          {...form.register('displayName')}
        />
        <FieldError message={form.formState.errors.displayName?.message} />

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          {...form.register('email')}
        />
        <FieldError message={form.formState.errors.email?.message} />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          {...form.register('password')}
        />
        <FieldError message={form.formState.errors.password?.message} />

        <label htmlFor="timezone">Timezone</label>
        <input
          id="timezone"
          autoComplete="off"
          {...form.register('timezone')}
        />
        <FieldError message={form.formState.errors.timezone?.message} />

        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}

function FieldError({ message }: { message?: string }) {
  return message ? <p className="field-error">{message}</p> : null
}
