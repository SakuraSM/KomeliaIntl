package snd.komelia

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.github.snd_r.komelia.R
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import snd.komelia.ui.error.ErrorView

class CrashActivity : AppCompatActivity() {
    @OptIn(ExperimentalResourceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResourceReaderAndroidContext(this)

        val exceptionData = GlobalExceptionHandler.getExceptionDataFromIntent(intent)
        val exceptionMessage = if (exceptionData == null) getString(R.string.unknown_error)
        else "${exceptionData.exceptionName}: ${exceptionData.message}"

        setContent {
            ErrorView(
                exceptionMessage = exceptionMessage,
                stacktrace = exceptionData?.stacktrace,
                isRestartable = true,
                onRestart = {
                    finishAffinity()
                    startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                },
                onExit = { this.finishAndRemoveTask() }
            )
        }
    }
}
