import {defineConfig} from 'vite';
import preact from '@preact/preset-vite';

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [preact()],
    define: {
        "process.env.IS_PREACT": JSON.stringify("true"),
    },
});
