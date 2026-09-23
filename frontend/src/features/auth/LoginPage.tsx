import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { login } from '../../shared/api/client'
import { ErrorNotice } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

type FormValues = { email: string; password: string }

export function LoginPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const schema = useMemo(
    () =>
      z.object({
        email: z.email(t('validation.email')),
        password: z.string().min(1, t('validation.passwordRequired')).max(128),
      }),
    [t],
  )
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
      <p className="eyebrow">{t('login.eyebrow')}</p>
      <h1>{t('login.title')}</h1>
      <p className="lede">{t('login.lede')}</p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <div className="form-group">
          <label htmlFor="email">{t('common.email')}</label>
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
          <label htmlFor="password">{t('common.password')}</label>
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
          {mutation.isPending ? t('login.pending') : t('login.title')}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.875rem' }}>
        {t('login.new')} <Link to="/register">{t('login.create')}</Link>
      </p>
    </section>
  )
}
