package com.royalemind.ai.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.royalemind.ai.R

/**
 * Settings: per-app language (API 33+; older devices follow the system locale),
 * plus read-only policy info. No automation settings exist by design.
 */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { setLang(ctx, "en") }) { Text("English") }
        TextButton(onClick = { setLang(ctx, "fa") }) { Text("فارسی") }
        TextButton(onClick = { setLang(ctx, null) }) { Text(stringResource(R.string.system_language)) }

        Spacer(Modifier.height(16.dp))
        Card {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.policy_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.policy_text), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun setLang(ctx: Context, tag: String?) {
    if (Build.VERSION.SDK_INT >= 33) {
        val lm = ctx.getSystemService(LocaleManager::class.java)
        lm.applicationLocales = if (tag == null)
            android.os.LocaleList.getEmptyLocaleList()
        else android.os.LocaleList.forLanguageTags(tag)
    }
    // below API 33 the app follows the system language (values-fa picked automatically)
}
