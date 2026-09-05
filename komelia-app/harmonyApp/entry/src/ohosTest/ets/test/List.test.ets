import komgaClientTest from './KomgaClient.test';
import coreModelsTest from './CoreModels.test';
import appUiTest from './AppUi.test';
import mindSporeRuntimeTest from './MindSporeRuntime.test';
import localContentTest from './LocalContent.test';

export default function tests(): void {
  komgaClientTest();
  coreModelsTest();
  localContentTest();
  appUiTest();
  mindSporeRuntimeTest();
}
