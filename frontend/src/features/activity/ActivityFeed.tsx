import { useActivity, useGroups } from '../../api/queries'
import type { Activity } from '../../api/types'
import { ErrorState, Loading } from '../../components/States'
import { relativeTime } from '../../lib/dates'

/** Colour of the dot: added, removed, changed, or anything else. */
function tone(type: Activity['type']): string {
  if (/_(ADDED|CREATED|RESTORED)$/.test(type)) return 'bg-positive'
  if (/_(DELETED|REMOVED|LEFT|STOPPED)$/.test(type)) return 'bg-negative'
  if (/_(UPDATED|RENAMED|PAUSED)$/.test(type)) return 'bg-accent'
  return 'bg-primary'
}

/** Everything that happened, newest first, with exactly what changed in each edit. */
export function ActivityFeed({ groupId }: { groupId?: number }) {
  const feed = useActivity(groupId)
  const groups = useGroups()
  const groupName = (id: number | null) => (id === null ? null : groups.data?.find((g) => g.id === id)?.name)

  if (feed.isPending) return <Loading label="Loading activity" />
  if (feed.isError) return <ErrorState error={feed.error} onRetry={() => void feed.refetch()} />

  const items = feed.data.pages.flatMap((page) => page.content)
  if (items.length === 0) return <p className="m-0 text-muted">Nothing has happened yet.</p>

  return (
    <div className="grid gap-4">
      <ol className="relative m-0 list-none border-l-2 border-line py-1 pl-6">
        {items.map((item) => (
          <li key={item.id} className="relative pb-5 last:pb-0">
            <span className={`absolute top-1.5 -left-[31px] h-3 w-3 rounded-full ${tone(item.type)}`} aria-hidden="true" />
            <p className="m-0 font-semibold">{item.summary}</p>
            <p className="m-0 mt-0.5 text-xs text-muted">
              <time dateTime={item.createdAt} title={new Date(item.createdAt).toLocaleString()}>
                {relativeTime(item.createdAt)}
              </time>
              {groupId === undefined && groupName(item.groupId) && <> {'·'} {groupName(item.groupId)}</>}
            </p>
            {item.details.length > 0 && (
              <ul className="mt-2 mb-0 list-none rounded-xl border-l-[3px] border-accent bg-surface-2 py-2 pr-3 pl-3 text-sm">
                {item.details.map((line, i) => (
                  <li key={i} className="py-0.5">
                    {line}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ol>
      {feed.hasNextPage && (
        <button
          type="button"
          className="btn btn-ghost justify-self-start"
          onClick={() => void feed.fetchNextPage()}
          disabled={feed.isFetchingNextPage}
        >
          {feed.isFetchingNextPage ? 'Loading...' : 'Show more'}
        </button>
      )}
    </div>
  )
}
