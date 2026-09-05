import { common } from '@kit.AbilityKit';
import { bookParser } from '@kit.ReaderKit';
import { ReadStateCode, TextReader } from '@kit.SpeechKit';
import { epubMarkupToSpeechText, epubSpeechArticleId, isSpeechArticleForBook,
  resourceIndexFromSpeechArticleId } from
  './EpubReadAloudContent';

export interface EpubReadAloudSource {
  bookId: string;
  currentResourceIndex: number;
  handler: bookParser.BookParserHandler;
  catalog: bookParser.CatalogItem[];
}

export type EpubReadAloudArticleSelected = (resourceIndex: number) => void;

export class EpubReadAloudUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'EpubReadAloudUnavailableError';
  }
}

export class EpubReadAloudService {
  private initialized: boolean = false;
  private activeBookId: string = '';
  private activeArticleId: string = '';
  private articleSelected: EpubReadAloudArticleSelected | undefined;
  private readonly articleClickListener: (articleId: string) => void = (articleId: string): void => {
    this.acceptActiveArticle(articleId, true);
  };
  private readonly stateChangeListener: (state: TextReader.ReadState) => void =
    (state: TextReader.ReadState): void => {
      this.acceptActiveArticle(state.id, state.state === ReadStateCode.PLAYING ||
        state.state === ReadStateCode.PAUSED);
    };
  private readonly readProgressListener: (progress: TextReader.ReadProgress) => void =
    (progress: TextReader.ReadProgress): void => {
      this.acceptActiveArticle(progress.id, true);
    };
  private readonly stopListener: () => void = (): void => {
    this.activeArticleId = '';
  };
  private readonly releaseListener: () => void = (): void => {
    this.resetSession();
  };

  isAvailable(): boolean {
    return canIUse('SystemCapability.AI.Component.TextReader');
  }

  isActiveFor(bookId: string): boolean {
    if (!this.initialized || this.activeBookId !== bookId) return false;
    try {
      const state = TextReader.queryReadState();
      this.acceptActiveArticle(state.id, false);
      return state.state === ReadStateCode.PLAYING || state.state === ReadStateCode.PAUSED;
    } catch (_) {
      return false;
    }
  }

  activeResourceIndexFor(bookId: string): number | undefined {
    if (!this.initialized || this.activeBookId !== bookId ||
      !isSpeechArticleForBook(this.activeArticleId, bookId)) return undefined;
    return resourceIndexFromSpeechArticleId(this.activeArticleId);
  }

  setArticleSelected(listener?: EpubReadAloudArticleSelected): void {
    this.articleSelected = listener;
  }

  async start(context: common.UIAbilityContext, source: EpubReadAloudSource,
    onArticleSelected?: EpubReadAloudArticleSelected): Promise<void> {
    if (!this.isAvailable()) {
      throw new EpubReadAloudUnavailableError('SpeechKit TextReader is unavailable on this device');
    }
    if (source.bookId.trim().length === 0) {
      throw new EpubReadAloudUnavailableError('Book identifier is missing');
    }
    this.articleSelected = onArticleSelected;
    if (this.initialized && this.activeBookId === source.bookId) {
      try {
        const state = TextReader.queryReadState();
        this.acceptActiveArticle(state.id, false);
        if (state.state === ReadStateCode.PLAYING || state.state === ReadStateCode.PAUSED) {
          if (state.state === ReadStateCode.PAUSED) TextReader.resume();
          TextReader.showPanel();
          return;
        }
      } catch (_) {
        await this.release();
      }
    }

    if (this.activeBookId !== source.bookId) await this.release();
    const articles = await this.createArticles(source);
    if (articles.length === 0) {
      throw new EpubReadAloudUnavailableError('No readable text was found in this EPUB');
    }
    if (!this.initialized) {
      await TextReader.init(context, {
        isVoiceBrandVisible: false,
        businessBrandInfo: {
          panelName: 'Komelia',
          notificationName: 'Komelia'
        },
        isFastForward: false,
        keepBackgroundRunning: true,
        online: 1,
        isMinibarNeeded: true,
        displayTab: TextReader.DisplayTab.CONTENT,
        reportProgressPeriod: 1000
      });
      this.initialized = true;
      try {
        this.registerListeners();
      } catch (error) {
        await this.release();
        throw error instanceof Error ? error : new Error(`${error}`);
      }
    }
    this.activeBookId = source.bookId;
    this.activeArticleId = epubSpeechArticleId(source.bookId, source.currentResourceIndex);
    if (!articles.some((article: TextReader.ReadInfo) => article.id === this.activeArticleId)) {
      this.activeArticleId = articles[0].id;
    }
    try {
      await TextReader.start(articles, this.activeArticleId, { isMinibarHidden: false });
    } catch (error) {
      await this.release();
      throw error instanceof Error ? error : new Error(`${error}`);
    }
  }

