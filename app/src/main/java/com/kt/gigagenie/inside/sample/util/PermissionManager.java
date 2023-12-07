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

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {

    private Activity activity;
    private static final int MY_PERMISSIONS_REQUEST = 1;
    private String[] permissionList_TIRAMISU = {Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO, Manifest.permission
            .ACCESS_NETWORK_STATE};
    private String[] permissionList = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO, Manifest.permission
            .ACCESS_NETWORK_STATE};

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public boolean permissonCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (String permission : permissionList_TIRAMISU) {
                if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(activity, permission)) {
                    ActivityCompat.requestPermissions(activity, permissionList_TIRAMISU, MY_PERMISSIONS_REQUEST);
                    return false;
                }
            }
        } else {
            for (String permission : permissionList) {
                if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(activity, permission)) {
                    ActivityCompat.requestPermissions(activity, permissionList, MY_PERMISSIONS_REQUEST);
                    return false;
                }
            }
        }
        return true;
    }
}