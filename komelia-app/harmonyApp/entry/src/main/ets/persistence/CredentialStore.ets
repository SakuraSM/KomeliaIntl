import { asset } from '@kit.AssetStoreKit';
import { util } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';

export interface StoredCredential {
  email: string;
  password: string;
}

const CREDENTIAL_ALIAS_PREFIX = 'komelia.komga.credentials';

function encode(value: string): Uint8Array {
  return new util.TextEncoder().encodeInto(value);
}

function decode(value: Uint8Array): string {
  return util.TextDecoder.create('utf-8', { ignoreBOM: true }).decodeToString(value, { stream: false });
}

export class CredentialStore {
  static createAlias(): string {
    return `${CREDENTIAL_ALIAS_PREFIX}.${util.generateRandomUUID(true)}`;
  }

  static async save(alias: string, value: StoredCredential): Promise<void> {
    await CredentialStore.remove(alias);
    const attributes: asset.AssetMap = new Map();
    attributes.set(asset.Tag.ALIAS, encode(alias));
    attributes.set(asset.Tag.SECRET, encode(JSON.stringify(value)));
    attributes.set(asset.Tag.ACCESSIBILITY, asset.Accessibility.DEVICE_FIRST_UNLOCKED);
    attributes.set(asset.Tag.SYNC_TYPE, asset.SyncType.NEVER);
    await asset.add(attributes);
  }

  static async load(alias: string): Promise<StoredCredential | undefined> {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, encode(alias));
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
    try {
      const result = await asset.query(query);
      if (result.length === 0) {
        return undefined;
      }
      const secret = result[0].get(asset.Tag.SECRET) as Uint8Array;
      return JSON.parse(decode(secret)) as StoredCredential;
    } catch (error) {
      const businessError = error as BusinessError;
      if (businessError.code === 24000002) {
        return undefined;
      }
      throw error;
    }
  }

  static async remove(alias: string): Promise<void> {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, encode(alias));
    try {
      await asset.remove(query);
    } catch (error) {
      const businessError = error as BusinessError;
      if (businessError.code !== 24000002) {
        throw error;
      }
    }
  }
}
