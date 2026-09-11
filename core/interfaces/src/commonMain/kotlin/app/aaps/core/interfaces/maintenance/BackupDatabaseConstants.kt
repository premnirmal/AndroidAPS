package app.aaps.core.interfaces.maintenance

object BackupDatabaseConstants {
    const val DATABASE_MAIN_FILE = "androidaps.db"
    const val DATABASE_WAL_FILE = "androidaps.db-wal"
    const val DATABASE_SHM_FILE = "androidaps.db-shm"
    const val PENDING_DB_RESTORE_DIR = "pending-database-restore"
    const val PENDING_DB_RESTORE_FLAG = "AAPS.PendingDatabaseRestore"
    const val SHARED_PREFERENCES_SUFFIX = "_preferences"
}
