import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  createGoal,
  resetApiStateForTests,
  submitDiagnosticAttempt,
} from './client'

describe('API client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    resetApiStateForTests()
    window.localStorage.clear()
  })

  it('loads CSRF and sends credentials and idempotency headers', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-value',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: '9',
            goalTemplateId: '1',
            targetDate: '2027-01-01',
            timezone: 'Asia/Bangkok',
            defaultDailyMinutes: 60,
            status: 'ACTIVE',
            version: 0,
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        ),
      )

    await createGoal(
      {
        goalTemplateId: '1',
        targetDate: '2027-01-01',
        timezone: 'Asia/Bangkok',
        defaultDailyMinutes: 60,
      },
      'request-1',
    )

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const options = fetchMock.mock.calls[1]?.[1]
    const headers = new Headers(options?.headers)
    expect(options?.credentials).toBe('include')
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-value')
    expect(headers.get('Idempotency-Key')).toBe('request-1')
    expect(headers.get('Accept-Language')).toBe('vi-VN')
  })

  it('maps problem details into ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://skillpath.app/problems/unauthenticated',
          title: 'Unauthorized',
          status: 401,
          code: 'UNAUTHENTICATED',
          detail: 'Authentication is required.',
          instance: '/api/v1/goals',
          correlationId: 'test',
          fieldErrors: [],
        }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/problem+json' },
        },
      ),
    )

    await expect(fetch('/unused')).resolves.toBeInstanceOf(Response)
    const error = new ApiError(401, {
      type: 'https://skillpath.app/problems/unauthenticated',
      title: 'Unauthorized',
      status: 401,
      code: 'UNAUTHENTICATED',
      detail: 'Authentication is required.',
      instance: '/api/v1/goals',
      correlationId: 'test',
      fieldErrors: [],
    })
    expect(error.message).toBe('Authentication is required.')
  })

  it('sends the caller-provided idempotency key for diagnostic retries', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-diagnostic',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            attemptId: '44',
            sessionStatus: 'IN_PROGRESS',
            replayed: false,
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        ),
      )

    await submitDiagnosticAttempt(
      '12',
      {
        sessionQuestionId: '90',
        selectedOptionIds: ['log'],
        selfConfidence: 0.6,
        timeSpentSeconds: 20,
      },
      'stable-attempt-key',
    )

    const options = fetchMock.mock.calls[1]?.[1]
    const headers = new Headers(options?.headers)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/assessments/12/attempts')
    expect(headers.get('Idempotency-Key')).toBe('stable-attempt-key')
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-diagnostic')
    expect(headers.get('Accept-Language')).toBe('vi-VN')
  })
})
