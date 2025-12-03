import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import security from 'eslint-plugin-security'
import eslintReact from '@eslint-react/eslint-plugin'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}', '**/test/**', '**/__tests__/**', '**/components/ui/**', '*.config.{ts,js}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    plugins: {
      security,
      '@eslint-react': eslintReact,
    },
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // Allow exported variants from shadcn components
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      // Prohibit manual memoization - React Compiler handles optimization automatically
      // See: https://react.dev/learn/react-compiler
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'react',
              importNames: ['useMemo', 'useCallback', 'memo'],
              message:
                'Manual memoization is prohibited. React Compiler handles optimization automatically.',
            },
          ],
        },
      ],
      // Security: Prevent RegExp ReDoS vulnerabilities (CWE-1333)
      // Non-literal regexp patterns from user input can cause catastrophic backtracking
      'security/detect-non-literal-regexp': 'error',
      // Enforce React props to be read-only (SonarQube S6759)
      // Props are read-only snapshots - every render receives a new version
      // See: https://react.dev/learn/passing-props-to-a-component
      '@eslint-react/prefer-read-only-props': 'error',
    },
  },
])
