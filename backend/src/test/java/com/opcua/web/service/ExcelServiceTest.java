package com.opcua.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.DeviceView;
import com.opcua.web.dto.TagView;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExcelServiceTest {

    private ExcelService excelService;

    @BeforeEach
    void setUp() {
        DeviceService deviceService = mock(DeviceService.class);
        TagService tagService = mock(TagService.class);

        DeviceView device = new DeviceView(
            "dev_1", "Demo", "demo", "opc.tcp://127.0.0.1:4840",
            SecurityPolicyOption.BASIC256SHA256.name(), SecurityModeOption.SIGN_AND_ENCRYPT.name(),
            AuthMode.ANONYMOUS.name(), null, false, true, true,
            10_000, 15_000, 120_000, 1_000, true,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            false, "DISCONNECTED", null, null, 1
        );
        TagView tag = new TagView(
            "tag_1", "dev_1", "Demo", "Temperature", "ns=2;i=3",
            TagDataType.FLOAT.name(), false, AccessMode.READ_WRITE.name(), true,
            "°C", "出口温度", 1.0, 0.0, 0.2, 500, 2,
            null, "ANALOG_VALUE", 1, "present-value", 0.2,
            true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")
        );
        when(deviceService.list()).thenReturn(List.of(device));
        when(deviceService.get("dev_1")).thenReturn(device);
        when(tagService.list((String) null)).thenReturn(List.of(tag));
        when(tagService.list("dev_1")).thenReturn(List.of(tag));

        excelService = new ExcelService(deviceService, tagService);
    }

    @Test
    void templateHasExpectedSheetsAndHeaders() throws Exception {
        byte[] bytes = excelService.template();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("设备")).isNotNull();
            assertThat(workbook.getSheet("点位")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet("设备").getRow(0).getCell(0).getStringCellValue())
                .contains("设备名称");
            assertThat(workbook.getSheet("点位").getRow(0).getCell(2).getStringCellValue())
                .isEqualTo("NodeId*");
        }
    }

    @Test
    void exportContainsDeviceAndTagRows() throws Exception {
        byte[] bytes = excelService.export(null);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("设备").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("设备").getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("Demo");
            assertThat(workbook.getSheet("点位").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("点位").getRow(1).getCell(1).getStringCellValue())
                .isEqualTo("Temperature");
        }
    }
}
