import coreModelsTest from '../ohosTest/ets/test/CoreModels.test';
import komgaClientTest from '../ohosTest/ets/test/KomgaClient.test';
import localContentTest from '../ohosTest/ets/test/LocalContent.test';

export default function tests(): void {
  komgaClientTest();
  coreModelsTest();
  localContentTest();
}
