package com.opcua.web.dto;

import java.util.List;

public record MetaView(
    List<OptionView> securityPolicies,
    List<OptionView> securityModes,
    List<OptionView> authModes,
    List<OptionView> dataTypes,
    List<OptionView> accessModes,
    List<OptionView> bacnetObjectTypes,
    List<String> bacnetProperties,
    String applicationVersion,
    String opcUaStackVersion
) {
    public record OptionView(String value, String label, String description) {
    }
}
