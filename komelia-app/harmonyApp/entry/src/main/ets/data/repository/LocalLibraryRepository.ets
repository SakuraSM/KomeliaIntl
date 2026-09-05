import { LocalBookRecord, LocalLibraryRecord, LocalReadingProgress } from '../../model/LocalContentModels';
import { LocalIndexStorage } from '../../domain/service/LocalLibraryIndexer';
import { SerialTaskGate } from '../../domain/service/ReaderRequestToken';
import { HarmonyDatabase } from '../local/HarmonyDatabase';
import { relationalStore } from '@kit.ArkData';

export class LocalLibraryRepository implements LocalIndexStorage {
  private gate: SerialTaskGate = new SerialTaskGate();

  async libraries(): Promise<LocalLibraryRecord[]> {
    const result = await HarmonyDatabase.query('SELECT PAYLOAD FROM LOCAL_LIBRARY ORDER BY ID');
    const values: LocalLibraryRecord[] = [];
    try {
      while (result.goToNextRow()) values.push(JSON.parse(result.getString(0)) as LocalLibraryRecord);
      return values;
    } finally { result.close(); }
  }

  async saveLibrary(library: LocalLibraryRecord): Promise<void> {
    await this.gate.enter();
    try { await this.writeLibrary(library); } finally { this.gate.leave(); }
  }

  private async execute(sql: string, args: relationalStore.ValueType[],
    transaction?: relationalStore.Transaction): Promise<void> {
    if (transaction === undefined) await HarmonyDatabase.execute(sql, args);
    else await transaction.execute(sql, args);
  }

  private async writeLibrary(library: LocalLibraryRecord, transaction?: relationalStore.Transaction): Promise<void> {
    // UPSERT without REPLACE: replacing the parent can break a previously populated child index.
    await this.execute('INSERT OR IGNORE INTO LOCAL_LIBRARY (ID, PAYLOAD) VALUES (?, ?)',
      [library.id, JSON.stringify(library)], transaction);
    await this.execute('UPDATE LOCAL_LIBRARY SET PAYLOAD = ? WHERE ID = ?',
      [JSON.stringify(library), library.id], transaction);
  }

  async books(libraryId: string = '', transaction?: relationalStore.Transaction): Promise<LocalBookRecord[]> {
    const sql = 'SELECT PAYLOAD FROM LOCAL_BOOK' + (libraryId.length > 0 ? ' WHERE LIBRARY_ID = ?' : '') + ' ORDER BY ID';
    const args: relationalStore.ValueType[] = libraryId.length > 0 ? [libraryId] : [];
    const result = transaction === undefined ? await HarmonyDatabase.query(sql, args) : await transaction.querySql(sql, args);
    const values: LocalBookRecord[] = [];
    try {
      while (result.goToNextRow()) values.push(JSON.parse(result.getString(0)) as LocalBookRecord);
      return values;
    } finally { result.close(); }
  }

  async book(bookId: string): Promise<LocalBookRecord | undefined> {
    const result = await HarmonyDatabase.query('SELECT PAYLOAD FROM LOCAL_BOOK WHERE ID = ?', [bookId]);
    try { return result.goToFirstRow() ? JSON.parse(result.getString(0)) as LocalBookRecord : undefined; }
    finally { result.close(); }
  }

  async replaceIndex(library: LocalLibraryRecord, books: LocalBookRecord[]): Promise<void> {
    await this.gate.enter();
    let transaction: relationalStore.Transaction | undefined = undefined;
    try {
      // A dedicated transaction keeps concurrent server/download writes outside local index rollback.
      transaction = await HarmonyDatabase.requireStore().createTransaction({ transactionType: relationalStore.TransactionType.IMMEDIATE });
      await this.writeLibrary(library, transaction);
      const previous = await this.books(library.id, transaction);
      const kept = new Set(books.map((book: LocalBookRecord): string => book.id));
      for (let index = 0; index < previous.length; index++) {
        if (!kept.has(previous[index].id)) await this.deleteBookIndex(previous[index].id, transaction);
      }
      for (let index = 0; index < books.length; index++) {
        const book = books[index];
        if (book.libraryId !== library.id) throw new Error('LOCAL_LIBRARY_INDEX_MISMATCH');
        await transaction.execute('INSERT OR IGNORE INTO LOCAL_BOOK (ID, LIBRARY_ID, PAYLOAD) VALUES (?, ?, ?)',
          [book.id, book.libraryId, JSON.stringify(book)]);
        await transaction.execute('UPDATE LOCAL_BOOK SET PAYLOAD = ? WHERE ID = ? AND LIBRARY_ID = ?',
          [JSON.stringify(book), book.id, library.id]);
      }
      await transaction.commit();
    } catch (error) {
      try { await transaction?.rollback(); } catch (_) { }
      throw error as Error;
    } finally { this.gate.leave(); }
  }

  async removeLibrary(libraryId: string): Promise<void> {
    await this.gate.enter();
    let transaction: relationalStore.Transaction | undefined = undefined;
    try {
      transaction = await HarmonyDatabase.requireStore().createTransaction({ transactionType: relationalStore.TransactionType.IMMEDIATE });
      const books = await this.books(libraryId, transaction);
      for (let index = 0; index < books.length; index++) await this.deleteBookIndex(books[index].id, transaction);
      await transaction.execute('DELETE FROM LOCAL_LIBRARY WHERE ID = ?', [libraryId]);
      await transaction.commit();
    } catch (error) {
      try { await transaction?.rollback(); } catch (_) { }
      throw error as Error;
    } finally { this.gate.leave(); }
  }

  private async deleteBookIndex(bookId: string, transaction: relationalStore.Transaction): Promise<void> {
    await transaction.execute('DELETE FROM LOCAL_PROGRESS WHERE BOOK_ID = ?', [bookId]);
    await transaction.execute('DELETE FROM LOCAL_BOOK WHERE ID = ?', [bookId]);
  }

  async progress(bookId: string): Promise<LocalReadingProgress | undefined> {
    const result = await HarmonyDatabase.query('SELECT PAYLOAD FROM LOCAL_PROGRESS WHERE BOOK_ID = ?', [bookId]);
    try { return result.goToFirstRow() ? JSON.parse(result.getString(0)) as LocalReadingProgress : undefined; }
    finally { result.close(); }
  }

  async allProgress(): Promise<LocalReadingProgress[]> {
    const result = await HarmonyDatabase.query('SELECT PAYLOAD FROM LOCAL_PROGRESS');
    const values: LocalReadingProgress[] = [];
    try {
      while (result.goToNextRow()) values.push(JSON.parse(result.getString(0)) as LocalReadingProgress);
      return values;
    } finally { result.close(); }
  }

  async saveProgress(value: LocalReadingProgress): Promise<void> {
    await this.gate.enter();
    try {
      // A reader finishing after exclusion/removal must not recreate orphaned progress.
      if (await this.book(value.bookId) === undefined) return;
      await HarmonyDatabase.replace('LOCAL_PROGRESS', { 'BOOK_ID': value.bookId, 'PAYLOAD': JSON.stringify(value) });
    } finally { this.gate.leave(); }
  }

  async clearProgress(bookId: string): Promise<void> {
    await this.gate.enter();
    try { await HarmonyDatabase.execute('DELETE FROM LOCAL_PROGRESS WHERE BOOK_ID = ?', [bookId]); }
    finally { this.gate.leave(); }
  }
}

export const localLibraryRepository = new LocalLibraryRepository();
