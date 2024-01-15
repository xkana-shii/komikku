package eu.kanade.tachiyomi.ui.migration

import android.view.View
import eu.kanade.tachiyomi.databinding.SourceMainControllerCardItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.util.view.gone
import io.github.mthli.slice.Slice

class SourceHolder(view: View, override val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter),
    SlicedHolder {

    private val binding = SourceMainControllerCardItemBinding.bind(view)

    override val slice = Slice(binding.card).apply {
        setColor(adapter.cardBackground)
    }

    override val viewToSlice: View
        get() = binding.card

    init {
        binding.sourceBrowse.gone()
        binding.sourceLatest.text = "All"
        binding.sourceLatest.setOnClickListener {
            adapter.allClickListener?.onAllClick(bindingAdapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        setCardEdges(item)

        // Set source name
        binding.title.text = source.name

        // Set source icon
        itemView.post {
            binding.image.setImageDrawable(source.icon())
        }
    }
}
