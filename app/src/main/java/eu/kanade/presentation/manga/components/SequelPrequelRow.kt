package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/** The manga's related titles from its trackers, as a row of compact cards. */
@Composable
fun SequelPrequelRow(
    entries: List<SequelPrequelEntry>,
    onClick: (SequelPrequelEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(KMR.strings.sequel_prequel),
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            items(entries, key = { "${it.relation}-${it.url}" }) { entry ->
                SequelPrequelCard(entry = entry, onClick = { onClick(entry) })
            }
        }
    }
}

@Composable
private fun SequelPrequelCard(entry: SequelPrequelEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = stringResource(entry.relation.label()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MangaCover.Book(
            data = entry.coverUrl,
            modifier = Modifier.width(96.dp),
            contentDescription = entry.title,
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.inLibrary) {
            Text(
                text = stringResource(MR.strings.in_library),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun SequelPrequelRelation.label(): StringResource = when (this) {
    SequelPrequelRelation.PREQUEL -> KMR.strings.relation_prequel
    SequelPrequelRelation.SEQUEL -> KMR.strings.relation_sequel
    SequelPrequelRelation.ADAPTATION -> KMR.strings.relation_adaptation
    SequelPrequelRelation.ALTERNATIVE -> KMR.strings.relation_alternative
    SequelPrequelRelation.SIDE_STORY -> KMR.strings.relation_side_story
    SequelPrequelRelation.SPIN_OFF -> KMR.strings.relation_spin_off
    SequelPrequelRelation.PARENT -> KMR.strings.relation_parent
    SequelPrequelRelation.SUMMARY -> KMR.strings.relation_summary
    SequelPrequelRelation.CAMEO -> KMR.strings.relation_cameo
    SequelPrequelRelation.CHARACTER_FOCUS -> KMR.strings.relation_character_focus
    SequelPrequelRelation.COMPILATION -> KMR.strings.relation_compilation
    SequelPrequelRelation.CONTAINS -> KMR.strings.relation_contains
    SequelPrequelRelation.CROSSOVER -> KMR.strings.relation_crossover
    SequelPrequelRelation.EXPANSION -> KMR.strings.relation_expansion
    SequelPrequelRelation.MAIN -> KMR.strings.relation_main
    SequelPrequelRelation.MAIN_STORY -> KMR.strings.relation_main_story
    SequelPrequelRelation.PARODY -> KMR.strings.relation_parody
    SequelPrequelRelation.REBOOT -> KMR.strings.relation_reboot
    SequelPrequelRelation.REMAKE -> KMR.strings.relation_remake
    SequelPrequelRelation.SAME_UNIVERSE -> KMR.strings.relation_same_universe
    SequelPrequelRelation.SERIES -> KMR.strings.relation_series
    SequelPrequelRelation.SOURCE -> KMR.strings.relation_source
    SequelPrequelRelation.UNCOLLECTED -> KMR.strings.relation_uncollected
    SequelPrequelRelation.DOUJINSHI -> KMR.strings.relation_doujinshi
    SequelPrequelRelation.COLORED -> KMR.strings.relation_colored
    SequelPrequelRelation.MONOCHROME -> KMR.strings.relation_monochrome
    SequelPrequelRelation.ALTERNATE_STORY -> KMR.strings.relation_alternate_story
    SequelPrequelRelation.ALTERNATE_VERSION -> KMR.strings.relation_alternate_version
    SequelPrequelRelation.PRESERIALIZATION -> KMR.strings.relation_preserialization
    SequelPrequelRelation.SERIALIZATION -> KMR.strings.relation_serialization
    SequelPrequelRelation.OTHER -> KMR.strings.relation_other
}
// KNS <--
