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
```

For LAN device preview or deployed H5 builds, change it to the reachable API origin, for example:

```env
VITE_API_BASE_URL=http://192.168.1.20:8080/api
```

If the API is unreachable during local preview, supported routes fall back to the built-in demo state so product flows can still be explored.

## Commands

```bash
npm install
npm run dev:h5
npm run check:errors
npm run typecheck
npm run build:h5
```

Run `npm run check:errors`, `npm run typecheck`, and `npm run build:h5` before handing off mobile changes.
