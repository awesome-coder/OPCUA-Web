package com.opcua.web.bacnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingBacnetPointSink implements BacnetPointSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingBacnetPointSink.class);

    @Override
    public void onPointValue(BacnetPointValue value) {
        if (log.isDebugEnabled()) {
            log.debug("BACnet point update {}={} quality={}", value.objectId(), value.value(), value.quality());
        }
    }
}
