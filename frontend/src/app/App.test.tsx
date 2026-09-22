import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('renders the product navigation', () => {
    window.history.pushState({}, '', '/login')
    render(<App />)
    expect(screen.getByRole('link', { name: 'SkillPath' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
