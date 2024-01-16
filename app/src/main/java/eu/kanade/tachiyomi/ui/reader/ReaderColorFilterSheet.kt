package eu.kanade.tachiyomi.ui.reader

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.ColorInt
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderColorFilterSheetBinding
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.widget.IgnoreFirstSpinnerListener
import eu.kanade.tachiyomi.widget.SimpleSeekBarListener
import kotlin.math.abs
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import uy.kohesive.injekt.injectLazy

/**
 * Color filter sheet to toggle custom filter and brightness overlay.
 */
class ReaderColorFilterSheet(private val activity: ReaderActivity) : BottomSheetDialog(activity) {

    private val preferences by injectLazy<PreferencesHelper>()

    private var sheetBehavior: BottomSheetBehavior<*>? = null

    private val binding = ReaderColorFilterSheetBinding.inflate(layoutInflater)

    init {
        // val view = activity.layoutInflater.inflate(R.layout.reader_color_filter_sheet, null)
        val view = binding.root
        setContentView(view)

        sheetBehavior = BottomSheetBehavior.from(view.parent as ViewGroup)

        preferences.colorFilter().asFlow()
            .onEach { setColorFilter(it, view) }
            .launchIn(activity.scope)

        preferences.colorFilterMode().asFlow()
            .onEach { setColorFilter(preferences.colorFilter().get(), view) }
            .launchIn(activity.scope)

        preferences.customBrightness().asFlow()
            .onEach { setCustomBrightness(it, view) }
            .launchIn(activity.scope)

        // Get color and update values
        val color = preferences.colorFilterValue().get()
        val brightness = preferences.customBrightnessValue().get()

        val argb = setValues(color, view)

        binding.includedReaderColorFilter!!
        // Set brightness value
        binding.includedReaderColorFilter.txtBrightnessSeekbarValue.text = brightness.toString()
        binding.includedReaderColorFilter.brightnessSeekbar.progress = brightness

        // Initialize seekBar progress
        binding.includedReaderColorFilter.seekbarColorFilterAlpha.progress = argb[0]
        binding.includedReaderColorFilter.seekbarColorFilterRed.progress = argb[1]
        binding.includedReaderColorFilter.seekbarColorFilterGreen.progress = argb[2]
        binding.includedReaderColorFilter.seekbarColorFilterBlue.progress = argb[3]

        // Set listeners
        binding.includedReaderColorFilter.switchColorFilter.isChecked = preferences.colorFilter().get()
        binding.includedReaderColorFilter.switchColorFilter.setOnCheckedChangeListener { _, isChecked ->
            preferences.colorFilter().set(isChecked)
        }

        binding.includedReaderColorFilter.customBrightness.isChecked = preferences.customBrightness().get()
        binding.includedReaderColorFilter.customBrightness.setOnCheckedChangeListener { _, isChecked ->
            preferences.customBrightness().set(isChecked)
        }

        binding.includedReaderColorFilter.colorFilterMode.onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
            preferences.colorFilterMode().set(position)
        }
        binding.includedReaderColorFilter.colorFilterMode.setSelection(preferences.colorFilterMode().get(), false)

