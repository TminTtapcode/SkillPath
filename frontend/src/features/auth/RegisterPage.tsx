import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { register as registerAccount } from '../../shared/api/client'
import { ErrorNotice } from '../../shared/components/AsyncState'
import { DEFAULT_TIMEZONE } from '../../shared/config/localization'
import { useI18n } from '../../shared/i18n/I18n'

type FormValues = {
  email: string
  password: string
  displayName: string
  timezone: string
}

export function RegisterPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const schema = useMemo(
    () =>
      z.object({
        email: z.email(t('validation.email')),
        password: z.string().min(12, t('validation.passwordLength')).max(128),
        displayName: z.string().trim().min(1, t('validation.name')).max(100),
        timezone: z.string().min(1, t('validation.timezoneRequired')),
      }),
    [t],
  )
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      email: '',
      password: '',
      displayName: '',
      timezone: DEFAULT_TIMEZONE,
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
      <p className="eyebrow">{t('register.eyebrow')}</p>
      <h1>{t('register.title')}</h1>
      <p className="lede">{t('register.lede')}</p>
      {mutation.error && <ErrorNotice error={mutation.error} />}
      <form
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        noValidate
      >
        <div className="form-group">
          <label htmlFor="displayName">{t('register.name')}</label>
          <input
            id="displayName"
            autoComplete="name"
            aria-invalid={Boolean(form.formState.errors.displayName)}
            aria-describedby={
              form.formState.errors.displayName
                ? 'displayName-error'
                : undefined
            }
            {...form.register('displayName')}
          />
          <FieldError
            id="displayName-error"
            message={form.formState.errors.displayName?.message}
          />
        </div>

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
          <FieldError
            id="email-error"
            message={form.formState.errors.email?.message}
          />
        </div>

        <div className="form-group">
          <label htmlFor="password">{t('common.password')}</label>
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
          <label htmlFor="timezone">{t('common.timezone')}</label>
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
          {mutation.isPending ? t('register.pending') : t('register.submit')}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.875rem' }}>
        {t('register.existing')} <Link to="/login">{t('common.signIn')}</Link>
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
