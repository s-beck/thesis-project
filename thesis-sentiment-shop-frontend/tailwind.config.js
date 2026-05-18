/** @type {import('tailwindcss').Config} */
// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        sentiment: {
          positive: '#10b981', // emerald-500
          neutral:  '#6b7280', // gray-500
          negative: '#ef4444', // red-500
        }
      }
    },
  },
  plugins: [],
}
