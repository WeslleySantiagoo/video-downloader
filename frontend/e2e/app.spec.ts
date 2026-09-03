import { expect, test } from '@playwright/test'

test('mostra o formulário principal de forma responsiva', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /Sua mídia/ })).toBeVisible()
  await expect(page.getByLabel('Link do YouTube')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Analisar vídeo' })).toBeDisabled()
})
