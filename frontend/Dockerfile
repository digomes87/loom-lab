# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /app

# rewrites do Next são resolvidos em BUILD-time (viram routes-manifest.json),
# então o BACKEND_URL precisa entrar aqui, não só em runtime
ARG BACKEND_URL=http://localhost:8080
ENV BACKEND_URL=$BACKEND_URL

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- runtime: Next.js standalone ----------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# output: "standalone" gera um server.js mínimo com as dependências embutidas
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
