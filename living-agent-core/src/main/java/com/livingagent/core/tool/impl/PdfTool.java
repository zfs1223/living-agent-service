package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class PdfTool implements Tool {
    private static final String NAME = "pdf";
    private static final String DESCRIPTION = "PDF operations: extract text/tables, merge, split, rotate, watermark, create, encrypt. Uses pypdf/pdfplumber (Python) or Java libraries.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "productivity";
    private static final int INCH = 72;
    
    private ToolStats stats = ToolStats.empty(NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: extract_text, extract_tables, merge, split, rotate, watermark, create, encrypt, decrypt, info", true)
                .parameter("input_path", "string", "输入PDF文件路径", false)
                .parameter("output_path", "string", "输出PDF文件路径", false)
                .parameter("input_paths", "array", "多个输入文件路径 (用于merge)", false)
                .parameter("pages", "string", "页码范围 (如: 1-5, 1,3,5)", false)
                .parameter("rotation", "integer", "旋转角度 (90, 180, 270)", false)
                .parameter("watermark_path", "string", "水印PDF文件路径", false)
                .parameter("password", "string", "密码 (用于加密/解密)", false)
                .parameter("content", "string", "内容 (用于create)", false)
                .build();
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("extract_text", "extract_tables", "merge", "split", "rotate", "watermark", "create", "encrypt", "decrypt", "info");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action parameter is required");
        }

        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "extract_text":
                    result = extractText(params);
                    break;
                case "extract_tables":
                    result = extractTables(params);
                    break;
                case "merge":
                    result = mergePdfs(params);
                    break;
                case "split":
                    result = splitPdf(params);
                    break;
                case "rotate":
                    result = rotatePdf(params);
                    break;
                case "watermark":
                    result = addWatermark(params);
                    break;
                case "create":
                    result = createPdf(params);
                    break;
                case "encrypt":
                    result = encryptPdf(params);
                    break;
                case "decrypt":
                    result = decryptPdf(params);
                    break;
                case "info":
                    result = getPdfInfo(params);
                    break;
                default:
                    result = ToolResult.failure("Unknown action: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("PDF operation failed: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private ToolResult extractText(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        Path path = Paths.get(inputPath);
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + inputPath);
        }

        String pages = params.getString("pages");
        
        if (isPythonAvailable()) {
            return extractTextWithPython(inputPath, pages);
        } else {
            return ToolResult.failure("Python with pypdf/pdfplumber is required for text extraction");
        }
    }

    private ToolResult extractTextWithPython(String inputPath, String pages) throws Exception {
        StringBuilder script = new StringBuilder();
        script.append("import pdfplumber\n");
        script.append("import json\n\n");
        script.append("result = {'pages': []}\n");
        script.append("with pdfplumber.open('").append(inputPath.replace("\\", "/")).append("') as pdf:\n");
        
        if (pages != null && !pages.isBlank()) {
            script.append("    page_nums = ").append(parsePageRange(pages)).append("\n");
            script.append("    for i in page_nums:\n");
            script.append("        if i < len(pdf.pages):\n");
            script.append("            text = pdf.pages[i].extract_text() or ''\n");
            script.append("            result['pages'].append({'page': i+1, 'text': text})\n");
        } else {
            script.append("    for i, page in enumerate(pdf.pages):\n");
            script.append("        text = page.extract_text() or ''\n");
            script.append("        result['pages'].append({'page': i+1, 'text': text})\n");
        }
        
        script.append("print(json.dumps(result, ensure_ascii=False))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("result", output, "source", inputPath));
    }

    private ToolResult extractTables(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pdfplumber is required for table extraction");
        }

        String pages = params.getString("pages");
        
        StringBuilder script = new StringBuilder();
        script.append("import pdfplumber\n");
        script.append("import json\n\n");
        script.append("result = {'tables': []}\n");
        script.append("with pdfplumber.open('").append(inputPath.replace("\\", "/")).append("') as pdf:\n");
        
        if (pages != null && !pages.isBlank()) {
            script.append("    page_nums = ").append(parsePageRange(pages)).append("\n");
            script.append("    for i in page_nums:\n");
            script.append("        if i < len(pdf.pages):\n");
            script.append("            tables = pdf.pages[i].extract_tables() or []\n");
            script.append("            for t in tables:\n");
            script.append("                result['tables'].append({'page': i+1, 'data': t})\n");
        } else {
            script.append("    for i, page in enumerate(pdf.pages):\n");
            script.append("        tables = page.extract_tables() or []\n");
            script.append("        for t in tables:\n");
            script.append("            result['tables'].append({'page': i+1, 'data': t})\n");
        }
        
        script.append("print(json.dumps(result, ensure_ascii=False))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("result", output, "source", inputPath));
    }

    private ToolResult mergePdfs(ToolParams params) throws Exception {
        String outputPath = params.getString("output_path");
        if (outputPath == null) {
            return ToolResult.failure("output_path is required");
        }

        @SuppressWarnings("unchecked")
        List<String> inputPaths = params.get("input_paths");
        if (inputPaths == null || inputPaths.isEmpty()) {
            return ToolResult.failure("input_paths is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF merge");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("writer = PdfWriter()\n");
        
        for (String inputPath : inputPaths) {
            script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
            script.append("for page in reader.pages:\n");
            script.append("    writer.add_page(page)\n");
        }
        
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('Merged successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDFs merged successfully", "output", outputPath));
    }

    private ToolResult splitPdf(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        String pages = params.getString("pages");
        
        if (inputPath == null || outputPath == null) {
            return ToolResult.failure("input_path and output_path are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF split");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("writer = PdfWriter()\n");
        
        if (pages != null && !pages.isBlank()) {
            script.append("page_nums = ").append(parsePageRange(pages)).append("\n");
            script.append("for i in page_nums:\n");
            script.append("    if i < len(reader.pages):\n");
            script.append("        writer.add_page(reader.pages[i])\n");
        }
        
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('Split successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDF split successfully", "output", outputPath));
    }

    private ToolResult rotatePdf(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        Integer rotation = params.getInteger("rotation");
        if (rotation == null) rotation = 90;
        
        if (inputPath == null || outputPath == null) {
            return ToolResult.failure("input_path and output_path are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF rotation");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("writer = PdfWriter()\n");
        script.append("for page in reader.pages:\n");
        script.append("    page.rotate(").append(rotation).append(")\n");
        script.append("    writer.add_page(page)\n");
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('Rotated successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDF rotated successfully", "output", outputPath, "rotation", rotation));
    }

    private ToolResult addWatermark(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        String watermarkPath = params.getString("watermark_path");
        
        if (inputPath == null || outputPath == null || watermarkPath == null) {
            return ToolResult.failure("input_path, output_path, and watermark_path are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for watermark");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("watermark = PdfReader('").append(watermarkPath.replace("\\", "/")).append("').pages[0]\n");
        script.append("writer = PdfWriter()\n");
        script.append("for page in reader.pages:\n");
        script.append("    page.merge_page(watermark)\n");
        script.append("    writer.add_page(page)\n");
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('Watermark added successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Watermark added successfully", "output", outputPath));
    }

    private ToolResult createPdf(ToolParams params) throws Exception {
        String outputPath = params.getString("output_path");
        String content = params.getString("content");
        
        if (outputPath == null || content == null) {
            return ToolResult.failure("output_path and content are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with reportlab is required for PDF creation");
        }

        StringBuilder script = new StringBuilder();
        script.append("from reportlab.lib.pagesizes import letter\n");
        script.append("from reportlab.pdfgen import canvas\n");
        script.append("from reportlab.lib.units import inch\n");
        script.append("c = canvas.Canvas('").append(outputPath.replace("\\", "/")).append("', pagesize=letter)\n");
        script.append("width, height = letter\n");
        
        String[] lines = content.split("\n");
        int y = 11 * INCH - INCH;
        for (String line : lines) {
            if (y < INCH) {
                script.append("c.showPage()\n");
                y = 11 * INCH - INCH;
            }
            String escapedLine = line.replace("\\", "\\\\").replace("'", "\\'");
            script.append("c.drawString(inch, ").append(y).append(", '").append(escapedLine).append("')\n");
            y -= 14;
        }
        
        script.append("c.save()\n");
        script.append("print('PDF created successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDF created successfully", "output", outputPath));
    }

    private ToolResult encryptPdf(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        String password = params.getString("password");
        
        if (inputPath == null || outputPath == null || password == null) {
            return ToolResult.failure("input_path, output_path, and password are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF encryption");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("writer = PdfWriter()\n");
        script.append("for page in reader.pages:\n");
        script.append("    writer.add_page(page)\n");
        script.append("writer.encrypt('").append(password).append("')\n");
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('PDF encrypted successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDF encrypted successfully", "output", outputPath));
    }

    private ToolResult decryptPdf(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        String password = params.getString("password");
        
        if (inputPath == null || outputPath == null || password == null) {
            return ToolResult.failure("input_path, output_path, and password are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF decryption");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfWriter, PdfReader\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("if reader.is_encrypted:\n");
        script.append("    reader.decrypt('").append(password).append("')\n");
        script.append("writer = PdfWriter()\n");
        script.append("for page in reader.pages:\n");
        script.append("    writer.add_page(page)\n");
        script.append("with open('").append(outputPath.replace("\\", "/")).append("', 'wb') as f:\n");
        script.append("    writer.write(f)\n");
        script.append("print('PDF decrypted successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "PDF decrypted successfully", "output", outputPath));
    }

    private ToolResult getPdfInfo(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with pypdf is required for PDF info");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pypdf import PdfReader\n");
        script.append("import json\n");
        script.append("reader = PdfReader('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("info = {\n");
        script.append("    'pages': len(reader.pages),\n");
        script.append("    'encrypted': reader.is_encrypted,\n");
        script.append("    'metadata': {}\n");
        script.append("}\n");
        script.append("if reader.metadata:\n");
        script.append("    for key, value in reader.metadata.items():\n");
        script.append("        info['metadata'][key] = str(value) if value else ''\n");
        script.append("print(json.dumps(info, ensure_ascii=False))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("info", output, "source", inputPath));
    }

    private String parsePageRange(String pages) {
        List<Integer> pageList = new ArrayList<>();
        String[] parts = pages.split(",");
        
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim()) - 1;
                int end = Integer.parseInt(range[1].trim()) - 1;
                for (int i = start; i <= end; i++) {
                    pageList.add(i);
                }
            } else {
                pageList.add(Integer.parseInt(part) - 1);
            }
        }
        
        return pageList.toString();
    }

    private boolean isPythonAvailable() {
        try {
            Process process = new ProcessBuilder("python", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            try {
                Process process = new ProcessBuilder("python3", "--version").start();
                return process.waitFor() == 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private String executePython(String script) throws Exception {
        File tempFile = File.createTempFile("pdf_tool_", ".py");
        tempFile.deleteOnExit();
        
        Files.writeString(tempFile.toPath(), script);
        
        ProcessBuilder pb = new ProcessBuilder("python", tempFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python script failed: " + output);
        }
        
        return output.toString().trim();
    }
}
