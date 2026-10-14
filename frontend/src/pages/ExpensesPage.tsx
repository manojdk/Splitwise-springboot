import { AddExpenseButton } from '../features/expenses/AddExpenseButton'
import { MyExpenseList } from '../features/expenses/MyExpenseList'

/** Every expense the user is in, across groups and outside them, with search; and adding one anywhere. */
export function ExpensesPage() {
  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="m-0 text-2xl">All expenses</h1>
        <span className="ml-auto">
          <AddExpenseButton />
        </span>
      </div>
      <section className="card p-5" aria-label="Expenses">
        <MyExpenseList filters />
      </section>
    </div>
  )
}
