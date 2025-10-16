package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.databinding.MigrationSourceItemBinding
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourceHolder(view: View, val adapter: MigrationSourceAdapter) :
    FlexibleViewHolder(view, adapter) {
    val binding = MigrationSourceItemBinding.bind(view)
    val uiPreferences = Injekt.get<UiPreferences>()
    init {
        setDragHandleView(binding.reorder)
    }

    fun bind(source: HttpSource, sourceEnabled: Boolean) {
        // Set capitalized title.
        val sourceName =
            // KMK -->
            source.getNameForMangaInfo(uiPreferences = uiPreferences)
        // KMK <--
        binding.title.text = sourceName
        // Update circle letter image.
        itemView.post {
            val icon = Injekt.get<ExtensionManager>().getAppIconForSource(source.id)
            if (icon != null) {
                binding.image.setImageDrawable(icon)
            }
        }

        if (sourceEnabled) {
            binding.title.alpha = 1.0f
            binding.image.alpha = 1.0f
            binding.title.paintFlags = binding.title.paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            binding.title.alpha = DISABLED_ALPHA
            binding.image.alpha = DISABLED_ALPHA
            binding.title.paintFlags = binding.title.paintFlags or STRIKE_THRU_TEXT_FLAG
        }
    }

    companion object {
        private const val DISABLED_ALPHA = 0.3f
    }
}
