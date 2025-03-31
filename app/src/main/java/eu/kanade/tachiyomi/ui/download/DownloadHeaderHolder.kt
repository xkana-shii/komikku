package eu.kanade.tachiyomi.ui.download

 import android.annotation.SuppressLint
 import android.view.View
 import androidx.recyclerview.widget.ItemTouchHelper
 import eu.davidea.flexibleadapter.FlexibleAdapter
 import eu.davidea.viewholders.ExpandableViewHolder
 import eu.kanade.tachiyomi.databinding.DownloadHeaderBinding
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.Text
 import androidx.compose.runtime.Composable
 import androidx.compose.ui.platform.ComposeView
 import androidx.compose.ui.unit.sp
 import androidx.compose.foundation.layout.Row
 import androidx.compose.foundation.layout.Spacer
 import androidx.compose.foundation.layout.width
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.unit.dp

 class DownloadHeaderHolder(view: View, adapter: FlexibleAdapter<*>) : ExpandableViewHolder(view, adapter) {

     private val binding = DownloadHeaderBinding.bind(view)

     @SuppressLint("SetTextI18n")
     fun bind(item: DownloadHeaderItem) {
         setDragHandleView(binding.reorder)
         binding.title.apply {
             setContent {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Text(text = item.name)
                     Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                     Pill(
                         text = "$${item.size}",
                         color = MaterialTheme.colorScheme.primary,
                         contentColor = MaterialTheme.colorScheme.onPrimary,
                         fontSize = 14.sp,
                     )
                 }
             }
         }
     }

     override fun onActionStateChanged(position: Int, actionState: Int) {
         super.onActionStateChanged(position, actionState)
         if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
             binding.container.isDragged = true
             mAdapter.collapseAll()
         }
     }

     override fun onItemReleased(position: Int) {
         super.onItemReleased(position)
         binding.container.isDragged = false
         mAdapter.expandAll()
         (mAdapter as DownloadAdapter).downloadItemListener.onItemReleased(position)
     }
 }

 @Composable
 fun Pill(text: String, color: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color, fontSize: androidx.compose.ui.unit.TextUnit) {
     androidx.compose.material3.Surface(
         shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
         color = color,
         contentColor = contentColor
     ) {
         androidx.compose.foundation.layout.padding(horizontal = 8.dp, vertical = 4.dp) {
             Text(text = text, fontSize = fontSize)
         }
     }
 }
