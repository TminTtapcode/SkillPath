import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { resetApiStateForTests } from '../../shared/api/client'
import { I18nProvider } from '../../shared/i18n/I18n'
import { TaskCheckPanel } from './TaskCheckPanel'

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => window.localStorage.setItem('skillpath.locale', 'en'))
afterEach(() => {
  vi.restoreAllMocks()
  resetApiStateForTests()
  window.localStorage.clear()
})

it('offers bilingual-safe option IDs and retries an ambiguous submit with the same key', async () => {
  const keys: string[] = []
  let submissions = 0
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.endsWith('/learning/tasks/42/check'))
      return response({
        taskId: '42',
        graphVersionId: '1',
        questionVersionId: '3109',
        type: 'MULTIPLE_CHOICE',
        taskStatus: 'IN_PROGRESS',
        prompt: 'Trace the counter',
        options: [
          { id: 'count-six', label: 'count is 6' },
          { id: 'iterations-three', label: 'three iterations' },
        ],
      })
    if (url.endsWith('/auth/csrf'))
      return response({
        headerName: 'X-XSRF-TOKEN',
        parameterName: '_csrf',
        token: 'test',
      })
    if (url.endsWith('/learning/tasks/42/check/attempts')) {
      submissions += 1
      keys.push(new Headers(init?.headers).get('Idempotency-Key')!)
      expect(JSON.parse(String(init?.body)).selectedOptionIds).toEqual([
        'count-six',
      ])
      if (submissions === 1) throw new Error('Connection lost after submit')
      return response({ attemptId: '7', score: 1, replayed: true })
    }
    throw new Error(`Unexpected request ${url}`)
  })
  const completed = vi.fn()
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <I18nProvider>
        <TaskCheckPanel taskId="42" onComplete={completed} />
      </I18nProvider>
    </QueryClientProvider>,
  )
  expect(await screen.findByText('Trace the counter')).toBeInTheDocument()
  fireEvent.click(screen.getByLabelText('count is 6'))
  fireEvent.change(screen.getByLabelText('Minutes spent'), {
    target: { value: '12' },
  })
  fireEvent.click(
    screen.getByRole('button', { name: 'Submit check and finish task' }),
  )
  expect(
    await screen.findByRole('button', { name: 'Retry the same command' }),
  ).toBeInTheDocument()
  fireEvent.click(
    screen.getByRole('button', { name: 'Retry the same command' }),
  )
  expect(await screen.findByRole('status')).toHaveTextContent('100%')
  expect(completed).not.toHaveBeenCalled()
  fireEvent.click(
    screen.getByRole('button', { name: 'Continue to your session' }),
  )
  await waitFor(() => expect(completed).toHaveBeenCalledOnce())
  expect(keys).toHaveLength(2)
  expect(keys[0]).toBe(keys[1])
})
