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

`POST /api/couples/invite` 返回 `inviteCode`、`expiresAt` 和 `coupleId`。邀请码 24 小时有效；如果当前用户已有未绑定的邀请空间，再次调用会刷新同一个邀请的密语和过期时间。

`POST /api/couples/bind` 请求体为 `{ "inviteCode": "NEAR2026" }`。接口会校验邀请码存在、未过期、空间未满，并禁止绑定自己创建的邀请码。

## Core

- `GET /api/home`
- `POST /api/files/upload`
- `POST /api/files`
- `GET /api/files/{id}/signed-url`
- `GET/POST /api/albums`
- `PUT/DELETE /api/albums/{id}`
- `GET/POST /api/statuses`
- `DELETE /api/statuses/{id}`
- `POST /api/statuses/{id}/reactions`
- `GET/POST /api/diaries`
- `GET /api/diaries/{id}`
- `PUT/DELETE /api/diaries/{id}`
- `GET/POST /api/diaries/{id}/comments`
- `GET/POST /api/anniversaries`
- `PUT/DELETE /api/anniversaries/{id}`

纪念日写入字段包括 `title`、`eventDate`、`eventType`、`remindTime` 和 `cardTheme`。`eventType` 用于区分在一起、生日、初见、下次见面等场景，`remindTime` 为当天提醒时间。

`GET /api/home` 返回首页聚合数据：

- `togetherDays`：在一起天数。
- `partnerStatus`：对方最近一条此刻。
- `anniversaries`：最近 3 个即将到来的纪念日，含 `days_left`。
- `enabledModules`：当前空间已开启模块。
- `unreadCount`：当前用户未读通知数。
- `latestStatus` / `latestAlbum`：空间内最新此刻和相册项。
- `wishProgress`：共同愿望总数、完成数和下一个待完成愿望。
- `dailyTopic`：今日话题题目、当前用户是否已答、是否已解锁。
- `futureLetterCount` / `unlockedFutureLetterCount`：未来信总数和已可读数量。

## Notifications

- `GET /api/notifications`
- `POST /api/notifications/{id}/read`
- `POST /api/notifications/read-all`

## Modules

- `GET /api/modules`
- `PUT /api/modules/{moduleKey}`
- `GET/POST /api/modules/pet/profiles`
- `PUT/DELETE /api/modules/pet/profiles/{id}`
- `GET/POST /api/modules/pet/events`
- `DELETE /api/modules/pet/events/{id}`

宠物栏接口要求当前情侣空间已开启 `pet` 模块，否则返回 `MODULE_DISABLED`。

## Interactions

- `GET/POST /api/interactions/affection-cards`
- `GET/POST /api/interactions/wishes`
- `PUT/DELETE /api/interactions/wishes/{id}`
- `POST /api/interactions/wishes/{id}/complete`
- `POST /api/interactions/wishes/{id}/reopen`
- `GET /api/interactions/daily-topic`
- `POST /api/interactions/daily-topic/answer`

## Future Letters

- `GET /api/future-letters`
- `POST /api/future-letters`
- `GET /api/future-letters/{id}`

`POST /api/future-letters` 创建未来信：

- `title` / `content`：标题和正文。
- `openAt`：开启时间，必须是未来的 ISO 日期时间。
- `recipientMode`：`partner` 表示只给对方，`both` 表示两个人都可读。

## Memories

- `GET /api/memories/today`

## Privacy

- `GET /api/privacy/export`
- `POST /api/privacy/deletion-request`
- `POST /api/privacy/deletion-request/cancel`
- `GET /api/privacy/requests`

## Files

- `POST /api/files/upload` accepts multipart form data with `file` and optional `originalName`; it stores the file under `MEDIA_STORAGE_DIR` and returns `id`, `objectKey`, and `url`.
- `POST /api/files` registers an existing object-storage key without uploading binary content.
- `GET /api/files/{id}/signed-url` returns `{ "url": "...", "ttlSeconds": 900 }` for image preview; the URL includes `expires` and `sig` query parameters.
