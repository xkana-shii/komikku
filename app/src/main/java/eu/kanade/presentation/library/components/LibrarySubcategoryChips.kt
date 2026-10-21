package eu.kanade.presentation.library.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.library.SubcategoryNavigation
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/**
 * Subcategory navigation of a top-level library tab: "All" (the whole tab), the selected path, and
 * the children of the deepest selected subcategory. Tapping a selected chip goes back up to it.
 */
@Composable
fun LibrarySubcategoryChips(
    navigation: SubcategoryNavigation,
    onSelect: (rootId: Long, subcategoryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        val rootId = navigation.root.id
        FilterChip(
            selected = navigation.path.isEmpty(),
            onClick = { onSelect(rootId, null) },
            label = { Text(stringResource(MR.strings.all)) },
        )
        navigation.path.forEach { category ->
            FilterChip(
                selected = true,
                onClick = { onSelect(rootId, category.id) },
                label = { Text(category.name) },
            )
        }
        navigation.children.forEach { category ->
            FilterChip(
                selected = false,
                onClick = { onSelect(rootId, category.id) },
                label = { Text(category.name) },
            )
        }
    }
}
// KNS <--
