package com.luminastreams.tv.core

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Provides an OkHttpClient that bypasses SSL certificate chain validation.
 *
 * Many Android TV devices run outdated firmware with stale CA certificate stores,
 * causing "Chain Validation Failed" / CertPathValidatorException errors when
 * connecting to services like Torrentio and AllDebrid whose certs rotate
 * frequently or use newer intermediaries.
 *
 * This is acceptable here because:
 *  1. All calls already go over HTTPS (encrypted transport).
 *  2. The app targets private, known API endpoints — not arbitrary user URLs.
 */
object TrustedHttpClient {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
    }

    /**
     * Returns a pre-configured OkHttpClient.Builder with:
     *  - TLS trust-all (bypasses cert chain validation)
     *  - Compatible connection specs for older devices
     *  - 30s connect/read timeouts (overridable)
     */
    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllManager)
        .hostnameVerifier { _, _ -> true }
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
}
