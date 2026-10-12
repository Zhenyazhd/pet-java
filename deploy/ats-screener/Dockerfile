# ATS Screener (tools/ats-screener, MIT, SvelteKit) as a plain Node server. Build it with the submodule as the context:
#   docker build -f deploy/ats-screener/Dockerfile tools/ats-screener
# The project targets Vercel (adapter-vercel); the line below swaps in the Node adapter without touching the submodule.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
RUN npm install -g pnpm@10.32.1
WORKDIR /src
COPY . .
RUN pnpm install --frozen-lockfile
RUN sed -i "s#@sveltejs/adapter-vercel#@sveltejs/adapter-node#" svelte.config.js \
 && pnpm add -D @sveltejs/adapter-node@5
RUN pnpm exec svelte-kit sync && pnpm build:app
RUN pnpm prune --prod

FROM node:22-alpine
WORKDIR /app
COPY --from=build /src/build ./build
COPY --from=build /src/node_modules ./node_modules
COPY --from=build /src/package.json ./
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000 BODY_SIZE_LIMIT=2M

USER 1000
EXPOSE 3000
CMD ["node", "build"]
