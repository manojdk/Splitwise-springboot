import type { components } from './schema'

/** Generated from frontend/openapi.json (npm run api:types); never edit schema.d.ts by hand. */
type Schemas = components['schemas']

export type User = Schemas['UserResponse']
export type TokenResponse = Schemas['TokenResponse']
export type LoginRequest = Schemas['LoginRequest']
export type RegisterRequest = Schemas['RegisterRequest']
export type UpdateProfileRequest = Schemas['UpdateProfileRequest']
export type ChangePasswordRequest = Schemas['ChangePasswordRequest']

export type CurrencyAmount = Schemas['CurrencyAmount']
export type FriendBalances = Schemas['FriendBalancesResponse']
export type FriendTotals = Schemas['Totals']
export type FriendBalance = Schemas['FriendBalanceResponse']
/** One open balance with a friend: in a group, or outside groups (groupId null). */
export type FriendBalanceItem = Schemas['Item']
export type SettleUpResult = Schemas['SettleUpResponse']

export type Group = Schemas['GroupResponse']
export type CreateGroupRequest = Schemas['CreateGroupRequest']
export type UpdateGroupRequest = Schemas['UpdateGroupRequest']

export type Expense = Schemas['ExpenseResponse']
export type ExpenseShare = Schemas['ShareResponse']
export type ExpensePage = Schemas['PageResponseExpenseResponse']
export type CreateExpenseRequest = Schemas['CreateExpenseRequest']
export type UpdateExpenseRequest = Schemas['UpdateExpenseRequest']
export type ShareRequest = Schemas['ShareRequest']
export type Category = Expense['category']

export type MemberBalance = Schemas['BalanceResponse']
export type Transfer = Schemas['TransferResponse']
export type Settlement = Schemas['SettlementResponse']
export type CreateSettlementRequest = Schemas['CreateSettlementRequest']
export type SettlementPage = Schemas['PageResponseSettlementResponse']

export type Activity = Schemas['ActivityResponse']
export type ActivityPage = Schemas['PageResponseActivityResponse']
export type RecurringExpense = Schemas['RecurringExpenseResponse']
export type CreateRecurringExpenseRequest = Schemas['CreateRecurringExpenseRequest']
export type Frequency = RecurringExpense['frequency']
export type GroupReport = Schemas['GroupReportResponse']
