package eu.kanade.tachiyomi.ui.source

import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceMainControllerCardItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import io.github.mthli.slice.Slice
class SourceHolder(view: View, override val adapter: SourceAdapter, val showButtons: Boolean) :
    BaseFlexibleViewHolder(view, adapter),
    SlicedHolder {

    private val binding = SourceMainControllerCardItemBinding.bind(view)

    override val slice = Slice(binding.card).apply {
        setColor(adapter.cardBackground)
    }

    override val viewToSlice: View
        get() = binding.card

    init {
        binding.sourceBrowse.setOnClickListener {
            adapter.browseClickListener.onBrowseClick(bindingAdapterPosition)
        }

        binding.sourceLatest.setOnClickListener {
            adapter.latestClickListener.onLatestClick(bindingAdapterPosition)
        }

        if (!showButtons) {
            binding.sourceBrowse.gone()
            binding.sourceLatest.gone()
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        setCardEdges(item)

        // Set source name
        binding.title.text = source.name

        // Set source icon
        itemView.post {
            val icon = source.icon()
            when {
                icon != null -> binding.image.setImageDrawable(icon)
                item.source.id == LocalSource.ID -> binding.image.setImageResource(R.mipmap.ic_local_source)
            }
        }

        binding.sourceBrowse.setText(R.string.browse)
        if (source.supportsLatest && showButtons) {
            binding.sourceLatest.visible()
        } else {
            binding.sourceLatest.gone()
        }
    }
}
