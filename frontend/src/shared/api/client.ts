import type { components } from './schema'
import { getPreferredLocale } from '../config/localization'

export type Profile = components['schemas']['ProfileResponse']
export type GoalTemplate = components['schemas']['GoalTemplateResponse']
export type Goal = components['schemas']['GoalResponse']
export type ApiProblem = components['schemas']['ApiProblem']
export type RegisterInput = components['schemas']['RegisterRequest']
export type LoginInput = components['schemas']['LoginRequest']
export type CreateGoalInput = components['schemas']['CreateGoalRequest']
export type AssessmentSession =
  components['schemas']['AssessmentSessionResponse']
export type AssessmentQuestion =
  components['schemas']['AssessmentQuestionResponse']
export type SubmitAssessmentAttemptInput =
  components['schemas']['SubmitAssessmentAttemptRequest']
export type AssessmentAttempt =
  components['schemas']['AssessmentAttemptResponse']
export type AssessmentResult = components['schemas']['AssessmentResultResponse']
export interface KnowledgeState {
  knowledgeNodeId: string
  knowledgeNodeSlug: string
  knowledgeNodeName: string
  graphVersionId: string
  recognition: number
  understanding: number
  recall: number
  application: number
  storedMastery: number
  effectiveMastery: number
  confidence: number
  evidenceCount: number
  lastEvidenceAt?: string
  nextReviewAt?: string
  status: 'UNKNOWN' | 'LEARNING' | 'PROVISIONAL' | 'MASTERED' | 'REVIEW_DUE'
  policyVersion: string
}
export interface KnowledgeStatePage {
  items: KnowledgeState[]
  hasMore: boolean
  nextCursor?: string
}

type CsrfResponse = components['schemas']['CsrfResponse']

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ApiProblem

  constructor(status: number, problem?: ApiProblem) {
    super(problem?.detail ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

let csrf: CsrfResponse | undefined

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? 'GET'
  const stateChanging = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const headers = new Headers(init.headers)
  headers.set('Accept-Language', getPreferredLocale())

  if (stateChanging) {
    csrf ??= await getCsrf()
    headers.set(csrf.headerName, csrf.token)
  }
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'include',
  })
  if (!response.ok) {
    let problem: ApiProblem | undefined
    if (
      response.headers.get('content-type')?.includes('application/problem+json')
    ) {
      problem = (await response.json()) as ApiProblem
    }
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function getCsrf(): Promise<CsrfResponse> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'include',
    headers: { 'Accept-Language': getPreferredLocale() },
  })
  if (!response.ok) {
    throw new ApiError(response.status)
  }
  csrf = (await response.json()) as CsrfResponse
  return csrf
}

export async function register(input: RegisterInput): Promise<Profile> {
  const profile = await request<Profile>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  csrf = undefined
  return profile
}

export async function login(input: LoginInput): Promise<Profile> {
  const profile = await request<Profile>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  csrf = undefined
  return profile
}

export async function logout(): Promise<void> {
  await request<void>('/api/v1/auth/logout', { method: 'POST' })
  csrf = undefined
}

export const getMe = () => request<Profile>('/api/v1/me')

export const listGoalTemplates = () =>
  request<GoalTemplate[]>('/api/v1/goal-templates')

export const getActiveGoal = () => request<Goal>('/api/v1/goals/active')

export const createGoal = (input: CreateGoalInput, idempotencyKey: string) =>
  request<Goal>('/api/v1/goals', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(input),
  })

export const startDiagnostic = () =>
  request<AssessmentSession>('/api/v1/assessments/diagnostic', {
    method: 'POST',
  })

export const getNextDiagnosticQuestion = async (sessionId: string) =>
  (await request<AssessmentQuestion | undefined>(
    `/api/v1/assessments/${encodeURIComponent(sessionId)}/next-question`,
  )) ?? null

export const submitDiagnosticAttempt = (
  sessionId: string,
  input: SubmitAssessmentAttemptInput,
  idempotencyKey: string,
) =>
  request<AssessmentAttempt>(
    `/api/v1/assessments/${encodeURIComponent(sessionId)}/attempts`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(input),
    },
  )

export const getDiagnosticResult = (sessionId: string) =>
  request<AssessmentResult>(
    `/api/v1/assessments/${encodeURIComponent(sessionId)}/result`,
  )

export const getKnowledgeStates = () =>
  request<KnowledgeStatePage>('/api/v1/knowledge/me?limit=50')

export function resetApiStateForTests() {
  csrf = undefined
}
