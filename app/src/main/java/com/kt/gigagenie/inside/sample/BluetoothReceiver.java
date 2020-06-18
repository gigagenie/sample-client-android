/*
 * Copyright 2020 KT AI Lab.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.kt.gigagenie.inside.sample;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.kt.gigagenie.inside.api.InsideListener;

public class BluetoothReceiver extends BroadcastReceiver {
    private static final String TAG = BluetoothReceiver.class.getSimpleName();
    private InsideListener mListener;

    public BluetoothReceiver(InsideListener insideListener) {
        mListener = insideListener;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter != null
                && adapter.isEnabled()
                && BluetoothProfile.STATE_CONNECTED == adapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
            //Logger.i(TAG + " BluetoothAdapter : STATE_CONNECTED");
            //sendEvent(Inside.EVENT_BLUETOOTH_CONNECTED, null);
        } else {
            //Logger.i(TAG + " BluetoothAdapter : STATE_DISCONNECTED");
            //sendEvent(Inside.EVENT_BLUETOOTH_DISCONNECTED, null);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if(state == BluetoothA2dp.STATE_CONNECTING) {
                //sendEvent(Inside.EVENT_BLUETOOTH_CONNECTED, null);
            } else if(state == BluetoothA2dp.STATE_CONNECTED) {
                //sendEvent(Inside.EVENT_BLUETOOTH_CONNECTED, null);
            } else if(state == BluetoothA2dp.STATE_DISCONNECTED) {
                //sendEvent(Inside.EVENT_BLUETOOTH_DISCONNECTED, null);
            } else if(state == BluetoothA2dp.STATE_DISCONNECTING) {
                //sendEvent(Inside.EVENT_BLUETOOTH_NOT_PLAYING, null);
            }
        }
        /*else if (BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if(state == BluetoothA2dp.STATE_PLAYING) {
                //Logger.i(TAG + " ACTION_PLAYING_STATE_CHANGED : STATE_PLAYING");
                sendEvent(Inside.EVENT_BLUETOOTH_PLAYING, null);
            } else if(state == BluetoothA2dp.STATE_NOT_PLAYING) {
                sendEvent(Inside.EVENT_BLUETOOTH_NOT_PLAYING, null);
            }
        }*/
    }
    private void sendEvent(int evt, String opt) {
        if(mListener != null) mListener.agent_onEvent(evt, opt);
    }
}
