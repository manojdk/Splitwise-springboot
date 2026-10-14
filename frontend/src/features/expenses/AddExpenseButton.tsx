import { useState } from 'react'
import { Link } from 'react-router'
import { useContacts, useGroups } from '../../api/queries'
import type { Group } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Loading } from '../../components/States'
import { ExpenseForm } from './ExpenseForm'

/**
 * "+ Add expense" from anywhere: in one of the user's groups, or outside groups with their contacts. On a friend's
 * page (`withId`) it goes straight to a non-group expense with that friend.
 */
export function AddExpenseButton({ withId }: { withId?: number }) {
  const groups = useGroups()
  const contacts = useContacts()
  const [choosing, setChoosing] = useState(false)
  const [target, setTarget] = useState<{ group: Group | null } | null>(null)

  function start() {
    if (withId !== undefined) setTarget({ group: null })
    else setChoosing(true)
  }

  return (
    <>
      <button type="button" className="btn btn-accent" onClick={start} disabled={withId !== undefined && contacts.isPending}>
        + Add expense
      </button>

      {choosing && (
        <Modal title="Where does it go?" onClose={() => setChoosing(false)}>
          {groups.isPending ? (
            <Loading label="Loading groups" rows={2} />
          ) : (
            <ul className="m-0 grid list-none gap-2 p-0">
              {(groups.data ?? []).map((group) => (
                <li key={group.id}>
                  <button
                    type="button"
                    className="btn btn-ghost w-full justify-between"
                    onClick={() => {
                      setChoosing(false)
                      setTarget({ group })
                    }}
                  >
                    {group.name}
                    <span className="text-xs text-muted">{group.currency}</span>
                  </button>
                </li>
              ))}
              <li>
                <button
                  type="button"
                  className="btn btn-ghost w-full justify-between"
                  disabled={contacts.isPending || (contacts.data ?? []).length === 0}
                  onClick={() => {
                    setChoosing(false)
                    setTarget({ group: null })
                  }}
                >
                  No group: split with friends
                  <span className="text-xs text-muted">any currency</span>
                </button>
              </li>
            </ul>
          )}
          {contacts.data?.length === 0 && (
            <p className="mb-0 text-sm text-muted">
              To split outside groups, first share a group with someone. <Link to="/groups">Your groups</Link>
            </p>
          )}
        </Modal>
      )}

      {target && (
        <ExpenseForm
          group={target.group}
          contacts={contacts.data ?? []}
          withIds={withId !== undefined ? [withId] : []}
          onClose={() => setTarget(null)}
        />
      )}
    </>
  )
}
