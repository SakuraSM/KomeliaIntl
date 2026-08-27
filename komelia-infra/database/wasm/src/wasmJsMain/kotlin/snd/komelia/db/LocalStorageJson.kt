package snd.komelia.db

import kotlinx.serialization.json.Json
import snd.komelia.homefilters.HomeScreenFilterSerializersModule

object LocalStorageJson {
    val json = Json {
        ignoreUnknownKeys = true
        serializersModule = HomeScreenFilterSerializersModule
    }
}
