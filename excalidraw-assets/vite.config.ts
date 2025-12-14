import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Serve deprecated fonts from node_modules during development
      "/fonts": path.resolve(__dirname, "node_modules/@excalidraw/excalidraw/dist/prod/fonts"),
    },
  },
  server: {
    port: 3006,
    open: false, // Don't auto-open browser - we're embedding in JCEF
    fs: {
      // Allow serving files from node_modules for deprecated fonts
      allow: [".", "../node_modules/@excalidraw/excalidraw/dist/prod/fonts"],
    },
  },
  build: {
    outDir: "build/react-build", // Match existing Gradle expectations
    sourcemap: "inline", // Match existing CRA config for debugging in JCEF
    minify: false, // Match existing CRA config - easier debugging in JCEF
  },
  publicDir: "public",
  optimizeDeps: {
    esbuildOptions: {
      // Support "arbitrary module namespace identifier names" required by Excalidraw
      target: "es2022",
    },
  },
});
