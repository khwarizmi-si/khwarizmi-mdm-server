/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.persistence.domain;

import java.io.Serializable;

/**
 * <p>A single "application brought to foreground" event reported by the agent,
 * forming the lightweight app-usage history for a device. The most recent event
 * represents the application the user is currently (or was last) using.</p>
 *
 * <p>Collected only when the device has granted the usage-access permission.
 * Serialized inside {@code DeviceInfo.appUsageEvents} and stored in the
 * {@code devices.infojson} document; the panel reads it back via a JSONB query.</p>
 */
public class DeviceAppUsageEvent implements Serializable {

    private static final long serialVersionUID = -4487611094023456781L;

    /**
     * <p>A package ID of the application brought to foreground.</p>
     */
    private String pkg;

    /**
     * <p>A human readable application label.</p>
     */
    private String name;

    /**
     * <p>The time (epoch millis) the application was brought to foreground.</p>
     */
    private Long ts;

    public DeviceAppUsageEvent() {
    }

    public String getPkg() {
        return pkg;
    }

    public void setPkg(String pkg) {
        this.pkg = pkg;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    @Override
    public String toString() {
        return "DeviceAppUsageEvent{" +
                "pkg='" + pkg + '\'' +
                ", name='" + name + '\'' +
                ", ts=" + ts +
                '}';
    }
}
