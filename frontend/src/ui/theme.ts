import type { GlobalThemeOverrides } from 'naive-ui'

export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#4f46e5',
    primaryColorHover: '#4338ca',
    primaryColorPressed: '#3730a3',
    primaryColorSuppl: '#4f46e5',
    successColor: '#059669',
    warningColor: '#d97706',
    errorColor: '#dc2626',
    textColorBase: '#111827',
    textColor1: '#111827',
    textColor2: '#4b5563',
    textColor3: '#8b93a1',
    borderColor: '#e8eaef',
    dividerColor: '#e8eaef',
    bodyColor: '#f6f7f9',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    borderRadius: '8px',
    borderRadiusSmall: '6px',
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Roboto, sans-serif",
    boxShadow1: '0 1px 3px rgba(16,24,40,0.06), 0 1px 2px rgba(16,24,40,0.04)',
    boxShadow2: '0 8px 24px rgba(16,24,40,0.08), 0 2px 6px rgba(16,24,40,0.04)',
    boxShadow3: '0 20px 48px rgba(16,24,40,0.14), 0 4px 12px rgba(16,24,40,0.06)',
  },
  Button: {
    borderRadiusMedium: '8px',
    fontWeight: '500',
  },
  Input: {
    borderRadius: '8px',
  },
  Card: {
    borderRadius: '12px',
  },
  Pagination: {
    itemBorderRadius: '8px',
  },
}
