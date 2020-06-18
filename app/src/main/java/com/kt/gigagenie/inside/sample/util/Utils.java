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

package com.kt.gigagenie.inside.sample.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

public class Utils {
    private Context mContext;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private AudioManager mAudioManager;
    private Toast mToast;

    public Utils(Context context) {
        mContext = context;
        if(mSharedPreferences == null)
            mSharedPreferences = context.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        if(mAudioManager == null)
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }
    /**
     * Toast 를 보여준다.
     * **/
    public void setToast(String msg) {
        if(mToast != null) {
            try { mToast.cancel(); } catch (Exception e) { }
            mToast = null;
        }
        mToast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        try {
            TextView v = mToast.getView().findViewById(android.R.id.message);
            if(v != null) v.setGravity(Gravity.CENTER);
        } catch (Exception e) { }
        mToast.show();
    }
    public void setSharedPreferences(String key, String value) {
        mEditor = mSharedPreferences.edit();
        mEditor.putString(key, value);
        mEditor.apply();
        mEditor = null;
    }
    public String getSharedPreferences(String key, String defValue) {
        return mSharedPreferences.getString(key, defValue);
    }
    public void removeSharedPreferences(String key) {
        mEditor = mSharedPreferences.edit();
        mEditor.remove(key);
        mEditor.apply();
        mEditor = null;
    }
    public int getCurVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    public int getMaxVolume() {
        return mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }
    public int calVolume(boolean isVolumeUp) {
        int curVolume = getCurVolume();
        int maxVolume = getMaxVolume();
        if(isVolumeUp) {
            if(curVolume < maxVolume) return (curVolume+1);
            else return maxVolume;
        } else {
            if(curVolume > 0) return (curVolume-1);
            else return 0;
        }
    }
    public void setStreamMute(boolean mute) {
        mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
    }
    public void setStreamVolume(int volume) {
        try { mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI); }
        catch (Exception e) { }
    }
    public void reset() {
        mContext = null;
        mAudioManager = null;
        mSharedPreferences = null;
        mEditor = null;
    }
}
