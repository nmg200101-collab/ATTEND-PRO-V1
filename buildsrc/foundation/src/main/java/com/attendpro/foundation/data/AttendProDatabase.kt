package com.attendpro.foundation.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AttendProDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA busy_timeout=5000")
        db.execSQL("PRAGMA secure_delete=ON")
    }

    override fun onCreate(db: SQLiteDatabase) = createSchema(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Missing explicit database migration $oldVersion -> $newVersion")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Database downgrade is not supported: $oldVersion -> $newVersion")
    }

    private fun createSchema(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
    }

    companion object {
        const val NAME = "attend_pro_v2.db"
        const val VERSION = 1

        internal val SCHEMA = listOf(
            """CREATE TABLE employees(
                id TEXT PRIMARY KEY NOT NULL,
                store_id TEXT NOT NULL,
                branch_id TEXT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                job_title TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
                updated_at INTEGER NOT NULL CHECK(updated_at >= 0)
            )""".trimIndent(),
            "CREATE INDEX employees_store_branch ON employees(store_id, branch_id, enabled, name)",
            """CREATE TABLE attendance(
                id TEXT PRIMARY KEY NOT NULL,
                employee_id TEXT NOT NULL,
                store_id TEXT NOT NULL,
                branch_id TEXT NOT NULL,
                action TEXT NOT NULL,
                state TEXT NOT NULL,
                method TEXT NOT NULL,
                occurred_at INTEGER NOT NULL CHECK(occurred_at >= 0),
                shift_start INTEGER NOT NULL CHECK(shift_start >= 0),
                shift_end INTEGER NOT NULL CHECK(shift_end >= shift_start),
                verified INTEGER NOT NULL CHECK(verified IN (0,1)),
                evidence_ciphertext BLOB,
                synced INTEGER NOT NULL DEFAULT 0 CHECK(synced IN (0,1)),
                FOREIGN KEY(employee_id) REFERENCES employees(id) ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
            )""".trimIndent(),
            "CREATE INDEX attendance_employee_time ON attendance(employee_id, occurred_at DESC)",
            "CREATE INDEX attendance_pending_sync ON attendance(synced, occurred_at) WHERE synced=0",
            """CREATE TABLE settings(
                namespace TEXT NOT NULL,
                key TEXT NOT NULL,
                value_type TEXT NOT NULL,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL CHECK(updated_at >= 0),
                PRIMARY KEY(namespace, key)
            )""".trimIndent(),
            """CREATE TABLE messages(
                id TEXT PRIMARY KEY NOT NULL,
                sender_id TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                channel TEXT NOT NULL,
                status TEXT NOT NULL,
                body_ciphertext BLOB NOT NULL,
                created_at INTEGER NOT NULL CHECK(created_at >= 0),
                delivered_at INTEGER,
                replied_to_id TEXT
            )""".trimIndent(),
            "CREATE INDEX messages_inbox ON messages(recipient_id, created_at DESC)",
            "CREATE INDEX messages_delivery ON messages(status, created_at)",
            """CREATE TABLE activation(
                id INTEGER PRIMARY KEY NOT NULL CHECK(id=1),
                encrypted_record BLOB NOT NULL,
                updated_at INTEGER NOT NULL CHECK(updated_at >= 0)
            )""".trimIndent(),
            """CREATE TABLE role_assignments(
                subject_id TEXT NOT NULL,
                store_id TEXT NOT NULL,
                role TEXT NOT NULL,
                updated_at INTEGER NOT NULL CHECK(updated_at >= 0),
                PRIMARY KEY(subject_id, store_id)
            )""".trimIndent(),
            """CREATE TABLE legacy_preferences(
                source TEXT NOT NULL,
                key TEXT NOT NULL,
                value_type TEXT NOT NULL,
                value TEXT NOT NULL,
                copied_at INTEGER NOT NULL CHECK(copied_at >= 0),
                PRIMARY KEY(source, key)
            )""".trimIndent(),
            """CREATE TABLE migration_state(
                source TEXT PRIMARY KEY NOT NULL,
                schema_version INTEGER NOT NULL,
                completed_at INTEGER NOT NULL CHECK(completed_at >= 0),
                item_count INTEGER NOT NULL CHECK(item_count >= 0),
                checksum_sha256 TEXT NOT NULL
            )""".trimIndent()
        )
    }
}
