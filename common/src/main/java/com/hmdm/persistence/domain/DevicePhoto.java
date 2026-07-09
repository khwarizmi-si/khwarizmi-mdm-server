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
 * <p>Metadata for a single photo uploaded by the device. The binary file lives under
 * {@code files.directory}/photos/&lt;deviceNumber&gt;/&lt;name&gt;; this record is stored in the
 * {@code devices.infojson} document (no schema migration) and read back by the panel gallery.</p>
 */
public class DevicePhoto implements Serializable {

    private static final long serialVersionUID = -7702431664915330001L;

    /** Server-generated file name (e.g. {@code 1720000000000.jpg}). Never a client-supplied name. */
    private String name;

    /** Upload time, epoch millis. */
    private Long ts;

    /** File size in bytes. */
    private Long size;

    public DevicePhoto() {
    }

    public DevicePhoto(String name, Long ts, Long size) {
        this.name = name;
        this.ts = ts;
        this.size = size;
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

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
