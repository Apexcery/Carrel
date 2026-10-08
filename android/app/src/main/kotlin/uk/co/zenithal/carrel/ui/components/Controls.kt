package uk.co.zenithal.carrel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.ui.theme.Carrel

/** The website's .primary-button: accent-filled (or `color`, e.g. for deleting), mono capitals. Faded while disabled or busy. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Carrel.colors.accent) {
    val colors = Carrel.colors
    Text(
        text = text.uppercase(),
        style = Carrel.type.monoMedium.copy(letterSpacing = 0.08.em),
        color = colors.onAccent,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.6f)
            .background(color, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The website's .secondary-button: accent-outlined mono capitals, for an action that isn't the page's main one. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Carrel.colors
    Text(
        text = text.uppercase(),
        style = Carrel.type.monoMedium.copy(letterSpacing = 0.08.em),
        color = colors.accent,
        modifier = modifier
            .border(1.dp, colors.accent, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The website's .link-button: underlined mono text for lesser actions. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Carrel.colors.inkSoft) {
    Text(
        text = text,
        style = Carrel.type.mono.copy(textDecoration = TextDecoration.Underline),
        color = color,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** Small accent capitals above a title, as on the website. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
        color = Carrel.colors.accent,
        modifier = modifier,
    )
}

/** A mono section label with a rule under it, like the website's .section-title. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Carrel.colors.inkSoft) {
    val rule = Carrel.colors.rule
    Text(
        text = text.uppercase(),
        style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(bottom = 10.dp),
    )
}

/**
 * A labelled text field as on the website's forms: a mono label above, then the text on a rule that turns accent and
 * thicker while focused.
 */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** What the keyboard's Done key does, e.g. submitting the form. */
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = Carrel.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = colors.inkSoft)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = Carrel.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val width = (if (focused) 2 else 1).dp.toPx()
                    val y = size.height - width / 2
                    drawLine(if (focused) colors.accent else colors.ruleStrong, Offset(0f, y), Offset(size.width, y), width)
                }
                .padding(vertical = 6.dp),
        )
    }
}

enum class Tone { Neutral, Ok, Error }

/** A message under a form: soft ink, ink when something is fine, accent for a problem (as the website does). */
@Composable
fun FormMessage(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val colors = Carrel.colors
    Text(
        text = text,
        style = Carrel.type.body,
        color = when (tone) {
            Tone.Neutral -> colors.inkSoft
            Tone.Ok -> colors.ink
            Tone.Error -> colors.accent
        },
        modifier = modifier,
    )
}

/** A checkbox in Carrel's colours, labelled like the fields. */
@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Carrel.colors
    Row(
        modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.ruleStrong,
                checkmarkColor = colors.onAccent,
            ),
        )
        Text(label.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = colors.inkSoft)
    }
}

/** Vertical space in the rhythm of the website's margins. */
@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))
