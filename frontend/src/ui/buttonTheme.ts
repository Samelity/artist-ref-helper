import type { ButtonProps } from 'naive-ui'

export const brandSoftButtonTheme: NonNullable<ButtonProps['themeOverrides']> = {
  color: 'var(--brand-soft)',
  colorHover: '#e3e6ff',
  colorPressed: '#dbe0ff',
  colorFocus: 'var(--brand-soft)',
  textColor: 'var(--brand)',
  textColorHover: 'var(--brand)',
  textColorPressed: 'var(--brand-pressed)',
  textColorFocus: 'var(--brand)',
  border: '1px solid rgba(79, 70, 229, 0.38)',
  borderHover: '1px solid var(--brand)',
  borderPressed: '1px solid var(--brand-pressed)',
  borderFocus: '1px solid var(--brand)',
  fontWeight: '550',
}
