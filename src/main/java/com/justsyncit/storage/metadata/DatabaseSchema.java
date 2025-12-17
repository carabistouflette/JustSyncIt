/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.storage.metadata;

/**
 * Defines the database schema for the JustSyncIt metadata system.
 * Contains SQL statements for creating tables and indexes.
 */
public final class DatabaseSchema {

        /** Current version of the database schema. */
        public static final int SCHEMA_VERSION = 6;

        /** Private constructor to prevent instantiation. */
        private DatabaseSchema() {
                // Utility class
        }

        /**
         * Gets the SQL statements to create the database schema.
         *
         * @return array of SQL statements for schema creation
         */
        public static String[] getCreateStatements() {
                return new String[] {
                                // Snapshots table - represents backup points in time
                                "CREATE TABLE IF NOT EXISTS snapshots ("
                                                + "id TEXT PRIMARY KEY,"
                                                + "name TEXT NOT NULL UNIQUE,"
                                                + "created_at INTEGER NOT NULL,"
                                                + "description TEXT,"
                                                + "active INTEGER DEFAULT 1," // Added active for completeness if
                                                                              // needed, but sticking to existing + new
                                                                              // columns
                                                + "total_files INTEGER DEFAULT 0,"
                                                + "total_size INTEGER DEFAULT 0,"
                                                + "merkle_root TEXT"
                                                + ")",

                                // Files table - represents files in snapshots
                                "CREATE TABLE IF NOT EXISTS files ("
                                                + "id TEXT PRIMARY KEY,"
                                                + "snapshot_id TEXT NOT NULL,"
                                                + "path TEXT NOT NULL,"
                                                + "size INTEGER NOT NULL,"
                                                + "modified_time INTEGER NOT NULL,"
                                                + "file_hash TEXT NOT NULL,"
                                                + "encryption_mode TEXT DEFAULT 'NONE',"
                                                + "FOREIGN KEY (snapshot_id) REFERENCES snapshots(id) ON DELETE CASCADE,"
                                                + "UNIQUE(snapshot_id, path)"
                                                + ")",

                                // File chunks table - maps files to their constituent chunks
                                "CREATE TABLE IF NOT EXISTS file_chunks ("
                                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                                + "file_id TEXT NOT NULL,"
                                                + "chunk_hash TEXT NOT NULL,"
                                                + "chunk_order INTEGER NOT NULL,"
                                                + "chunk_size INTEGER NOT NULL,"
                                                + "FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,"
                                                + "FOREIGN KEY (chunk_hash) REFERENCES chunks(hash) ON DELETE CASCADE,"
                                                + "UNIQUE(file_id, chunk_order)"
                                                + ")",

                                // File keywords table - for encrypted search
                                "CREATE TABLE IF NOT EXISTS file_keywords ("
                                                + "file_id TEXT NOT NULL,"
                                                + "keyword_hash TEXT NOT NULL,"
                                                + "FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE"
                                                + ")",

                                // Chunks table - metadata for stored chunks
                                "CREATE TABLE IF NOT EXISTS chunks ("
                                                + "hash TEXT PRIMARY KEY,"
                                                + "size INTEGER NOT NULL,"
                                                + "first_seen INTEGER NOT NULL,"
                                                + "reference_count INTEGER DEFAULT 1,"
                                                + "last_accessed INTEGER NOT NULL"
                                                + ")",

                                // Schema version table for migrations
                                "CREATE TABLE IF NOT EXISTS schema_version ("
                                                + "version INTEGER PRIMARY KEY"
                                                + ")",

                                // Indexes for performance
                                "CREATE INDEX IF NOT EXISTS idx_files_snapshot_id ON files(snapshot_id)",
                                "CREATE INDEX IF NOT EXISTS idx_files_path ON files(path)",
                                "CREATE INDEX IF NOT EXISTS idx_file_chunks_file_id ON file_chunks(file_id)",
                                "CREATE INDEX IF NOT EXISTS idx_file_chunks_chunk_hash ON file_chunks(chunk_hash)",
                                "CREATE INDEX IF NOT EXISTS idx_chunks_reference_count ON chunks(reference_count)",
                                "CREATE INDEX IF NOT EXISTS idx_chunks_last_accessed ON chunks(last_accessed)",
                                "CREATE INDEX IF NOT EXISTS idx_file_keywords_hash ON file_keywords(keyword_hash)",
                                "CREATE INDEX IF NOT EXISTS idx_file_keywords_file_id ON file_keywords(file_id)",

                                // FTS5 Virtual Table for full-text search (Legacy/Plaintext support)
                                "CREATE VIRTUAL TABLE IF NOT EXISTS files_search USING fts5("
                                                + "file_id UNINDEXED, " // Store ID but don't index it for search
                                                + "path" // Index path
                                                + ")",

                                // Triggers to keep FTS index in sync with files table
                                "CREATE TRIGGER IF NOT EXISTS files_ai AFTER INSERT ON files BEGIN "
                                                + "  INSERT INTO files_search(file_id, path) "
                                                + "  VALUES (new.id, new.path); "
                                                + "END",

                                "CREATE TRIGGER IF NOT EXISTS files_ad AFTER DELETE ON files BEGIN "
                                                + "  DELETE FROM files_search WHERE file_id = old.id; "
                                                + "END",

                                "CREATE TRIGGER IF NOT EXISTS files_au AFTER UPDATE ON files BEGIN "
                                                + "  UPDATE files_search SET path = new.path "
                                                + "  WHERE file_id = old.id; "
                                                + "END",

                                // Merkle Nodes table (Version 5)
                                "CREATE TABLE IF NOT EXISTS merkle_nodes ("
                                                + "hash TEXT PRIMARY KEY,"
                                                + "type TEXT NOT NULL,"
                                                + "name TEXT NOT NULL,"
                                                + "size INTEGER NOT NULL,"
                                                + "children TEXT,"
                                                + "file_id TEXT,"
                                                + "compression TEXT," // Added in v6
                                                + "FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE SET NULL"
                                                + ")"
                };
        }

        /**
         * Gets the SQL statement to insert the initial schema version.
         *
         * @return SQL statement for schema version insertion
         */
        public static String getInsertVersionStatement() {
                return "INSERT OR IGNORE INTO schema_version (version) VALUES (" + SCHEMA_VERSION + ")";
        }

        /**
         * Gets the SQL statement to query the current schema version.
         *
         * @return SQL statement for schema version query
         */
        public static String getVersionQuery() {
                return "SELECT version FROM schema_version ORDER BY version DESC LIMIT 1";
        }

        /**
         * Gets the SQL statements to drop all tables (for testing).
         *
         * @return array of SQL statements for dropping tables
         */
        public static String[] getDropStatements() {
                return new String[] {
                                "DROP TABLE IF EXISTS file_chunks",
                                "DROP TABLE IF EXISTS files",
                                "DROP TABLE IF EXISTS chunks",
                                "DROP TABLE IF EXISTS snapshots",
                                "DROP TABLE IF EXISTS schema_version"
                };
        }
}