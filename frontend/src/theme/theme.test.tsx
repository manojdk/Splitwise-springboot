import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../test/renderApp'

describe('themes', () => {
  it('starts in Aurora', async () => {
    renderApp('/login')

    expect(await screen.findByRole('radio', { name: 'Aurora' })).toHaveAttribute('aria-checked', 'true')
    expect(document.documentElement.dataset.theme).toBe('aurora')
  })

  it('switches theme and remembers the choice', async () => {
    const { user } = renderApp('/login')

    await user.click(await screen.findByRole('radio', { name: 'Neon' }))

    expect(document.documentElement.dataset.theme).toBe('neon')
    expect(screen.getByRole('radio', { name: 'Neon' })).toHaveAttribute('aria-checked', 'true')
    expect(localStorage.getItem('splitwise-theme')).toBe('neon')
  })

  it('restores a saved theme and ignores an unknown one', async () => {
    localStorage.setItem('splitwise-theme', 'solar')
    const first = renderApp('/login')
    await screen.findByRole('radio', { name: 'Solar', checked: true })
    expect(document.documentElement.dataset.theme).toBe('solar')
    first.unmount()

    localStorage.setItem('splitwise-theme', 'hacker-green')
    renderApp('/login')
    expect(await screen.findByRole('radio', { name: 'Aurora', checked: true })).toBeInTheDocument()
  })

  it('still works when the browser blocks storage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    const { user } = renderApp('/login')

    await user.click(await screen.findByRole('radio', { name: 'Solar' }))

    expect(document.documentElement.dataset.theme).toBe('solar')
  })
})
