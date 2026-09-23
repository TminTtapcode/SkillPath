import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  beforeEach(() => window.localStorage.clear())

  it('defaults to Vietnamese and persists an English selection', () => {
    window.history.pushState({}, '', '/login')
    render(<App />)
    expect(screen.getByRole('link', { name: 'SkillPath' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Đăng nhập' }),
    ).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Ngôn ngữ'), {
      target: { value: 'en' },
    })

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(window.localStorage.getItem('skillpath.locale')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('defaults new registrations to the Ho Chi Minh City timezone', () => {
    window.history.pushState({}, '', '/register')
    render(<App />)

    expect(screen.getByLabelText('Múi giờ')).toHaveValue('Asia/Ho_Chi_Minh')
  })
})
