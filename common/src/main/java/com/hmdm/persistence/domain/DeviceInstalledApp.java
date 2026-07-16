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
 * <p>A single application present on device, as part of the full installed-app
 * inventory reported by the mobile agent.</p>
 *
 * <p>Unlike {@link DeviceApplication} (which only covers the applications managed
 * by the MDM configuration), this covers <em>every</em> package on device,
 * including user-installed and system apps. It is serialized inside
 * {@code DeviceInfo.installedApplications} and stored in the {@code devices.infojson}
 * JSON document; the panel reads it back via a JSONB query.</p>
 */
public class DeviceInstalledApp implements Serializable {

    private static final long serialVersionUID = -6021958166137894110L;

    /**
     * <p>A package ID for application (e.g. {@code com.whatsapp}).</p>
     */
    private String pkg;

    /**
     * <p>A human readable application label.</p>
     */
    private String name;

    /**
     * <p>An application version name (e.g. {@code 2.24.1}).</p>
     */
    private String version;

    /**
     * <p>An application version code (monotonic integer). May be {@code null}.</p>
     */
    private Long versionCode;

    /**
     * <p>{@code true} if this is a system / pre-installed application.</p>
     */
    private boolean system;

    /**
     * {@code true} when Android exposes a launcher activity for this package.
     */
    private boolean launchable;

    /**
     * <p>The package name of the installer (e.g. the app store), if known.</p>
     */
    private String installer;

    /**
     * <p>The time (epoch millis) the app was first installed. May be {@code null}.</p>
     */
    private Long firstInstall;

    /**
     * <p>The time (epoch millis) the app was last updated. May be {@code null}.</p>
     */
    private Long lastUpdate;

    public DeviceInstalledApp() {
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Long getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(Long versionCode) {
        this.versionCode = versionCode;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isLaunchable() {
        return launchable;
    }

    public void setLaunchable(boolean launchable) {
        this.launchable = launchable;
    }

    public String getInstaller() {
        return installer;
    }

    public void setInstaller(String installer) {
        this.installer = installer;
    }

    public Long getFirstInstall() {
        return firstInstall;
    }

    public void setFirstInstall(Long firstInstall) {
        this.firstInstall = firstInstall;
    }

    public Long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public String toString() {
        return "DeviceInstalledApp{" +
                "pkg='" + pkg + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", versionCode=" + versionCode +
                ", system=" + system +
                '}';
    }
}