  pause(): void {
    if (this.initialized) TextReader.pause();
  }

  resume(): void {
    if (this.initialized) TextReader.resume();
  }

  async stop(): Promise<void> {
    if (!this.initialized) return;
    await TextReader.stop();
    this.activeArticleId = '';
  }

  async release(): Promise<void> {
    if (!this.initialized) return;
    try {
      this.unregisterListeners();
      await TextReader.release();
    } finally {
      this.resetSession();
    }
  }

  private registerListeners(): void {
    TextReader.on('clickArticle', this.articleClickListener);
    TextReader.on('stateChange', this.stateChangeListener);
    TextReader.on('readProgress', this.readProgressListener);
    TextReader.on('stop', this.stopListener);
    TextReader.on('release', this.releaseListener);
  }

  private unregisterListeners(): void {
    try { TextReader.off('clickArticle', this.articleClickListener); } catch (_) {}
    try { TextReader.off('stateChange', this.stateChangeListener); } catch (_) {}
    try { TextReader.off('readProgress', this.readProgressListener); } catch (_) {}
    try { TextReader.off('stop', this.stopListener); } catch (_) {}
    try { TextReader.off('release', this.releaseListener); } catch (_) {}
  }

  private acceptActiveArticle(articleId: string, notifySelection: boolean): void {
    if (!isSpeechArticleForBook(articleId, this.activeBookId)) return;
    const changed = articleId !== this.activeArticleId;
    this.activeArticleId = articleId;
    if (!notifySelection || !changed || this.articleSelected === undefined) return;
    const resourceIndex = resourceIndexFromSpeechArticleId(articleId);
    if (resourceIndex !== undefined) this.articleSelected(resourceIndex);
  }

  private resetSession(): void {
    this.initialized = false;
    this.activeBookId = '';
    this.activeArticleId = '';
    this.articleSelected = undefined;
  }

  private async createArticles(source: EpubReadAloudSource): Promise<TextReader.ReadInfo[]> {
    const book = source.handler.getBookInfo();
    const spine = source.handler.getSpineList() || [];
    const articles: TextReader.ReadInfo[] = [];
    for (const item of spine) {
      const markup = await source.handler.getSpineItemContent(item.index);
      const body = epubMarkupToSpeechText(markup);
      if (body.length === 0) continue;
      const catalogItem = source.catalog.find((candidate: bookParser.CatalogItem) =>
        candidate.resourceFile === item.href || candidate.idRef === item.idRef);
      const title = catalogItem?.catalogName || book.bookTitle || `${item.index + 1}`;
      articles.push({
        id: epubSpeechArticleId(source.bookId, item.index),
        title: { text: title, isClickable: false },
        author: book.bookCreator === undefined ? undefined : { text: book.bookCreator, isClickable: false },
        bodyInfoObject: { type: TextReader.BodyInfoType.CONTENT, body: body },
        category: book.bookTitle
      });
    }
    return articles;
  }
}

export const epubReadAloudService = new EpubReadAloudService();
