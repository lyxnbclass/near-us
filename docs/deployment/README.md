# 部署说明

生产建议：

- API 服务部署在支持 JDK 17 的服务器或容器环境。
- MySQL 开启自动备份。
- Redis 用于验证码、邀请码缓存、限流和 WebSocket 会话。
- OSS Bucket 设置为私有，禁止公开列举。
- 域名开启 HTTPS。
- 如果服务部署在中国大陆并面向公众访问，需要提前完成 ICP 备案和小程序主体/类目审核。

## Local H5 API debugging

The H5 client reads `VITE_API_BASE_URL` from `apps/mobile/.env` at build time.

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

When previewing from a phone on the same LAN, use the machine IP that runs the API:

```env
VITE_API_BASE_URL=http://192.168.1.20:8080/api
```

The Spring Boot API allows local H5 origins through `CORS_ALLOWED_ORIGIN_PATTERNS`.
The development default is:

```env
CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*,http://192.168.*:*
```

For production, set this value to the exact deployed H5 domain, for example:

```env
CORS_ALLOWED_ORIGIN_PATTERNS=https://app.example.com
```

## Private media URLs

`GET /api/files/{id}/signed-url` returns a short-lived URL for image previews.
The URL includes `expires` and `sig` query parameters and is signed with
`JWT_SECRET`.
By default it uses the API origin plus `/private-media/{objectKey}`.
Uploaded files are stored under `MEDIA_STORAGE_DIR` when using the built-in
local storage adapter:

```env
MEDIA_STORAGE_DIR=var/media
```

When media is served through a CDN, object-storage gateway, or reverse proxy,
set `MEDIA_BASE_URL` to that public origin:

```env
MEDIA_BASE_URL=https://media.example.com
```

The application permits `/private-media/**` so signed image URLs can be loaded
by browser and mini-program image components without an `Authorization` header.
