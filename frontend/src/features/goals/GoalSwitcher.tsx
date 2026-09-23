import { useState } from 'react'
import { Link } from 'react-router'
import { useI18n } from '../../shared/i18n/I18n'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { switchGoal } from '../../shared/api/client'

type GoalTrack = {
  id: string
  title: string
  status: 'ACTIVE' | 'PAUSED'
}

export function GoalSwitcher({ currentGoalId, allGoals }: { currentGoalId: string, allGoals: GoalTrack[] }) {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [isOpen, setIsOpen] = useState(false)

  const switchMutation = useMutation({
    mutationFn: (goalId: string) => switchGoal(goalId),
    onSuccess: () => {
      setIsOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['active-goal'] })
      void queryClient.invalidateQueries({ queryKey: ['learning-session-active'] })
    }
  })

  return (
    <div className="goal-switcher" style={{ position: 'relative' }}>
      <button 
        className="button-secondary"
        onClick={() => setIsOpen(!isOpen)}
        aria-expanded={isOpen}
      >
        {t('goal.switchTrack', 'Switch Goal Track')}
      </button>

      {isOpen && (
        <div className="switcher-menu" style={{
          position: 'absolute', top: '100%', right: 0, 
          background: 'var(--panel-bg)', border: '1px solid var(--border-color)',
          padding: '1rem', borderRadius: '8px', zIndex: 100, minWidth: '250px'
        }}>
          <h4>{t('goal.yourTracks', 'Your Tracks')}</h4>
          <ul style={{ listStyle: 'none', padding: 0, margin: '1rem 0' }}>
            {allGoals.map(goal => (
              <li key={goal.id} style={{ marginBottom: '0.5rem' }}>
                <button 
                  disabled={goal.id === currentGoalId || switchMutation.isPending}
                  onClick={() => switchMutation.mutate(goal.id)}
                  style={{ width: '100%', textAlign: 'left', padding: '0.5rem' }}
                >
                  {goal.title} {goal.id === currentGoalId ? '(Active)' : '(Paused)'}
                </button>
              </li>
            ))}
          </ul>
          <Link to="/goals/new" className="button" style={{ display: 'block', textAlign: 'center' }}>
            {t('goal.startNew', 'Start New Track')}
          </Link>
        </div>
      )}
    </div>
  )
}
