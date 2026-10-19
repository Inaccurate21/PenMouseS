package pl.jojczak.penmouses.screen.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.jojczak.penmouses.core.common.utils.PrefKey
import pl.jojczak.penmouses.core.ui.theme.pad_s
import pl.jojczak.penmouses.core.ui.theme.pad_xl
import kotlin.math.round

internal fun LazyListScope.settingsSlider(
    @StringRes text: Int,
    @StringRes textOnLastValue: Int? = null,
    value: Float,
    prefKey: PrefKey<Float>,
    valueDisplay: (Float) -> String = { round(it).toInt().toString() },
    onPrefChanging: (PrefKey<Float>, Float) -> Unit = { _, _ -> },
    onPrefChanged: (PrefKey<Float>, Float) -> Unit = { _, _ -> },
) = item {
    Column(
        verticalArrangement = Arrangement.spacedBy(pad_s),
        modifier = Modifier.padding(pad_xl)
    ) {
        var sliderValue by remember(value) { mutableFloatStateOf(value) }
        Text(
            stringResource(
                textOnLastValue.takeIf { it != null && sliderValue == prefKey.range.endInclusive }
                    ?: text,
                valueDisplay(sliderValue)
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                val next = (sliderValue - prefKey.step).coerceIn(prefKey.range.start, prefKey.range.endInclusive)
                sliderValue = next
                onPrefChanging(prefKey, next)
                onPrefChanged(prefKey, next)
            }, enabled = sliderValue > prefKey.range.start) { Text("−") }
            Slider(
                value = sliderValue,
                onValueChange = {
                    val roundedValue = (round(it / prefKey.step)) * prefKey.step
                    sliderValue = roundedValue.coerceIn(prefKey.range.start, prefKey.range.endInclusive)
                    onPrefChanging(prefKey, sliderValue)
                },
                valueRange = prefKey.range,
                onValueChangeFinished = { onPrefChanged(prefKey, sliderValue) },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                val next = (sliderValue + prefKey.step).coerceIn(prefKey.range.start, prefKey.range.endInclusive)
                sliderValue = next
                onPrefChanging(prefKey, next)
                onPrefChanged(prefKey, next)
            }, enabled = sliderValue < prefKey.range.endInclusive) { Text("+") }
        }
        /* original slider removed */
        /* Slider(
            value = sliderValue,
            onValueChange = {
                val roundedValue = (round(it / prefKey.step)) * prefKey.step
                sliderValue = roundedValue
                onPrefChanging(prefKey, roundedValue)
            },
            valueRange = prefKey.range,
            onValueChangeFinished = { onPrefChanged(prefKey, sliderValue) },
        ) */
    }
}
