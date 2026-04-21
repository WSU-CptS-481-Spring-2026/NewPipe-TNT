package org.schabi.newpipe.settings.notifications

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.databinding.FragmentChannelsNotificationsBinding
import org.schabi.newpipe.local.subscription.SubscriptionManager

/**
 * [NotificationModeConfigFragment] is a settings fragment
 * which allows changing the [NotificationMode] of all subscribed channels.
 * The [NotificationMode] can either be changed one by one or toggled for all channels.
 */
class NotificationModeConfigFragment : Fragment() {
    private var _binding: FragmentChannelsNotificationsBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val disposables = CompositeDisposable()
    private var loader: Disposable? = null
    private lateinit var adapter: NotificationModeConfigAdapter
    private lateinit var subscriptionManager: SubscriptionManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationModeConfigAdapter { position, mode ->
            // Notification mode has been changed via the UI.
            // Now change it in the database.
            updateNotificationMode(adapter.currentList[position], mode)
        }
        binding.recyclerView.adapter = adapter

        setupMenu()

        loader?.dispose()
        loader = subscriptionManager.subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(adapter::update)
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_notifications_channels, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_toggle_all -> {
                            toggleAll()
                            true
                        }

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    override fun onDestroyView() {
        loader?.dispose()
        loader = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun toggleAll() {
        val mode = adapter.currentList.firstOrNull()?.notificationMode ?: return
        val newMode = when (mode) {
            NotificationMode.DISABLED -> NotificationMode.ENABLED
            else -> NotificationMode.DISABLED
        }
        adapter.currentList.forEach { updateNotificationMode(it, newMode) }
    }

    private fun updateNotificationMode(item: SubscriptionItem, @NotificationMode mode: Int) {
        disposables.add(
            subscriptionManager.updateNotificationMode(item.serviceId, item.url, mode)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }
}
