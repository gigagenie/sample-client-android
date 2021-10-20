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


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.annotation.NonNull;
import android.util.Log;

import com.kt.gigagenie.inside.util.Logger;


public class VoiceRecorder {

    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000, 11025, 22050, 44100};

    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int AMPLITUDE_THRESHOLD = 1500;
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;
    private static boolean mIsRecording = false;

    public static abstract class Callback {
        public void onVoiceStart() { }
        public void onVoice(short[] data, int size) { }
        public void onVoiceEnd() { }
    }

    private final Callback mCallback;

    private AudioRecord mAudioRecord;

    private Thread mThread;

    private short[] mBuffer;

    private final Object mLock = new Object();

    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    private long mVoiceStartedMillis;

    public VoiceRecorder(@NonNull Callback callback) {
        mCallback = callback;
    }

    public void start() {
        // Stop recording if it is currently ongoing.
        stop();
        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            throw new RuntimeException("Cannot instantiate VoiceRecorder");
        }
        // Start recording.
        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException e) {
            Logger.d("VoiceRecorder start Exception : " + e.toString());
        }

        // Start processing the captured audio.
        mThread = new Thread(new ProcessVoice());
        mThread.start();
        mIsRecording = true;
    }

    public void stop() {
        mIsRecording = false;
        synchronized (mLock) {
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if (mAudioRecord != null) {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
            mBuffer = null;
        }
    }

    public void dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }

    /**
     * sampleRate 취득
      * @return
     */
    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }

    public int isWakeupVoiceRecorder() {
        if (mAudioRecord != null) {
            return mAudioRecord.getRecordingState();
        }
        return 0;
    }

    private AudioRecord createAudioRecord() {
        if(mAudioRecord != null) {
            Log.d("DEBUG", "createAudioRecord : mAudioRecord != null");
            mAudioRecord.release();
            return mAudioRecord;
        }
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate, CHANNEL, ENCODING, sizeInBytes);
            Log.d("DEBUG", "sampleRate: " + sampleRate);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d("DEBUG", "createAudioRecord : STATE_INITIALIZED");
                mBuffer = new short[sizeInBytes];
                return audioRecord;
            } else {
                Log.d("DEBUG", "createAudioRecord : else");
                audioRecord.release();
            }
        }
        return null;
    }


    private class ProcessVoice implements Runnable {
        @Override
        public void run() {
            while (mIsRecording) {
                synchronized (mLock) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    final int size = mAudioRecord.read(mBuffer, 0, mBuffer.length);
                    final long now = System.currentTimeMillis();
                    if (isHearingVoice(mBuffer, size)) {
                        if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                            mVoiceStartedMillis = now;
                            mCallback.onVoiceStart();
                        }
                        mCallback.onVoice(mBuffer, size);
                        mLastVoiceHeardMillis = now;
                        if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                            //end();
                        }
                    } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                        if(mIsRecording) {
                            mCallback.onVoice(mBuffer, size);
                        }
                    }
                }
            }
        }
        private boolean isHearingVoice(short[] buffer, int size) {
            for (int i = 0; i < size - 1; i += 2) {
                // The buffer has LINEAR16 in little endian.
                int s = buffer[i + 1];
                if (s < 0) s *= -1;
                s <<= 8;
                s += Math.abs(buffer[i]);
                if (s > AMPLITUDE_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }
    }
}