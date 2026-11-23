package com.hive.agents

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hive.agents.ui.HiveApp
import com.hive.agents.ui.HiveViewModel
import com.hive.agents.ui.theme.HiveTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLastCrash()
        enableEdgeToEdge()
        setContent {
            HiveTheme {
                val vm: HiveViewModel = viewModel()
                HiveApp(vm)
            }
        }
    }

    /** If the previous launch died, show the stack and let the owner share it. */
    private fun showLastCrash() {
        val file = File(filesDir, "last-crash.txt")
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) {
            file.delete()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Hive crashed last launch")
            .setMessage(text)
            .setPositiveButton("Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Hive crash log")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, "Share crash log"))
            }
            .setNeutralButton("Dismiss") { _, _ -> file.delete() }
            .setNegativeButton("Close", null)
            .show()
    }
}
