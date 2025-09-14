package com.store.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ProductExcel {
    public static final String[] HEADERS = { "id", "name", "image", "sizes", "price", "quantity" };

    public record RowData(
            String id,
            String name,
            String image,
            List<String> sizes,
            BigDecimal price,
            Integer quantity
    ) {}

    public record ParseResult(List<RowData> rows, List<String> errors) {}

    // ================== EXPORT ==================
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
                setCell(row, 0, d.id);
                setCell(row, 1, d.name);
                setCell(row, 2, d.image);
                setCell(row, 3, joinSizes(d.sizes));
                if (d.price != null) row.createCell(4).setCellValue(d.price.doubleValue());
                row.createCell(5).setCellValue(d.quantity == null ? 0 : d.quantity);
            }

            // autosize
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Export Excel failed", e);
        }
    }

    // =================== IMPORT ===================
    public static ParseResult read(InputStream is) {
        List<RowData> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return new ParseResult(rows, List.of("Sheet not found"));

            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            if (last < first + 1) return new ParseResult(rows, List.of("No data rows"));

            // validate header (đúng thứ tự, không phân biệt hoa/thường)
            Row header = sheet.getRow(first);
            for (int i = 0; i < HEADERS.length; i++) {
                String h = getString(header.getCell(i));
                if (!HEADERS[i].equalsIgnoreCase(h)) {
                    errors.add("Header mismatch at col " + (i + 1) + ": expect '" + HEADERS[i] + "', got '" + h + "'");
                }
            }

            for (int r = first + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    String id = trimToNull(getString(row.getCell(0)));
                    String name = trimToNull(getString(row.getCell(1)));
                    String image = trimToNull(getString(row.getCell(2)));
                    String sizesRaw = getString(row.getCell(3));
                    BigDecimal price = getDecimal(row.getCell(4));
                    Integer quantity = getInt(row.getCell(5));

                    // dòng trống hoàn toàn -> bỏ qua
                    if (isBlank(id) && isBlank(name) && isBlank(image)
                            && (sizesRaw == null || sizesRaw.isBlank())
                            && price == null && quantity == null) {
                        continue;
                    }

                    // validate cơ bản
                    if (isBlank(name)) throw new IllegalArgumentException("name is required");
                    if (price == null) throw new IllegalArgumentException("price is required");
                    if (quantity != null && quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");

                    List<String> sizes = parseSizes(sizesRaw);

                    rows.add(new RowData(id, name, image, sizes, price, quantity == null ? 0 : quantity));
                } catch (Exception ex) {
                    errors.add("Row " + (r + 1) + ": " + ex.getMessage());
                }
            }

            return new ParseResult(rows, errors);
        } catch (Exception e) {
            throw new RuntimeException("Import Excel failed to parse", e);
        }
    }

    // ================= Helpers =================
    private static void setCell(Row row, int col, Object v) {
        Cell c = row.createCell(col);
        if (v == null) { c.setBlank(); return; }
        if (v instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(String.valueOf(v));
    }

    private static String getString(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                if (Math.floor(d) == d) yield String.valueOf((long) d);       // 40 -> "40"
                else yield BigDecimal.valueOf(d).toPlainString();             // 40.5 -> "40.5"
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> c.getRichStringCellValue() != null ? c.getRichStringCellValue().getString() : "";
            case BLANK, _NONE, ERROR -> "";
        };
    }

    private static BigDecimal getDecimal(Cell c) {
        if (c == null) return null;
        return switch (c.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue());
            case STRING -> {
                String s = c.getStringCellValue();
                if (s == null || s.isBlank()) yield null;
                yield new BigDecimal(s.trim());
            }
            case FORMULA -> {
                try { yield BigDecimal.valueOf(c.getNumericCellValue()); }
                catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private static Integer getInt(Cell c) {
        if (c == null) return null;
        return switch (c.getCellType()) {
            case NUMERIC -> (int) Math.round(c.getNumericCellValue());
            case STRING -> {
                String s = c.getStringCellValue();
                if (s == null || s.isBlank()) yield null;
                yield Integer.valueOf(s.trim());
            }
            case FORMULA -> (int) Math.round(c.getNumericCellValue());
            default -> null;
        };
    }

    // "S, M; L | 40\n41" -> ["S","M","L","40","41"]
    private static List<String> parseSizes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] tokens = raw.split("[,;\\|\\n]+");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : tokens) {
            String s = t.trim();
            if (!s.isEmpty()) set.add(s);
        }
        return new ArrayList<>(set);
    }

    private static String joinSizes(List<String> sizes) {
        if (sizes == null || sizes.isEmpty()) return "";
        return sizes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String trimToNull(String s) { return isBlank(s) ? null : s.trim(); }
}
