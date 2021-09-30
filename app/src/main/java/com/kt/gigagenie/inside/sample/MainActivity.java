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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.google.gson.Gson;
import com.kt.gigagenie.inside.api.Inside;
import com.kt.gigagenie.inside.api.InsideListener;
import com.kt.gigagenie.inside.network.grpc.model.MetaInfo;
import com.kt.gigagenie.inside.network.grpc.model.Payload;
import com.kt.gigagenie.inside.network.grpc.model.RsResult;
import com.kt.gigagenie.inside.util.Logger;
import com.kt.gigagenie.inside.sample.util.PermissionManager;
import com.kt.gigagenie.inside.sample.util.RsUtils;
import com.kt.gigagenie.inside.sample.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements InsideListener, View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    //API Link에서 키 발급 후 세팅
    private final String CLIENT_ID = "YOUR-CLIENT-ID";
    private final String CLIENT_KEY = "YOUR-CLIENT-KEY";
    private final String CLIENT_SECRET = "YOUR-CLIENT-SECRET";

    private String serverIP = "inside-dev.gigagenie.ai";
    private String grpcPort = "50109";
    private String restPort = "30109";

    private Context mContext;
    private PermissionManager permissionManager;

    private EditText sendTextEditView1, getTtsEditView;
    private TextView tvReq, tvRes;

    private Utils utils;
    private RsUtils rsUtils;
    private static Inside insideSDK = null;

    private boolean mIsRecording = false;

    private ImageView mBtnPlayPause;

    // MediaPlayerHelper
    private MediaPlayerHelper mMediaPlayerHelper;

    // VoiceRecorder
    private VoiceRecorder mVoiceRecorder;

    // Timer
    private ArrayList<HashMap<String, String>> mTimerArr;

    // main_progress_voice
    private ProgressBar mVoiceProgressBar;

    // 저장된 UUID 사용했는지
    boolean useStoredUUID = false;

    // bluetoothReceiver
    private BluetoothReceiver mBluetoothReceiver;

    // KWS
    private KwsDetectThread mKwsThread = null;

    private int[] mViewArr = {
            R.id.main_btn_agent_init, R.id.main_btn_agent_unregister, R.id.main_btn_agent_reset, R.id.main_btn_agent_debugmod,
            R.id.main_btn_kws_getVersion, R.id.main_btn_kws_getKeyword,
            R.id.main_btn_kws_init, R.id.main_btn_kws_reset, R.id.main_btn_kws_1, R.id.main_btn_kws_2, R.id.main_btn_kws_3, R.id.main_btn_kws_4,
            R.id.main_btn_agent_startvoice, R.id.main_btn_agent_stopvoice, R.id.main_btn_agent_sendtext1,
            R.id.main_btn_agent_serviceLogin, R.id.main_btn_agent_serviceLoginStatus, R.id.main_btn_agent_serviceLogout,
            R.id.main_btn_agent_setServerInfo, R.id.partial_set_server_info_cancel, R.id.partial_set_server_info_confirm,
            R.id.partial_set_location_cancel, R.id.partial_set_location_confirm,
            R.id.main_btn_agent_getVersion, R.id.main_btn_agent_setLocation, R.id.main_btn_agent_texttovoice,
            R.id.main_img_play_pause, R.id.main_img_prev, R.id.main_img_next, R.id.main_img_volume_down, R.id.main_img_volume_up,
            R.id.main_btn_clear_log
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();
        utils = new Utils(mContext);
        rsUtils = new RsUtils();

        permissionManager = new PermissionManager(this);
        permissionManager.permissonCheck();

        for(int id : mViewArr) {
            findViewById(id).setOnClickListener(this);
        }

        sendTextEditView1 = findViewById(R.id.main_edit_sendtext1);

        // 결과 텍스트
        tvReq = findViewById(R.id.main_text_request);
        tvRes = findViewById(R.id.main_text_response);
        tvReq.setMovementMethod(new ScrollingMovementMethod());
        tvRes.setMovementMethod(new ScrollingMovementMethod());

        getTtsEditView = findViewById(R.id.main_edit_texttovoice);

        mBtnPlayPause = findViewById(R.id.main_img_play_pause);

        // main progress voice
        mVoiceProgressBar = findViewById(R.id.main_progress_voice);
        mVoiceProgressBar.setMax(100);

        JobManager.create(this).addJobCreator(new TimerJobCreator());

    }
    /**
     * registerInsideSDK : get UUID
     * **/
    private void registerInsideSDK() {
        // try reset before init
        resetInsideSDK(false);

        Inside.agent_setServerInfo(serverIP, grpcPort, restPort);

        insideSDK = new Inside(mContext, this);

        // 기존 인증 성공한 UUID 있는지 확인
        useStoredUUID = false;
        String uuid = utils.getSharedPreferences("inside_uuid", null);
        if(uuid != null) {
            useStoredUUID = true;
            initInsideSDK(uuid);
            return;
        }

        try {
            //사용자 식별 ID로 android_id 사용
            String android_id = Settings.Secure.getString(this.getContentResolver(),Settings.Secure.ANDROID_ID);
            Logger.d(TAG + " registerInsideSDK user_id(android_id) : " + android_id);
            
            String ret = insideSDK.agent_register(CLIENT_ID, CLIENT_KEY, CLIENT_SECRET, android_id);
            Logger.d(TAG + " registerInsideSDK agent_register ret : " + ret);

            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");

            if (rc == 200) {
                initInsideSDK(jsonObject.getString("uuid"));
            } else {
                // 케이스별로 처리 필요
                runOnUiThread(() -> utils.setToast("insideSDK agent_register fail : " + rcmsg));
                resetInsideSDK(true);
            }
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK agent_register fail : " + e.toString()));
            resetInsideSDK(true);
        }
    }
    /**
     * check UUID is valid
     * **/
    private void initInsideSDK(String uuid) {
        initMediaPlayerHelper();

        try {
            String ret = insideSDK.agent_init(CLIENT_ID, CLIENT_KEY, CLIENT_SECRET, uuid);
            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");

            if(rc == 200) initInsideSDKSuccess(uuid);
            else {
                // init fail
                resetInsideSDK(true);
                if(useStoredUUID) {
                    // 저장된 UUID 가지고 실패했을 경우
                    if(rc == 404) {
                        runOnUiThread(() -> utils.setToast("insideSDK initInsideSDK invalied UUID --> clear UUID! try retry..."));
                        registerInsideSDK();
                    } else {
                        runOnUiThread(() -> utils.setToast("insideSDK initInsideSDK server not working..."));
                    }
                } else {
                    runOnUiThread(() -> utils.setToast("insideSDK initInsideSDK fail.. plz retry : " + ret));
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK initInsideSDK Exception.. plz retry : " + e.toString()));
        }
    }
    private void initInsideSDKSuccess(String uuid) {
        resetTimerArr();
        utils.setSharedPreferences("inside_uuid", uuid);
        runOnUiThread(() -> utils.setToast("insideSDK initInsideSDK SUCCESS"));
    }
    private void unregisterInsideSDK() {
        try {
            String ret = insideSDK.agent_unregister();
            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");

            if(rc == 200) resetInsideSDK(true);
            runOnUiThread(() -> utils.setToast(rcmsg));
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK unregisterInsideSDK Exception.. plz retry : " + e.toString()));
        }
    }
    private static Inside getInsideSDK() {
        return insideSDK;
    }
    /**
     * insideSDK 를 초기화한다.
     * 미디어플레이어, 블루투스 리시버 등도 같이 초기화한다.
     * **/
    private void resetInsideSDK(boolean isUnregister) {
        // kws stop
        stopKws();
        // insideSDK 초기화
        if(insideSDK != null) insideSDK.agent_reset();
        insideSDK = null;
        // mediaplayer 초기화
        if(mMediaPlayerHelper != null) mMediaPlayerHelper.reset();
        mMediaPlayerHelper = null;
        // 설정된 타이머 해제
        resetTimerArr();
        // 음성녹음 해제
        mIsRecording = false;
        stopVoiceRecord();
        // 필요 시 저장된 UUID 삭제
        if(isUnregister) utils.removeSharedPreferences("inside_uuid");

        // 버튼 플레이로 바꿈.
        resetMetaInfo();

        // bluetoothReceiver init
        resetBluetoothReceiver();
    }
    /**
     * bluetoothReceiver init
     * **/
    private void initBluetoothReceiver() {
        if(mBluetoothReceiver != null) resetBluetoothReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        mBluetoothReceiver = new BluetoothReceiver(this);
        registerReceiver(mBluetoothReceiver, filter);
    }
    /**
     * bluetoothReceiver reset
     * **/
    private void resetBluetoothReceiver() {
        if(mBluetoothReceiver != null) unregisterReceiver(mBluetoothReceiver);
        mBluetoothReceiver = null;
    }
    /**
     * 타이머 정보를 가지고 있는 Arr 를 초기화시킨다. Job 도 초기화시킨다.
     * **/
    private void resetTimerArr() {
        JobManager.instance().cancelAllForTag(TimerJob.TAG);
        mTimerArr = new ArrayList<>();
    }
    private void resetMetaInfo() {
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.main_text_artist_subject)).setText("");
            mBtnPlayPause.setImageResource(R.drawable.btn_play);
        });
    }
    private void initMediaPlayerHelper() {
        mMediaPlayerHelper = new MediaPlayerHelper();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        if(insideSDK != null && mIsRecording) voiceStop();
    }

    private void scrollToBottom(TextView tv) {
        final int scrollAmount = tv.getLayout().getLineTop(tv.getLineCount()) - tv.getHeight();
        if (scrollAmount > 0) tv.scrollTo(0, scrollAmount);
        else tv.scrollTo(0, 0);
    }
    /**
     * 서버로부터 받은 데이터를 처리
     * **/
    @Override
    public void agent_onCommand(String actionType, String json) {
        Logger.d(TAG + " agent_onCommand actionType : " + actionType + "/ json : " + json);

        // Debugmode 로그용 - 시작
        if(actionType.equals("log_res")) {
            try {
                final JSONObject j = new JSONObject(json);
                final String m1 = "payload : " + json;
                final String m2 = tvRes.getText().toString();
                runOnUiThread(() -> {
                    tvRes.setText(m2 + "\n\n" + m1);
                    scrollToBottom(tvRes);
                });
            } catch (Exception e) {}
            return;
        } else if(actionType.equals("log_req")) {
            try {
                final JSONObject j = new
                        JSONObject(json);
                final String m1 = "payload : " + json;
                final String m2 = tvReq.getText().toString();
                runOnUiThread(() -> {
                    tvReq.setText(m2 + "\n\n" + m1);
                    scrollToBottom(tvReq);
                });
            } catch (Exception e) {}
            return;
        }
        // Debugmode 로그용 - 끝

        try {
            Payload payload = new Gson().fromJson(json, Payload.class);
            Logger.d(TAG + " agent_onCommand payload : " + payload.toString());
            if(actionType != null) {
                switch (actionType) {
                    case "start_voice":
                        // Mic On 후 음성인식 시작
                        // 여기 들어올 때 미디어가 재생중이라면 pause 시켜준다.
                        boolean b = mMediaPlayerHelper.isPlaying();
                        mMediaPlayerHelper.actOnOther = b ? "pauseR" : "pause";
                        mMediaPlayerHelper.pause();

                        mIsRecording = true;
                        startVoiceRecord();
                        break;
                    case "stop_voice":
                        // 음성인식 중지 (화면이 있는 경우 uword로 전달되는 음성인식 결과를 화면에 노출할 수 있음)
                        Logger.d(TAG + " agent_onCommand stop_voice!!! uword : " + payload.cmdOpt.uword);

                        mIsRecording = false;
                        stopVoiceRecord();

                        if(insideSDK.isKwsStart) startKwsThread();
                        break;
                    case "exec_dialogkit":
                        // 개발자센터 사이트의 DialogKit에 등록한 정보를 전달하며, payload를 파싱하여 시나리오에 맞게 단말 특화 대화 처리.
                        break;
                    case "play_media":
                        // TTS 재생의 경우 TTS 메시지 정보와 다른 미디어에 대한 제어 정보 등 전달 (실제 TTS mediastream 정보는 이어서 media_data로 전달)
                        // URL 재생의 경우 재생할 미디어의 URL 정보와 다른 미디어에 대한 제어 정보 등 전달
                        mMediaPlayerHelper.start(payload);
                        break;
                    case "control_media":
                        // 재생중인 곡을 제어 (발화로 음악 중지를 요청한 경우 등)
                        mMediaPlayerHelper.handle(payload);
                        break;
                    case "media_data":
                        // TTS mediastream 정보가 전달되며, mediastream값을 base64 decoding하여 재생
                        mMediaPlayerHelper.startWav(payload);
                        break;
                    case "webview_url":
                        // 전달받은 URL을 디바이스의 브라우저로 실행
                        startActivity(makeWebbrowserIntent(payload.cmdOpt.oauth_url));
                        break;
                    case "control_hardware":
                        // 전달받은 정보로 HW 제어 (볼륨 제어, 음소거 등)
                        String target = payload.cmdOpt.target;
                        String hwCmd = payload.cmdOpt.hwCmd;
                        String control;
                        switch (target) {
                            case "volume":
                                int volume = utils.getCurVolume();
                                int maxVolume = utils.getMaxVolume();
                                control = payload.cmdOpt.hwCmdOpt.control;
                                if(hwCmd.equals("setVolume")) {
                                    // setVolume
                                    if(control.equals("UP") ) {
                                        String value = payload.cmdOpt.hwCmdOpt.value;
                                        volume = volume + (value.equals("LE") ? 1 : (value.equals("GN") ? 2 : (value.equals("MO") ? 3 : 0)));
                                        if(volume > maxVolume) volume = maxVolume;
                                        utils.setStreamVolume(volume);
                                    } else if(control.equals("DN")) {
                                        String value = payload.cmdOpt.hwCmdOpt.value;
                                        volume = volume - (value.equals("LE") ? 1 : (value.equals("GN") ? 2 : (value.equals("MO") ? 3 : 0)));
                                        if(volume < 0) volume = 0;
                                        utils.setStreamVolume(volume);
                                    } else if(control.equals("ST")) {
                                        String value = payload.cmdOpt.hwCmdOpt.value;
                                        utils.setStreamVolume(Integer.parseInt(value));
                                    } else if(control.equals("MT")) {
                                        utils.setStreamMute(true);
                                    } else if(control.equals("UMT")) {
                                        utils.setStreamMute(false);
                                    }
                                }
                                break;
                            case "bluetooth":
                                // 처리 필요.
                                if(hwCmd == null || !hwCmd.equals("controlClassicSvc")) return;
                                control = payload.cmdOpt.hwCmdOpt.control;
                                if(control == null || control.trim().length() == 0) return;
                                switch (control) {
                                    case "play":
                                        break;
                                    case "stop":
                                        break;
                                    case "pause":
                                        break;
                                    case "rewind":
                                        break;
                                    case "forward":
                                        break;
                                    case "backward":
                                        break;
                                }
                                break;
                        }
                        break;
                    case "set_timer":
                        // 전달받은 정보로 타이머 설정
                        try {
                            if(payload.cmdOpt.setOpt.equals("set")) {
                                resetTimerArr();
                                // set Timer
                                PersistableBundleCompat extras = new PersistableBundleCompat();
                                extras.putString("actionTrx", payload.cmdOpt.actionTrx);
                                extras.putString("reqAct", payload.cmdOpt.reqAct);

                                int jobId = new JobRequest.Builder(TimerJob.TAG)
                                        .setExact(Long.parseLong(payload.cmdOpt.setTime) * 1000)
                                        .setExtras(extras)
                                        .build()
                                        .schedule();

                                HashMap<String, String> map = new HashMap<>();
                                map.put("actionTrx", payload.cmdOpt.actionTrx);
                                map.put("reqAct", payload.cmdOpt.reqAct);
                                map.put("jobId", jobId+"");

                                // Arr 구조이지만 한개만 저장
                                mTimerArr.add(map);
                            } else {
                                // clear Timer
                                resetTimerArr();
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> utils.setToast("Req_STTM Called! but setJob Fail..."));
                        }
                        break;
                }
            } else {
                Logger.d(TAG + " agent_onCommand actionType null");
            }
        } catch (Exception e) {
            Logger.i(TAG + " agent_onCommand Exception : " + e.toString());
        }
    }
    /**
     * 이벤트 처리, 음성시작, 음성중지, 결과처리 등
     * **/
    @Override
    public void agent_onEvent(int evt, String opt) {
        Logger.d(TAG + " agent_onEvent evt : " + evt);
        Logger.d(TAG + " agent_onEvent opt : " + opt);
        switch (evt) {
            case Inside.SERVER_ERROR:
                // 에러코드에 맞게 재시도 또는 클라이언트 초기화 후 재시도
                Logger.d(TAG + " agent_onEvent SERVER_ERROR opt : " + opt);

                mMediaPlayerHelper.playAfterVoiceFail();

                mIsRecording = false;
                stopVoiceRecord();
                runOnUiThread(() -> utils.setToast("agent_onEvent SERVER_ERROR opt : " + opt));

                if(insideSDK.isKwsStart) startKwsThread();
                break;
            case Inside.GO_TO_STANDBY:
                //음성명령 대기상태로 전환, 미디어 서비스 상태는 유지
                Logger.d(TAG + " GO_TO_STANDBY");
                break;
            case Inside.GRPC_INIT_SUCCESS:
                // gRPC 연결 성공 (필요에 따라 메시지 출력)
                Logger.d(TAG + " agent_onEvent GRPC_INIT_SUCCESS");
                runOnUiThread(() -> utils.setToast("GRPC_INIT_SUCCESS"));
                break;
            case Inside.GRPC_INIT_FAIL:
                // gRPC 연결 실패 --> 연결 재시도
                Logger.d(TAG + " agent_onEvent GRPC_INIT_FAIL");
                runOnUiThread(() -> utils.setToast("GRPC_INIT_FAIL"));
                break;
            case Inside.GRPC_DISCONNECTED:
                // gRPC 연결 끊김 (필요에 따라 메시지 출력)
                Logger.d(TAG + " agent_onEvent GRPC_DISCONNECTED opt : " + opt);
                runOnUiThread(() -> utils.setToast("GRPC_DISCONNECTED opt : " + opt));
                break;
            default:
                Logger.d(TAG + " agent_onEvent evt : " + evt);
                break;
        }
    }
    /**
     * 음성인식 시작 요청
     * **/
    private void voiceStart() {
        // 기존에 음성녹음중이라면 멈춘다.
        stopVoiceRecord();
        // 음성시작 요청한다.
        insideSDK.agent_startVoice();
    }
    /**
     * 음성인식 중지 요청
     * **/
    private void voiceStop() {
        // 녹음 모듈 초기화
        stopVoiceRecord();
        // 음성인식 중이었다면, 중지 요청한다.
        if(mIsRecording) insideSDK.agent_stopVoice();
        // 음성 중지 요청을 한 후 kws 가 시작되었다면, 시작시킨다.
        if(insideSDK.isKwsStart) startKwsThread();
    }
    /**
     * 텍스트 인식 요청
     * **/
    private void sendText(String msg) {
        voiceStop();
        insideSDK.agent_sendText(msg);
    }
    /**
     * 음성인식 시작
     * **/
    private void startVoiceRecord() {
        // KWS 가 동작중이라면 멈춘다.
        if(insideSDK.isKwsStart && mKwsThread != null) stopKwsThread();

        stopVoiceRecord();

        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
        setProgressBarVisible(true);
    }
    /**
     * 음성인식 progress bar 없애고, 음성녹음모듈 초기화한다.
     * **/
    private void stopVoiceRecord() {
        setProgressBarVisible(false);
        if(mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }
    private void setProgressBarVisible(final boolean visible) {
        runOnUiThread(() -> {
            mVoiceProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            mVoiceProgressBar.setProgress(0);
        });
    }
    /**
     * serviceLogin
     * **/
    private void serviceLogin() {
        try {
            String ret = insideSDK.agent_serviceLogin("geniemusic", null);

            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");
            if(rc == 200)
                startActivity(makeWebbrowserIntent(jsonObject.getString("oauth_url")));
            else
                runOnUiThread(() -> utils.setToast(TAG + " serviceLogin Fail : " + rc + ", " + rcmsg));
            Logger.i(TAG + " serviceLogin ret : " + ret);
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK serviceLogin Exception.. plz retry : " + e.toString()));
        }
    }
    /**
     * serviceLoginStatus
     * **/
    private void serviceLoginStatus() {
        try {
            String ret = insideSDK.agent_serviceLoginStatus("geniemusic");

            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");

            Logger.i(TAG + " serviceLoginStatus rc : " + rc + ", rcmsg : " + rcmsg);
            Logger.i(TAG + " serviceLoginStatus ret : " + ret);
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK serviceLogin Exception.. plz retry : " + e.toString()));
        }
    }
    /**
     * serviceLogout
     * **/
    private void serviceLogout() {
        try {
            String ret = insideSDK.agent_serviceLogout("geniemusic");

            JSONObject jsonObject = new JSONObject(ret);
            int rc = jsonObject.getInt("rc");
            String rcmsg = jsonObject.getString("rcmsg");

            Logger.i(TAG + " serviceLoginStatus rc : " + rc + ", rcmsg : " + rcmsg);
            Logger.i(TAG + " serviceLoginStatus ret : " + ret);
        } catch (Exception e) {
            runOnUiThread(() -> utils.setToast("insideSDK serviceLogin Exception.. plz retry : " + e.toString()));
        }
    }
    /**
     * make webView intent
     * **/
    private Intent makeWebViewIntent(String targetUrl) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", targetUrl);
        return intent;
    }
    /**
     * make webbrowser intent (chrome)
     * **/
    private Intent makeWebbrowserIntent(String targetUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        intent.setPackage("com.android.chrome");
        startActivity(intent);
        return intent;
    }
    /**
     * 음성인식 시 callback
     * **/
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {
        @Override
        public void onVoiceStart() {
            Logger.d(TAG + " mVoiceCallback onVoiceStart 프로파일 생성");
        }
        @Override
        public void onVoice(short[] data, int size) {
            int total = 0;
            for(int i = 0; i < 160; ++i){
                total += Math.abs(data[i]);
            }
            float average = (float)(total / 160) / 100 - 3;
            if(average < 0) average = 0.0f;
            else if(average > 10) average = 10.0f;

            // 파형 구현 부분 추가 필요 average 이용
            mVoiceProgressBar.setProgress((int) (average * 10));

            try {
                insideSDK.agent_sendVoice(data, size);
            } catch (Exception e) {
                Logger.i(TAG + " agent_sendVoice Exception : " + e.toString());
            }
        }
        @Override
        public void onVoiceEnd() {
            Logger.d(TAG + " mVoiceCallback onVoiceEnd");
        }
    };
    /**
     * 노래의 제목 등 세팅
     * **/
    private void setMediaMetaInfo(RsResult r) {
        Logger.i(TAG + " setMediaMetaInfo : " + r.toString());
        try {
            // mediaurl 타입이 아닐경우 리턴
            if(!r.actionType.equals("media_url")) return;
            MetaInfo metaInfo = r.payload.cmdOpt.metaInfo;
            String ArtistSubject = metaInfo.infoDetail.artist + " - " + metaInfo.infoDetail.title;
            runOnUiThread(() -> {
                // 추후 앱에 맞게 수정 필요
                ((TextView) findViewById(R.id.main_text_artist_subject)).setText(ArtistSubject);
                findViewById(R.id.main_text_artist_subject).setSelected(true);
            });
        } catch (Exception e) {
            Logger.i(TAG + " setMediaMetaInfo Exception : " + e.toString());
        }
    }
    /**
     * 노래의 제목 등 세팅
     * **/
    private void setMediaMetaInfo(Payload p) {
        Logger.i(TAG + " setMediaMetaInfo : " + p.cmdOpt);
        try {
            MetaInfo metaInfo = p.cmdOpt.metaInfo;
            if(metaInfo == null) return;
            if(metaInfo.infoDetail != null) {
                String ArtistSubject = metaInfo.infoDetail.artist + " - " + metaInfo.infoDetail.title;
                runOnUiThread(() -> {
                    // 추후 앱에 맞게 수정 필요
                    ((TextView) findViewById(R.id.main_text_artist_subject)).setText(ArtistSubject);
                    findViewById(R.id.main_text_artist_subject).setSelected(true);
                });
            }
        } catch (Exception e) {
            Logger.i(TAG + " setMediaMetaInfo Exception : " + e.toString());
        }
    }
    @Override
    public void onClick(View view) {
        String msg;
        RsResult rs;
        switch (view.getId()) {
            case R.id.main_btn_agent_unregister:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::unregisterInsideSDK).start();
                break;
            case R.id.main_btn_agent_init:
                if(permissionManager.permissonCheck()) new Thread(this::registerInsideSDK).start();
                else errPermission();
                break;
            case R.id.main_btn_agent_reset:
                agentReset();
                break;
            case R.id.main_btn_agent_debugmod:
                if(insideSDK == null) { errNotInit(); return; }
                insideSDK.agent_debugmode(!Logger.LOG_ENABLE);
                break;
            case R.id.main_btn_agent_startvoice:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::voiceStart).start();
                break;
            case R.id.main_btn_agent_stopvoice:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::voiceStop).start();
                break;
            case R.id.main_btn_agent_sendtext1:
                if(insideSDK == null) { errNotInit(); return; }
                msg = sendTextEditView1.getText().toString();
                if(msg.length() == 0) { errEditViewEmpty(); return; }
                new Thread(() -> sendText(msg)).start();
                break;
            case R.id.main_btn_agent_texttovoice:
                if(insideSDK == null) { errNotInit(); return; }
                msg = getTtsEditView.getText().toString();
                if(msg.length() == 0) { errEditViewEmpty(); return; }
                //new Thread(() -> insideSDK.agent_getTTS(msg)).start();

                String getTTS = insideSDK.agent_getTTS(msg);
                Logger.d(TAG + "Main getTTS!! : " + getTTS);

                int rc = 0;
                String rcmsg = "";
                try {
                    JSONObject jsonObject = new JSONObject(getTTS);
                    rc = jsonObject.getInt("rc");
                    rcmsg = jsonObject.getString("rcmsg");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //getTTS 요청 성공
                if (rc == 200) {
                    byte[] decodedString = Base64.decode(rcmsg, Base64.DEFAULT);
                    RawPlayer(decodedString, true);
                } else {
                    // 케이스별로 처리 필요
                    runOnUiThread(() -> utils.setToast("insideSDK getTTS fail : " + getTTS));
                    resetInsideSDK(true);
                }

                break;
            case R.id.main_btn_agent_serviceLogin:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::serviceLogin).start();
                break;
            case R.id.main_btn_agent_serviceLoginStatus:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::serviceLoginStatus).start();
                break;
            case R.id.main_btn_agent_serviceLogout:
                if(insideSDK == null) { errNotInit(); return; }
                new Thread(this::serviceLogout).start();
                break;
            case R.id.main_img_play_pause:
                if(insideSDK == null || mMediaPlayerHelper == null) { errNotInit(); return; }
                rs = rsUtils.makeSndHWEV("button", "Btn_PU", null, null, null);
                new Thread(() -> insideSDK.agent_sendCommand(new Gson().toJson(rs))).start();
                break;
            case R.id.main_img_prev:
                if(insideSDK == null || mMediaPlayerHelper == null) { errNotInit(); return; }
                rs = rsUtils.makeSndHWEV("button", "Btn_PV", null, null, null);
                new Thread(() -> insideSDK.agent_sendCommand(new Gson().toJson(rs))).start();
                break;
            case R.id.main_img_next:
                if(insideSDK == null || mMediaPlayerHelper == null) { errNotInit(); return; }
                rs = rsUtils.makeSndHWEV("button", "Btn_NX", null, null, null);
                new Thread(() -> insideSDK.agent_sendCommand(new Gson().toJson(rs))).start();
                break;
            case R.id.main_img_volume_down:
            case R.id.main_img_volume_up:
                if(insideSDK == null || mMediaPlayerHelper == null) { errNotInit(); return; }
                int volume = utils.calVolume(view.getId() == R.id.main_img_volume_up);
                utils.setStreamVolume(volume);
                rs = rsUtils.makeSndHWEV("volume", "setVolume", null, volume+"", null);
                new Thread(() -> insideSDK.agent_sendCommand(new Gson().toJson(rs))).start();
                break;
            case R.id.main_btn_clear_log:
                tvReq.setText("");
                tvRes.setText("");
                break;
            case R.id.main_btn_kws_init:
                if(insideSDK == null) { errNotInit(); return; }
                startKws();
                break;
            case R.id.main_btn_kws_reset:
                if(insideSDK == null) { errNotInit(); return; }
                if(!insideSDK.isKwsStart) { errKwsNotInit(); return; }
                stopKws();
                break;
            case R.id.main_btn_kws_1: // 기가지니
                if(insideSDK == null) { errNotInit(); return; }
                if(!insideSDK.isKwsStart) { errKwsNotInit(); return; }
                insideSDK.kws_setKeyword(0);
                break;
            case R.id.main_btn_kws_2: // 지니야
                if(insideSDK == null) { errNotInit(); return; }
                if(!insideSDK.isKwsStart) { errKwsNotInit(); return; }
                insideSDK.kws_setKeyword(1);
                break;
            case R.id.main_btn_kws_3: // 친구야
                if(insideSDK == null) { errNotInit(); return; }
                if(!insideSDK.isKwsStart) { errKwsNotInit(); return; }
                insideSDK.kws_setKeyword(2);
                break;
            case R.id.main_btn_kws_4: // 자기야
                if(insideSDK == null) { errNotInit(); return; }
                if(!insideSDK.isKwsStart) { errKwsNotInit(); return; }
                insideSDK.kws_setKeyword(3);
                break;
            case R.id.main_btn_agent_setServerInfo:
                findViewById(R.id.partial_set_server_info).setVisibility(View.VISIBLE);
                break;
            case R.id.main_btn_agent_getVersion:
                utils.setToast(Inside.agent_getVersion());
                break;
            case R.id.partial_set_server_info_cancel:
                findViewById(R.id.partial_set_server_info).setVisibility(View.GONE);
                break;
            case R.id.partial_set_server_info_confirm:
                serverIP = ((EditText) findViewById(R.id.partial_set_server_info_ip)).getText().toString();
                grpcPort = ((EditText) findViewById(R.id.partial_set_server_info_grpc_port)).getText().toString();
                restPort = ((EditText) findViewById(R.id.partial_set_server_info_rest_port)).getText().toString();

                findViewById(R.id.partial_set_server_info).setVisibility(View.GONE);

                // 존재한다면 다시 register 시도
                if(insideSDK != null) new Thread(this::registerInsideSDK).start();
                // 없다면 값만 업데이트
                else Inside.agent_setServerInfo(serverIP, grpcPort, restPort);
                break;
            case R.id.main_btn_kws_getVersion:
                if(insideSDK == null) { errNotInit(); return; }
                runOnUiThread(() -> utils.setToast("kws_getVersion : " + insideSDK.kws_getVersion()));
                break;
            case R.id.main_btn_kws_getKeyword:
                if(insideSDK == null) { errNotInit(); return; }
                runOnUiThread(() -> utils.setToast("kws_getKeyword : " + insideSDK.kws_getKeyword()+""));
                break;
            case R.id.main_btn_agent_setLocation:
                if(insideSDK == null) { errNotInit(); return; }
                findViewById(R.id.partial_set_location).setVisibility(View.VISIBLE);
                break;
            case R.id.partial_set_location_cancel:
                findViewById(R.id.partial_set_location).setVisibility(View.GONE);
                break;
            case R.id.partial_set_location_confirm:
                String Longitude = ((EditText) findViewById(R.id.partial_set_location_1)).getText().toString();
                String Latitude = ((EditText) findViewById(R.id.partial_set_location_2)).getText().toString();
                String Address = ((EditText) findViewById(R.id.partial_set_location_3)).getText().toString();

                insideSDK.agent_setLocation(Longitude, Latitude, Address);

                findViewById(R.id.partial_set_location).setVisibility(View.GONE);
                break;
        }
    }
    private void errNotInit() { runOnUiThread(() -> utils.setToast("agent_init 이 실행되지 않았습니다. 시작 버튼을 먼저 눌러주세요."));  }
    private void errKwsNotInit() { runOnUiThread(() -> utils.setToast("kws 가 실행중이 아닙니다."));  }
    private void errEditViewEmpty()  { runOnUiThread(() -> utils.setToast("텍스트를 입력해주세요."));  }
    private void errPermission() { runOnUiThread(() -> utils.setToast("앱 권한이 거부되었습니다. 권한을 활성화해주세요."));  }

    private void agentReset() {
        resetInsideSDK(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(insideSDK != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP){
                // 볼륨 다운 혹은 업
                int volume = utils.calVolume(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                RsResult rs = rsUtils.makeSndHWEV("volume", "setVolume", null, volume+"", null);
                insideSDK.agent_sendCommand(new Gson().toJson(rs));
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        agentReset();
        if(mBluetoothReceiver != null) unregisterReceiver(mBluetoothReceiver);
        super.onDestroy();
    }

    class MediaPlayerHelper {
        // 서버에서 모든 음악을 제어해주지는 않는다.
        // 일부는 3rd party 에서 제어하기 위해 현재 재생중인 채널과 이전에 재생한 채널을 세팅한다.
        int curChannel = -1;
        int prevChannel = -1;
        // 이전 채널을 어떻게 제어할지에 대한 값이다.
        String actOnOther = null;
        // 채널별로 미디어 플레이어를 세팅한다.
        ArrayList<MyMediaPlayer> arrMediaPlayer;
        Context mContext;
        boolean isBlueToothConnected = false;
        MediaPlayerHelper() {
            reset();
            mContext = getApplicationContext();
            arrMediaPlayer = new ArrayList<>();
            for(int i=0; i<110; i++) {
                arrMediaPlayer.add(new MyMediaPlayer(insideSDK, i, this));
            }
        }
        void start(Payload p) {
            try {
                // channel 및 actOnOther 정보를 세팅해준다.
                setChannelAndActOnOther(p);
                if(p.cmdOpt != null) {
                    //url이 있는 경우 해당 url 재생
                    if (p.cmdOpt.url != null) {
                        //metaInfo가 있다면 세팅
                        if(p.cmdOpt.metaInfo != null){
                            setMediaMetaInfo(p);
                        }
                        arrMediaPlayer.get(curChannel).playUrl(p);
                    //url이 없는 경우 TTS 메시지 확인 후 media_data 수신 대기
                    } else {
                        String mesg = p.cmdOpt.metaInfo.mesg;
                        Logger.d(TAG + "TTS mesg : "+ mesg);
                        runOnUiThread(() -> {
                            // 추후 앱에 맞게 수정 필요
                            ((TextView) findViewById(R.id.main_text_artist_subject)).setText(mesg);
                            findViewById(R.id.main_text_artist_subject).setSelected(true);
                        });
                    }
                }

            } catch (Exception e) {
                Logger.i(TAG + " MediaPlayer start Exception : " + e.toString());
            }
        }
        void startWav(Payload p) {
            try {
                //Wav는 메타인포 미세팅
                arrMediaPlayer.get(curChannel).playWav(p);

            } catch (Exception e) {
                Logger.i(TAG + " MediaPlayer start Exception : " + e.toString());
            }
        }
        /**
         * Req_UPMD 에서 온 값들을 제어한다.
         * **/
        void handle(Payload payload) {
            if(payload == null || payload.cmdOpt == null || payload.cmdOpt.act == null || payload.cmdOpt.channel == null) return;
            try {
                // 현재 해야 할 액션
                String act = payload.cmdOpt.act;
                // 제어 해야 할 채널
                int c = payload.cmdOpt.channel;
                switch (act) {
                    case "resume":
                        // resume 을 한다는것을 현재 채널이 바뀌는 것을 의미한다
                        // 현재 재생중인 채널과 다르다면, 현재 재생중인 채널을 이전 채널로 세팅하고
                        // Req_UPMD 에서 온 채널을 현재 채널로 만들어준 후
                        // 이전 채널을 중지시키고, 현재 채널을 다시 재생시킨다.
                        if(curChannel != c) {
                            if(curChannel >= 0) {
                                arrMediaPlayer.get(curChannel).pause();
                                prevChannel = curChannel;
                            }
                            curChannel = c;
                        }
                        // 지금 채널을 resume 시킨다.
                        arrMediaPlayer.get(curChannel).start(false);
                        break;
                    case "pause":
                        arrMediaPlayer.get(c).pause();
                        break;
                    case "stop":
                        arrMediaPlayer.get(c).stop();
                        break;
                    case "seek":
                        if(payload.cmdOpt.playTime == null) return;
                        arrMediaPlayer.get(c).seek(payload.cmdOpt.playTime);
                        break;
                }
            } catch (Exception e) {
                Logger.i(TAG + " MediaPlayer handle Exception : " + e.toString());
            }
        }
        private void resetChannel() {
            curChannel = -1;
            prevChannel = -1;
        }
        /**
         * 초기화
         * **/
        void reset() {
            resetChannel();
            actOnOther = null;
            if(arrMediaPlayer != null && arrMediaPlayer.size() > 0) {
                try {
                    for(int i=0; i<arrMediaPlayer.size(); i++) {
                        arrMediaPlayer.get(i).reset();
                    }
                } catch (Exception e) {}
            }
            arrMediaPlayer = null;
        }
        /**
         * prevChannel, curChannel 을 세팅하고, prevChannel 이 해야할 actOnOther 세팅한다.
         * **/
        private void setChannelAndActOnOther(Payload p) {
            // payload 없다면 실행하지 않는다.
            if(p == null) return;
            Logger.d("--------- setChannelAndActOnOther called! before - payload.cmdOpt.actOnOther : " + actOnOther);
            Logger.d("--------- setChannelAndActOnOther called! before - payload.cmdOpt.channel : " + curChannel);

            prevChannel = curChannel;
            curChannel = p.cmdOpt.channel;

            // set actOnOther
            actOnOther = p.cmdOpt.actOnOther;

            Logger.d("--------- setChannelAndActOnOther called! after - payload.cmdOpt.actOnOther : " + actOnOther);
            Logger.d("--------- setChannelAndActOnOther called! after - payload.cmdOpt.channel : " + curChannel);

            runActOnOther(prevChannel == curChannel);
        }
        /**
         * 이전 채널의 오디오를 제어한다.
         * **/
        private void runActOnOther(boolean isSameChannel) {
            if(isSameChannel && curChannel != 1) notiPlay(null);

            if((actOnOther == null || prevChannel < 0) && !isSameChannel) return;
            Logger.d("runActOnOther called! ");

            if(prevChannel == -1) return;
            try {
                switch (actOnOther) {
                    case "mute":
                    case "muteR":
                        Logger.d(TAG + " runActOnOther channel : " + prevChannel + ", mute or muteR Call!!");
                        //1. 모든 채널에 대해서 mute 을 하고, curChannel은 예외처리
                        for(int i=0; i<110; i++) {
                            if(curChannel != i) arrMediaPlayer.get(i).mute(true);
                        }

                        break;
                    case "pause":
                    case "pauseR":
                        Logger.d(TAG + " runActOnOther channel : " + prevChannel + ", pause or pauseR Call!!");
                        //1. 모든 채널에 대해서 pause 을 하고, curChannel은 예외처리
                        for(int i=0; i<110; i++) {
                            if(curChannel != i) arrMediaPlayer.get(i).pause();
                        }

                        break;
                    case "stop":
                    case "Stop":
                        Logger.d(TAG + " runActOnOther channel : " + prevChannel + ", stop Call!!");
                        //1. 모든 채널에 대해서 stop 을 하고, curChannel은 예외처리
                        for(int i=0; i<110; i++) {
                            if(curChannel != i) arrMediaPlayer.get(i).stop();
                        }
                        break;
                    case "volDown":
                    case "VolDown":
                    case "volDownR":
                    case "VolDownR":

                        break;
                }
            } catch (Exception e) {
                Logger.i(TAG + " MediaPlayer runActOnOther Exception : " + e.toString());
            }
        }
        void playAfterVoiceFail() {
            if(curChannel == -1 || actOnOther == null) return;
            if(actOnOther.equals("pauseR")) arrMediaPlayer.get(curChannel).start(false);
        }
        void pause() {
            if(curChannel == -1) return;
            arrMediaPlayer.get(curChannel).pause();
        }
        boolean isPlaying() {
            if(curChannel == -1) return false;
            return arrMediaPlayer.get(curChannel).isPlaying();
        }
        /**
         * 종료된 후에 여길 호출한 경우는 prev 가 cur 가 되면서 prev 는 -1 로 초기화한다.
         * **/
        void stopAndRunActOnOther() {
            if(prevChannel >= 0) {
                curChannel = prevChannel;
                prevChannel = -1;
                switch (actOnOther) {
                    case "pauseR":
                        arrMediaPlayer.get(curChannel).start(false);
                        break;
                    case "muteR":
                        arrMediaPlayer.get(curChannel).mute(false);
                }
            }
        }
        void notiPlay(Boolean isPlay) {
            if(isPlay == null) {
                resetMetaInfo();
            } else {
                runOnUiThread(() -> mBtnPlayPause.setImageResource(isPlay ? R.drawable.btn_pause : R.drawable.btn_play));
            }
        }
    }
    /**
     * 타이머 세팅을 위한 Job 을 등록한다.
     * **/
    class TimerJobCreator implements JobCreator {
        @Nullable
        @Override
        public Job create(@NonNull String tag) {
            if(tag.equals("timerJob")) {
                return new TimerJob();
            }
            return null;
        }
    }
    /**
     * 타이머 시간이 되었을 때 SDK 로 시간이 도래했음을 알린다.
     * **/
    class TimerJob extends Job {
        static final String TAG = "timerJob";
        @Override
        @NonNull
        protected Result onRunJob(@NonNull Params params) {
            Logger.d("TimerJob onRunJob called!");
            try {
                if(MainActivity.getInsideSDK() != null) {
                    String actionTrx = params.getExtras().getString("actionTrx", null);
                    String reqAct = params.getExtras().getString("reqAct", null);
                    String localTime = new SimpleDateFormat("YYYYmmddHHMMSSsss", Locale.getDefault()).format(new Date());

                    if(actionTrx == null || reqAct == null) {
                        Logger.i("TimerJob cant run, actionTrx or reqAct Null!");
                        return Result.SUCCESS;
                    }

                    RsResult rs = rsUtils.makeSndTMEV(actionTrx, reqAct, localTime);
                    insideSDK.agent_sendCommand(new Gson().toJson(rs));
                } else {
                    Logger.i("TimerJob cant run, insideSDK is Null!");
                }
            } catch (Exception e) {
                Logger.i("TimerJob onRunJob Exception : " + e.toString());
            }
            return Result.SUCCESS;
        }
    }

    /**
     * KWS 를 시작한다.
     * **/
    private void startKws() {
        int ret = insideSDK.kws_init();
        runOnUiThread(() -> utils.setToast(TAG + (ret == 0 ? " KWS INIT Success" : " KWS INIT Fail : " + ret)));
        if(ret == 0) startKwsThread();
    }
    /**
     * KWS 인식용 thread 시작
     * **/
    private void startKwsThread() {
        if(mKwsThread != null) stopKwsThread();
        mKwsThread = new KwsDetectThread();
        mKwsThread.start();
    }
    /**
     * KWS 인식용 thread 중지
     * **/
    private void stopKwsThread() {
        try {
            if(mKwsThread != null) {
                mKwsThread.close();
                mKwsThread.join();
            }
        } catch (Exception e) { }
        mKwsThread = null;
    }
    /**
     * KWS 를 종료한다.
     * **/
    private void stopKws() {
        stopKwsThread();
        if(insideSDK != null) insideSDK.kws_reset();
    }
    class KwsDetectThread extends Thread {
        private boolean stop = false;
        private short[][] buffers = new short[2][160];
        private int ix = 0;

        void close() {
            stop = true;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            AudioRecord recorder = null;

            try {
                int N = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (AudioRecord.ERROR_BAD_VALUE == N) {
                    runOnUiThread(() -> utils.setToast("Device does not supports 16kHz audio recording!"));
                    return;
                }

                // recorder init
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, N * 16);


                Logger.d("AudioRecord recorder.getState = "+recorder.getState());
                Logger.d("AudioRecord STATE_INITIALIZED value  = "+AudioRecord.STATE_INITIALIZED);


                recorder.startRecording();

                while (!stop) {
                    short[] buffer = buffers[ix % buffers.length];
                    N = recorder.read(buffer, 0, buffer.length);
                    if (N <= 0) {
                        try {
                            sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    // frame 값
                    ix++;
                    // kws 에러 검출 시
                    if (0 != insideSDK.kws_error()) break;
                    // 호출어 검출 시
                    int ret = insideSDK.kws_detect(buffer, N);

                    if(ret == 1){
                        //start recording thread
                        Logger.d("KwsDetectThread Start Recording ");
                    }
                    if(ret == 3){
                        //stop recording thread
                        Logger.d("KwsDetectThread STOP Recording ");
                    }
                    if(ret == 4) {
                        // Detect 시 recorder release
                        try {
                            recorder.stop();
                            recorder.release();
                            recorder = null;
                        } catch (Exception e) {}
                        new Thread(MainActivity.this::voiceStart).start();
                    }
                }
                if(recorder != null) recorder.stop();
            } catch (Throwable x) {
                Logger.i(Inside.TAG +  " KeywordDetector Error reading voice audio : " + x.toString());
            } finally {
                Logger.i(Inside.TAG +  " KeywordDetector finally called!");
                if (recorder != null) recorder.release();
            }
        }
    }

    //getTTS로 전달받은 mediastream 재생
    private void RawPlayer(byte[] raw, boolean useMediaStatus) {

        int mSampleRate = 24000;
        int mChannelCount = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mBufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

        if(useMediaStatus) {
            try {
                insideSDK.agent_updateMediaStatus(0, "started", 0);
            } catch (Exception e) {
            }
        }
        AudioTrack mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                mSampleRate,
                mChannelCount,
                mAudioFormat,
                mBufferSize, AudioTrack.MODE_STREAM); // AudioTrack 생성
        mAudioTrack.setNotificationMarkerPosition(mBufferSize);
        mAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onPeriodicNotification(AudioTrack track) {
                Log.d("DEBUG", "onPeriodicNotification");
            }

            @Override
            public void onMarkerReached(AudioTrack track) {
                if(useMediaStatus) {
                    try {
                        Log.d("DEBUG", "getPlaybackHeadPosition: " + track.getPlaybackHeadPosition());
                        insideSDK.agent_updateMediaStatus(0, "complete", track.getPlaybackHeadPosition());
                    } catch (Exception e) {
                    }
                }
            }
        });
        mAudioTrack.play();
        mAudioTrack.write(raw, 0, raw.length);
        mAudioTrack.stop();
        mAudioTrack.release();
    }
}