        binding.includedReaderColorFilter.seekbarColorFilterAlpha.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    setColorValue(value, ALPHA_MASK, 24)
                }
            }
        })

        binding.includedReaderColorFilter.seekbarColorFilterRed.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    setColorValue(value, RED_MASK, 16)
                }
            }
        })

        binding.includedReaderColorFilter.seekbarColorFilterGreen.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    setColorValue(value, GREEN_MASK, 8)
                }
            }
        })

        binding.includedReaderColorFilter.seekbarColorFilterBlue.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    setColorValue(value, BLUE_MASK, 0)
                }
            }
        })

        binding.includedReaderColorFilter.brightnessSeekbar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    preferences.customBrightnessValue().set(value)
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior?.skipCollapsed = true
        sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * Set enabled status of seekBars belonging to color filter
     * @param enabled determines if seekBar gets enabled
     * @param view view of the dialog
     */
    private fun setColorFilterSeekBar(enabled: Boolean, view: View) = with(view) {
        binding.includedReaderColorFilter!!
        binding.includedReaderColorFilter.seekbarColorFilterRed.isEnabled = enabled
        binding.includedReaderColorFilter.seekbarColorFilterGreen.isEnabled = enabled
        binding.includedReaderColorFilter.seekbarColorFilterBlue.isEnabled = enabled
        binding.includedReaderColorFilter.seekbarColorFilterAlpha.isEnabled = enabled
    }

    /**
     * Set enabled status of seekBars belonging to custom brightness
     * @param enabled value which determines if seekBar gets enabled
     * @param view view of the dialog
     */
    private fun setCustomBrightnessSeekBar(enabled: Boolean, view: View) = with(view) {
        binding.includedReaderColorFilter!!.brightnessSeekbar.isEnabled = enabled
    }

    /**
     * Set the text value's of color filter
     * @param color integer containing color information
     * @param view view of the dialog
     */
    fun setValues(color: Int, view: View): Array<Int> {
        val alpha = getAlphaFromColor(color)
        val red = getRedFromColor(color)
        val green = getGreenFromColor(color)
        val blue = getBlueFromColor(color)

        // Initialize values
        binding.includedReaderColorFilter!!
        binding.includedReaderColorFilter.txtColorFilterAlphaValue.text = alpha.toString()
        binding.includedReaderColorFilter.txtColorFilterRedValue.text = red.toString()
        binding.includedReaderColorFilter.txtColorFilterGreenValue.text = green.toString()
        binding.includedReaderColorFilter.txtColorFilterBlueValue.text = blue.toString()

        return arrayOf(alpha, red, green, blue)
    }

    /**
     * Manages the custom brightness value subscription
     * @param enabled determines if the subscription get (un)subscribed
     * @param view view of the dialog
     */
    private fun setCustomBrightness(enabled: Boolean, view: View) {
        if (enabled) {
            preferences.customBrightnessValue().asFlow()
                .sample(100)
                .onEach { setCustomBrightnessValue(it, view) }
                .launchIn(activity.scope)
        } else {
            setCustomBrightnessValue(0, view, true)
        }
        setCustomBrightnessSeekBar(enabled, view)
    }

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    private fun setCustomBrightnessValue(value: Int, view: View, isDisabled: Boolean = false) = with(view) {
        // Set black overlay visibility.
        if (value < 0) {
            binding.brightnessOverlay.visible()
            val alpha = (abs(value) * 2.56).toInt()
            binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        } else {
            binding.brightnessOverlay.gone()
        }

        if (!isDisabled) {
            binding.includedReaderColorFilter!!.txtBrightnessSeekbarValue.text = value.toString()
        }
    }

    /**
     * Manages the color filter value subscription
     * @param enabled determines if the subscription get (un)subscribed
     * @param view view of the dialog
     */
    private fun setColorFilter(enabled: Boolean, view: View) {
        if (enabled) {
            preferences.colorFilterValue().asFlow()
                .sample(100)
                .onEach { setColorFilterValue(it, view) }
                .launchIn(activity.scope)
        } else {
            binding.colorOverlay.gone()
        }
        setColorFilterSeekBar(enabled, view)
    }

    /**
     * Sets the color filter overlay of the screen. Determined by HEX of integer
     * @param color hex of color.
     * @param view view of the dialog
     */
    private fun setColorFilterValue(@ColorInt color: Int, view: View) = with(view) {
        binding.colorOverlay.visible()
        binding.colorOverlay.setFilterColor(color, preferences.colorFilterMode().get())
        setValues(color, view)
    }

    /**
     * Updates the color value in preference
     * @param color value of color range [0,255]
     * @param mask contains hex mask of chosen color
     * @param bitShift amounts of bits that gets shifted to receive value
     */
    fun setColorValue(color: Int, mask: Long, bitShift: Int) {
        val currentColor = preferences.colorFilterValue().get()
        val updatedColor = (color shl bitShift) or (currentColor and mask.inv().toInt())
        preferences.colorFilterValue().set(updatedColor)
    }

    /**
     * Returns the alpha value from the Color Hex
     * @param color color hex as int
     * @return alpha of color
     */
    fun getAlphaFromColor(color: Int): Int {
        return color shr 24 and 0xFF
    }

    /**
     * Returns the red value from the Color Hex
     * @param color color hex as int
     * @return red of color
     */
    fun getRedFromColor(color: Int): Int {
        return color shr 16 and 0xFF
    }

    /**
     * Returns the green value from the Color Hex
     * @param color color hex as int
     * @return green of color
     */
    fun getGreenFromColor(color: Int): Int {
        return color shr 8 and 0xFF
    }

    /**
     * Returns the blue value from the Color Hex
     * @param color color hex as int
     * @return blue of color
     */
    fun getBlueFromColor(color: Int): Int {
        return color and 0xFF
    }

    private companion object {
        /** Integer mask of alpha value **/
        const val ALPHA_MASK: Long = 0xFF000000

        /** Integer mask of red value **/
        const val RED_MASK: Long = 0x00FF0000

        /** Integer mask of green value **/
        const val GREEN_MASK: Long = 0x0000FF00

        /** Integer mask of blue value **/
        const val BLUE_MASK: Long = 0x000000FF
    }
}
