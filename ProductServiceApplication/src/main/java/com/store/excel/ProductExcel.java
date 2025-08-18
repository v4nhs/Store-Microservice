package com.store.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

public class ProductExcel {

    public static final String[] HEADERS = { "id", "name", "price", "quantity" };

    // Xuất: nhận list -> byte[]
    public static byte[] write(List<RowData> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("products");

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont(); font.setBold(true);
            headerStyle.setFont(font);

            Row hr = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            // data
            int r = 1;
            for (RowData d : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullSafe(d.id));
                row.createCell(1).setCellValue(nullSafe(d.name));
                if (d.price != null) row.createCell(2).setCellValue(d.price);
                row.createCell(3).setCellValue(d.quantity == null ? 0 : d.quantity);
            }

            // autosize
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Export Excel failed", e);
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    // Nhập: đọc từ InputStream -> danh sách RowData + lỗi từng dòng
    public static ParseResult read(InputStream is) {
        List<RowData> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return new ParseResult(rows, List.of("Sheet0 not found"));

            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            if (last < first + 1) return new ParseResult(rows, List.of("No data rows"));

            // validate header
            Row header = sheet.getRow(first);
            for (int i = 0; i < HEADERS.length; i++) {
                String h = getString(header.getCell(i));
                if (!HEADERS[i].equalsIgnoreCase(h)) {
                    errors.add("Header mismatch at col " + (i+1) + ": expect '" + HEADERS[i] + "', got '" + h + "'");
                }
            }

            for (int r = first + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    String id = getString(row.getCell(0)).trim();
                    String name = getString(row.getCell(1)).trim();
                    Double price = getDouble(row.getCell(2));
                    Integer quantity = getInt(row.getCell(3));

                    // dòng trống hoàn toàn -> bỏ qua
                    if (isBlank(id) && isBlank(name) && price == null && quantity == null) continue;

                    // validate
                    if (isBlank(name)) throw new IllegalArgumentException("name is required");
                    if (price == null) throw new IllegalArgumentException("price is required");
                    if (quantity != null && quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");

                    rows.add(new RowData(id.isEmpty() ? null : id, name, price, quantity));
                } catch (Exception ex) {
                    errors.add("Row " + (r + 1) + ": " + ex.getMessage());
                }
            }

            return new ParseResult(rows, errors);
        } catch (Exception e) {
            throw new RuntimeException("Import Excel failed to parse", e);
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String getString(Cell c) {
        if (c == null) return "";
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue();
        if (c.getCellType() == CellType.NUMERIC) return String.valueOf(c.getNumericCellValue());
        if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        if (c.getCellType() == CellType.BLANK) return "";
        return Objects.toString(c, "");
    }
    private static Double getDouble(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        if (c.getCellType() == CellType.STRING && !c.getStringCellValue().isBlank())
            return Double.parseDouble(c.getStringCellValue().trim());
        return null;
    }
    private static Integer getInt(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
        if (c.getCellType() == CellType.STRING && !c.getStringCellValue().isBlank())
            return Integer.parseInt(c.getStringCellValue().trim());
        return null;
    }

    // ===== DTO phụ trợ (đồng bộ với ProductDTO) =====
    public record RowData(String id, String name, Double price, Integer quantity) {}
    public record ParseResult(List<RowData> rows, List<String> errors) {}
}
