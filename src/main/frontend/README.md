# JAiRouter Web Admin Interface

This is the frontend application for the JAiRouter web administration interface.

## Technology Stack

- **Vue.js 3** - Progressive JavaScript framework
- **TypeScript** - Type-safe JavaScript
- **Element Plus** - Vue 3 UI component library
- **Vite** - Fast build tool
- **Pinia** - State management
- **Vue Router** - Client-side routing
- **ECharts** - Data visualization
- **Axios** - HTTP client

## Development

### Prerequisites

- Node.js 18.x or higher
- npm 9.x or higher

### Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint code
npm run lint

# Type check
npm run type-check
```

### Development Server

The development server runs on `http://localhost:3000` and proxies API requests to the backend server at `http://localhost:8080`.

### Build Integration

This frontend is integrated with the Maven build process. When building the main JAiRouter project, the frontend will be automatically built and included in the final JAR file.

## Project Structure

```
src/
├── components/     # Reusable Vue components
├── views/         # Page components
├── router/        # Vue Router configuration
├── stores/        # Pinia stores
├── utils/         # Utility functions
├── types/         # TypeScript type definitions
├── locales/       # Internationalization files
└── assets/        # Static assets
```

## Configuration

- **Vite Config**: `vite.config.ts`
- **TypeScript Config**: `tsconfig.json`
- **ESLint Config**: `.eslintrc.cjs`
- **Prettier Config**: `.prettierrc.json`

## Environment Variables

- `VITE_API_BASE_URL`: Backend API base URL
- `VITE_WS_BASE_URL`: WebSocket base URL
- `VITE_APP_TITLE`: Application title