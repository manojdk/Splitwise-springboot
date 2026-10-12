/** "Alice Smith" -> "AS", "Bob" -> "BO", for avatars. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const first = parts[0] ?? ''
  const last = parts[parts.length - 1] ?? ''
  const letters = parts.length > 1 ? first.charAt(0) + last.charAt(0) : first.slice(0, 2)
  return letters.toUpperCase() || '?'
}
