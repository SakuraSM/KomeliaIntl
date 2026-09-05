package snd.komelia.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.TtsuReaderSettings

interface EpubReaderSettingsRepository {
    fun getDisplaySettings(): Flow<EpubDisplaySettings>
    suspend fun putDisplaySettings(settings: EpubDisplaySettings)

    fun getReaderType(): Flow<EpubReaderType>
    suspend fun putReaderType(type: EpubReaderType)

    suspend fun getKomgaReaderSettings(): JsonObject
    suspend fun putKomgaReaderSettings(settings: JsonObject)

    suspend fun getTtsuReaderSettings(): TtsuReaderSettings
    suspend fun putTtsuReaderSettings(settings: TtsuReaderSettings)
}