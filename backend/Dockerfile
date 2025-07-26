# Build stage
FROM node:18-alpine AS builder

# Set working directory
WORKDIR /app

# Install pnpm
RUN npm install -g pnpm@8.15.0

# Copy package files
COPY package*.json pnpm-lock.yaml ./
COPY backend/package.json ./backend/

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source code
COPY backend/ ./backend/
COPY nx.json ./

# Build the application
WORKDIR /app/backend
RUN pnpm build

# Production stage
FROM node:18-alpine AS production

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create app user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S whatsup -u 1001

# Set working directory
WORKDIR /app

# Install pnpm
RUN npm install -g pnpm@8.15.0

# Copy package files
COPY --chown=whatsup:nodejs backend/package.json ./
COPY --chown=whatsup:nodejs pnpm-lock.yaml ./

# Install production dependencies only
RUN pnpm install --prod --frozen-lockfile

# Copy built application
COPY --from=builder --chown=whatsup:nodejs /app/backend/dist ./dist
COPY --from=builder --chown=whatsup:nodejs /app/backend/drizzle ./drizzle

# Create directories for models cache
RUN mkdir -p /app/models_cache && chown whatsup:nodejs /app/models_cache

# Switch to non-root user
USER whatsup

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD node -e "require('http').request('http://localhost:3000/api/health', (res) => process.exit(res.statusCode === 200 ? 0 : 1)).end()"

# Start the application
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "dist/main.js"]
