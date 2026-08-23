import { describe, expect, it } from '@ohos/hypium';
import { KomgaClient } from '../../../main/ets/network/KomgaClient';

export default function komgaClientTest(): void {
  describe('KomgaClient connection policy', () => {
    it('rejects URL without scheme', 0, () => {
      const client = new KomgaClient();
      expect(() => client.configure('komga.example.test', 'user', 'password')).assertThrowError();
    });

    it('rejects plain HTTP by default', 0, () => {
      const client = new KomgaClient();
      expect(() => client.configure('http://192.168.1.10:25600', 'user', 'password')).assertThrowError();
    });

    it('allows explicitly confirmed LAN HTTP', 0, () => {
      const client = new KomgaClient();
      client.configure('http://192.168.1.10:25600/', 'user', 'password', true);
      expect(client.getServerUrl()).assertEqual('http://192.168.1.10:25600');
    });
  });
}
