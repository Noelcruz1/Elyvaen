package com.mardous.booming.ui.screen.deezer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mardous.booming.R
import com.mardous.booming.ui.theme.BoomingMusicTheme

class DownloadsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BoomingMusicTheme {
                    DownloadsScreen(
                        onBack = { findNavController().navigateUp() },
                        onOpenDeezer = { findNavController().navigate(R.id.nav_deezer) }
                    )
                }
            }
        }
    }
}
