package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.BacnetObjectType;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.DeviceRequest;
import com.opcua.web.dto.DeviceView;
import com.opcua.web.dto.ExcelImportResult;
import com.opcua.web.dto.TagRequest;
import com.opcua.web.dto.TagView;
import com.opcua.web.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelService {

    public static final String DEVICE_SHEET = "设备";
    public static final String TAG_SHEET = "点位";
    public static final String HELP_SHEET = "填写说明";

    private static final String[] DEVICE_HEADERS = {
        "设备名称*", "端点URL*", "安全策略", "安全模式", "认证方式", "用户名", "密码",
        "信任所有服务器证书", "连接超时(ms)", "请求超时(ms)", "会话超时(ms)",
        "发布周期(ms)", "启用", "描述", "设备ID"
    };
    private static final String[] TAG_HEADERS = {
        "设备名称*", "点位名称*", "NodeId*", "数据类型", "数组", "访问方式", "工程单位",
        "描述", "缩放系数", "偏移量", "死区", "采样周期(ms)", "队列长度", "枚举值",
        "BACnet对象类型", "BACnet实例号", "BACnet属性", "COV增量", "启用", "点位ID"
    };
    private static final String[] BACNET_PROPERTIES = {
        "present-value", "object-name", "description", "units", "cov-increment",
        "status-flags", "reliability", "out-of-service", "min-present-value", "max-present-value"
    };

    private final DeviceService deviceService;
    private final TagService tagService;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public ExcelService(DeviceService deviceService, TagService tagService) {
        this.deviceService = deviceService;
        this.tagService = tagService;
    }

    public byte[] template() {
        return buildWorkbook(null, List.of(), true);
    }

    public byte[] export(String deviceId) {
        DeviceView device = deviceId == null || deviceId.isBlank() ? null : deviceService.get(deviceId);
        List<DeviceView> devices = device == null ? deviceService.list() : List.of(device);
        List<TagView> tags = device == null
            ? tagService.list(null)
            : tagService.list(device.id());
        return buildWorkbook(devices, tags, false);
    }

    private byte[] buildWorkbook(
        List<DeviceView> devices,
        List<TagView> tags,
        boolean template
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = createStyles(workbook);
            XSSFSheet deviceSheet = workbook.createSheet(DEVICE_SHEET);
            XSSFSheet tagSheet = workbook.createSheet(TAG_SHEET);
            writeHeader(deviceSheet, DEVICE_HEADERS, styles);
            writeHeader(tagSheet, TAG_HEADERS, styles);
            applyDeviceLayout(deviceSheet, styles);
            applyTagLayout(tagSheet, styles);
            addValidations(deviceSheet, tagSheet);
            writeHelpSheet(workbook, styles);

            if (devices != null) {
                int rowIndex = 1;
                for (DeviceView device : devices) {
                    Row row = deviceSheet.createRow(rowIndex++);
                    set(row, 0, device.name());
                    set(row, 1, device.endpointUrl());
                    set(row, 2, device.securityPolicy());
                    set(row, 3, device.securityMode());
                    set(row, 4, device.authMode());
                    set(row, 5, device.username());
                    set(row, 6, "");
                    set(row, 7, device.trustAllCertificates() ? "是" : "否");
                    set(row, 8, device.connectTimeoutMs());
                    set(row, 9, device.requestTimeoutMs());
                    set(row, 10, device.sessionTimeoutMs());
                    set(row, 11, device.publishingIntervalMs());
                    set(row, 12, device.enabled() ? "是" : "否");
                    set(row, 13, device.description());
                    set(row, 14, device.id());
                }
            }
            if (tags != null) {
                int rowIndex = 1;
                for (TagView tag : tags) {
                    Row row = tagSheet.createRow(rowIndex++);
                    set(row, 0, tag.deviceName());
                    set(row, 1, tag.name());
                    set(row, 2, tag.nodeId());
                    set(row, 3, tag.dataType());
                    set(row, 4, tag.array() ? "是" : "否");
                    set(row, 5, tag.accessMode());
                    set(row, 6, tag.unit());
                    set(row, 7, tag.description());
                    set(row, 8, tag.scale());
                    set(row, 9, tag.offset());
                    set(row, 10, tag.deadband());
                    set(row, 11, tag.samplingIntervalMs());
                    set(row, 12, tag.queueSize());
                    set(row, 13, tag.enumValues());
                    set(row, 14, tag.bacnetObjectType());
                    set(row, 15, tag.bacnetInstance());
                    set(row, 16, tag.bacnetProperty());
                    set(row, 17, tag.covIncrement());
                    set(row, 18, tag.enabled() ? "是" : "否");
                    set(row, 19, tag.id());
                }
            }
            if (template) {
                // Template intentionally contains no data rows. Instructions are on the help sheet.
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 Excel 失败: " + e.getMessage(), e);
        }
    }

    public ExcelImportResult importWorkbook(MultipartFile file, boolean overwrite) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要导入的 Excel 文件");
        }
        List<ExcelImportResult.ExcelImportError> errors = new ArrayList<>();
        int devicesCreated = 0;
        int devicesUpdated = 0;
        int devicesSkipped = 0;
        int tagsCreated = 0;
        int tagsUpdated = 0;
        int tagsSkipped = 0;

        Map<String, String> deviceIdsByName = new HashMap<>();
        for (DeviceView device : deviceService.list()) {
            deviceIdsByName.put(device.name().toLowerCase(Locale.ROOT), device.id());
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet deviceSheet = findSheet(workbook, DEVICE_SHEET);
            if (deviceSheet == null) {
                throw ApiException.badRequest("Excel 中缺少“设备”工作表");
            }
            DeviceHeader deviceHeader = new DeviceHeader(deviceSheet);
            for (int rowIndex = 1; rowIndex <= deviceSheet.getLastRowNum(); rowIndex++) {
                Row row = deviceSheet.getRow(rowIndex);
                if (isBlankRow(row)) continue;
                try {
                    DeviceRequest request = parseDevice(row, deviceHeader);
                    DeviceService.ImportDevice imported = deviceService.upsertForImport(request, overwrite);
                    if (imported.skipped()) {
                        devicesSkipped++;
                    } else if (imported.created()) {
                        devicesCreated++;
                    } else {
                        devicesUpdated++;
                    }
                    deviceIdsByName.put(imported.name().toLowerCase(Locale.ROOT), imported.id());
                } catch (Exception e) {
                    addError(errors, DEVICE_SHEET, rowIndex, "设备", e);
                }
            }

            Sheet tagSheet = findSheet(workbook, TAG_SHEET);
            if (tagSheet != null) {
                TagHeader tagHeader = new TagHeader(tagSheet);
                for (int rowIndex = 1; rowIndex <= tagSheet.getLastRowNum(); rowIndex++) {
                    Row row = tagSheet.getRow(rowIndex);
                    if (isBlankRow(row)) continue;
                    try {
                        String deviceName = tagHeader.text(row, "设备名称");
                        if (deviceName.isBlank()) {
                            throw new IllegalArgumentException("设备名称不能为空");
                        }
                        String deviceId = deviceIdsByName.get(deviceName.trim().toLowerCase(Locale.ROOT));
                        if (deviceId == null) {
                            throw new IllegalArgumentException("设备不存在: " + deviceName);
                        }
                        TagRequest request = parseTag(row, tagHeader, deviceId);
                        boolean existing = tagService.list(deviceId).stream()
                            .anyMatch(t -> t.name().equalsIgnoreCase(request.name()));
                        TagConfig saved = tagService.upsertForImport(request, overwrite);
                        if (saved == null) {
                            tagsSkipped++;
                        } else if (existing) {
                            tagsUpdated++;
                        } else {
                            tagsCreated++;
                        }
                    } catch (Exception e) {
                        addError(errors, TAG_SHEET, rowIndex, "点位", e);
                    }
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("无法读取 Excel: " + e.getMessage());
        }

        String message = "导入完成：设备新增 " + devicesCreated + "，更新 " + devicesUpdated
            + "，跳过 " + devicesSkipped + "；点位新增 " + tagsCreated + "，更新 " + tagsUpdated
            + "，跳过 " + tagsSkipped + "；错误 " + errors.size();
        return new ExcelImportResult(
            devicesCreated, devicesUpdated, devicesSkipped,
            tagsCreated, tagsUpdated, tagsSkipped,
            errors.size(), errors, message
        );
    }

    private DeviceRequest parseDevice(Row row, DeviceHeader header) {
        String name = header.text(row, "设备名称");
        String endpoint = header.text(row, "端点URL");
        if (name.isBlank()) throw new IllegalArgumentException("设备名称不能为空");
        if (endpoint.isBlank()) throw new IllegalArgumentException("端点URL不能为空");
        String policyText = header.text(row, "安全策略");
        SecurityPolicyOption policy = policyText.isBlank()
            ? SecurityPolicyOption.NONE
            : SecurityPolicyOption.fromMiloName(policyText);
        String modeText = header.text(row, "安全模式");
        SecurityModeOption mode = modeText.isBlank()
            ? (policy == SecurityPolicyOption.NONE ? SecurityModeOption.NONE : SecurityModeOption.SIGN_AND_ENCRYPT)
            : SecurityModeOption.fromMiloName(modeText);
        String authText = header.text(row, "认证方式");
        AuthMode auth = parseAuth(authText);
        String password = header.text(row, "密码");
        return new DeviceRequest(
            name,
            endpoint,
            policy,
            mode,
            auth,
            header.text(row, "用户名"),
            password.isBlank() ? null : password,
            header.bool(row, "信任所有服务器证书", true),
            null,
            header.integer(row, "连接超时(ms)", 10_000),
            header.integer(row, "请求超时(ms)", 15_000),
            header.integer(row, "会话超时(ms)", 120_000),
            header.integer(row, "发布周期(ms)", 1_000),
            header.bool(row, "启用", true),
            header.text(row, "描述")
        );
    }

    private TagRequest parseTag(Row row, TagHeader header, String deviceId) {
        String name = header.text(row, "点位名称");
        String nodeId = header.text(row, "NodeId");
        if (name.isBlank()) throw new IllegalArgumentException("点位名称不能为空");
        if (nodeId.isBlank()) throw new IllegalArgumentException("NodeId 不能为空");
        String typeText = header.text(row, "数据类型");
        TagDataType dataType = typeText.isBlank()
            ? TagDataType.AUTO
            : TagDataType.fromName(typeText)
                .orElseThrow(() -> new IllegalArgumentException("未知数据类型: " + typeText));
        String accessText = header.text(row, "访问方式");
        AccessMode accessMode = parseAccess(accessText);
        String bacnetTypeText = header.text(row, "BACnet对象类型");
        String bacnetType = null;
        if (!bacnetTypeText.isBlank() && !"AUTO".equalsIgnoreCase(bacnetTypeText)) {
            bacnetType = BacnetObjectType.fromName(bacnetTypeText).name();
        }
        return new TagRequest(
            deviceId,
            name,
            nodeId,
            dataType,
            header.bool(row, "数组", false),
            accessMode,
            header.text(row, "工程单位"),
            header.text(row, "描述"),
            header.decimal(row, "缩放系数", 1.0d),
            header.decimal(row, "偏移量", 0.0d),
            header.decimal(row, "死区", 0.0d),
            header.integer(row, "采样周期(ms)", 0),
            header.integer(row, "队列长度", 1),
            header.text(row, "枚举值"),
            bacnetType,
            header.optionalInteger(row, "BACnet实例号"),
            header.text(row, "BACnet属性"),
            header.optionalDecimal(row, "COV增量"),
            header.bool(row, "启用", true)
        );
    }

    private static AuthMode parseAuth(String value) {
        if (value == null || value.isBlank()) return AuthMode.ANONYMOUS;
        String text = value.trim();
        if (text.equalsIgnoreCase("匿名")) return AuthMode.ANONYMOUS;
        if (text.equalsIgnoreCase("用户名 / 密码") || text.equalsIgnoreCase("用户名密码")) return AuthMode.USERNAME;
        if (text.equalsIgnoreCase("客户端证书")) return AuthMode.CERTIFICATE;
        try {
            return AuthMode.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("未知认证方式: " + value);
        }
    }

    private static AccessMode parseAccess(String value) {
        if (value == null || value.isBlank()) return AccessMode.READ_ONLY;
        String text = value.trim();
        if (text.equals("只读")) return AccessMode.READ_ONLY;
        if (text.equals("读写")) return AccessMode.READ_WRITE;
        if (text.equals("只写")) return AccessMode.WRITE_ONLY;
        try {
            return AccessMode.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("未知访问方式: " + value);
        }
    }

    private void writeHelpSheet(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet(HELP_SHEET);
        String[][] lines = {
            {"OPC UA 配置 Excel 说明", ""},
            {"设备工作表", "每行一台 OPC UA 设备。设备名称必须唯一，端点URL 必须以 opc.tcp:// 开头。"},
            {"认证方式", "ANONYMOUS（匿名）、USERNAME（用户名/密码）、CERTIFICATE（客户端证书）。"},
            {"安全策略/模式", "安全策略不是 None 时，必须使用 Sign 或 SignAndEncrypt。"},
            {"密码", "出于安全考虑，导出时不会包含密码；导入时留空表示保留已有密码。"},
            {"服务器证书", "关闭“信任所有服务器证书”时，请在设备表单中配置服务器证书 PEM 进行固定。"},
            {"点位工作表", "每行一个 OPC UA 变量。NodeId 示例：ns=2;s=Demo.Temperature。"},
            {"数据类型", "AUTO 表示保持 OPC UA 原生类型；也可显式选择 Boolean、Int32、Float、String 等。"},
            {"缩放", "工程值 = 原始值 × 缩放系数 + 偏移量；写入时自动反算。"},
            {"数组", "数组点位写入时使用 JSON 数组，例如 [1,2,3]；也支持逗号分隔。"},
            {"BACnet", "留空或 AUTO 时按数据类型自动映射；可在点位表单中覆盖对象类型和实例号。"},
            {"颜色", "深蓝色为表头，请勿修改表头名称；ID 列仅供系统导出使用。"}
        };
        for (int i = 0; i < lines.length; i++) {
            Row row = sheet.createRow(i);
            Cell left = row.createCell(0);
            left.setCellValue(lines[i][0]);
            left.setCellStyle(i == 0 ? styles.title : styles.bold);
            Cell right = row.createCell(1);
            right.setCellValue(lines[i][1]);
            right.setCellStyle(i == 0 ? styles.title : styles.body);
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 110 * 256);
    }

    private void addValidations(XSSFSheet deviceSheet, XSSFSheet tagSheet) {
        addList(deviceSheet, 2, SecurityPolicyOption.values());
        addList(deviceSheet, 3, new String[]{"None", "Sign", "SignAndEncrypt"});
        addList(deviceSheet, 4, new String[]{"ANONYMOUS", "USERNAME", "CERTIFICATE"});
        addBool(deviceSheet, 7);
        addBool(deviceSheet, 12);

        addList(tagSheet, 3, TagDataType.values());
        addBool(tagSheet, 4);
        addList(tagSheet, 5, new String[]{"READ_ONLY", "READ_WRITE", "WRITE_ONLY"});
        addList(tagSheet, 14, concat(new String[]{"AUTO"}, enumNames(BacnetObjectType.values())));
        addList(tagSheet, 16, BACNET_PROPERTIES);
        addBool(tagSheet, 18);
    }

    private void addList(XSSFSheet sheet, int column, Object[] values) {
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i] instanceof Enum<?> e ? e.name() : String.valueOf(values[i]);
        }
        addList(sheet, column, names);
    }

    private void addList(XSSFSheet sheet, int column, String[] values) {
        CellRangeAddressList range = new CellRangeAddressList(1, 2000, column, column);
        var helper = sheet.getDataValidationHelper();
        var constraint = helper.createExplicitListConstraint(values);
        var validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(org.apache.poi.ss.usermodel.DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("取值无效", "请从下拉列表中选择有效值。");
        sheet.addValidationData(validation);
    }

    private void addBool(XSSFSheet sheet, int column) {
        addList(sheet, column, new String[]{"是", "否"});
    }

    private static String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String[] enumNames(Enum<?>[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].name();
        return result;
    }

    private void applyDeviceLayout(XSSFSheet sheet, Styles styles) {
        int[] widths = {22, 36, 22, 18, 16, 16, 18, 16, 14, 14, 14, 14, 10, 36, 18};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, DEVICE_HEADERS.length - 1));
    }

    private void applyTagLayout(XSSFSheet sheet, Styles styles) {
        int[] widths = {22, 22, 36, 16, 8, 14, 14, 32, 12, 12, 10, 14, 10, 24, 20, 14, 18, 10, 8, 18};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, TAG_HEADERS.length - 1));
    }

    private void writeHeader(Sheet sheet, String[] headers, Styles styles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(28);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
    }

    private Styles createStyles(XSSFWorkbook workbook) {
        Styles styles = new Styles();
        styles.header = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 11);
        styles.header.setFont(headerFont);
        styles.header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        styles.header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.header.setAlignment(HorizontalAlignment.CENTER);
        styles.header.setVerticalAlignment(VerticalAlignment.CENTER);
        border(styles.header);

        styles.body = workbook.createCellStyle();
        styles.body.setVerticalAlignment(VerticalAlignment.CENTER);
        border(styles.body);

        styles.bold = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        styles.bold.setFont(boldFont);
        styles.bold.setWrapText(true);

        styles.title = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        styles.title.setFont(titleFont);
        return styles;
    }

    private static void border(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private static Sheet findSheet(Workbook workbook, String name) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.getSheetName(i).equalsIgnoreCase(name)) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    private static void set(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static boolean isBlankRow(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && !cell.toString().isBlank()) return false;
        }
        return true;
    }

    private static void addError(
        List<ExcelImportResult.ExcelImportError> errors,
        String sheet,
        int row,
        String column,
        Exception e
    ) {
        if (errors.size() >= 200) return;
        errors.add(new ExcelImportResult.ExcelImportError(
            sheet,
            row + 1,
            column,
            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
        ));
    }

    private class DeviceHeader {
        private final Map<String, Integer> columns = new LinkedHashMap<>();

        private DeviceHeader(Sheet sheet) {
            readHeader(sheet, DEVICE_HEADERS);
        }

        private void readHeader(Sheet sheet, String[] expected) {
            Row header = sheet.getRow(0);
            if (header == null) throw ApiException.badRequest("“设备”工作表缺少表头");
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell cell = header.getCell(i);
                if (cell != null) {
                    columns.put(normalize(cell.toString()), i);
                }
            }
            for (String name : expected) {
                if (!columns.containsKey(normalize(name))) {
                    throw ApiException.badRequest("“设备”工作表缺少列: " + name);
                }
            }
        }

        String text(Row row, String name) {
            return cellText(row, columns.get(normalize(name)));
        }

        boolean bool(Row row, String name, boolean defaultValue) {
            String value = text(row, name);
            if (value.isBlank()) return defaultValue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("是") || normalized.equals("true") || normalized.equals("1")
                || normalized.equals("yes") || normalized.equals("y")) return true;
            if (normalized.equals("否") || normalized.equals("false") || normalized.equals("0")
                || normalized.equals("no") || normalized.equals("n")) return false;
            throw new IllegalArgumentException(name + " 必须填写 是/否");
        }

        int integer(Row row, String name, int defaultValue) {
            String value = text(row, name);
            if (value.isBlank()) return defaultValue;
            try {
                return (int) Math.round(Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是整数: " + value);
            }
        }
    }

    private class TagHeader {
        private final Map<String, Integer> columns = new LinkedHashMap<>();

        private TagHeader(Sheet sheet) {
            Row header = sheet.getRow(0);
            if (header == null) throw ApiException.badRequest("“点位”工作表缺少表头");
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell cell = header.getCell(i);
                if (cell != null) {
                    columns.put(normalize(cell.toString()), i);
                }
            }
            for (String name : TAG_HEADERS) {
                if (!columns.containsKey(normalize(name))) {
                    throw ApiException.badRequest("“点位”工作表缺少列: " + name);
                }
            }
        }

        String text(Row row, String name) {
            return cellText(row, columns.get(normalize(name)));
        }

        boolean bool(Row row, String name, boolean defaultValue) {
            String value = text(row, name);
            if (value.isBlank()) return defaultValue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("是") || normalized.equals("true") || normalized.equals("1")
                || normalized.equals("yes") || normalized.equals("y")) return true;
            if (normalized.equals("否") || normalized.equals("false") || normalized.equals("0")
                || normalized.equals("no") || normalized.equals("n")) return false;
            throw new IllegalArgumentException(name + " 必须填写 是/否");
        }

        int integer(Row row, String name, int defaultValue) {
            String value = text(row, name);
            if (value.isBlank()) return defaultValue;
            try {
                return (int) Math.round(Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是整数: " + value);
            }
        }

        Integer optionalInteger(Row row, String name) {
            String value = text(row, name);
            if (value.isBlank()) return null;
            try {
                return (int) Math.round(Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是整数: " + value);
            }
        }

        double decimal(Row row, String name, double defaultValue) {
            String value = text(row, name);
            if (value.isBlank()) return defaultValue;
            try {
                return Double.parseDouble(value.replace(",", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是数字: " + value);
            }
        }

        Double optionalDecimal(Row row, String name) {
            String value = text(row, name);
            if (value.isBlank()) return null;
            try {
                return Double.parseDouble(value.replace(",", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是数字: " + value);
            }
        }
    }

    private String cellText(Row row, Integer index) {
        if (row == null || index == null) return "";
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("*", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static class Styles {
        private CellStyle header;
        private CellStyle body;
        private CellStyle bold;
        private CellStyle title;
    }
}
