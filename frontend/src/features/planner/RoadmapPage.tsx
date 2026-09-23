import { useInfiniteQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { getRoadmap, type Roadmap } from '../../shared/api/client'
import { ErrorNotice, Loading } from '../../shared/components/AsyncState'
import { useI18n, type TranslationKey } from '../../shared/i18n/I18n'

type Node = Roadmap['nodes'][number]
const knowledgeStatusKeys: Record<string, TranslationKey> = {
  UNKNOWN: 'roadmap.status.UNKNOWN',
  LEARNING: 'roadmap.status.LEARNING',
  PROVISIONAL: 'roadmap.status.PROVISIONAL',
  MASTERED: 'roadmap.status.MASTERED',
  REVIEW_DUE: 'roadmap.status.REVIEW_DUE',
}

function positions(map: Roadmap) {
  const level = new Map<string, number>()
  const incoming = new Map<string, string[]>()
  map.nodes.forEach((node) => incoming.set(node.id, []))
  map.edges
    .filter((edge) => edge.type === 'PREREQUISITE')
    .forEach((edge) => {
      incoming.get(edge.targetId)?.push(edge.sourceId)
    })
  const visit = (id: string, seen = new Set<string>()): number => {
    if (level.has(id)) return level.get(id) ?? 0
    if (seen.has(id)) return 0
    seen.add(id)
    const parents = incoming.get(id) ?? []
    const value = parents.length
      ? Math.max(...parents.map((parent) => visit(parent, seen) + 1))
      : 0
    level.set(id, value)
    seen.delete(id)
    return value
  }
  map.nodes.forEach((node) => visit(node.id))
  const groups = new Map<number, Node[]>()
  map.nodes.forEach((node) => {
    const column = level.get(node.id) ?? 0
    groups.set(column, [...(groups.get(column) ?? []), node])
  })
  const result = new Map<string, { x: number; y: number }>()
  groups.forEach((nodes, column) =>
    nodes
      .sort((a, b) => a.slug.localeCompare(b.slug))
      .forEach((node, row) =>
        result.set(node.id, { x: 18 + column * 202, y: 20 + row * 90 }),
      ),
  )
  return {
    result,
    width: 220 + Math.max(...level.values(), 0) * 202,
    height:
      112 +
      Math.max(...[...groups.values()].map((items) => items.length), 1) * 90,
  }
}

export function RoadmapPage() {
  const { locale, t } = useI18n()
  const query = useInfiniteQuery({
    queryKey: ['roadmap', locale],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => getRoadmap(pageParam ?? undefined),
    getNextPageParam: (last) =>
      last.hasMore ? (last.nextCursor ?? undefined) : undefined,
    retry: false,
  })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const pages = query.data?.pages
  const consistent =
    !pages ||
    pages.every(
      (page) =>
        page.graphVersionId === pages[0].graphVersionId &&
        page.knowledgeStatePolicyVersion ===
          pages[0].knowledgeStatePolicyVersion &&
        page.plannerPolicyVersion === pages[0].plannerPolicyVersion &&
        page.progressDigest === pages[0].progressDigest &&
        page.reviewDigest === pages[0].reviewDigest &&
        page.planId === pages[0].planId &&
        page.revision === pages[0].revision &&
        page.projectionAsOf === pages[0].projectionAsOf,
    )
  const map = useMemo(() => {
    if (!pages || !consistent) return null
    const first = pages[0]
    const edges = new Map<string, Roadmap['edges'][number]>()
    pages.forEach((page) =>
      page.edges.forEach((edge) =>
        edges.set(`${edge.sourceId}:${edge.targetId}:${edge.type}`, edge),
      ),
    )
    return {
      ...first,
      stale: pages.some((page) => page.stale),
      nodes: pages.flatMap((page) => page.nodes),
      edges: [...edges.values()],
    }
  }, [pages, consistent])
  const layout = useMemo(() => (map ? positions(map) : null), [map])

  if (query.isPending) return <Loading />
  if (query.error) return <ErrorNotice error={query.error} />
  if (!consistent) return <p role="alert">{t('roadmap.snapshotChanged')}</p>
  if (!map || !layout) return null
  const selected = map.nodes.find((node) => node.id === selectedId)
  const names = new Map(map.nodes.map((node) => [node.id, node.name]))

  return (
    <section className="panel roadmap-page">
      <p className="eyebrow">SkillPath · Roadmap</p>
      <h1>{t('roadmap.title')}</h1>
      <p className="lede">{t('roadmap.intro')}</p>
      {map.stale && (
        <p className="notice notice-error" role="status">
          {t('roadmap.stale')}
        </p>
      )}
      <div className="planner-actions">
        <span className="status-badge">Graph {map.graphVersionId}</span>
        <span className="status-badge">{map.knowledgeStatePolicyVersion}</span>
        <Link className="button-link button-secondary" to="/today">
          {t('planner.open')}
        </Link>
      </div>
      <div className="roadmap-legend-bar" aria-label="Roadmap Legend">
        <span className="legend-chip legend-current">
          <span className="legend-dot" /> {t('roadmap.current')}
        </span>
        <span className="legend-chip legend-ready">
          <span className="legend-dot" /> {t('roadmap.ready')}
        </span>
        <span className="legend-chip legend-blocked">
          <span className="legend-dot" /> {t('roadmap.blocked')}
        </span>
      </div>
      <div
        className="roadmap-viewport"
        role="region"
        aria-label={t('roadmap.diagram')}
        tabIndex={0}
      >
        <svg
          viewBox={`0 0 ${layout.width} ${layout.height}`}
          width={layout.width}
          height={layout.height}
          role="img"
          aria-label={t('roadmap.diagram')}
        >
          <defs>
            <marker
              id="roadmap-arrow"
              markerWidth="7"
              markerHeight="7"
              refX="6"
              refY="3.5"
              orient="auto"
            >
              <path d="M0 0 L7 3.5 L0 7" fill="none" stroke="currentColor" />
            </marker>
          </defs>
          {map.edges
            .filter((edge) => edge.type === 'PREREQUISITE')
            .map((edge) => {
              const from = layout.result.get(edge.sourceId)
              const to = layout.result.get(edge.targetId)
              if (!from || !to) return null
              return (
                <line
                  key={`${edge.sourceId}-${edge.targetId}`}
                  x1={from.x + 165}
                  y1={from.y + 28}
                  x2={to.x - 4}
                  y2={to.y + 28}
                  stroke="var(--sp-text-muted)"
                  strokeWidth="1.5"
                  markerEnd="url(#roadmap-arrow)"
                />
              )
            })}
          {map.nodes.map((node) => {
            const point = layout.result.get(node.id)
            if (!point) return null
            const label = node.current
              ? t('roadmap.current')
              : node.ready
                ? t('roadmap.ready')
                : t('roadmap.blocked')
            return (
              <g
                key={node.id}
                role="button"
                tabIndex={0}
                aria-label={`${node.name}: ${label}, ${knowledgeStatusKeys[node.knowledgeStatus] ? t(knowledgeStatusKeys[node.knowledgeStatus]) : node.knowledgeStatus}`}
                onClick={() => setSelectedId(node.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    setSelectedId(node.id)
                  }
                }}
              >
                <rect
                  x={point.x}
                  y={point.y}
                  width="165"
                  height="56"
                  rx="8"
                  className={`roadmap-node ${node.current ? 'current' : node.ready ? 'ready' : 'blocked'} ${node.id === selectedId ? 'selected' : ''}`}
                />
                <text x={point.x + 10} y={point.y + 24}>
                  {node.name.slice(0, 22)}
                </text>
                <text
                  className="roadmap-node-state"
                  x={point.x + 10}
                  y={point.y + 44}
                >
                  {label}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      <div className="roadmap-detail" aria-live="polite">
        {selected ? (
          <>
            <h2>{selected.name}</h2>
            <p>
              {t('roadmap.status')}:{' '}
              {knowledgeStatusKeys[selected.knowledgeStatus]
                ? t(knowledgeStatusKeys[selected.knowledgeStatus])
                : selected.knowledgeStatus}
            </p>
            <p>
              {selected.current
                ? t('roadmap.current')
                : selected.ready
                  ? t('roadmap.ready')
                  : t('roadmap.blocked')}
            </p>
            {selected.blockedBy.length > 0 && (
              <p>
                {t('roadmap.prerequisites')}:{' '}
                {selected.blockedBy.map((id) => names.get(id) ?? id).join(', ')}
              </p>
            )}
          </>
        ) : (
          <p>{t('roadmap.none')}</p>
        )}
      </div>
      <h2>{t('roadmap.list')}</h2>
      <ul className="roadmap-list">
        {map.nodes.map((node) => (
          <li key={node.id}>
            <button type="button" onClick={() => setSelectedId(node.id)}>
              {node.name} —{' '}
              {knowledgeStatusKeys[node.knowledgeStatus]
                ? t(knowledgeStatusKeys[node.knowledgeStatus])
                : node.knowledgeStatus}{' '}
              —{' '}
              {node.current
                ? t('roadmap.current')
                : node.ready
                  ? t('roadmap.ready')
                  : t('roadmap.blocked')}
            </button>
          </li>
        ))}
      </ul>
      {query.hasNextPage && (
        <button
          type="button"
          disabled={query.isFetchingNextPage}
          onClick={() => void query.fetchNextPage()}
        >
          {query.isFetchingNextPage
            ? t('roadmap.loadingMore')
            : t('roadmap.loadMore')}
        </button>
      )}
    </section>
  )
}
