import { localizedString } from '../../core/i18n/LocalizedResources';
import { komfRepository } from '../../data/repository/DefaultRepositories';
import { AppPreferences } from '../../persistence/AppPreferences';

export async function hasStoredKomfConfiguration(): Promise<boolean> {
  try {
    return (await AppPreferences.getString('komfOrigin')).trim().length > 0;
  } catch (_) {
    return false;
  }
}

export async function configureStoredKomf(): Promise<void> {
  const origin = await AppPreferences.getString('komfOrigin');
  if (origin.length === 0) {
    throw new Error(localizedString($r('app.string.komf_not_configured')));
  }
  const allowHttp = await AppPreferences.getBoolean('komfAllowHttp');
  komfRepository.configure(origin, allowHttp);
}
