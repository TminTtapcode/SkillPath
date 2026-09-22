FROM node:24.19.0-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/ || exit 1
