package snd.komelia.ui.login.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_back_to_online
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_offline_mode
import org.jetbrains.compose.resources.stringResource
import snd.komelia.offline.server.model.OfflineMediaServer
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.ui.common.components.KomeliaWordmark
import snd.komelia.ui.settings.offline.users.RootUserCard
import snd.komelia.ui.settings.offline.users.ServerCard
import snd.komga.client.user.KomgaUserId

@Composable
fun OfflineLoginContent(
    serverUsers: Map<OfflineMediaServer, List<OfflineUser>>,
    loginAs: (KomgaUserId) -> Unit,
    onServerDelete: (OfflineMediaServerId) -> Unit,
    onUserDelete: (KomgaUserId) -> Unit,
    onReturnToLogin: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
    ) {
        KomeliaWordmark(Modifier.padding(bottom = 8.dp))
        Text(stringResource(Res.string.login_offline_mode), style = MaterialTheme.typography.headlineMedium)

        for ((server, users) in serverUsers) {
            ServerCard(
                server = server,
                users = users,
                onServerDelete = onServerDelete,
                goOffline = loginAs,
                onUserDelete = onUserDelete,
                expandByDefault = serverUsers.size == 1
            )
        }

        if (serverUsers.size > 1) {
            RootUserCard({ loginAs(OfflineUser.ROOT) })
        }

        OutlinedButton(onClick = onReturnToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.login_back_to_online))
        }
    }
}
