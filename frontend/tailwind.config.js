/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#17231f',
        cream: '#f4f0e8',
        lime: '#d8ff63',
        forest: '#204d3b',
      },
      fontFamily: {
        sans: ['Manrope', 'ui-sans-serif', 'system-ui'],
        display: ['Space Grotesk', 'ui-sans-serif', 'system-ui'],
      },
      boxShadow: {
        card: '0 24px 80px rgba(23, 35, 31, 0.12)',
      },
    },
  },
  plugins: [],
}
