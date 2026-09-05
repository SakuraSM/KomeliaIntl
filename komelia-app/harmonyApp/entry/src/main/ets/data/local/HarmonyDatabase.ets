import { common } from '@kit.AbilityKit';
import { relationalStore } from '@kit.ArkData';
import { LOCAL_LIBRARY_SCHEMA } from './LocalLibrarySchema';
import { DOWNLOAD_PROGRESS_SCHEMA } from '../../model/DownloadProgressModels';

const DATABASE_VERSION = 10;
const STORE_CONFIG: relationalStore.StoreConfig = {
  name: 'komelia_harmony.db',
  securityLevel: relationalStore.SecurityLevel.S3
};

const V1_SCHEMA: string[] = [
  'CREATE TABLE IF NOT EXISTS SERVER_PROFILE (ID TEXT PRIMARY KEY, ORIGIN TEXT NOT NULL, DISPLAY_NAME TEXT NOT NULL, ALLOW_HTTP INTEGER NOT NULL DEFAULT 0, LAST_USER_ID TEXT NOT NULL DEFAULT "", UPDATED_AT INTEGER NOT NULL)',
  'CREATE TABLE IF NOT EXISTS USER_PROFILE (NAMESPACE TEXT NOT NULL, ENTITY_ID TEXT NOT NULL, PAYLOAD TEXT NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, ENTITY_ID))',
  'CREATE TABLE IF NOT EXISTS CATALOG_ENTITY (NAMESPACE TEXT NOT NULL, ENTITY_TYPE TEXT NOT NULL, ENTITY_ID TEXT NOT NULL, PAYLOAD TEXT NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, ENTITY_TYPE, ENTITY_ID))',
  'CREATE TABLE IF NOT EXISTS HOME_GROUP (NAMESPACE TEXT NOT NULL, ENTITY_ID TEXT NOT NULL, PAYLOAD TEXT NOT NULL, SORT_INDEX INTEGER NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, ENTITY_ID))',
  'CREATE TABLE IF NOT EXISTS OFFLINE_BOOK (NAMESPACE TEXT NOT NULL, BOOK_ID TEXT NOT NULL, MEDIA_KIND TEXT NOT NULL, TITLE TEXT NOT NULL, LOCAL_PATH TEXT NOT NULL, PAGE_COUNT INTEGER NOT NULL, DOWNLOADED_PAGES INTEGER NOT NULL, SIZE_BYTES INTEGER NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, BOOK_ID))',
  'CREATE TABLE IF NOT EXISTS OFFLINE_PAGE (NAMESPACE TEXT NOT NULL, BOOK_ID TEXT NOT NULL, PAGE_NUMBER INTEGER NOT NULL, LOCAL_PATH TEXT NOT NULL, MEDIA_TYPE TEXT NOT NULL, SIZE_BYTES INTEGER NOT NULL, COMPLETED INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, BOOK_ID, PAGE_NUMBER))',
  'CREATE TABLE IF NOT EXISTS DOWNLOAD_JOB (NAMESPACE TEXT NOT NULL, JOB_ID TEXT NOT NULL, BOOK_ID TEXT NOT NULL, PHASE TEXT NOT NULL, COMPLETED_PARTS INTEGER NOT NULL, TOTAL_PARTS INTEGER NOT NULL, ATTEMPTS INTEGER NOT NULL, ERROR_MESSAGE TEXT NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, JOB_ID))',
  'CREATE TABLE IF NOT EXISTS DOWNLOAD_PART (NAMESPACE TEXT NOT NULL, JOB_ID TEXT NOT NULL, PART_NUMBER INTEGER NOT NULL, STATE TEXT NOT NULL, ETAG TEXT NOT NULL DEFAULT "", BYTES INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (NAMESPACE, JOB_ID, PART_NUMBER))',
  'CREATE TABLE IF NOT EXISTS PROGRESS_OUTBOX (NAMESPACE TEXT NOT NULL, BOOK_ID TEXT NOT NULL, PAGE INTEGER NOT NULL, COMPLETED INTEGER NOT NULL, READ_DATE TEXT NOT NULL, ATTEMPTS INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (NAMESPACE, BOOK_ID))',
  'CREATE TABLE IF NOT EXISTS APP_SETTING (NAMESPACE TEXT NOT NULL, SETTING_KEY TEXT NOT NULL, VALUE TEXT NOT NULL, UPDATED_AT INTEGER NOT NULL, PRIMARY KEY (NAMESPACE, SETTING_KEY))',
  'CREATE INDEX IF NOT EXISTS IDX_CATALOG_TYPE ON CATALOG_ENTITY (NAMESPACE, ENTITY_TYPE)',
  'CREATE INDEX IF NOT EXISTS IDX_DOWNLOAD_PHASE ON DOWNLOAD_JOB (NAMESPACE, PHASE)',
  'CREATE INDEX IF NOT EXISTS IDX_OFFLINE_PAGE_BOOK ON OFFLINE_PAGE (NAMESPACE, BOOK_ID)'
];

export class HarmonyDatabase {
  private static store?: relationalStore.RdbStore;

