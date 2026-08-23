# syntax=docker/dockerfile:1
# 多阶段构建：Node 构建静态资源 + Nginx 托管与 API 反代
# 构建上下文为仓库根目录（deploy/docker-compose.yml 中 context: ..）

FROM node:20-bookworm-slim AS builder

WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10 --activate

COPY client/package.json client/pnpm-lock.yaml client/pnpm-workspace.yaml ./
COPY client/.npmrc ./
COPY client/packages ./packages
COPY client/build ./build
COPY client/public ./public
COPY client/src ./src
COPY client/index.html client/vite.config.ts client/tsconfig.json client/uno.config.ts ./

COPY client/.env client/.env.prod ./

RUN pnpm install --frozen-lockfile --ignore-scripts
RUN pnpm build

FROM nginx:1.24-alpine

ARG NGINX_CONF=deploy/nginx.conf

COPY --from=builder /app/dist /usr/share/nginx/html
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf

# Docker 网络内反代后端；统一静态根目录；去掉未替换的 server_name 占位符
RUN sed -i \
    -e 's|127.0.0.1:8080|zdmj-backend:8080|g' \
    -e 's|\${SERVER_NAME}|_|g' \
    /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
