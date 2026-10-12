import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateSettlementRequest,
  Expense,
  ExpensePage,
  FriendBalances,
  Group,
  MemberBalance,
  Settlement,
  SettleUpResult,
  Transfer,
  UpdateExpenseRequest,
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

/** After any change of money: balances anywhere may have moved. */
function moneyChanged(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: keys.friends }),
    queryClient.invalidateQueries({ queryKey: keys.groups }),
  ])
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
    onSuccess: (group) => {
      queryClient.setQueryData(keys.group(group.id), group)
      return queryClient.invalidateQueries({ queryKey: keys.groups, exact: true })
    },
  })
}

export function useAddMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (email: string) =>
      api<Group>(`/groups/${groupId}/members`, { method: 'POST', body: { email } }),
    onSuccess: (group) => {
      queryClient.setQueryData(keys.group(groupId), group)
      return queryClient.invalidateQueries({ queryKey: keys.groups, exact: true })
    },
  })
}

export function useRemoveMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => api<Group>(`/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: (group) => {
      queryClient.setQueryData(keys.group(groupId), group)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.groups, exact: true }),
        queryClient.invalidateQueries({ queryKey: keys.friends }),
      ])
    },
  })
}
