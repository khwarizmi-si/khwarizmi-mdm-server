/** @type {import('tailwindcss').Config} */
module.exports = {
    // Prefix all utilities with tw- so they never collide with the legacy
    // Bootstrap 3 classes during the gradual migration.
    prefix: 'tw-',
    // Preflight (CSS reset) is OFF while Bootstrap is still present; it will be
    // enabled at the final migration stage once Bootstrap is removed.
    corePlugins: {
        preflight: false,
    },
    content: [
        './index.html',
        './app/**/*.{html,js}',
    ],
    theme: {
        extend: {
            colors: {
                brand: {
                    teal: '#12897A',
                    tealDark: '#0C6157',
                    tealLight: '#2BA891',
                    orange: '#EE7B2E',
                    orangeLight: '#F5A44B',
                    onTeal: '#CDEDE6',
                },
            },
            fontFamily: {
                sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system',
                    'Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
            },
            boxShadow: {
                card: '0 24px 60px -18px rgba(12, 97, 87, 0.45), 0 4px 12px rgba(0,0,0,0.08)',
            },
        },
    },
    plugins: [],
};
