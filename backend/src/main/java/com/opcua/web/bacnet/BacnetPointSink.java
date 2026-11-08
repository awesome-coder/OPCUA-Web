package com.opcua.web.bacnet;

import com.opcua.web.dto.TagValueView;
import java.time.Instant;

/**
 * Receives normalized values from the OPC UA subscription pipeline.
 * A BACnet/IP stack adapter can implement this interface to expose the mapped objects.
 */
public interface BacnetPointSink {

    void onPointValue(BacnetPointValue value);

    default void onDeviceOnline(String deviceId, String deviceName, Instant connectedAt) {
    }

    default void onDeviceOffline(String deviceId, String deviceName, String reason) {
    }

    record BacnetPointValue(
        String deviceId,
        String deviceName,
        String tagId,
        String objectType,
        int objectTypeNumber,
        int instance,
        String objectId,
        String property,
        Object value,
        String quality,
        String sourceTime,
        String dataType,
        String units
    ) {
    }
}
