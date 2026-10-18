package eu.kanade.presentation.manga.components

import androidx.compose.ui.graphics.Color
import tachiyomi.domain.chapter.model.BookmarkColor

// KMK -->
/** Fixed color palette shared by bookmark indicators and selection controls. */
fun BookmarkColor.composeColor(): Color? = when (this) {
    BookmarkColor.NONE -> null
    BookmarkColor.RED -> Color(0xFFD32F2F)
    BookmarkColor.ORANGE -> Color(0xFFE64A19)
    BookmarkColor.YELLOW -> Color(0xFFF9A825)
    BookmarkColor.GREEN -> Color(0xFF2E7D32)
    BookmarkColor.BLUE -> Color(0xFF1565C0)
    BookmarkColor.PURPLE -> Color(0xFF6A1B9A)
}
// KMK <--
