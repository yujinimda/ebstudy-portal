# ── 빌드 단계 ────────────────────────────────────────────────
# process.md 6.2 — 로컬도 Docker 로 띄워 이식성을 유지한다.
FROM node:22.23.0-bookworm-slim AS build
WORKDIR /workspace

# 의존성 계층을 소스보다 먼저 만든다 — 소스만 바뀌면 npm ci 를 다시 하지 않는다
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# ★ BACKEND_ORIGIN 은 **빌드 시점** 값이다.
#   next build 가 rewrites() 를 평가해 .next/routes-manifest.json 에 목적지를 굽는다.
#   그래서 실행 시점 environment 로 주면 무시되고 빌드 때의 기본값이 쓰인다
#   (실제로 겪었다 — 컨테이너가 127.0.0.1:8080 으로 붙으려 해서 ECONNREFUSED).
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=${BACKEND_ORIGIN}
RUN npm run build

# ── 실행 단계 ────────────────────────────────────────────────
FROM node:22.23.0-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /workspace/package.json /workspace/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=build /workspace/.next .next
COPY --from=build /workspace/public public
COPY --from=build /workspace/next.config.ts ./

USER node
EXPOSE 3000
CMD ["npm", "run", "start"]
