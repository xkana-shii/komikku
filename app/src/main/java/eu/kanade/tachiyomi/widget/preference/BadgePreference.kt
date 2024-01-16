package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible

class BadgePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {

    private var badgeNumber: Int = 0

    init {
        widgetLayoutResource = R.layout.pref_badge
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val badge = holder.itemView.findViewById<TextView>(R.id.badge)

        if (badgeNumber > 0) {
            badge.text = badgeNumber.toString()
            badge.visible()
        } else {
            badge.text = null
            badge.gone()
        }
    }

    fun setBadge(number: Int) {
        this.badgeNumber = number
        notifyChanged()
    }
}
