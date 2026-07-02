package com.ourspace.home;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public HomeController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> home(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        var couple = jdbc.queryForMap("select anniversary_start_date from couples where id = ?", coupleId);
        Object start = couple.get("anniversary_start_date");
        long togetherDays = start == null ? 0 : ChronoUnit.DAYS.between(LocalDate.parse(start.toString()), LocalDate.now()) + 1;
        var partnerStatus = jdbc.queryForList("""
                select s.*, u.nickname from statuses s join users u on u.id = s.created_by
                where s.couple_id = ? and s.created_by <> ? and s.deleted_at is null
                order by s.created_at desc limit 1
                """, coupleId, userId);
        var anniversaries = jdbc.queryForList("""
                select a.*,
                       datediff(
                         case
                           when date(concat(year(curdate()), '-', date_format(a.event_date, '%m-%d'))) < curdate()
                           then date(concat(year(curdate()) + 1, '-', date_format(a.event_date, '%m-%d')))
                           else date(concat(year(curdate()), '-', date_format(a.event_date, '%m-%d')))
                         end,
                         curdate()
                       ) as days_left
                from anniversaries a
                where couple_id = ? and deleted_at is null
                order by days_left asc limit 3
                """, coupleId);
        var modules = jdbc.queryForList("""
                select module_key from couple_modules
                where couple_id = ? and enabled = true
                """, coupleId);
        var latestStatus = jdbc.queryForList("""
                select s.*, u.nickname
                from statuses s join users u on u.id = s.created_by
                where s.couple_id = ? and s.deleted_at is null
                order by s.created_at desc limit 1
                """, coupleId);
        var latestAlbum = jdbc.queryForList("""
                select ai.*, u.nickname as creator_name, f.object_key, f.mime_type
                from album_items ai
                join users u on u.id = ai.created_by
                join files f on f.id = ai.file_id
                where ai.couple_id = ? and ai.deleted_at is null and f.deleted_at is null
                order by ai.created_at desc limit 1
                """, coupleId);
        var nextWish = jdbc.queryForList("""
                select w.*, u.nickname
                from wishes w join users u on u.id = w.created_by
                where w.couple_id = ? and w.deleted_at is null
                order by w.completed asc, w.created_at desc limit 1
                """, coupleId);
        int totalWishes = countForInt("select count(*) from wishes where couple_id = ? and deleted_at is null", coupleId);
        int completedWishes = countForInt("""
                select count(*) from wishes
                where couple_id = ? and completed = true and deleted_at is null
                """, coupleId);
        int unreadCount = countForInt("""
                select count(*) from notifications
                where recipient_user_id = ? and read_at is null
                """, userId);
        int futureLetterCount = countForInt("""
                select count(*) from future_letters
                where couple_id = ? and deleted_at is null
                  and (sender_user_id = ? or recipient_user_id is null or recipient_user_id = ?)
                """, coupleId, userId, userId);
        int unlockedFutureLetterCount = countForInt("""
                select count(*) from future_letters
                where couple_id = ? and deleted_at is null and open_at <= now()
                  and (sender_user_id = ? or recipient_user_id is null or recipient_user_id = ?)
                """, coupleId, userId, userId);
        var dailyTopicRows = jdbc.queryForList("""
                select dt.*,
                       exists(select 1 from daily_topic_answers a where a.topic_id = dt.id and a.user_id = ?) as answered_by_me,
                       (select count(*) from daily_topic_answers a where a.topic_id = dt.id) >= 2 as unlocked
                from daily_topics dt
                where dt.couple_id = ? and dt.topic_date = curdate()
                limit 1
                """, userId, coupleId);
        Map<String, Object> dailyTopic = dailyTopicRows.isEmpty()
                ? Map.of("question", "今晚想和对方聊点什么？", "answeredByMe", false, "unlocked", false)
                : Map.of(
                        "question", dailyTopicRows.get(0).get("question"),
                        "answeredByMe", dailyTopicRows.get(0).get("answered_by_me"),
                        "unlocked", dailyTopicRows.get(0).get("unlocked")
                );
        var payload = new HashMap<String, Object>();
        payload.put("togetherDays", togetherDays);
        payload.put("partnerStatus", partnerStatus.isEmpty() ? null : partnerStatus.get(0));
        payload.put("anniversaries", anniversaries);
        payload.put("enabledModules", modules);
        payload.put("unreadCount", unreadCount);
        payload.put("latestStatus", latestStatus.isEmpty() ? null : latestStatus.get(0));
        payload.put("latestAlbum", latestAlbum.isEmpty() ? null : latestAlbum.get(0));
        var wishProgress = new HashMap<String, Object>();
        wishProgress.put("total", totalWishes);
        wishProgress.put("completed", completedWishes);
        wishProgress.put("next", nextWish.isEmpty() ? null : nextWish.get(0));
        payload.put("wishProgress", wishProgress);
        payload.put("dailyTopic", dailyTopic);
        payload.put("futureLetterCount", futureLetterCount);
        payload.put("unlockedFutureLetterCount", unlockedFutureLetterCount);
        return ApiResponse.ok(payload);
    }

    private int countForInt(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }
}
