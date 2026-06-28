# API 草案

所有业务接口以 `/api` 开头，除登录和健康检查外都需要 `Authorization: Bearer <token>`。

## Auth

- `POST /api/auth/mock-login`

## Couple

- `GET /api/couples/me`
- `POST /api/couples/invite`
- `POST /api/couples/bind`
- `POST /api/couples/unbind-request`
- `POST /api/couples/unbind-cancel`
- `POST /api/couples/unbind-confirm`

## Core

- `GET/POST /api/albums`
- `GET/POST /api/statuses`
- `POST /api/statuses/{id}/reactions`
- `GET/POST /api/diaries`
- `GET /api/diaries/{id}`
- `GET/POST /api/diaries/{id}/comments`
- `GET/POST /api/anniversaries`
- `PUT/DELETE /api/anniversaries/{id}`

## Notifications

- `GET /api/notifications`
- `POST /api/notifications/{id}/read`

## Modules

- `GET /api/modules`
- `PUT /api/modules/{moduleKey}`
- `GET/POST /api/modules/pet/profiles`
- `GET/POST /api/modules/pet/events`

宠物栏接口要求当前情侣空间已开启 `pet` 模块，否则返回 `MODULE_DISABLED`。

## Interactions

- `GET/POST /api/interactions/affection-cards`
- `GET/POST /api/interactions/wishes`
- `POST /api/interactions/wishes/{id}/complete`
- `GET /api/interactions/daily-topic`
- `POST /api/interactions/daily-topic/answer`

## Future Letters

- `GET /api/future-letters`
- `POST /api/future-letters`
- `GET /api/future-letters/{id}`

## Memories

- `GET /api/memories/today`

## Privacy

- `GET /api/privacy/export`
- `POST /api/privacy/deletion-request`
- `GET /api/privacy/requests`
