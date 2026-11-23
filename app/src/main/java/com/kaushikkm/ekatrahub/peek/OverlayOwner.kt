package com.kaushikkm.ekatrahub.peek

import android.app.Application
import android.view.View
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose normally runs inside an Activity, which provides a lifecycle, a place to keep
 * ViewModels, and saved state. The Peek panel lives in a floating window owned by a Service,
 * which has none of those - so this class provides all three for the overlay windows.
 */
internal class OverlayOwner(private val app: Application) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app)
        }

    fun create() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }

    fun attachTo(root: View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
    }
}
