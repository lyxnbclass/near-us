package com.ourspace.common.tenant;

import com.ourspace.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantService {
    private final JdbcTemplate jdbc;

    public TenantService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long activeCoupleId(long userId) {
        var rows = jdbc.queryForList("""
                select cm.couple_id from couple_members cm
                join couples c on c.id = cm.couple_id
                where cm.user_id = ? and cm.status = 'active' and cm.left_at is null
                  and c.status = 'active' and c.deleted_at is null
                limit 1
                """, Long.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long requireCoupleId(long userId) {
        var coupleId = activeCoupleId(userId);
        if (coupleId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PAIR_REQUIRED");
        }
        return coupleId;
    }

    public void requireModuleEnabled(long coupleId, String moduleKey) {
        var enabled = jdbc.queryForObject("""
                select count(*) from couple_modules
                where couple_id = ? and module_key = ? and enabled = true
                """, Integer.class, coupleId, moduleKey);
        if (enabled == null || enabled == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "MODULE_DISABLED");
        }
    }
}
