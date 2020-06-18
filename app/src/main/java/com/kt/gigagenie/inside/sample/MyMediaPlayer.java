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

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.kt.gigagenie.inside.api.Inside;
import com.kt.gigagenie.inside.network.grpc.model.Payload;
import com.kt.gigagenie.inside.network.grpc.model.RsResult;
import com.kt.gigagenie.inside.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class MyMediaPlayer {
    private static final String TAG = MyMediaPlayer.class.getSimpleName();
    private MediaPlayer mediaPlayer;
    private boolean isPrepared;
    private Inside insideSDK;
    private int channel;
    private Payload payload;
    private AudioTrack mAudioTrack;
    private int mBufferSize;
    private int mSampleRate = 16000;
    private MainActivity.MediaPlayerHelper parent;

    private ByteArrayOutputStream outputStream = null;
    private ByteArrayInputStream is = null;
    private DataInputStream dis = null;
    private int mPlayType = -1; // 0 url, 1 voice 2 stream
    private ArrayList<PlayPcmThread> mPcmThreadArray = new ArrayList<>();

    MyMediaPlayer(Inside insideSDK, int channel, MainActivity.MediaPlayerHelper parent) {
        this.insideSDK = insideSDK;
        this.channel = channel;
        this.parent = parent;
    }
    /**
     * PCM STREAM PLAYER 생성
     * **/
    private void makePcmStreamPlayer() {
        int mChannelCount = AudioFormat.CHANNEL_OUT_MONO;
        int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
        mBufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                mSampleRate,
                mChannelCount,
                mAudioFormat,
                mBufferSize,
                AudioTrack.MODE_STREAM); // AudioTrack 생성
        mAudioTrack.play();
    }
    /**
     * PCM STREAM 재생
     * **/
    synchronized private void playPcmStream(RsResult r, PlayPcmRunnable runnable) {
        if(runnable.isStop) return;

        // 0시작, 2끝
        int end = r.end;
        try {
            // init player
            if(mAudioTrack == null) makePcmStreamPlayer();

            // 미디어 시작처리
            if(end == 0) updateMediaStatus(r.payload, Inside.MEDIA_STARTED, 0);

            outputStream = new ByteArrayOutputStream();
            outputStream.write(android.util.Base64.decode(r.mediastream, android.util.Base64.DEFAULT));

            is = new ByteArrayInputStream(outputStream.toByteArray());
            dis = new DataInputStream(is);

            byte[] data = new byte[mBufferSize];
            int count;
            while((count = dis.read(data, 0, mBufferSize)) > -1) {
                if(runnable.isStop) return;
                mAudioTrack.write(data, 0, count);
            }

            // 미디어 종료처리
            if(end == 2) {
                float time = (float) mAudioTrack.getPlaybackHeadPosition() / mSampleRate * 1000;
                updateMediaStatus(r.payload, Inside.MEDIA_COMPLETE, (int) time);

                is.close();
                dis.close();

                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;

                if(parent != null) parent.stopAndRunActOnOther();
            }
        } catch (Exception e) {
            Logger.i(TAG + " playPcmStream Exception : " + e.toString());
        } finally {
            if(is != null) {
                try { is.close(); is=null; } catch (Exception e1) { }
            }
            if(dis != null) {
                try { dis.close(); dis=null; } catch (Exception e1) { }
            }
            if(outputStream != null) {
                try { outputStream.close(); outputStream = null; } catch (Exception e) { }
            }
            if(end == 2 && mAudioTrack != null) {
                // 방어코드, 위에서 에러났을 때 대비해서 여기서 초기화 시도.
                try { mAudioTrack.release(); } catch (Exception e1) { }
                mAudioTrack = null;
            }
        }
    }
    /**
     * 이미 들어온 PCM Data 를 초기화시킨다.
     * **/
    private void resetPcmThreadArray() {
        try {
            for(PlayPcmThread t : mPcmThreadArray) {
                t.getRunnable().isStop = true;
                t.interrupt();
                t = null;
            }
            mPcmThreadArray = new ArrayList<>();
        } catch (Exception e) { }

    }

    class PlayPcmThread extends Thread {
        PlayPcmRunnable runnable;
        PlayPcmThread(PlayPcmRunnable playPcmRunnable) {
            runnable = playPcmRunnable;
        }
        PlayPcmRunnable getRunnable() {
            return runnable;
        }
        @Override
        public void run() {
            runnable.run();
        }
    }
    class PlayPcmRunnable implements Runnable {
        boolean isStop = false;
        RsResult r;
        PlayPcmRunnable(@NonNull RsResult r) {
            this.r = r;
        }
        @Override
        public void run() {
            if(isStop) return;
            if(r != null) playPcmStream(r, this);
        }
    }
    /**
     * 미디어를 재생시킬 PLAYER 를 init 한다. PCM_STREAM 제외
     * **/
    private MediaPlayer initPlayer(Payload p) {
        try {
            boolean isTimer = false;
            // 타이머 설정으로 온 데이터인지 체크한다.
            if(p.cmdOpt != null) {
                if (p.cmdOpt.metaInfo != null) {
                    if (mPlayType == 0 && p.cmdOpt.metaInfo.mesg != null && p.cmdOpt.metaInfo.mesg.equals("TimerEvent"))
                        isTimer = true;
                }
            }

            payload = p;

            MediaPlayer mediaPlayer = new MediaPlayer();
            if(isTimer) {
                // 타이머 경우 특정 벨소리를 재생한다.
                AssetFileDescriptor descriptor = insideSDK.mContext.getAssets().openFd("YOUR-BELL.mp3");//타이머에 사용할 벨소리 세팅
                mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                descriptor.close();
            } else {
                // 그 외엔 url 혹은 미디어스트림 재생한다.
                mediaPlayer.setDataSource(mPlayType == 0 ? p.cmdOpt.url : base64StringToFile(p.mediastream));
            }

            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setVolume(1, 1);
            mediaPlayer.setOnPreparedListener(mp -> {
                Logger.d(TAG + " playMedia onPrepared");
                isPrepared = true;
                start(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if(!isPrepared) return;
                isPrepared = false;

                Logger.d(TAG + " playMedia onCompletion");

                updateMediaStatus(p, Inside.MEDIA_COMPLETE, mp.getDuration());
                if(parent != null) parent.stopAndRunActOnOther();
            });
            mediaPlayer.setOnErrorListener((mp, i, i1) -> {
                Logger.d(TAG + " playMedia onError : " + i + ", " + i1);
                // 에러 났을 때도 stopAndRunActOnOther 실행
                if(parent != null) parent.stopAndRunActOnOther();
                return false;
            });
            mediaPlayer.setOnBufferingUpdateListener((mp, i) -> {
                Logger.d(TAG + " playMedia onBufferingUpdate : " + i);
            });
            return mediaPlayer;
        } catch (Exception e) {
            Logger.i(TAG + " initPlayer Exception : " + e.toString());
        }
        return null;
    }
    private void initAndPrepareAsync(Payload p, int playType) {
        mPlayType = playType;
        mediaPlayer = initPlayer(p);
        if(mediaPlayer != null) mediaPlayer.prepareAsync();
        else { /* 미디어 플레이어 생성 실패 */ }
    }
    void playUrl(Payload p) {
        release();
        // 지니뮤직, 라디오 등 url
        initAndPrepareAsync(p, 0);
    }
    void playWav(Payload p) {
        release();
        // wav, stream 등 raw data 플레이
        if(p.contentType != null && p.contentType.equals(Inside.CONTENT_TYPE_PCM)) {
            // pcmStrema 처리 - 현재 전달되는 case 없음
        } else {
            // voice 처리
            initAndPrepareAsync(p, 1);
        }
    }
    /**
     * 미디어 플레이어를 play 시킨다.
     * isFirstStart true : started, false : resumed
     * **/
    void start(boolean isFirstStart) {
        try {
            if(mPlayType != 2) {
                if(mediaPlayer != null && isPrepared) {
                    mediaPlayer.start();

                    // payload 가 존재한다면, 해당 채널 값등을 가지고 있다.
                    if(payload != null)
                        updateMediaStatus(payload,
                                isFirstStart ? Inside.MEDIA_STARTED : Inside.MEDIA_RESUMED,
                                isFirstStart ? 0 : mediaPlayer.getCurrentPosition());
                    // play, pause 등 아이콘을 바꾸기 위해 이벤트를 보내준다.
                    if(mPlayType == 0) {
                        //if(parent.isBlueToothConnected) insideSDK.agent_onEvent(Inside.EVENT_BLUETOOTH_PLAYING, null);
                        parent.notiPlay(true);
                    }
                }
            } else {
                if(mAudioTrack != null) mAudioTrack.play();
            }
        } catch (Exception e) {
            Logger.i(TAG + " start Exception : " + e.toString());
        }
    }
    /**
     * 미디어 플레이어를 pause 시킨다.
     * **/
    void pause() {
        try {
            if(mPlayType != 2) {
                if(mediaPlayer != null && isPrepared) {
                    mediaPlayer.pause();

                    // payload 가 존재한다면, 해당 채널 값등을 가지고 있다.
                    if(payload != null) updateMediaStatus(payload, Inside.MEDIA_PAUSED, mediaPlayer.getCurrentPosition());

                    // play, pause 등 아이콘을 바꾸기 위해 이벤트를 보내준다.
                    if(mPlayType == 0) {
                        //if(parent.isBlueToothConnected) insideSDK.agent_onEvent(Inside.EVENT_BLUETOOTH_NOT_PLAYING, null);
                        parent.notiPlay(false);
                    }
                }
            } else {
                if(mAudioTrack != null) releaseAudioTrack();
            }
        } catch (Exception e) {
            Logger.i(TAG + " pause Exception : " + e.toString());
        }

    }
    /**
     * 현재 위치 + time 으로 탐색한다.
     * **/
    void seek(int time) {
        try {
            if(mediaPlayer != null && isPrepared) mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + time * 1000);
        } catch (Exception e) {
            Logger.i(TAG + " seek Exception : " + e.toString());
        }
    }
    /**
     * 미디어 플레이어를 mute 시키거나 해제한다.
     * **/
    void mute(boolean mute) {
        try {
            if(mPlayType != 2) {
                if(mediaPlayer != null && isPrepared) mediaPlayer.setVolume(mute ? 0 : 1, mute ? 0 : 1);
            } else {
                if(mAudioTrack != null) {
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) mAudioTrack.setStereoVolume(mute ? 0 : 1, mute ? 0 : 1);
                    else mAudioTrack.setVolume(mute ? 0 : 1);
                }
            }
        } catch (Exception e) {
            Logger.i(TAG + " mute Exception : " + e.toString());
        }
    }
    /**
     * 미디어 플레이어의 재생중 여부를 리턴한다.
     * **/
    boolean isPlaying() {
        try {
            if(mPlayType != 2) {
                if(mediaPlayer != null) return mediaPlayer.isPlaying();
            } else {
                if(mAudioTrack != null) return mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
            }
        } catch (Exception e) {
            Logger.i(TAG + " isPlaying Exception : " + e.toString());
        }
        return false;
    }
    /**
     * 재생중인 미디어를 stop 하고 미디어 플레이어를 Release 시킨다.
     * **/
    void stop() {
        try {
            if(mPlayType != 2) {
                if(mediaPlayer != null) {
                    if(payload != null) {
                        if(isPrepared) updateMediaStatus(payload, Inside.MEDIA_STOPPED, mediaPlayer.getCurrentPosition());
                        else updateMediaStatus(payload, Inside.MEDIA_STOPPED, 0);
                    }
                }

                // play, pause 등 아이콘을 바꾸기 위해 이벤트를 보내준다.
                if(mPlayType == 0) {
                    //if(parent.isBlueToothConnected) insideSDK.agent_onEvent(Inside.EVENT_BLUETOOTH_NOT_PLAYING, null);
                    parent.notiPlay(null);
                }
            }
        } catch (Exception e) {
            Logger.i(TAG + " stop Exception : " + e.toString());
        }
        release();
    }
    /**
     * 미디어 플레이어의 현재 상태를 서버로 전송한다.
     * **/
    private void updateMediaStatus(Payload payload, String state, int playtime) {
        Logger.d(TAG + " updateMediaStatus state=", state);
        try {
            int channel = 0;
            if(payload.cmdOpt != null){
                if(payload.cmdOpt.channel != null){
                    channel = payload.cmdOpt.channel;
                }
            }
            insideSDK.agent_updateMediaStatus(channel, state, playtime);
        } catch (Exception e) {
            Logger.d(TAG + " updateMediaStatus Exception : ", e.getMessage());
        }
    }
    /**
     * 미디어 플레이어와 오디오트랙을 release 시킨다.
     * **/
    private void release() {
        releaseMediaPlayer();
        releaseAudioTrack();
    }
    private void releaseMediaPlayer() {
        try {
            if(mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) { }
        isPrepared = false;
    }
    private void releaseAudioTrack() {
        try { resetPcmThreadArray(); }
        catch (Exception e) { }
        try {
            if(mAudioTrack != null) {
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
        } catch (Exception e) { }
    }
    void reset() {
        release();
        insideSDK = null;
    }


    //임시 wav file write
    private String base64StringToFile(String base64AudioData) {

        String fullPath = Environment.getExternalStorageDirectory() + "/mediastream.wav";

        byte[] decoded = Base64.decode(base64AudioData,1);
        //Logger.i("Decoded: ", Arrays.toString(decoded));

        try {
            File file2 = new File(fullPath);
            if(file2.exists()){
                file2.delete();
            }
            FileOutputStream os = new FileOutputStream(file2, true);
            os.write(decoded);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fullPath;
    }
}