/*
 * Copyright (c) 2016 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.sdk.android.core.auth;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Pair;
import com.uber.sdk.android.core.BuildConfig;
import com.uber.sdk.android.core.Deeplink;
import com.uber.sdk.android.core.SupportedAppType;
import com.uber.sdk.android.core.utils.AppProtocol;
import com.uber.sdk.core.auth.Scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.uber.sdk.android.core.SupportedAppType.UBER;
import static com.uber.sdk.android.core.SupportedAppType.UBER_EATS;
import static com.uber.sdk.android.core.UberSdk.UBER_SDK_LOG_TAG;
import static com.uber.sdk.android.core.utils.Preconditions.checkNotEmpty;
import static com.uber.sdk.android.core.utils.Preconditions.checkNotNull;
import static com.uber.sdk.android.core.utils.Preconditions.checkState;

/**
 * Provides deep link to login into the installed Uber app. For a simpler integration see
 * {@link LoginButton} or {@link LoginManager#login(Activity)}.
 */
public class SsoDeeplink implements Deeplink {

    public static final int DEFAULT_REQUEST_CODE = LoginManager.REQUEST_CODE_LOGIN_DEFAULT;

    @VisibleForTesting
    static final int MIN_UBER_RIDES_VERSION_SUPPORTED = 31302;
    @VisibleForTesting
    static final int MIN_UBER_EATS_VERSION_SUPPORTED = 983;

    private static final String URI_QUERY_CLIENT_ID = "client_id";
    private static final String URI_QUERY_SCOPE = "scope";
    private static final String URI_QUERY_PLATFORM = "sdk";
    private static final String URI_QUERY_SDK_VERSION = "sdk_version";
    private static final String URI_HOST = "connect";

    private final Activity activity;
    private final AppProtocol appProtocol;
    private final String clientId;
    private final Collection<Scope> requestedScopes;
    private final Collection<String> requestedCustomScopes;
    private final int requestCode;

    private SsoDeeplink(
            @NonNull Activity activity,
            @NonNull AppProtocol appProtocol,
            @NonNull String clientId,
            @NonNull Collection<Scope> requestedScopes,
            @NonNull Collection<String> requestedCustomScopes,
            int requestCode) {
        this.activity = activity;
        this.appProtocol = appProtocol;
        this.clientId = clientId;
        this.requestCode = requestCode;
        this.requestedScopes = requestedScopes;
        this.requestedCustomScopes = requestedCustomScopes;
    }

    /**
     * Start {@link Activity#startActivityForResult(Intent, int)} with the right configurations. Use {@link Builder}
     * to instantiate the object.
     *
     * @throws IllegalStateException if compatible Uber app is not installed. Use {@link #isSupported()} to check.
     */
    @Override
    public void execute() {
        checkState(isSupported(), "Single sign on is not supported on the device. " +
                "Please install or update to the latest version of Uber app.");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        final Uri deepLinkUri = createSsoUri();
        intent.setData(deepLinkUri);

        List<PackageInfo> validatedPackages = new ArrayList<>();
        validatedPackages.addAll(appProtocol.getInstalledPackages(activity, UBER, MIN_UBER_RIDES_VERSION_SUPPORTED));
        validatedPackages.addAll(appProtocol.getInstalledPackages(activity, UBER_EATS, MIN_UBER_EATS_VERSION_SUPPORTED));

        if(!validatedPackages.isEmpty()) {
            intent.setPackage(validatedPackages.get(0).packageName);
        }
        activity.startActivityForResult(intent, requestCode);
    }

    private Uri createSsoUri() {
        String scopes = AuthUtils.scopeCollectionToString(requestedScopes);
        if (!requestedCustomScopes.isEmpty()) {
            scopes = AuthUtils.mergeScopeStrings(scopes,
                    AuthUtils.customScopeCollectionToString(requestedCustomScopes));
        }
        return new Uri.Builder().scheme(Deeplink.DEEPLINK_SCHEME)
                .authority(URI_HOST)
                .appendQueryParameter(URI_QUERY_CLIENT_ID, clientId)
                .appendQueryParameter(URI_QUERY_SCOPE, scopes)
                .appendQueryParameter(URI_QUERY_PLATFORM, AppProtocol.PLATFORM)
                .appendQueryParameter(URI_QUERY_SDK_VERSION, BuildConfig.VERSION_NAME)
                .build();
    }

    /**
     * Check if SSO deep linking is supported in this device.
     *
     * @return true if package name and minimum version conditions are met.
     */
    @Override
    public boolean isSupported() {
        return appProtocol.isInstalled(activity, UBER, MIN_UBER_RIDES_VERSION_SUPPORTED)
            || appProtocol.isInstalled(activity, UBER_EATS, MIN_UBER_EATS_VERSION_SUPPORTED);
    }

    public static class Builder {

        private final Activity activity;
        private AppProtocol appProtocol;
        private String clientId;
        private Collection<Scope> requestedScopes;
        private Collection<String> requestedCustomScopes;
        private int requestCode = DEFAULT_REQUEST_CODE;

        public Builder(@NonNull Activity activity) {
            this.activity = activity;
        }

        public Builder clientId(@NonNull String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder scopes(@NonNull Scope... scopes) {
            this.requestedScopes = Arrays.asList(scopes);
            return this;
        }

        public Builder scopes(@NonNull Collection<Scope> scopes) {
            this.requestedScopes = scopes;
            return this;
        }

        public Builder customScopes(@NonNull Collection<String> customScopes) {
            this.requestedCustomScopes = customScopes;
            return this;
        }

        public Builder activityRequestCode(int requestCode) {
            this.requestCode = requestCode;
            return this;
        }

        @VisibleForTesting
        Builder appProtocol(@NonNull AppProtocol appProtocol) {
            this.appProtocol = appProtocol;
            return this;
        }

        public SsoDeeplink build() {
            checkNotNull(clientId, "Client Id must be set");

            checkNotEmpty(requestedScopes, "Scopes must be set.");

            if (requestedCustomScopes == null) {
                requestedCustomScopes = new ArrayList<>();
            }

            if (requestCode == DEFAULT_REQUEST_CODE) {
                Log.i(UBER_SDK_LOG_TAG, "Request code is not set, using default request code");
            }

            if (appProtocol == null) {
                appProtocol = new AppProtocol();
            }

            return new SsoDeeplink(activity,
                    appProtocol,
                    clientId,
                    requestedScopes,
                    requestedCustomScopes,
                    requestCode);
        }
    }
}
