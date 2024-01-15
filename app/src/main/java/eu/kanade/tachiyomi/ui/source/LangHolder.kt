package eu.kanade.tachiyomi.ui.source

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.databinding.SourceMainControllerCardBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper

class LangHolder(view: View, adapter: FlexibleAdapter<*>) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = SourceMainControllerCardBinding.bind(view)
    fun bind(item: LangItem) {
        binding.title.text = LocaleHelper.getSourceDisplayName(item.code, itemView.context)
    }
}
