/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.concavenp.nanodegree.sunshine;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * A {@link WearableListenerService} listening for {@link SunshineWatchFaceService} Weather messages
 * in order to update the weather icon {@link com.google.android.gms.wearable.DataItem} accordingly.
 */
public class SunshineWatchFaceListenerService extends WearableListenerService {

    private static final String TAG = "SunshineListenerService";

    private GoogleApiClient mGoogleApiClient;

    @Override // WearableListenerService
    public void onMessageReceived(MessageEvent messageEvent) {

        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (!messageEvent.getPath().equals(SunshineWatchFaceUtil.PATH_WITH_FEATURE)) {

            return;

        }
        byte[] rawData = messageEvent.getData();
        // It's allowed that the message carries only some of the keys used in the config DataItem
        // and skips the ones that we don't want to change.
        DataMap weatherKeysToOverwrite = DataMap.fromByteArray(rawData);

        Log.d(TAG, "Received watch face weather message: " + weatherKeysToOverwrite);

        if (mGoogleApiClient == null) {

            mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();

        }

        if (!mGoogleApiClient.isConnected()) {

            ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {

                Log.e(TAG, "Failed to connect to GoogleApiClient.");
                return;

            }

        }

        SunshineWatchFaceUtil.overwriteKeysInWeatherDataMap(mGoogleApiClient, weatherKeysToOverwrite);
    }

}
