package eu.kanade.tachiyomi.ui.manga.track

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackSearchDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.visible
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.widget.itemClicks
import reactivecircus.flowbinding.android.widget.textChanges
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackSearchDialog : DialogController {

    private var binding: TrackSearchDialogBinding? = null

    private var adapter: TrackSearchAdapter? = null

    private var selectedItem: Track? = null

    private val service: TrackService

    private val trackController
        get() = targetController as TrackController

    constructor(target: TrackController, service: TrackService) : super(
        Bundle().apply {
            putInt(KEY_SERVICE, service.id)
        }
    ) {
        targetController = target
        this.service = service
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        service = Injekt.get<TrackManager>().getService(bundle.getInt(KEY_SERVICE))!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = TrackSearchDialogBinding.inflate(LayoutInflater.from(activity!!))

        val dialog = MaterialDialog(activity!!)
            .customView(view = binding!!.root)
            .positiveButton(android.R.string.ok) { onPositiveButtonClick() }
            .negativeButton(android.R.string.cancel)
            .neutralButton(R.string.action_remove) { onRemoveButtonClick() }

        onViewCreated(dialog.view, savedViewState)

        return dialog
    }

    fun onViewCreated(view: View, savedState: Bundle?) {
        // Create adapter
        val adapter = TrackSearchAdapter(view.context)
        this.adapter = adapter
        binding!!.trackSearchList.adapter = adapter

        // Set listeners
        selectedItem = null

        binding!!.trackSearchList.itemClicks()
            .onEach { position ->
                selectedItem = adapter.getItem(position)
            }
            .launchIn(trackController.scope)

        // Do an initial search based on the manga's title
        if (savedState == null) {
            val title = trackController.presenter.manga.title
            binding!!.trackSearch.append(title)
            search(title)
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        binding = null
        adapter = null
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        binding!!.trackSearch.textChanges()
            .debounce(TimeUnit.SECONDS.toMillis(1))
            .map { it.toString() }
            .filter { it.isNotBlank() }
            .onEach { search(it) }
            .launchIn(trackController.scope)
    }

    private fun search(query: String) {
        val binding = binding ?: return
        binding.progress.visible()
        binding.trackSearchList.invisible()
        trackController.presenter.search(query, service)
    }

    fun onSearchResults(results: List<TrackSearch>) {
        selectedItem = null
        val binding = binding ?: return
        binding.progress.invisible()
        binding.trackSearchList.visible()
        adapter?.setItems(results)
    }

    fun onSearchResultsError() {
        val binding = binding ?: return
        binding.progress.visible()
        binding.trackSearchList.invisible()
        adapter?.setItems(emptyList())
    }

    private fun onPositiveButtonClick() {
        trackController.presenter.registerTracking(selectedItem, service)
    }

    private fun onRemoveButtonClick() {
        trackController.presenter.unregisterTracking(service)
    }

    private companion object {
        const val KEY_SERVICE = "service_id"
    }
}
