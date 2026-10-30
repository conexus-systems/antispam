package br.com.conexus.antispam.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MIconButton
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType

/** Alvo mínimo de toque (acessibilidade): 48dp. */
val MinTouch: Dp = 48.dp

/** Activity por trás de um Context do Compose (necessária para o formulário UMP). */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Cabeçalho das telas secundárias: seta de voltar (48dp) + título. */
@Composable
fun SubScreenHeader(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val palette = useW10MTheme().palette
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = W10MSpacing.s, vertical = W10MSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(W10MSpacing.xs),
    ) {
        W10MIconButton(
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = palette.text) },
            onClick = onBack,
            contentDescription = backDescription,
            modifier = Modifier.size(MinTouch),
        )
        W10MText(
            text = title,
            variant = W10MType::title,
            tone = W10MTextTone.Text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailing()
    }
}

/** Texto de apoio abaixo de um título de seção. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    W10MText(
        text = text,
        variant = W10MType::caption,
        tone = W10MTextTone.Muted,
        modifier = modifier.padding(bottom = W10MSpacing.s),
    )
}

@Composable
fun SectionGap() {
    Spacer(Modifier.height(W10MSpacing.xl))
}
