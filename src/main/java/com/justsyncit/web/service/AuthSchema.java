package com.justsyncit.web.service;

/**
 * SQL Schema definitions for Authentication Store.
 */
public final class AuthSchema {

    // V1 Schema
    public static final String CREATE_USERS_TABLE = """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    display_name TEXT,
                    role TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    salt TEXT
                );
            """;

    public static final String CREATE_SESSIONS_TABLE = """
                CREATE TABLE IF NOT EXISTS sessions (
                    token TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    username TEXT NOT NULL,
                    role TEXT NOT NULL,
                    expiry_time INTEGER NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """;

    public static final String[] INIT_STATEMENTS = {
            CREATE_USERS_TABLE,
            CREATE_SESSIONS_TABLE,
            "CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);",
            "CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expiry_time);"
    };

    private AuthSchema() {
    }
}
