# DCM Web Frontend

React 19 + TypeScript + Vite frontend for the Dynamic Virtual Machine Manager.

## Tech Stack

- **React 19.2** with React Compiler (automatic optimization)
- **Vite 7.2** with @vitejs/plugin-react
- **TypeScript 5.9**
- **Tailwind CSS 4** with shadcn/ui components
- **Vitest** for unit tests
- **Playwright** for E2E tests

## React Compiler

This project uses [React Compiler](https://react.dev/learn/react-compiler) for automatic memoization optimization.

**Key Rule: Manual memoization is PROHIBITED.**

```tsx
// ❌ FORBIDDEN - ESLint will error on these imports
import { useMemo, useCallback, memo } from 'react'

// ✅ CORRECT - Write normal code, compiler optimizes automatically
const value = computeExpensive(a, b)
const handleClick = () => doSomething(a)
```

The compiler analyzes your code at build time and automatically adds memoization where it provides benefit. This is more reliable than manual optimization because:

1. Developers often memoize incorrectly (wrong dependencies, unnecessary overhead)
2. Compiler can optimize more aggressively than manual patterns
3. Code stays cleaner and more maintainable

See [CLAUDE.md](/CLAUDE.md#react-coding-standards-zero-tolerance) for full coding standards.

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```
