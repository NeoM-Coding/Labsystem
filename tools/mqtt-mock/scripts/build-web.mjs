import { mkdir, copyFile } from "node:fs/promises";
import { build } from "esbuild";

await mkdir("dist/public/assets", { recursive: true });
await copyFile("src/web/index.html", "dist/public/index.html");

await build({
  entryPoints: ["src/web/main.tsx"],
  bundle: true,
  outdir: "dist/public/assets",
  entryNames: "[name]",
  assetNames: "[name]",
  format: "esm",
  sourcemap: true,
  minify: false,
  loader: {
    ".tsx": "tsx",
    ".ts": "ts",
    ".css": "css"
  }
});
