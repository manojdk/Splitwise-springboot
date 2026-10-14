import { z } from 'zod'

/** All that bcrypt reads; the API counts the same way (UTF-8 bytes, not characters). */
export const MAX_PASSWORD_BYTES = 72

const bytes = (value: string) => new TextEncoder().encode(value).length

/** A new password, with the API's rules: at least 8 characters, at most 72 bytes. */
export const newPassword = z
  .string()
  .refine((value) => [...value].length >= 8, 'Use at least 8 characters')
  .refine(
    (value) => bytes(value) <= MAX_PASSWORD_BYTES,
    'Too long: use at most 72 bytes (letters with accents and symbols count as 2 to 4)',
  )
