import { deviceInfo } from '@kit.BasicServicesKit';

export function isReaderKitSupportedProduct(productModel: string): boolean {
  return productModel.trim().toLowerCase() !== 'emulator';
}

export class ReaderCapabilities {
  static nativeEpubAvailable(): boolean {
    return isReaderKitSupportedProduct(deviceInfo.productModel);
  }
}

export const readerCapabilities = new ReaderCapabilities();
