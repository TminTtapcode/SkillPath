import { useQuery } from '@tanstack/react-query'
import { getTaskPractices } from '../../shared/api/client'
import { Loading, ErrorNotice } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

export function PracticePanel({ taskTemplateVersionId }: { taskTemplateVersionId: string }) {
  const { t } = useI18n()
  const practicesQuery = useQuery({
    queryKey: ['task-practices', taskTemplateVersionId],
    queryFn: () => getTaskPractices(taskTemplateVersionId),
  })

  if (practicesQuery.isPending) return <Loading label={t('common.loading', 'Loading practices...')} />
  if (practicesQuery.error) return <ErrorNotice error={practicesQuery.error} />

  const practices = practicesQuery.data ?? []

  if (practices.length === 0) {
    return <p>{t('learning.noPractices', 'No practice exercises for this task.')}</p>
  }

  return (
    <div className="practice-panel">
      {practices.map((practice, index) => (
        <div key={practice.id} className="practice-exercise" style={{ marginBottom: '2rem', padding: '1rem', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
          <h4>{t('learning.practiceExercise', 'Practice')} {index + 1}</h4>
          <p>{practice.prompt}</p>
          {practice.starterCode && (
            <div className="starter-code" style={{ marginTop: '1rem' }}>
              <strong>{t('learning.starterCode', 'Starter Code:')}</strong>
              <pre style={{ background: 'var(--panel-bg)', padding: '1rem', borderRadius: '4px' }}>
                <code>{practice.starterCode}</code>
              </pre>
            </div>
          )}
          {practice.expectedOutput && (
            <div className="expected-output" style={{ marginTop: '1rem' }}>
              <strong>{t('learning.expectedOutput', 'Expected Output:')}</strong>
              <pre style={{ background: 'var(--panel-bg)', padding: '1rem', borderRadius: '4px' }}>
                <code>{practice.expectedOutput}</code>
              </pre>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
