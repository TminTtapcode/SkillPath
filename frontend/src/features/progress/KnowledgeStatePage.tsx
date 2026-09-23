import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { getKnowledgeStates } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n } from '../../shared/i18n/I18n'

export function KnowledgeStatePage() {
  const { t } = useI18n()
  const query = useQuery({
    queryKey: ['knowledge-state'],
    queryFn: getKnowledgeStates,
  })
  if (query.isPending) return <Loading label={t('knowledge.loading')} />
  if (query.error) return <ErrorNotice error={query.error} />
  const items = query.data?.items ?? []
  return (
    <section className="panel knowledge-panel">
      <p className="eyebrow">{t('knowledge.eyebrow')}</p>
      <h1>{t('knowledge.title')}</h1>
      <p className="lede">{t('knowledge.lede')}</p>
      {!items.length ? (
        <div className="empty-state">
          <h2>{t('knowledge.empty')}</h2>
          <p>{t('knowledge.emptyDetail')}</p>
        </div>
      ) : (
        <ul className="knowledge-grid">
          {items.map((item) => (
            <li key={item.knowledgeNodeId}>
              <div className="knowledge-card-heading">
                <strong>{item.knowledgeNodeName}</strong>
                <span>{t(`knowledge.status.${item.status}`)}</span>
              </div>
              <div
                className="knowledge-meter"
                aria-label={t('knowledge.mastery')}
              >
                <span
                  style={{
                    width: `${Math.round(item.effectiveMastery * 100)}%`,
                  }}
                />
              </div>
              <div className="knowledge-numbers">
                <span>
                  {t('knowledge.mastery')}:{' '}
                  {Math.round(item.effectiveMastery * 100)}%
                </span>
                <span>
                  {t('knowledge.confidence')}:{' '}
                  {Math.round(item.confidence * 100)}%
                </span>
              </div>
              <small>
                {t('knowledge.evidence', { count: item.evidenceCount })}
              </small>
            </li>
          ))}
        </ul>
      )}
      <p className="diagnostic-boundary-note">{t('knowledge.boundary')}</p>
      <Link className="button-link button-secondary" to="/goal">
        {t('common.returnGoal')}
      </Link>
    </section>
  )
}
