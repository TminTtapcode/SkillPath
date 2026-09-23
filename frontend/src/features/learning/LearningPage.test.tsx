import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import { resetApiStateForTests } from '../../shared/api/client'

const sequence = {
  key: 'programming-flow-foundations',
  version: 1,
  graphVersionId: '1',
  title: 'Program flow: learn, practice, recall',
  description: 'A self-selected sequence, not mastery.',
  totalMinutes: 30,
  steps: [
    { position: 1, activityType: 'LEARN', minutes: 10, title: 'Learn flow' },
    {
      position: 2,
      activityType: 'PRACTICE',
      minutes: 15,
      title: 'Practice flow',
    },
    { position: 3, activityType: 'RECALL', minutes: 5, title: 'Recall flow' },
  ],
}

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('learner-selected learning', () => {
  beforeEach(() => window.localStorage.setItem('skillpath.locale', 'en'))
  afterEach(() => {
    vi.restoreAllMocks()
    resetApiStateForTests()
    window.localStorage.clear()
  })

  it('shows a curated sequence but never creates a session while rendering', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockImplementation(async (input) => {
        const url = String(input)
        if (url.endsWith('/learning/sequences')) return response([sequence])
        if (url.endsWith('/learning/sessions/active'))
          return new Response(null, { status: 204 })
        throw new Error(`Unexpected request: ${url}`)
      })
    window.history.pushState({}, '', '/learning')
    render(<App />)
    expect(
      await screen.findByRole('heading', { name: sequence.title }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/not a personalized Today plan/i),
    ).toBeInTheDocument()
    expect(
      fetch.mock.calls.every(([, init]) => !init || init.method !== 'POST'),
    ).toBe(true)
  })

  it('starts a session only after the learner clicks the sequence', async () => {
    const requests: Array<{ url: string; method: string; key: string | null }> =
      []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      requests.push({
        url,
        method: init?.method ?? 'GET',
        key: new Headers(init?.headers).get('Idempotency-Key'),
      })
      if (url.endsWith('/learning/sequences')) return response([sequence])
      if (url.endsWith('/learning/sessions/active'))
        return new Response(null, { status: 204 })
      if (url.endsWith('/auth/csrf'))
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'test',
        })
      if (url.endsWith(`/learning/sequences/${sequence.key}/sessions`))
        return response(
          { sessionId: 'phase5-started-session', replayed: false },
          201,
        )
      if (url.endsWith('/learning/sessions/phase5-started-session'))
        return response({
          id: 'phase5-started-session',
          sequenceKey: sequence.key,
          title: sequence.title,
          status: 'ACTIVE',
          graphVersionId: '1',
          assignmentSource: 'LEARNER_SELECTED',
          startedAt: '2026-09-23T00:00:00Z',
          completedAt: null,
          tasks: [],
        })
      throw new Error(`Unexpected request: ${url}`)
    })
    window.history.pushState({}, '', '/learning')
    render(<App />)
    expect(
      await screen.findByRole('heading', { name: sequence.title }),
    ).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'POST')).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: /Start this sequence/ }))
    await waitFor(() =>
      expect(window.location.pathname).toBe(
        '/learning/session/phase5-started-session',
      ),
    )
    const starts = requests.filter((request) =>
      request.url.endsWith(`/learning/sequences/${sequence.key}/sessions`),
    )
    expect(starts).toHaveLength(1)
    expect(starts[0].method).toBe('POST')
    expect(starts[0].key).toBeTruthy()
  })

  it('keeps one idempotency key for an ambiguous task completion retry', async () => {
    const task = {
      id: 'phase5-test-task',
      position: 1,
      status: 'IN_PROGRESS',
      activityType: 'LEARN',
      evaluationMode: 'SELF_REPORT',
      plannedMinutes: 10,
      actualMinutes: null,
      title: 'Learn flow',
      instructions: 'Read the short explanation.',
      resourceTitle: 'Trace a program',
      resourceBody: 'A variable holds a value.',
      checklist: [
        { id: 'read', label: 'I read it' },
        { id: 'trace', label: 'I traced it' },
      ],
    }
    let completeCalls = 0
    const keys: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf'))
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'test',
        })
      if (url.endsWith('/learning/sessions/phase5-test-session'))
        return response({
          id: 'phase5-test-session',
          sequenceKey: sequence.key,
          title: sequence.title,
          status: task.status === 'COMPLETED' ? 'COMPLETED' : 'ACTIVE',
          graphVersionId: '1',
          assignmentSource: 'LEARNER_SELECTED',
          startedAt: '2026-09-23T00:00:00Z',
          completedAt: null,
          tasks: [task],
        })
      if (url.endsWith('/learning/tasks/phase5-test-task/complete')) {
        completeCalls += 1
        keys.push(new Headers(init?.headers).get('Idempotency-Key') ?? '')
        if (completeCalls === 1)
          throw new TypeError('Network interrupted after submit')
        task.status = 'COMPLETED'
        return response({
          taskId: task.id,
          status: 'COMPLETED',
          replayed: true,
        })
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    window.history.pushState({}, '', '/learning/session/phase5-test-session')
    render(<App />)
    expect(
      await screen.findByRole('heading', { name: 'Learn flow' }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('I read it'))
    fireEvent.click(screen.getByLabelText('I traced it'))
    fireEvent.change(screen.getByLabelText('Minutes spent'), {
      target: { value: '10' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Mark step complete' }))
    expect(
      await screen.findByRole('button', { name: 'Retry the same command' }),
    ).toBeInTheDocument()
    fireEvent.click(
      screen.getByRole('button', { name: 'Retry the same command' }),
    )
    await waitFor(() =>
      expect(screen.getByText('Sequence completed')).toBeInTheDocument(),
    )
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[0]).toBe(keys[1])
    expect(
      screen.getByText(/does not grade your work, update mastery/i),
    ).toBeInTheDocument()
  })

  it('shows a recoverable missing-session state', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      response(
        { code: 'LEARNING_SESSION_NOT_FOUND', detail: 'Not found' },
        404,
      ),
    )
    window.history.pushState({}, '', '/learning/session/missing')
    render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This study session was not found.',
    )
  })

  it('renders a blocked bilingual snapshot without offering completion', async () => {
    window.localStorage.setItem('skillpath.locale', 'vi-VN')
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      response({
        id: 'phase5-blocked-session',
        sequenceKey: sequence.key,
        title: 'Luồng chương trình: học, thực hành, nhớ lại',
        status: 'ACTIVE',
        graphVersionId: '1',
        assignmentSource: 'LEARNER_SELECTED',
        startedAt: '2026-09-23T00:00:00Z',
        completedAt: null,
        tasks: [
          {
            id: 'phase5-blocked-task',
            position: 1,
            status: 'BLOCKED',
            activityType: 'LEARN',
            evaluationMode: 'SELF_REPORT',
            plannedMinutes: 10,
            actualMinutes: null,
            title: 'Học: lần theo luồng chương trình',
            instructions: 'Đọc phần giải thích ngắn.',
            resourceTitle: 'Lần theo chương trình',
            resourceBody: 'Biến lưu một giá trị.',
            checklist: [{ id: 'read', label: 'Tôi đã đọc' }],
          },
        ],
      }),
    )
    window.history.pushState({}, '', '/learning/session/phase5-blocked-session')
    render(<App />)
    expect(
      await screen.findByRole('heading', {
        name: 'Học: lần theo luồng chương trình',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Tiếp tục bước này' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Đánh dấu hoàn thành' }),
    ).not.toBeInTheDocument()
  })
})
