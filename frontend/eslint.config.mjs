import eslint from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";

export default defineConfig([
  { ...eslint.configs.recommended, files: ["**/*.{js,mjs,cjs}"] },
  ...tseslint.configs.recommended.map((config) => ({
    ...config,
    files: ["**/*.{ts,tsx}"],
  })),
  globalIgnores([".next/**", "out/**", "public/landing/**", "next-env.d.ts"]),
]);

