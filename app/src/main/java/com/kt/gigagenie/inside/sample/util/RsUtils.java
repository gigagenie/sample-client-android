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

import com.kt.gigagenie.inside.network.grpc.model.CmdOpt;
import com.kt.gigagenie.inside.network.grpc.model.HwEventOpt;
import com.kt.gigagenie.inside.network.grpc.model.Payload;
import com.kt.gigagenie.inside.network.grpc.model.RsResult;

public class RsUtils {
    public RsResult makeSndHWEV(String target, String hwEvent, String hwEventOptType, String hwEventOptValue, Integer hwEventOptEvent) {
        RsResult rs = new RsResult();
        rs.payload = new Payload();
        rs.payload.cmdOpt = new CmdOpt();
        rs.payload.cmdOpt.hwEventOpt = new HwEventOpt();

        rs.commandType = "Snd_HWEV";
        if(target != null) rs.payload.cmdOpt.target = target;
        if(hwEvent != null) rs.payload.cmdOpt.hwEvent = hwEvent;
        if(hwEventOptType != null) rs.payload.cmdOpt.hwEventOpt.type = hwEventOptType;
        if(hwEventOptValue != null) rs.payload.cmdOpt.hwEventOpt.value = hwEventOptValue;
        if(hwEventOptEvent != null) rs.payload.cmdOpt.hwEventOpt.event = hwEventOptEvent;
        return rs;
    }
    public RsResult makeSndTMEV(String actionTrx, String reqAct, String localTime) {
        RsResult rs = new RsResult();
        rs.commandType = "Snd_TMEV";
        rs.payload = new Payload();
        rs.payload.cmdOpt = new CmdOpt();
        rs.payload.cmdOpt.actionTrx = actionTrx;
        rs.payload.cmdOpt.reqAct = reqAct;
        rs.payload.cmdOpt.localTime = localTime;
        return rs;
    }
}
