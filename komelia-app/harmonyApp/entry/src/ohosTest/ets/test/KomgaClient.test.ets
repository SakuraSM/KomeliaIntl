import { describe, expect, it } from '@ohos/hypium';
import { buildSeriesSearchRequest, KomgaClient, readProgressUpdateBody, seriesFilterConditions,
  seriesSearchSort } from
  '../../../main/ets/network/KomgaClient';
import { evaluateOriginPolicy, OriginPolicyError } from '../../../main/ets/network/OriginPolicy';

export default function komgaClientTest(): void {
  describe('KomgaClient connection policy', () => {
    it('creates mutually exclusive read progress payloads', 0, () => {
      expect(JSON.stringify(readProgressUpdateBody(12, false))).assertEqual('{"page":12}');
      expect(JSON.stringify(readProgressUpdateBody(12, true))).assertEqual('{"completed":true}');
    });
    it('scopes library series with the Komga AllOfSeries payload', 0, () => {
      expect(JSON.stringify(buildSeriesSearchRequest('  title  ', { libraryId: 'library-1' })))
        .assertEqual('{"fullTextSearch":"title","condition":{"type":"AllOfSeries","allOf":' +
          '[{"type":"LibraryId","libraryId":{"operator":"is","value":"library-1"}}]}}');
      expect(seriesSearchSort('', { sort: 'metadata.titleSort,asc' }))
        .assertEqual('metadata.titleSort,asc');
      expect(seriesSearchSort('title')).assertEqual('');
    });
    it('serializes Android-compatible include and exclude filter groups', 0, () => {
      const conditions = seriesFilterConditions({
        includeGenres: ['Fantasy'], includeTags: ['Magic'], inclusionMode: 'ANY',
        excludeGenres: ['Horror'], excludeTags: ['Spoiler'], exclusionMode: 'ALL'
      });
      expect(JSON.stringify(conditions)).assertEqual(
        '[{"type":"AnyOfSeries","anyOf":[' +
          '{"type":"Genre","genre":{"operator":"is","value":"Fantasy"}},' +
          '{"type":"Tag","tag":{"operator":"is","value":"Magic"}}]},' +
        '{"type":"AnyOfSeries","anyOf":[' +
          '{"type":"Genre","genre":{"operator":"isNot","value":"Horror"}},' +
          '{"type":"Tag","tag":{"operator":"isNot","value":"Spoiler"}}]}]');
    });
    it('serializes author, unset age, release year and boolean filters', 0, () => {
      const conditions = seriesFilterConditions({
        authors: [{ name: '  Alice  ', role: 'writer' }], ageRatings: ['None', '12'],
        releaseYears: ['2024', 'invalid'],
        complete: 'YES', oneshot: 'NO'
      });
      expect(JSON.stringify(conditions)).assertEqual(
        '[{"type":"Author","author":{"operator":"is","value":{"name":"Alice","role":"writer"}}},' +
        '{"type":"AnyOfSeries","anyOf":[' +
          '{"type":"AgeRating","ageRating":{"operator":"isNull"}},' +
          '{"type":"AgeRating","ageRating":{"operator":"is","value":12}}]},' +
        '{"type":"AnyOfSeries","anyOf":[{"type":"AllOfSeries","allOf":[' +
          '{"type":"ReleaseDate","releaseDate":{"operator":"after",' +
            '"dateTime":"2023-12-31T00:00:00Z"}},' +
          '{"type":"ReleaseDate","releaseDate":{"operator":"before",' +
            '"dateTime":"2025-01-01T00:00:00Z"}}]}]},' +
        '{"type":"Complete","complete":{"operator":"isTrue"}},' +
        '{"type":"OneShot","oneShot":{"operator":"isFalse"}}]');
    });
    it('rejects URL without scheme', 0, () => {
      const result = evaluateOriginPolicy('komga.example.test', false);
      expect(result.error).assertEqual(OriginPolicyError.SCHEME_REQUIRED);
    });

    it('rejects plain HTTP by default', 0, () => {
      const result = evaluateOriginPolicy('http://192.168.1.10:25600', false);
      expect(result.error).assertEqual(OriginPolicyError.HTTP_CONFIRMATION_REQUIRED);
    });

    it('allows explicitly confirmed LAN HTTP', 0, () => {
      const client = new KomgaClient();
      client.configure('http://192.168.1.10:25600/', 'user', 'password', true);
      expect(client.getServerUrl()).assertEqual('http://192.168.1.10:25600');
    });
  });
}
