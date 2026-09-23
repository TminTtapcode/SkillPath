import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

describe('Goal daily budget v2', () => {
  beforeEach(() => window.localStorage.setItem('skillpath.locale', 'en'))
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('offers a 20-minute daily Goal choice', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            id: '1',
            key: 'JAVA_BACKEND_INTERN',
            displayName: 'Java Backend',
            description: 'Project-authored track',
          },
        ]),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    window.history.pushState({}, '', '/goals/new')
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: '20m' }))
    expect(screen.getByRole('button', { name: '20m' })).toHaveClass('active')
    expect(screen.getByLabelText('Daily study budget')).toHaveValue('20')
  })
})
