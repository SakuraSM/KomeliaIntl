package snd.komelia.db

import kotlinx.serialization.json.Json
import snd.komelia.homefilters.HomeScreenFilterSerializersModule

val JsonDbDefault = Json {
    ignoreUnknownKeys = true
    serializersModule = HomeScreenFilterSerializersModule
}
