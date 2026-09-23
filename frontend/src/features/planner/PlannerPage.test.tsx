import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import { resetApiStateForTests } from '../../shared/api/client'

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const empty = {
  planId: null,
  outcome: 'NOT_GENERATED',
  revision: 0,
  budgetMinutes: 60,
  graphVersionId: null,
  projectionAsOf: null,
  sessionId: null,
  items: [],
  alternatives: [],
}

const planned = {
  ...empty,
  planId: '77',
  outcome: 'PLANNED',
  revision: 1,
  graphVersionId: '1',
  projectionAsOf: '2026-09-23T08:00:00Z',
  sessionId: '66',
  items: [
    {
      taskId: '55',
      nodeId: '1001',
      nodeName: 'Programming Fundamentals',
      title: 'Learn: trace program flow',
      status: 'ASSIGNED',
      minutes: 10,
      priorityScore: 60,
      reasons: ['GOAL_RELEVANT_GAP', 'TIME_FIT'],
    },
  ],
}

describe('Phase 6 Today and roadmap', () => {
  beforeEach(() => window.localStorage.setItem('skillpath.locale', 'en'))
  afterEach(() => {
    vi.restoreAllMocks()
    resetApiStateForTests()
    window.localStorage.clear()
  })

  it('reads Today without creating a plan, then sends one explicit idempotent command', async () => {
    const requests: Array<{ url: string; method: string; key: string | null }> =
      []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      requests.push({
        url,
        method: init?.method ?? 'GET',
        key: new Headers(init?.headers).get('Idempotency-Key'),
      })
      if (url.endsWith('/auth/csrf'))
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'test',
        })
      if (url.endsWith('/learning/today/generate'))
        return response(planned, 201)
      if (url.endsWith('/learning/today')) return response(empty)
      throw new Error(`Unexpected request: ${url}`)
    })
    window.history.pushState({}, '', '/today')
    render(<App />)
    expect(
      await screen.findByText('No plan has been generated for today.'),
    ).toBeInTheDocument()
    expect(requests.some((call) => call.method === 'POST')).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Generate Today plan' }))
    expect(
      await screen.findByText('Learn: trace program flow'),
    ).toBeInTheDocument()
    expect(
      requests.filter((call) => call.url.endsWith('/learning/today/generate')),
    ).toEqual([
      expect.objectContaining({ method: 'POST', key: expect.any(String) }),
    ])
    expect(
      screen.getByText(/records activity, not mastery/i),
    ).toBeInTheDocument()
  })

  it('shows a semantic concept list and prerequisite direction', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).endsWith('/roadmap'))
        return response({
          graphVersionId: '1',
          knowledgeStatePolicyVersion: 'knowledge-state-v1',
          progressDigest: 'a',
          reviewDigest: 'b',
          projectionAsOf: '2026-09-23T08:00:00Z',
          planId: '77',
          revision: 1,
          stale: false,
          nodes: [
            {
              id: '1004',
              slug: 'http',
              name: 'HTTP Fundamentals',
              knowledgeStatus: 'LEARNING',
              current: true,
              ready: true,
              blockedBy: [],
            },
            {
              id: '1011',
              slug: 'rest',
              name: 'REST API Design',
              knowledgeStatus: 'UNKNOWN',
              current: false,
              ready: false,
              blockedBy: ['1004'],
            },
          ],
          edges: [
            {
              sourceId: '1004',
              targetId: '1011',
              type: 'PREREQUISITE',
              strength: 1,
            },
          ],
        })
      throw new Error(`Unexpected request: ${String(input)}`)
    })
    window.history.pushState({}, '', '/roadmap')
    render(<App />)
    expect(
      await screen.findByRole('heading', { name: 'Goal roadmap' }),
    ).toBeInTheDocument()
    fireEvent.click(
      screen.getByRole('button', { name: /REST API Design — Not assessed/ }),
    )
    await waitFor(() =>
      expect(
        screen.getByText(/Blocked by prerequisites: HTTP Fundamentals/),
      ).toBeInTheDocument(),
    )
    expect(
      screen.getByRole('heading', { name: 'Accessible concept list' }),
    ).toBeInTheDocument()
  })

  it('keeps a learner-selected session separate from Today and does not generate on load', async () => {
    const requests: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      requests.push(String(input))
      if (String(input).endsWith('/learning/today'))
        return response({ ...empty, activeManualSessionId: '42' })
      throw new Error(`Unexpected request: ${String(input)}`)
    })
    window.history.pushState({}, '', '/today')
    render(<App />)
    expect(
      await screen.findByRole('link', { name: 'Open assigned session' }),
    ).toHaveAttribute('href', '/learning/session/42')
    expect(requests).toEqual([expect.stringMatching(/\/learning\/today$/)])
  })

  it('explains a no-content plan without inventing a task', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      response({
        ...empty,
        planId: '77',
        outcome: 'NO_SAFE_RECOMMENDATION',
        reasonCode: 'NO_CONTENT',
      }),
    )
    window.history.pushState({}, '', '/today')
    render(<App />)
    expect(
      await screen.findByText(
        'No curated task is available for the current learning frontier yet.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).toBeNull()
  })

  it('expands a 50-node roadmap only across matching snapshot pages', async () => {
    const stamp = {
      graphVersionId: '7',
      knowledgeStatePolicyVersion: 'knowledge-state-v1',
      plannerPolicyVersion: 'planner-v1',
      progressDigest: 'progress-7',
      reviewDigest: 'review-7',
      projectionAsOf: '2026-09-23T08:00:00Z',
      planId: '77',
      revision: 1,
      stale: false,
    }
    const nodes = Array.from({ length: 50 }, (_, index) => ({
      id: String(index + 1),
      slug: `concept-${index + 1}`,
      name: `Concept ${index + 1}`,
      knowledgeStatus: 'UNKNOWN',
      current: index === 0,
      ready: index === 0,
      blockedBy: index === 0 ? [] : [String(index)],
    }))
    const edges = Array.from({ length: 49 }, (_, index) => ({
      sourceId: String(index + 1),
      targetId: String(index + 2),
      type: 'PREREQUISITE',
      strength: 1,
    }))
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.endsWith('/roadmap'))
        return response({
          ...stamp,
          nodes: nodes.slice(0, 25),
          edges: edges.slice(0, 24),
          hasMore: true,
          nextCursor: 'page-2',
        })
      if (url.endsWith('/roadmap?cursor=page-2'))
        return response({
          ...stamp,
          nodes: nodes.slice(25),
          edges: edges.slice(24),
          hasMore: false,
          nextCursor: null,
        })
      throw new Error(`Unexpected request: ${url}`)
    })
    window.history.pushState({}, '', '/roadmap')
    render(<App />)
    fireEvent.click(
      await screen.findByRole('button', { name: 'Show more concepts' }),
    )
    await waitFor(() =>
      expect(
        screen.getAllByRole('button', { name: /Concept 50/ }),
      ).toHaveLength(2),
    )
    expect(screen.getAllByRole('button', { name: /Concept \d+/ })).toHaveLength(
      100,
    )
    expect(
      screen.queryByRole('button', { name: 'Show more concepts' }),
    ).toBeNull()
    fireEvent.keyDown(
      screen.getAllByRole('button', { name: /Concept 50/ })[0],
      {
        key: 'Enter',
      },
    )
    expect(
      screen.getByRole('heading', { name: 'Concept 50' }),
    ).toBeInTheDocument()
  })

  it('refuses to merge roadmap pages with different projection stamps', async () => {
    const first = {
      graphVersionId: '7',
      knowledgeStatePolicyVersion: 'knowledge-state-v1',
      plannerPolicyVersion: 'planner-v1',
      progressDigest: 'p1',
      reviewDigest: 'r1',
      projectionAsOf: '2026-09-23T08:00:00Z',
      planId: '77',
      revision: 1,
      stale: false,
      nodes: [
        {
          id: '1',
          slug: 'one',
          name: 'One',
          knowledgeStatus: 'UNKNOWN',
          current: true,
          ready: true,
          blockedBy: [],
        },
      ],
      edges: [],
      hasMore: true,
      nextCursor: 'next',
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      response(
        String(input).endsWith('?cursor=next')
          ? { ...first, progressDigest: 'p2', hasMore: false, nextCursor: null }
          : first,
      ),
    )
    window.history.pushState({}, '', '/roadmap')
    render(<App />)
    fireEvent.click(
      await screen.findByRole('button', { name: 'Show more concepts' }),
    )
    expect(await screen.findByText(/The roadmap changed/)).toHaveAttribute(
      'role',
      'alert',
    )
    expect(
      screen.queryByRole('heading', { name: 'Accessible concept list' }),
    ).toBeNull()
  })

  it('refetches localized roadmap names when the learner changes language', async () => {
    const languages: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      const headers = new Headers(init?.headers)
      const language = headers.get('Accept-Language') ?? 'en'
      languages.push(language)
      return response({
        graphVersionId: '7',
        knowledgeStatePolicyVersion: 'knowledge-state-v1',
        plannerPolicyVersion: 'planner-v1',
        progressDigest: 'p1',
        reviewDigest: 'r1',
        projectionAsOf: '2026-09-23T08:00:00Z',
        planId: null,
        revision: 0,
        stale: false,
        nodes: [
          {
            id: '1',
            slug: 'one',
            name:
              language === 'vi-VN'
                ? 'Lập trình căn bản'
                : 'Programming fundamentals',
            knowledgeStatus: 'UNKNOWN',
            current: false,
            ready: true,
            blockedBy: [],
          },
        ],
        edges: [],
        hasMore: false,
        nextCursor: null,
      })
    })
    window.history.pushState({}, '', '/roadmap')
    render(<App />)
    expect(
      await screen.findAllByRole('button', {
        name: /Programming fundamentals/,
      }),
    ).toHaveLength(2)
    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'vi-VN' },
    })
    await waitFor(() =>
      expect(
        screen.getAllByRole('button', { name: /Lập trình căn bản/ }),
      ).toHaveLength(2),
    )
    expect(languages).toContain('en')
    expect(languages).toContain('vi-VN')
  })
})
