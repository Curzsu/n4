# syntax=docker/dockerfile:1

# ---- Stage 1: build the Vue SPA with Vite ----
FROM node:22-alpine AS builder

WORKDIR /build

# Install dependencies first to leverage Docker layer caching
COPY package.json package-lock.json ./
RUN npm ci

# Build-time FUXA URL injected into the bundle via Vite (import.meta.env.VITE_FUXA_URL).
# Browser loads the FUXA iframe directly from this address, so it must be reachable
# from the client. Default points to the deployed server; override at build time if needed.
ARG VITE_FUXA_URL=http://47.121.30.136:1881/home?viewName=H1_EMSB_A4_03_EM
ENV VITE_FUXA_URL=${VITE_FUXA_URL}

COPY . .
RUN npm run build

# ---- Stage 2: serve static assets with Nginx ----
FROM nginx:stable-alpine

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Replace the default Nginx config with our reverse-proxy setup
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built SPA into the Nginx document root
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

# Nginx liveness: HEAD the served root. busybox wget (shipped in the alpine base
# image) supports --spider. Non-fatal if the binary were unavailable.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["wget", "-q", "--spider", "http://localhost/"]
