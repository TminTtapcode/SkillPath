import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ApiError, getActiveGoal, getMe, logout } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'

export function ActiveGoalPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['me'], queryFn: getMe, retry: false })
  const goal = useQuery({
    queryKey: ['active-goal'],
    queryFn: getActiveGoal,
    retry: false,
  })
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear()
      void navigate('/login')
    },
  })

  if (profile.error instanceof ApiError && profile.error.status === 401) {
    return <AuthRequired />
  }
  if (profile.isPending || goal.isPending)
    return <Loading label="Finding your route…" />
  if (profile.error) return <ErrorNotice error={profile.error} />
  if (goal.error instanceof ApiError && goal.error.status === 404) {
    return (
      <section className="panel">
        <h1>No active goal yet</h1>
        <p>Your route starts after you choose a destination.</p>
        <Link className="button-link" to="/goals/new">
          Create a goal
        </Link>
      </section>
    )
  }
  if (goal.error) return <ErrorNotice error={goal.error} />

  return (
    <section className="panel goal-summary">
      <div className="summary-heading">
        <div>
          <p className="eyebrow">Active route</p>
          <h1>{profile.data?.displayName}, your goal is set.</h1>
        </div>
        <button
          className="button-secondary"
          onClick={() => logoutMutation.mutate()}
        >
          Sign out
        </button>
      </div>
      <dl>
        <div>
          <dt>Target date</dt>
          <dd>{goal.data?.targetDate}</dd>
        </div>
        <div>
          <dt>Daily budget</dt>
          <dd>{goal.data?.defaultDailyMinutes} minutes</dd>
        </div>
        <div>
          <dt>Timezone</dt>
          <dd>{goal.data?.timezone}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{goal.data?.status}</dd>
        </div>
      </dl>
      <p className="next-step">
        Assessment and Today planning arrive in later phases.
      </p>
    </section>
  )
}

function AuthRequired() {
  return (
    <section className="panel">
      <h1>Your session has ended</h1>
      <p>Sign in again to continue with your learning route.</p>
      <Link className="button-link" to="/login">
        Sign in
      </Link>
    </section>
  )
}
