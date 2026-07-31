import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const clientRoot = path.resolve(__dirname, "../clients/typescript");
// Production (engine-hosted) builds use /app/; local `npm run dev` keeps /.
const base = process.env.VITE_BASE ?? "/";

export default defineConfig({
  base,
  plugins: [react()],
  resolve: {
    alias: {
      "@ai-dungeon-master/client": path.join(clientRoot, "index.ts"),
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/v2": { target: "http://127.0.0.1:8080", changeOrigin: true },
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: true },
      "/ws-stomp": { target: "ws://127.0.0.1:8080", ws: true, changeOrigin: true },
      "/ws": { target: "http://127.0.0.1:8080", ws: true, changeOrigin: true },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    emptyOutDir: true,
    commonjsOptions: {
      include: [/clients\/typescript/, /node_modules/],
    },
  },
  optimizeDeps: {
    exclude: ["@ai-dungeon-master/client"],
  },
});
