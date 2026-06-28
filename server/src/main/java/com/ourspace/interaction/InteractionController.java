package com.ourspace.interaction;

import com.ourspace.common.ApiResponse;
import com.ourspace.common.security.JwtAuthenticationFilter;
import com.ourspace.common.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {
    private static final List<String> TOPICS = List.of(
            "如果现在能瞬间到对方身边，你最想一起做什么？",
            "今天有没有一个瞬间，让你忽然想到对方？",
            "如果今晚只留一盏灯，你希望它照在哪里？",
            "下一次见面，想先抱一下还是先说话？",
            "最近最想和对方一起收藏的小事是什么？"
    );

    private final JdbcTemplate jdbc;
    private final TenantService tenantService;

    public InteractionController(JdbcTemplate jdbc, TenantService tenantService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
    }

    @GetMapping("/affection-cards")
    public ApiResponse<List<Map<String, Object>>> affectionCards(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        return ApiResponse.ok(jdbc.queryForList("""
                select ac.*, u.nickname from affection_cards ac
                join users u on u.id = ac.created_by
                where ac.couple_id = ? and ac.deleted_at is null
                order by ac.created_at desc limit 50
                """, coupleId));
    }

    @PostMapping("/affection-cards")
    public ApiResponse<Map<String, Object>> createAffectionCard(HttpServletRequest request, @RequestBody AffectionCardCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into affection_cards(couple_id, created_by, title, amount, message, file_id)
                    values(?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.title());
            ps.setObject(4, body.amount());
            ps.setString(5, body.message());
            ps.setObject(6, body.fileId());
            return ps;
        }, keyHolder);
        notifyPartner(coupleId, userId, "affection.created", "收到一张心意卡片", body.title());
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @GetMapping("/wishes")
    public ApiResponse<List<Map<String, Object>>> wishes(HttpServletRequest request) {
        long coupleId = tenantService.requireCoupleId(currentUser(request));
        return ApiResponse.ok(jdbc.queryForList("""
                select w.*, u.nickname from wishes w
                join users u on u.id = w.created_by
                where w.couple_id = ? and w.deleted_at is null
                order by w.completed asc, w.created_at desc
                """, coupleId));
    }

    @PostMapping("/wishes")
    public ApiResponse<Map<String, Object>> createWish(HttpServletRequest request, @RequestBody WishCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into wishes(couple_id, created_by, title, note)
                    values(?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setLong(2, userId);
            ps.setString(3, body.title());
            ps.setString(4, body.note());
            return ps;
        }, keyHolder);
        return ApiResponse.ok(Map.of("id", keyHolder.getKey().longValue()));
    }

    @PostMapping("/wishes/{id}/complete")
    public ApiResponse<Map<String, Object>> completeWish(HttpServletRequest request, @PathVariable long id) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        jdbc.update("""
                update wishes set completed = true, completed_by = ?, completed_at = now()
                where id = ? and couple_id = ? and deleted_at is null
                """, userId, id, coupleId);
        return ApiResponse.ok(Map.of("id", id, "completed", true));
    }

    @GetMapping("/daily-topic")
    public ApiResponse<Map<String, Object>> dailyTopic(HttpServletRequest request) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        long topicId = ensureDailyTopic(coupleId);
        var topic = jdbc.queryForMap("select * from daily_topics where id = ?", topicId);
        var answers = jdbc.queryForList("""
                select a.user_id, u.nickname, a.answer, a.created_at
                from daily_topic_answers a join users u on u.id = a.user_id
                where a.topic_id = ?
                order by a.created_at asc
                """, topicId);
        boolean answeredByMe = answers.stream().anyMatch(item -> ((Number) item.get("user_id")).longValue() == userId);
        boolean unlocked = answers.size() >= 2;
        return ApiResponse.ok(Map.of(
                "topic", topic,
                "answeredByMe", answeredByMe,
                "unlocked", unlocked,
                "answers", unlocked ? answers : answers.stream().filter(item -> ((Number) item.get("user_id")).longValue() == userId).toList()
        ));
    }

    @PostMapping("/daily-topic/answer")
    public ApiResponse<Map<String, Object>> answerDailyTopic(HttpServletRequest request, @RequestBody TopicAnswerCreate body) {
        long userId = currentUser(request);
        long coupleId = tenantService.requireCoupleId(userId);
        long topicId = ensureDailyTopic(coupleId);
        jdbc.update("""
                insert into daily_topic_answers(couple_id, topic_id, user_id, answer)
                values(?, ?, ?, ?)
                on duplicate key update answer = values(answer), updated_at = now()
                """, coupleId, topicId, userId, body.answer());
        return ApiResponse.ok(Map.of("topicId", topicId, "answered", true));
    }

    private long ensureDailyTopic(long coupleId) {
        var today = LocalDate.now();
        var rows = jdbc.queryForList("select id from daily_topics where couple_id = ? and topic_date = ?", Long.class, coupleId, today);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        var question = TOPICS.get(Math.floorMod(today.getDayOfYear(), TOPICS.size()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into daily_topics(couple_id, topic_date, question) values(?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, coupleId);
            ps.setObject(2, today);
            ps.setString(3, question);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void notifyPartner(long coupleId, long userId, String type, String title, String body) {
        var partners = jdbc.queryForList("""
                select user_id from couple_members
                where couple_id = ? and user_id <> ? and status = 'active'
                """, Long.class, coupleId, userId);
        for (Long partnerId : partners) {
            jdbc.update("""
                    insert into notifications(couple_id, recipient_user_id, actor_user_id, type, title, body)
                    values(?, ?, ?, ?, ?, ?)
                    """, coupleId, partnerId, userId, type, title, body);
        }
    }

    private long currentUser(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
    }

    public record AffectionCardCreate(@NotBlank String title, BigDecimal amount, String message, Long fileId) {
    }

    public record WishCreate(@NotBlank String title, String note) {
    }

    public record TopicAnswerCreate(@NotBlank String answer) {
    }
}
