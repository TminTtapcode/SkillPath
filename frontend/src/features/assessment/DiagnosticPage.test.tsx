import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import { resetApiStateForTests } from '../../shared/api/client'

describe('DiagnosticPage', () => {
  beforeEach(() => window.localStorage.setItem('skillpath.locale', 'en'))

  afterEach(() => {
    vi.restoreAllMocks()
    resetApiStateForTests()
    window.localStorage.clear()
  })

  it('renders a pinned objective question without answer-key data', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          sessionId: '12',
          sessionQuestionId: '90',
          questionVersionId: '3102',
          type: 'MULTIPLE_CHOICE',
          prompt: 'Which actions inspect existing history?',
          difficulty: 1,
          estimatedSeconds: 60,
          position: 1,
          totalQuestions: 8,
          expiresAt: '2026-10-01T00:00:00Z',
          options: [
            { id: 'log', label: 'git log' },
            { id: 'show', label: 'git show' },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    window.history.pushState({}, '', '/assessment/diagnostic?session=12')

    render(<App />)

    expect(
      await screen.findByRole('heading', { name: 'Question 1 of 8' }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('checkbox')).toHaveLength(2)
    expect(screen.getByText('git log')).toBeInTheDocument()
    expect(screen.queryByText(/correct answer/i)).not.toBeInTheDocument()
    expect(
      screen.getByText(
        /does not declare mastery or choose your next learning task/i,
      ),
    ).toBeInTheDocument()
  })

  it('labels completed output as evidence rather than mastery', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            sessionId: '13',
            graphVersionId: '1',
            assessmentPolicyVersion: 'assessment-objective-v1',
            startedAt: '2026-09-23T00:00:00Z',
            completedAt: '2026-09-23T00:06:00Z',
            answeredQuestions: 8,
            totalQuestions: 8,
            overallObjectiveScore: 0.75,
            evidence: [
              {
                evidenceId: '1',
                attemptId: '1',
                knowledgeNodeId: '1004',
                knowledgeNodeSlug: 'http',
                knowledgeNodeName: 'HTTP Fundamentals',
                dimension: 'RECOGNITION',
                score: 1,
                reliability: 0.45,
                evaluatorVersion: 'assessment-objective-v1',
                observedAt: '2026-09-23T00:01:00Z',
              },
            ],
            interpretation:
              'Diagnostic evidence describes what these attempts demonstrated; it is not authoritative mastery.',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    window.history.pushState({}, '', '/assessment/diagnostic?session=13')

    render(<App />)

    expect(
      await screen.findByRole('heading', {
        name: 'Evidence baseline recorded',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('HTTP Fundamentals')).toBeInTheDocument()
    expect(
      screen.getByText('Not a mastery or readiness score'),
    ).toBeInTheDocument()
  })

  it('switches server content language without losing the current selection', async () => {
    const question = {
      sessionId: '14',
      sessionQuestionId: '91',
      questionVersionId: '3103',
      type: 'SINGLE_CHOICE',
      difficulty: 1,
      estimatedSeconds: 60,
      position: 1,
      totalQuestions: 8,
      expiresAt: '2026-10-01T00:00:00Z',
    }
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            ...question,
            prompt: 'What does HTTP status 404 mean?',
            options: [
              { id: 'not-found', label: 'Resource not found' },
              { id: 'success', label: 'Success' },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            ...question,
            prompt: 'Mã trạng thái HTTP 404 có nghĩa là gì?',
            options: [
              { id: 'not-found', label: 'Không tìm thấy tài nguyên' },
              { id: 'success', label: 'Thành công' },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    window.history.pushState({}, '', '/assessment/diagnostic?session=14')
    render(<App />)

    const answer = await screen.findByLabelText('Resource not found')
    fireEvent.click(answer)
    expect(answer).toBeChecked()

    fireEvent.change(screen.getByLabelText('Language'), {
      target: { value: 'vi-VN' },
    })

    expect(
      await screen.findByText('Mã trạng thái HTTP 404 có nghĩa là gì?'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Không tìm thấy tài nguyên')).toBeChecked()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers)
    expect(headers.get('Accept-Language')).toBe('vi-VN')
  })
})
