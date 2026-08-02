/** @type {import('tailwindcss').Config} */
// Tailwind 配置：落地设计稿 token（暗色玻璃态主题）
// 颜色值与设计稿 :root CSS 变量一一对应
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 背景
        'bg-primary': '#000000',
        'bg-alt': '#13121C',
        // 文字
        'text-primary': '#FFFFFF',
        'text-secondary': 'rgba(255,255,255,0.65)',
        'text-tertiary': 'rgba(255,255,255,0.45)',
        // 强调色（设计稿三色）
        'accent-primary': '#B1E2FF',    // 天蓝
        'accent-secondary': '#9381FF',  // 紫
        'accent-tertiary': '#FFB347',   // 暖橙
        // 状态色
        success: '#34D399',
        warning: '#FBBF24',
        error: '#FB7185',
      },
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Noto Sans SC', 'system-ui', 'sans-serif'],
        mono: ['IBM Plex Mono', 'JetBrains Mono', 'monospace'],
      },
      backdropBlur: {
        glass: '32px',
      },
    },
  },
  plugins: [],
}
