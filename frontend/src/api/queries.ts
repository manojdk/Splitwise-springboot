import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { CreateGroupRequest, FriendBalances, Group, SettleUpResult } from './types'

/** One place for cache keys, so every change invalidates exactly what it affects. */
export const keys = {
  friends: ['friends'] as const,
  groups: ['groups'] as const,
  group: (id: number) => ['groups', id] as const,
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
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.friends }),
        queryClient.invalidateQueries({ queryKey: keys.groups }),
      ]),
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
