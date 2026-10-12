import type { components } from './schema'

/** Generated from frontend/openapi.json (npm run api:types); never edit schema.d.ts by hand. */
type Schemas = components['schemas']

/**
 * The spec marks every response field optional because the Java records carry no "required" annotations, yet the
 * API always sends these fields with a value. Present<T> states that for DTOs where it holds; DTOs with
 * genuinely nullable fields must not use it.
 */
type Present<T> = { [K in keyof T]-?: NonNullable<T[K]> }

export type User = Present<Schemas['UserResponse']>
export type TokenResponse = Present<Schemas['TokenResponse']>
export type LoginRequest = Schemas['LoginRequest']
export type RegisterRequest = Schemas['RegisterRequest']