  static async initialize(context: common.UIAbilityContext): Promise<void> {
    const store = await relationalStore.getRdbStore(context, STORE_CONFIG);
    if (store.version === 0) {
      for (let index = 0; index < V1_SCHEMA.length; index++) {
        await store.execute(V1_SCHEMA[index]);
      }
      store.version = 1;
    }
    if (store.version === 1) {
      await store.execute('ALTER TABLE USER_PROFILE ADD COLUMN CREDENTIAL_ALIAS TEXT NOT NULL DEFAULT ""');
      store.version = 2;
    }
    if (store.version === 2) {
      await store.execute('ALTER TABLE PROGRESS_OUTBOX ADD COLUMN LOCATOR TEXT NOT NULL DEFAULT ""');
      store.version = 3;
    }
    if (store.version === 3) {
      await store.execute('CREATE TABLE IF NOT EXISTS OFFLINE_LOG (ID INTEGER PRIMARY KEY AUTOINCREMENT, NAMESPACE TEXT NOT NULL, LEVEL TEXT NOT NULL, MESSAGE TEXT NOT NULL, TIMESTAMP INTEGER NOT NULL)');
      await store.execute('CREATE INDEX IF NOT EXISTS IDX_OFFLINE_LOG_LEVEL ON OFFLINE_LOG (NAMESPACE, LEVEL, TIMESTAMP DESC)');
      store.version = 4;
    }
    if (store.version === 4) {
      // Page-stream endpoints are zero-based, while Komga read progress is one-based.
      await store.execute("UPDATE PROGRESS_OUTBOX SET PAGE = PAGE + 1 WHERE LOCATOR = ''");
      store.version = 5;
    }
    if (store.version === 5) {
      await store.execute('DELETE FROM DOWNLOAD_PART WHERE JOB_ID IN (' +
        'SELECT JOB_ID FROM DOWNLOAD_JOB AS candidate WHERE JOB_ID <> (' +
        'SELECT JOB_ID FROM DOWNLOAD_JOB AS latest WHERE latest.NAMESPACE = candidate.NAMESPACE ' +
        'AND latest.BOOK_ID = candidate.BOOK_ID ORDER BY latest.UPDATED_AT DESC LIMIT 1))');
      await store.execute('DELETE FROM DOWNLOAD_JOB WHERE JOB_ID <> (' +
        'SELECT JOB_ID FROM DOWNLOAD_JOB AS latest WHERE latest.NAMESPACE = DOWNLOAD_JOB.NAMESPACE ' +
        'AND latest.BOOK_ID = DOWNLOAD_JOB.BOOK_ID ORDER BY latest.UPDATED_AT DESC LIMIT 1)');
      await store.execute('CREATE UNIQUE INDEX IF NOT EXISTS IDX_DOWNLOAD_BOOK ON DOWNLOAD_JOB (NAMESPACE, BOOK_ID)');
      store.version = 6;
    }
    if (store.version === 6) {
      await store.execute('CREATE TABLE IF NOT EXISTS BOOK_COLOR_CORRECTION (' +
        'NAMESPACE TEXT NOT NULL, BOOK_ID TEXT NOT NULL, PAYLOAD TEXT NOT NULL, UPDATED_AT INTEGER NOT NULL, ' +
        'PRIMARY KEY (NAMESPACE, BOOK_ID))');
      await store.execute('CREATE INDEX IF NOT EXISTS IDX_BOOK_COLOR_CORRECTION ON ' +
        'BOOK_COLOR_CORRECTION (NAMESPACE, BOOK_ID)');
      store.version = 7;
    }
    if (store.version === 7) {
      await store.execute("ALTER TABLE PROGRESS_OUTBOX ADD COLUMN OPERATION TEXT NOT NULL DEFAULT 'UPDATE'");
      store.version = 8;
    }
    if (store.version === 8) {
      store.beginTransaction();
      try {
        for (let index = 0; index < LOCAL_LIBRARY_SCHEMA.length; index++) {
          await store.execute(LOCAL_LIBRARY_SCHEMA[index]);
        }
        store.version = 9;
        store.commit();
      } catch (error) {
        store.rollBack();
        throw error as Error;
      }
    }
    if (store.version === 9) {
      store.beginTransaction();
      try {
        for (let index = 0; index < DOWNLOAD_PROGRESS_SCHEMA.length; index++) {
          await store.execute(DOWNLOAD_PROGRESS_SCHEMA[index]);
        }
        store.version = 10;
        store.commit();
      } catch (error) {
        store.rollBack();
        throw error as Error;
      }
    }
    if (store.version !== DATABASE_VERSION) {
      throw new Error(`UNSUPPORTED_HARMONY_DATABASE_VERSION:${store.version}`);
    }
    HarmonyDatabase.store = store;
  }

  static requireStore(): relationalStore.RdbStore {
    if (HarmonyDatabase.store === undefined) {
      throw new Error('HARMONY_DATABASE_NOT_INITIALIZED');
    }
    return HarmonyDatabase.store;
  }

  static async replace(table: string, values: relationalStore.ValuesBucket): Promise<number> {
    return HarmonyDatabase.requireStore().insert(
      table, values, relationalStore.ConflictResolution.ON_CONFLICT_REPLACE);
  }

  static async execute(sql: string, args: relationalStore.ValueType[] = []): Promise<void> {
    await HarmonyDatabase.requireStore().execute(sql, args);
  }

  static async query(sql: string, args: relationalStore.ValueType[] = []): Promise<relationalStore.ResultSet> {
    return HarmonyDatabase.requireStore().querySql(sql, args);
  }
}
