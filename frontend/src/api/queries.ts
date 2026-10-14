import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import { api } from './client'
import type {
  Activity,
  ActivityPage,
  CreateExpenseRequest,
  CreateRecurringExpenseRequest,
  GroupReport,
  RecurringExpense,
  CreateGroupRequest,
  CreateSettlementRequest,
  Expense,
  ExpensePage,
  FriendBalance,
  FriendBalances,
  Group,
  MemberBalance,
  Settlement,
  SettlementPage,
  SettleUpResult,
  Transfer,
  UpdateExpenseRequest,
  User,
} from './types'

/**
 * One place for cache keys. Everything about a group lives under ['groups', id], so a change of money in a
 * group refreshes its expenses, balances and suggested payments with one invalidation.
 */
export const keys = {
  friends: ['friends'] as const,
  groups: ['groups'] as const,
  group: (id: number) => ['groups', id] as const,
  groupExpenses: (id: number, filters: ExpenseFilters) => ['groups', id, 'expenses', filters] as const,
  groupBalances: (id: number) => ['groups', id, 'balances'] as const,
  groupDebts: (id: number) => ['groups', id, 'debts'] as const,
  groupActivity: (id: number) => ['groups', id, 'activity'] as const,
  groupReport: (id: number, from?: string, to?: string) => ['groups', id, 'report', { from, to }] as const,
  groupSettlements: (id: number) => ['groups', id, 'settlements'] as const,
  expense: (id: number) => ['expenses', id] as const,
  myExpenses: (filters: MyExpenseFilters) => ['expenses', 'mine', filters] as const,
  friend: (id: number) => ['friends', id] as const,
  contacts: ['contacts'] as const,
  expenseHistory: (id: number) => ['expenses', id, 'history'] as const,
  myActivity: ['activity'] as const,
  recurring: ['recurring'] as const,
}

export type ExpenseFilters = {
  page: number
  q?: string
  category?: string
  paidBy?: number
  from?: string
  to?: string
}

export const PAGE_SIZE = 20

/** The user's expenses across groups; `with`: only those shared with that person (a friend's page). */
export type MyExpenseFilters = {
  page: number
  q?: string
  category?: string
  from?: string
  to?: string
  with?: number
}

/** After any change of money: balances, reports, activity and open expenses anywhere may have moved. */
function moneyChanged(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: keys.friends }),
    queryClient.invalidateQueries({ queryKey: keys.groups }),
    queryClient.invalidateQueries({ queryKey: keys.myActivity }),
    queryClient.invalidateQueries({ queryKey: ['expenses'] }),
  ])
}

/** After a change to a group's members or name: the group, the lists that show it, and the feeds. */
function groupChanged(queryClient: QueryClient, group: Group) {
  queryClient.setQueryData(keys.group(group.id), group)
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: keys.contacts }),
    queryClient.invalidateQueries({ queryKey: keys.groups, exact: true }),
    queryClient.invalidateQueries({ queryKey: keys.groupActivity(group.id) }),
    queryClient.invalidateQueries({ queryKey: keys.myActivity }),
  ])
}

const ACTIVITY_PAGE_SIZE = 20

/** An activity feed read page by page ("Show more"): the user's own, or one group's. */
export function useActivity(groupId?: number) {
  const base = groupId === undefined ? '/users/me/activity' : `/groups/${groupId}/activity`
  return useInfiniteQuery({
    queryKey: groupId === undefined ? keys.myActivity : keys.groupActivity(groupId),
    queryFn: ({ pageParam, signal }) => api<ActivityPage>(`${base}?page=${pageParam}&size=${ACTIVITY_PAGE_SIZE}`, { signal }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
  })
}

export function useRecurringExpenses() {
  return useQuery({
    queryKey: keys.recurring,
    queryFn: ({ signal }) => api<RecurringExpense[]>('/recurring-expenses', { signal }),
  })
}

export function useCreateRecurring() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateRecurringExpenseRequest) =>
      api<RecurringExpense>('/recurring-expenses', { method: 'POST', body }),
    // Starting today creates the first expense straight away
    onSuccess: () => Promise.all([queryClient.invalidateQueries({ queryKey: keys.recurring }), moneyChanged(queryClient)]),
  })
}

