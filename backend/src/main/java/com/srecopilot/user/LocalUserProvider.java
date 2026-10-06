package com.srecopilot.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocalUserProvider implements CurrentUserProvider {

    private final JdbcTemplate jdbc;

    public LocalUserProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long userId() {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?",
                Long.class, "local@srecopilot.test");
    }
}
