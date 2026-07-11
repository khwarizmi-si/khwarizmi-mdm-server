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

package com.hmdm.rest.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.*;
import com.hmdm.persistence.domain.*;
import com.hmdm.rest.json.*;
import com.hmdm.rest.json.view.devicelist.DeviceListView;
import com.hmdm.rest.json.view.devicelist.DeviceView;
import com.hmdm.security.SecurityContext;
import com.hmdm.security.SecurityException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Api(tags = {"Device"}, authorizations = {@Authorization("Bearer Token")})
@Singleton
@Path("/private/devices")
public class DeviceResource {

    private static final Logger log = LoggerFactory.getLogger(DeviceResource.class);
    private static final Map<String, String> DEVICE_COMMAND_TO_PUSH_TYPE = new HashMap<>();

    static {
        DEVICE_COMMAND_TO_PUSH_TYPE.put("reboot", PushMessage.TYPE_REBOOT);
        DEVICE_COMMAND_TO_PUSH_TYPE.put("lock", PushMessage.TYPE_LOCK);
        DEVICE_COMMAND_TO_PUSH_TYPE.put("wipe", PushMessage.TYPE_WIPE);
    }

    private DeviceDAO deviceDAO;
    private ConfigurationDAO configurationDAO;
    private PushService pushService;
    private ConfigurationFileDAO configurationFileDAO;
    private CommonDAO commonDAO;
    private UnsecureDAO unsecureDAO;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public DeviceResource() {
    }

