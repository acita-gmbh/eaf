import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import security from 'eslint-plugin-security'
import eslintReact from '@eslint-react/eslint-plugin'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import sonarjs from 'eslint-plugin-sonarjs'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'coverage']),
  {
    files: ['**/*.{ts,tsx}'],
    ignores: [
      '**/*.test.{ts,tsx}',
      '**/test/**',
      '**/__tests__/**',
      '**/components/ui/**',
      '*.config.{ts,js}',
    ],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    plugins: {
      security,
      '@eslint-react': eslintReact,
      'jsx-a11y': jsxA11y,
      sonarjs,
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
      // ═══════════════════════════════════════════════════════════════════════
      // React Refresh (Vite HMR)
      // ═══════════════════════════════════════════════════════════════════════
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // ═══════════════════════════════════════════════════════════════════════
      // React Best Practices (@eslint-react)
      // ═══════════════════════════════════════════════════════════════════════

      // Props must be read-only (SonarQube S6759)
      // Props are snapshots - every render receives a new version
      '@eslint-react/prefer-read-only-props': 'error',

      // Prevent unstable values causing unnecessary re-renders
      '@eslint-react/no-unstable-context-value': 'error',
      '@eslint-react/no-unstable-default-props': 'error',

      // Prevent leaked conditional rendering: {0 && <Component/>} renders "0"
      '@eslint-react/no-leaked-conditional-rendering': 'error',

      // Array index as key causes bugs with reordering/filtering
      '@eslint-react/no-array-index-key': 'warn',

      // Use children slot instead of children prop for clarity
      '@eslint-react/no-children-prop': 'warn',

      // ═══════════════════════════════════════════════════════════════════════
      // TypeScript Strict Rules
      // ═══════════════════════════════════════════════════════════════════════

      // Catch unhandled promises - prevents silent failures
      '@typescript-eslint/no-floating-promises': 'error',

      // Prevent promise misuse in non-async contexts (e.g., onClick={async () => ...})
      '@typescript-eslint/no-misused-promises': [
        'error',
        { checksVoidReturn: { attributes: false } },
      ],

      // Prefer ?? over || for null/undefined checks (avoids falsy value bugs)
      '@typescript-eslint/prefer-nullish-coalescing': 'warn',

      // Flag conditions that are always true/false (dead code indicator)
      '@typescript-eslint/no-unnecessary-condition': 'warn',

      // ═══════════════════════════════════════════════════════════════════════
      // Security Rules
      // ═══════════════════════════════════════════════════════════════════════

      // Prevent RegExp ReDoS vulnerabilities (CWE-1333)
      'security/detect-non-literal-regexp': 'error',

      // Prevent prototype pollution via dynamic property access
      'security/detect-object-injection': 'warn',

      // ═══════════════════════════════════════════════════════════════════════
      // Accessibility (jsx-a11y)
      // ═══════════════════════════════════════════════════════════════════════

      // Images must have alt text for screen readers
      'jsx-a11y/alt-text': 'error',

      // Anchors must have content
      'jsx-a11y/anchor-has-content': 'error',

      // Anchors must be valid (no empty href="#")
      'jsx-a11y/anchor-is-valid': 'warn',

      // ARIA attributes must be valid
      'jsx-a11y/aria-props': 'error',
      'jsx-a11y/aria-proptypes': 'error',
      'jsx-a11y/aria-role': 'error',
      'jsx-a11y/aria-unsupported-elements': 'error',

      // Interactive elements must be focusable
      'jsx-a11y/click-events-have-key-events': 'warn',
      'jsx-a11y/no-static-element-interactions': 'warn',

      // Form labels must be associated with inputs
      'jsx-a11y/label-has-associated-control': 'warn',

      // ═══════════════════════════════════════════════════════════════════════
      // Code Quality (SonarJS)
      // ═══════════════════════════════════════════════════════════════════════

      // Cognitive complexity - functions shouldn't be too complex
      'sonarjs/cognitive-complexity': ['warn', 15],

      // No identical expressions on both sides of binary operator
      'sonarjs/no-identical-expressions': 'error',

      // No redundant boolean comparisons (if (x === true))
      'sonarjs/no-redundant-boolean': 'error',

      // No unused catch bindings
      'sonarjs/no-ignored-exceptions': 'warn',

      // No empty collections being iterated
      'sonarjs/no-empty-collection': 'error',

      // No function calls with identical arguments
      'sonarjs/no-extra-arguments': 'error',

      // No gratuitous expressions (expressions with no effect)
      'sonarjs/no-gratuitous-expressions': 'error',

      // Prefer immediate return over variable assignment then return
      'sonarjs/prefer-immediate-return': 'warn',

      // ═══════════════════════════════════════════════════════════════════════
      // Project-Specific Rules
      // ═══════════════════════════════════════════════════════════════════════

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
    },
  },
])