export function useStopRecurring() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/recurring-expenses/${id}`, { method: 'DELETE' }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.recurring }),
        queryClient.invalidateQueries({ queryKey: keys.myActivity }),
      ]),
  })
}

export function useGroupReport(groupId: number, from?: string, to?: string) {
  return useQuery({
    queryKey: keys.groupReport(groupId, from, to),
    queryFn: ({ signal }) => {
      const params = new URLSearchParams()
      if (from) params.set('from', from)
      if (to) params.set('to', to)
      const query = params.size > 0 ? `?${params}` : ''
      return api<GroupReport>(`/groups/${groupId}/report${query}`, { signal })
    },
    placeholderData: keepPreviousData,
  })
}

export function useGroupExpenses(groupId: number, filters: ExpenseFilters) {
  return useQuery({
    queryKey: keys.groupExpenses(groupId, filters),
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) })
      if (filters.q) params.set('q', filters.q)
      if (filters.category) params.set('category', filters.category)
      if (filters.paidBy) params.set('paidBy', String(filters.paidBy))
      if (filters.from) params.set('from', filters.from)
      if (filters.to) params.set('to', filters.to)
      return api<ExpensePage>(`/groups/${groupId}/expenses?${params}`, { signal })
    },
    // Keep the current page on screen while the next one loads, instead of flashing a spinner
    placeholderData: keepPreviousData,
  })
}

export function useGroupBalances(groupId: number) {
  return useQuery({
    queryKey: keys.groupBalances(groupId),
    queryFn: ({ signal }) => api<MemberBalance[]>(`/groups/${groupId}/balances`, { signal }),
  })
}

export function useSimplifiedDebts(groupId: number) {
  return useQuery({
    queryKey: keys.groupDebts(groupId),
    queryFn: ({ signal }) => api<Transfer[]>(`/groups/${groupId}/debts/simplified`, { signal }),
  })
}

/** Who the user can split with outside groups (shared groups, or earlier non-group expenses and payments). */
export function useContacts() {
  return useQuery({
    queryKey: keys.contacts,
    queryFn: ({ signal }) => api<User[]>('/users/me/contacts', { signal }),
  })
}

/** One friend's balances with the user, per group and outside groups (empty when settled up). */
export function useFriend(id: number) {
  return useQuery({
    queryKey: keys.friend(id),
    queryFn: ({ signal }) => api<FriendBalance>(`/users/me/friends/${id}`, { signal }),
  })
}

export function useMyExpenses(filters: MyExpenseFilters) {
  return useQuery({
    queryKey: keys.myExpenses(filters),
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) })
      if (filters.q) params.set('q', filters.q)
      if (filters.category) params.set('category', filters.category)
      if (filters.from) params.set('from', filters.from)
      if (filters.to) params.set('to', filters.to)
      if (filters.with) params.set('with', String(filters.with))
      return api<ExpensePage>(`/users/me/expenses?${params}`, { signal })
    },
    placeholderData: keepPreviousData,
  })
}

/** One expense by id, deleted ones included (flagged), starting from what a list already showed. */
export function useExpense(id: number, initial?: Expense) {
  return useQuery({
    queryKey: keys.expense(id),
    queryFn: ({ signal }) => api<Expense>(`/expenses/${id}`, { signal }),
    initialData: initial,
    // A list's copy is a good first paint, but check for edits by others straight away
    initialDataUpdatedAt: 0,
  })
}

/** What happened to one expense, newest first: added, each edit with its changes, deleted, restored. */
export function useExpenseHistory(id: number) {
  return useQuery({
    queryKey: keys.expenseHistory(id),
    queryFn: ({ signal }) => api<Activity[]>(`/expenses/${id}/activity`, { signal }),
  })
}

/** A group's recorded payments, newest first, page by page. */
export function useGroupSettlements(groupId: number) {
  return useInfiniteQuery({
    queryKey: keys.groupSettlements(groupId),
    queryFn: ({ pageParam, signal }) =>
      api<SettlementPage>(`/groups/${groupId}/settlements?page=${pageParam}&size=${PAGE_SIZE}`, { signal }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
  })
}

export function useDeleteSettlement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/settlements/${id}`, { method: 'DELETE' }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useRestoreSettlement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<Settlement>(`/settlements/${id}/restore`, { method: 'POST' }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useRenameGroup(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => api<Group>(`/groups/${groupId}`, { method: 'PUT', body: { name } }),
    onSuccess: (group) => groupChanged(queryClient, group),
  })
}

/** Only the creator, and only a group with no expenses or payments (deleted ones count). */
export function useDeleteGroup(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/groups/${groupId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: keys.group(groupId) })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.groups, exact: true }),
        queryClient.invalidateQueries({ queryKey: keys.myActivity }),
      ])
    },
  })
}

export function useSaveExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (save: { id?: number; body: CreateExpenseRequest | UpdateExpenseRequest }) =>
      save.id === undefined
        ? api<Expense>('/expenses', { method: 'POST', body: save.body })
        : api<Expense>(`/expenses/${save.id}`, { method: 'PUT', body: save.body }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useDeleteExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/expenses/${id}`, { method: 'DELETE' }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useRestoreExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<Expense>(`/expenses/${id}/restore`, { method: 'POST' }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useRecordPayment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateSettlementRequest) => api<Settlement>('/settlements', { method: 'POST', body }),
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useFriendBalances() {
  return useQuery({
    queryKey: keys.friends,
    queryFn: ({ signal }) => api<FriendBalances>('/users/me/friends', { signal }),
  })
}

export function useGroups() {
  return useQuery({
    queryKey: keys.groups,
    queryFn: ({ signal }) => api<Group[]>('/groups', { signal }),
  })
}

export function useGroup(id: number) {
  return useQuery({
    queryKey: keys.group(id),
    queryFn: ({ signal }) => api<Group>(`/groups/${id}`, { signal }),
  })
}

/** Records one payment per open balance with the friend (per group, plus one per currency outside groups). */
export function useSettleUp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (friendId: number) =>
      api<SettleUpResult>(`/users/me/friends/${friendId}/settle-up`, { method: 'POST' }),
    // Group balances change too, so refresh everything under "groups" as well
    onSuccess: () => moneyChanged(queryClient),
  })
}

export function useCreateGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateGroupRequest) => api<Group>('/groups', { method: 'POST', body: request }),
    onSuccess: (group) => groupChanged(queryClient, group),
  })
}

export function useAddMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (email: string) =>
      api<Group>(`/groups/${groupId}/members`, { method: 'POST', body: { email } }),
    onSuccess: (group) => groupChanged(queryClient, group),
  })
}

export function useRemoveMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => api<Group>(`/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: (group) =>
      Promise.all([groupChanged(queryClient, group), queryClient.invalidateQueries({ queryKey: keys.friends })]),
  })
}
