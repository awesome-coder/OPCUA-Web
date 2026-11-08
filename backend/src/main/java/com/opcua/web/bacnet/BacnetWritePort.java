package com.opcua.web.bacnet;

import com.opcua.web.dto.WriteResult;
import com.opcua.web.dto.BacnetWriteRequest;

/**
 * Entry point that a future BACnet server / gateway adapter can call when a BACnet
 * client requests a write to a mapped object.
 */
public interface BacnetWritePort {

    WriteResult write(BacnetWriteRequest request);
}
