package com.opcua.web.service;

import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.BacnetObjectType;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.MetaView;
import java.util.Arrays;
import java.util.List;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.springframework.stereotype.Service;

@Service
public class MetaService {

    private final PkiService pkiService;

    public MetaService(PkiService pkiService) {
        this.pkiService = pkiService;
    }

    public MetaView meta() {
        return new MetaView(
            Arrays.stream(SecurityPolicyOption.values())
                .map(v -> new MetaView.OptionView(v.name(), v.getLabel(), v.getDescription()))
                .toList(),
            Arrays.stream(SecurityModeOption.values())
                .map(v -> new MetaView.OptionView(v.name(), v.getLabel(), v.getDescription()))
                .toList(),
            Arrays.stream(AuthMode.values())
                .map(v -> new MetaView.OptionView(v.name(), v.getLabel(), v.getDescription()))
                .toList(),
            Arrays.stream(TagDataType.values())
                .map(v -> new MetaView.OptionView(v.name(), v.getLabel(), null))
                .toList(),
            Arrays.stream(AccessMode.values())
                .map(v -> new MetaView.OptionView(v.name(), v.getLabel(), null))
                .toList(),
            Arrays.stream(BacnetObjectType.values())
                .map(v -> new MetaView.OptionView(
                    v.name(),
                    v.getAbbreviation() + " · " + v.getChineseLabel(),
                    v.getLabel() + " (object-type " + v.getObjectTypeNumber() + ")"
                ))
                .toList(),
            List.of(
                "present-value", "object-name", "description", "units", "cov-increment",
                "status-flags", "reliability", "out-of-service", "min-present-value", "max-present-value"
            ),
            "0.1.0",
            OpcUaClient.SDK_VERSION
        );
    }
}