    @Inject
    public DeviceResource(DeviceDAO deviceDAO,
                          ConfigurationDAO configurationDAO,
                          PushService pushService,
                          ConfigurationFileDAO configurationFileDAO,
                          CommonDAO commonDAO,
                          UnsecureDAO unsecureDAO) {
        this.deviceDAO = deviceDAO;
        this.configurationDAO = configurationDAO;
        this.pushService = pushService;
        this.configurationFileDAO = configurationFileDAO;
        this.commonDAO = commonDAO;
        this.unsecureDAO = unsecureDAO;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Search devices",
            notes = "Search devices meeting the specified filter value",
            response = DeviceListView.class
    )
    @POST
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDevices(DeviceSearchRequest request) {
        PaginatedData<Device> devices = this.deviceDAO.getAllDevices(request);
        Map<Integer, List<Application>> configIdToApplicationsMap = new HashMap<>();
        Map<Integer, List<ConfigurationFile>> configIdToFilesMap = new HashMap<>();
        Map<Integer, Configuration> configIdToConfigurationsMap = new HashMap<>();
        for (Device device : devices.getItems()) {
            final Integer deviceConfigurationId = device.getConfigurationId();

            Configuration dbConfig = null;
            if (!configIdToConfigurationsMap.containsKey(deviceConfigurationId)) {
                dbConfig = configurationDAO.getConfigurationById(deviceConfigurationId);
                if (dbConfig == null) {
                    log.error("Device " + device.getNumber() + ": configuration does not exist: " + deviceConfigurationId);
                    device.setConfigurationId(null);     // Will be filtered out when converting to DeviceView
                    continue;
                }
            }

            if (!configIdToApplicationsMap.containsKey(deviceConfigurationId)) {
                configIdToApplicationsMap.put(deviceConfigurationId, this.configurationDAO.getConfigurationApplications(deviceConfigurationId));
            }
            if (!configIdToFilesMap.containsKey(deviceConfigurationId)) {
                configIdToFilesMap.put(deviceConfigurationId, this.configurationFileDAO.getConfigurationFiles(deviceConfigurationId));
            }

            if (!configIdToConfigurationsMap.containsKey(deviceConfigurationId)) {
                // Here we keep only required properties
                Configuration configuration = new Configuration();
                configuration.setId(deviceConfigurationId);
                configuration.setName(device.getConfigName());
                configuration.setPermissive(dbConfig.getPermissive());
                if (dbConfig.getMainAppId() != null && dbConfig.getMainAppId() > 0 &&
                        dbConfig.getEventReceivingComponent() != null && dbConfig.getEventReceivingComponent().length() > 0) {
                    configuration.setQrCodeKey(dbConfig.getQrCodeKey());
                    configuration.setBaseUrl(this.configurationDAO.getBaseUrl());
                }
                configuration.setApplications(configIdToApplicationsMap.get(deviceConfigurationId));
                configuration.setFiles(configIdToFilesMap.get(deviceConfigurationId));

                configIdToConfigurationsMap.put(deviceConfigurationId, configuration);
            }

            device.setConfiguration(configIdToConfigurationsMap.get(deviceConfigurationId));
        }

        final List<DeviceView> deviceViews = devices.getItems().stream()
                .filter(d -> d.getConfigurationId() != null)
                .map(DeviceView::new)
                .collect(Collectors.toList());
        PaginatedData<DeviceView> devicesPage = new PaginatedData<>(deviceViews, devices.getTotalItemsCount());

        DeviceListView view = new DeviceListView(configIdToConfigurationsMap.values(), devicesPage);

        return Response.OK(view);

    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the device info by number",
            notes = "Get the device info by number",
            response = DeviceListView.class
    )
    @GET
    @Path("/number/{number}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDevice(@PathParam("number") @ApiParam("Device number") String number) {
        try {
            Device device = this.deviceDAO.getDeviceByNumber(number);
            DeviceView deviceView = new DeviceView(device);
            return Response.OK(deviceView);
        } catch (Exception e) {
            log.error("Cannot find device by number: " + number);
            return Response.DEVICE_NOT_FOUND_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Gets the list of device ids/names matching the specified string filter for autocompletions.</p>
     *
     * @param filter a filter to be used for filtering the records.
     * @return a response with list of devices matching the specified filter.
     */
    @ApiOperation(value = "")
    @POST
    @Path("/autocomplete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDevicesForAutocomplete(String filter) {
        try {
            List<DeviceLookupItem> devices = this.deviceDAO.findDevices(filter, 10);
            return Response.OK(devices);
        } catch (Exception e) {
            log.error("Failed to search the devices due to unexpected error. Filter: {}", filter, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Create or update device",
            notes = "Create a new device (if id is not provided) or update existing one otherwise."
    )
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateDevice(Device device) {
        try {
            final boolean canEditDevices = SecurityContext.get().hasPermission("edit_devices");

            if (!canEditDevices) {
                log.error("Unauthorized attempt to create or edit device",
                        SecurityException.onCustomerDataAccessViolation(device.getId(), "device"));
                return Response.PERMISSION_DENIED();
            }

            Device dbDevice;
            try {
                dbDevice = this.deviceDAO.getDeviceByNumber(device.getNumber());
            } catch (SecurityException e) {
                log.error("A different device with same number exists in other organization: {}", device.getNumber());
                return Response.DEVICE_EXISTS();
            }
            if (dbDevice != null && !dbDevice.getId().equals(device.getId())) {
                log.error("A different device with same number exists: {}", dbDevice);
                return Response.DEVICE_EXISTS();
            } else {
                dbDevice = this.deviceDAO.getDeviceById(device.getId());
                if (device.getId() != null) {
                    if (dbDevice != null) {
                        boolean notify = (dbDevice.getConfigurationId() != null &&
                                !dbDevice.getConfigurationId().equals(device.getConfigurationId())) ||
                                (dbDevice.getOldNumber() == null && device.getOldNumber() != null);
                        this.deviceDAO.updateDevice(device);
                        if (notify) {
                            this.pushService.notifyDeviceOnSettingUpdate(device.getId());
                        }
                    }
                } else if (device.getIds() != null) {
                    // This is a bulk request to update configurations for selected devices
                    Iterator it = device.getIds().iterator();

                    while (it.hasNext()) {
                        Integer id = (Integer) it.next();
                        dbDevice = this.deviceDAO.getDeviceById(id);
                        if (dbDevice != null) {
                            this.deviceDAO.updateDeviceConfiguration(id, device.getConfigurationId());
                            this.pushService.notifyDeviceOnSettingUpdate(dbDevice.getId());
                        }
                    }
                } else {
                    Settings settings = new Settings();
                    if (!unsecureDAO.isSingleCustomer()) {
                        commonDAO.loadCustomerSettings(settings);
                    }
                    if (settings.getDeviceLimit() == 0 || settings.getDeviceCount() < settings.getDeviceLimit()) {
                        device.setLastUpdate(0L);
                        this.deviceDAO.insertDevice(device);
                    } else {
                        log.warn("New device {} not added by customer {} due to the license limit", device.getNumber(),
                                SecurityContext.get().getCurrentCustomerId().get());
                        return Response.ERROR();
                    }
                }

                return Response.OK();
            }
        } catch (Exception e) {
            log.error("Unexpected error when saving/creating device", e);
            return Response.ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Delete device",
            notes = "Delete an existing device"
    )
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeDevice(@PathParam("id") @ApiParam("Device ID") Integer id) {
        final boolean canEditDevices = SecurityContext.get().hasPermission("edit_devices");

        if (!(canEditDevices)) {
            log.error("Unauthorized attempt to delete device",
                    SecurityException.onCustomerDataAccessViolation(id, "device"));
            return Response.PERMISSION_DENIED();
        }

        this.deviceDAO.removeDeviceById(id);
        return Response.OK();
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Delete bulk devices",
            notes = "Delete multiple devices at once"
    )
    @POST
    @Path("/deleteBulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeBulkDevices(Device device) {
        final boolean canEditDevices = SecurityContext.get().hasPermission("edit_devices");

        if (!(canEditDevices)) {
            log.error("Unauthorized attempt to delete devices",
                    SecurityException.onCustomerDataAccessViolation(0, "device"));
            return Response.PERMISSION_DENIED();
        }

        if (device.getIds() != null) {
            // Device IDs are transferred in the "ids" parameter
            Iterator it = device.getIds().iterator();

            while (it.hasNext()) {
                Integer id = (Integer) it.next();
                this.deviceDAO.removeDeviceById(id);
            }
        }
        return Response.OK();
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Set or clear device groups in bulk"
    )
    @POST
    @Path("/groupBulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateDeviceGroupBulk(DeviceGroupBulkRequest request) {
        final boolean canEditDevices = SecurityContext.get().hasPermission("edit_devices");

        if (!(canEditDevices)) {
            log.error("Unauthorized attempt to delete devices",
                    SecurityException.onCustomerDataAccessViolation(0, "device"));
            return Response.PERMISSION_DENIED();
        }

        if (request.getIds() != null) {
            // Device IDs are transferred in the "ids" parameter
            Iterator it = request.getIds().iterator();

            while (it.hasNext()) {
                Integer id = (Integer) it.next();
                Device device = deviceDAO.getDeviceById(id);
                if (device == null) {
                    // Not found
                    continue;
                }
                List<LookupItem> groups = device.getGroups();
                groups.removeAll(request.getGroups());
                if (request.getAction().equals("set")) {
                    groups.addAll(request.getGroups());
                }
                deviceDAO.updateDevice(device);
                // No need to notify devices because changing a group doesn't affect a device
            }
        }
        return Response.OK();
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get device application settings",
            notes = "Get application settings set at device level"
    )
    @GET
    @Path("/{id}/applicationSettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceApplicationSettings(@PathParam("id") @ApiParam("Device ID") Integer id) {
        try {
            final List<ApplicationSetting> deviceApplicationSettings = this.deviceDAO.getDeviceApplicationSettings(id);
            return Response.OK(deviceApplicationSettings);
        } catch (Exception e) {
            log.error("Failed to retrieve the application settings for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get installed applications",
            notes = "Get the full inventory of applications present on the device (every package, " +
                    "including user-installed and system apps)"
    )
    @GET
    @Path("/{id}/installedApps")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceInstalledApps(@PathParam("id") @ApiParam("Device ID") Integer id) {
        try {
            final List<DeviceInstalledApp> installedApps = this.deviceDAO.getDeviceAllInstalledApplications(id);
            return Response.OK(installedApps);
        } catch (Exception e) {
            log.error("Failed to retrieve the installed applications for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get app usage history",
            notes = "Get the lightweight app-usage history (foreground events, newest first); " +
                    "the first entry is the app the user is currently/was last using"
    )
    @GET
    @Path("/{id}/appUsage")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceAppUsage(@PathParam("id") @ApiParam("Device ID") Integer id) {
        try {
            final List<DeviceAppUsageEvent> events = this.deviceDAO.getDeviceAppUsageEvents(id);
            return Response.OK(events);
        } catch (Exception e) {
            log.error("Failed to retrieve the app usage history for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save device application settings",
            notes = "Save application settings set at device level"
    )
    @POST
    @Path("/{id}/applicationSettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveDeviceApplicationSettings(@PathParam("id") @ApiParam("Device ID") Integer id,
                                                  List<ApplicationSetting> applicationSettings) {
        try {
            this.deviceDAO.saveDeviceApplicationSettings(id, applicationSettings);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to save the application settings for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Notify device on update",
            notes = "Sends a notification to device on application settings update",
            response = Void.class
    )
    @POST
    @Path("/{id}/applicationSettings/notify")
    @Produces(MediaType.APPLICATION_JSON)
    public Response notifyDevicesOnUpdate(@PathParam("id") Integer id) {
        try {
            this.pushService.notifyDeviceOnApplicationSettingUpdate(id);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to send notification on application settings update to device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Send remote command to device",
            notes = "Sends a remote command to a device. Supported commands: reboot, lock, wipe.",
            response = Void.class
    )
    @POST
    @Path("/{id}/command/{cmd}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendDeviceCommand(@PathParam("id") @ApiParam("Device ID") Integer id,
                                      @PathParam("cmd") @ApiParam("Command: reboot, lock, wipe") String command) {
        try {
            if (!SecurityContext.get().hasPermission("edit_devices")) {
                log.error("Unauthorized attempt to send remote command '{}' to device #{}",
                        command, id, SecurityException.onCustomerDataAccessViolation(id, "device"));
                return Response.PERMISSION_DENIED();
            }

            final String pushType = getDeviceCommandPushType(command);
            if (pushType == null) {
                return Response.ERROR("error.invalid.device.command");
            }

            final Device device = this.deviceDAO.getDeviceById(id);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            this.pushService.sendSimpleMessage(id, pushType);
            return Response.OK("success.device.command.sent");
        } catch (Exception e) {
            log.error("Failed to send remote command '{}' to device #{}", command, id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    static String getDeviceCommandPushType(String command) {
        if (command == null) {
            return null;
        }
        return DEVICE_COMMAND_TO_PUSH_TYPE.get(command);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get device location",
            notes = "Returns the last location reported by a device.",
            response = DeviceLocation.class
    )
    @GET
    @Path("/{id}/location")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceLocation(@PathParam("id") @ApiParam("Device ID") Integer id) {
        try {
            final Device device = this.deviceDAO.getDeviceById(id);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            final DeviceLocation location = this.deviceDAO.getDeviceLocation(id);
            if (location == null) {
                return Response.ERROR("error.notfound.device.location");
            }

            return Response.OK(location);
        } catch (Exception e) {
            log.error("Failed to get location for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get device location history",
            notes = "Returns the location history reported by a device, ordered from oldest to newest.",
            response = DeviceLocation.class,
            responseContainer = "List"
    )
    @GET
    @Path("/{id}/locations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceLocationHistory(@PathParam("id") @ApiParam("Device ID") Integer id) {
        try {
            final Device device = this.deviceDAO.getDeviceById(id);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            final List<DeviceLocation> locations = this.deviceDAO.getDeviceLocationHistory(id);
            if (locations.isEmpty()) {
                final DeviceLocation latestLocation = this.deviceDAO.getDeviceLocation(id);
                if (latestLocation != null) {
                    locations.add(latestLocation);
                }
            }

            return Response.OK(locations);
        } catch (Exception e) {
            log.error("Failed to get location history for device #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save device description",
            notes = "Updates existing device description"
    )
    @POST
    @Path("/{id}/description")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveDeviceDescription(@PathParam("id") @ApiParam("Device ID") Integer deviceId,
                                          String newDeviceDescription) {
        try {
            final boolean canEditDeviceDescription = SecurityContext.get().hasPermission("edit_device_desc");

            if (!canEditDeviceDescription) {
                log.error("Unauthorized attempt to edit device description",
                        SecurityException.onCustomerDataAccessViolation(deviceId, "device"));
                return Response.PERMISSION_DENIED();
            }

            this.deviceDAO.updateDeviceDescription(deviceId, newDeviceDescription);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to save the description for device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
