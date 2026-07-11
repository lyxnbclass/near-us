# OurSpace Mobile

uni-app + Vue 3 mobile client for H5 and WeChat Mini Program builds.

## API endpoint

The client reads `VITE_API_BASE_URL` at build time.

```bash
cp .env.example .env
```

Default:

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_USE_DEMO_FALLBACK=true
```

For LAN device preview or deployed H5 builds, change it to the reachable API origin, for example:

```env
VITE_API_BASE_URL=http://192.168.1.20:8080/api
```

If the API is unreachable during local preview, supported routes fall back to the built-in demo state so product flows can still be explored. Set `VITE_USE_DEMO_FALLBACK=false` when testing against the real backend and you want connection failures to surface immediately.

## Commands

```bash
npm install
npm run dev:h5
npm run check
npm run check:env
npm run check:errors
npm run check:migrations
npm run typecheck
npm run build:h5
```

Run `npm run check` and `npm run build:h5` before handing off mobile changes. `npm run check` includes env example coverage, backend error-code coverage, Flyway migration version checks, and TypeScript checks.
