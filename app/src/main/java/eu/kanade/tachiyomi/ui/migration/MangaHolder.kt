package eu.kanade.tachiyomi.ui.migration

import android.view.View
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.databinding.SourceListItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

class MangaHolder(
    view: View,
    adapter: FlexibleAdapter<*>
) : BaseFlexibleViewHolder(view, adapter) {

    val binding = SourceListItemBinding.bind(view)
    fun bind(item: MangaItem) {
        // Update the title of the manga.
        binding.title.text = item.manga.title

        // Create thumbnail onclick to simulate long click
        binding.thumbnail.setOnClickListener {
            // Simulate long click on this view to enter selection mode
            onLongClick(itemView)
        }

        // Update the cover.
        GlideApp.with(itemView.context).clear(binding.thumbnail)
        GlideApp.with(itemView.context)
            .load(item.manga.toMangaThumbnail())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .circleCrop()
            .dontAnimate()
            .into(binding.thumbnail)
    }
}
