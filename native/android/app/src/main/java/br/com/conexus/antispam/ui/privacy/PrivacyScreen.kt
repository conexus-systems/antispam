package br.com.conexus.antispam.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.settings.MinTouch
import br.com.conexus.antispam.ui.settings.SectionGap
import br.com.conexus.antispam.ui.settings.SubScreenHeader
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDialog
import br.com.conexus.antispam.ui.w10m.W10MDialogButton
import br.com.conexus.antispam.ui.w10m.W10MDialogButtonStyle
import br.com.conexus.antispam.ui.w10m.W10MNotice
import br.com.conexus.antispam.ui.w10m.W10MNoticeTone
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val vm: PrivacyViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()

    W10MScreen {
        Column(Modifier.fillMaxSize()) {
            SubScreenHeader(
                title = stringResource(R.string.privacy_title),
                backDescription = stringResource(R.string.privacy_back),
                onBack = onBack,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TextSection(R.string.privacy_section_device, listOf(R.string.privacy_device_body))
                SectionGap()
                TextSection(R.string.privacy_section_sent, listOf(R.string.privacy_sent_body))
                SectionGap()
                TextSection(R.string.privacy_section_never, listOf(R.string.privacy_never_body))
                SectionGap()
                TextSection(
                    R.string.privacy_section_permissions,
                    listOf(R.string.privacy_perm_screening, R.string.privacy_perm_notifications, R.string.privacy_perm_internet),
                )
                SectionGap()
                TextSection(R.string.privacy_section_ads, listOf(R.string.privacy_ads_body))
                SectionGap()
                W10MSection(title = stringResource(R.string.privacy_section_policy)) {
                    W10MText(stringResource(R.string.privacy_policy_label), variant = W10MType::body, tone = W10MTextTone.Muted)
                    // Placeholder até o proprietário publicar a política (não inventar URL).
                    W10MText(stringResource(R.string.privacy_policy_url), variant = W10MType::body, tone = W10MTextTone.Accent)
                }
                SectionGap()
                W10MSection(title = stringResource(R.string.privacy_section_erase)) {
                    W10MText(
                        stringResource(R.string.privacy_erase_body),
                        variant = W10MType::body,
                        tone = W10MTextTone.Muted,
                        modifier = Modifier.padding(bottom = W10MSpacing.s),
                    )
                    W10MButton(
                        label = stringResource(R.string.privacy_erase_button),
                        variant = W10MButtonVariant.Danger,
                        loading = ui.status == EraseStatus.ERASING,
                        onClick = vm::askErase,
                        block = true,
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
                when (ui.status) {
                    EraseStatus.DONE -> W10MNotice(stringResource(R.string.privacy_erase_done), W10MNoticeTone.Success)
                    EraseStatus.FAILED -> W10MNotice(stringResource(R.string.privacy_erase_error), W10MNoticeTone.Danger)
                    else -> Unit
                }
                SectionGap()
            }
        }
        W10MDialog(
            visible = ui.confirming,
            onDismiss = vm::cancelErase,
            title = stringResource(R.string.privacy_erase_title),
            message = stringResource(R.string.privacy_erase_message),
            buttons = listOf(
                W10MDialogButton(stringResource(R.string.privacy_erase_cancel), {}, W10MDialogButtonStyle.Cancel),
                W10MDialogButton(stringResource(R.string.privacy_erase_confirm), vm::confirmErase, W10MDialogButtonStyle.Destructive),
            ),
        )
    }
}

@Composable
private fun TextSection(title: Int, paragraphs: List<Int>) {
    W10MSection(title = stringResource(title)) {
        paragraphs.forEach {
            W10MText(
                text = stringResource(it),
                variant = W10MType::body,
                tone = W10MTextTone.Muted,
                modifier = Modifier.padding(bottom = W10MSpacing.s),
            )
        }
    }
}
