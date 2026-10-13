import type { Category } from '../api/types'

/** Category labels and a small glyph for lists (decorative; the label is always shown or announced). */
export const CATEGORIES: { value: Category; label: string; icon: string }[] = [
  { value: 'GENERAL', label: 'General', icon: '◇' },
  { value: 'FOOD_AND_DRINK', label: 'Food & drink', icon: '☕' },
  { value: 'GROCERIES', label: 'Groceries', icon: '☘' },
  { value: 'RENT', label: 'Rent', icon: '⌂' },
  { value: 'UTILITIES', label: 'Utilities', icon: '⚡' },
  { value: 'TRANSPORT', label: 'Transport', icon: '✈' },
  { value: 'TRAVEL', label: 'Travel', icon: '☀' },
  { value: 'ENTERTAINMENT', label: 'Entertainment', icon: '♫' },
  { value: 'SHOPPING', label: 'Shopping', icon: '✦' },
  { value: 'HEALTH', label: 'Health', icon: '✚' },
]

export function categoryOf(value: Category) {
  return CATEGORIES.find((c) => c.value === value) ?? CATEGORIES[0]!
}
