package com.mardous.booming.ui.screen.deezer

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.mardous.booming.databinding.ActivityDeezerLoginBinding

/**
 * Official Deezer login page in a WebView.
 *
 * The password is typed on deezer.com and is never seen or stored by Elyvaen.
 * When Deezer sets the `arl` session cookie we return it to the caller, which
 * validates it against the private client before saving it.
 */
class DeezerLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeezerLoginBinding
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeezerLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progress.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progress.isVisible = false
                view?.postDelayed({ checkLoginCookie() }, 700)
            }
        }
        binding.webView.loadUrl(LOGIN_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        })
    }

    private fun checkLoginCookie() {
        if (completed) return
        CookieManager.getInstance().flush()
        val cookies = CookieManager.getInstance().getCookie(LOGIN_URL)
        val arl = cookies
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("arl=") }
            ?.substringAfter('=')
            ?.trim()
        if (!arl.isNullOrBlank()) {
            completed = true
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_ARL, arl)
            )
            finish()
        }
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ARL = "com.mardous.booming.extra.DEEZER_ARL"
        private const val LOGIN_URL = "https://www.deezer.com/login"
    }
}
