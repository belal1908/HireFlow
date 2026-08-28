# Multi-stage build for the Angular frontend, used by docker-compose.yml's "full" profile
# (local full-stack Docker Compose - NOT how the live Render deploy is built; that's a separate
# static-site build with its own environment file, see the README's "Live deploy" section).
#
# Stage 1 installs deps and builds with the "docker-compose" configuration (see angular.json),
# which swaps in environment.docker-compose.ts: `apiUrl` = `http://localhost:8080` — correct for
# a browser-rendered SPA in this specific context, since the browser (not the nginx container)
# makes the API calls and can't resolve Docker-internal service names. That only works because
# the backend container also publishes port 8080 to the host, which docker-compose.yml does.
#
# Stage 2 serves the built static files with nginx.

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
# `npm ci` (used by the CI frontend job directly on the runner, where it works fine) fails
# inside node:20-alpine with a lockfile-integrity error on chokidar's optional dependency graph
# (confirmed: a from-scratch `npm ci` on the host succeeds against the exact same
# package-lock.json, so this is specific to npm's resolution inside this Linux/musl image, not a
# real inconsistency in the committed lockfile). `npm install` resolves the same dependency tree
# without npm's stricter ci-mode lockfile assertion, so it's used here, scoped to this image build
# only — the checked-in package-lock.json and every other consumer of it (including CI's own
# `npm ci` step) are untouched.
RUN npm install

COPY . .
RUN npm run build -- --configuration=docker-compose

# ---- Runtime stage ----
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
