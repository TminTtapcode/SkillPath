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
export type LearningSequence = components['schemas']['LearningSequenceResponse']
export type LearningSession = components['schemas']['LearningSessionResponse']
export type LearningTask = components['schemas']['LearningTaskResponse']
export type LearningStart = components['schemas']['LearningStartResponse']
export type LearningCommand = components['schemas']['LearningCommandResponse']
export type TodayPlan = components['schemas']['TodayPlanResponse']
export type Roadmap = components['schemas']['RoadmapResponse']
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

export const getTodayPlan = () => request<TodayPlan>('/api/v1/learning/today')

export const generateTodayPlan = (key: string) =>
  request<TodayPlan>('/api/v1/learning/today/generate', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
  })

export const reviseTodayPlan = (key: string) =>
  request<TodayPlan>('/api/v1/learning/today/revise', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
  })

export const getRoadmap = (cursor?: string) =>
  request<Roadmap>(
    cursor
      ? `/api/v1/roadmap?cursor=${encodeURIComponent(cursor)}`
      : '/api/v1/roadmap',
  )

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

export const listLearningSequences = () =>
  request<LearningSequence[]>('/api/v1/learning/sequences')

export const getActiveLearningSession = async () =>
  (await request<LearningSession | undefined>(
    '/api/v1/learning/sessions/active',
  )) ?? null

export const getLearningSession = (id: string) =>
  request<LearningSession>(
    `/api/v1/learning/sessions/${encodeURIComponent(id)}`,
  )

export const startLearningSession = (sequenceKey: string, key: string) =>
  request<LearningStart>(
    `/api/v1/learning/sequences/${encodeURIComponent(sequenceKey)}/sessions`,
    { method: 'POST', headers: { 'Idempotency-Key': key } },
  )

export type LearningOperation =
  'start' | 'complete' | 'skip' | 'blocked' | 'resume' | 'abandon'

export const commandLearningTask = (
  taskId: string,
  operation: LearningOperation,
  key: string,
  body?:
    | components['schemas']['LearningCompleteRequest']
    | components['schemas']['LearningReasonRequest'],
) =>
  request<LearningCommand>(
    `/api/v1/learning/tasks/${encodeURIComponent(taskId)}/${operation}`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      ...(body ? { body: JSON.stringify(body) } : {}),
    },
  )

export function resetApiStateForTests() {
  csrf = undefined
}
