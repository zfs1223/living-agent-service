package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class OfficeTool implements Tool {
    private static final String NAME = "office";
    private static final String DESCRIPTION = "Office document operations: Word(docx), Excel(xlsx), PowerPoint(pptx). Create, read, edit documents.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "productivity";
    
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
                .parameter("action", "string", "操作类型: read, create, edit, convert", true)
                .parameter("type", "string", "文档类型: docx, xlsx, pptx", true)
                .parameter("input_path", "string", "输入文件路径", false)
                .parameter("output_path", "string", "输出文件路径", false)
                .parameter("content", "string", "内容 (用于create)", false)
                .parameter("data", "object", "数据 (用于xlsx)", false)
                .parameter("sheet_name", "string", "工作表名称 (xlsx)", false)
                .parameter("slide_title", "string", "幻灯片标题 (pptx)", false)
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
        return List.of("docx_read", "docx_create", "xlsx_read", "xlsx_create", "pptx_read", "pptx_create");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        String type = params.getString("type");
        
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action parameter is required");
        }
        if (type == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("type parameter is required (docx, xlsx, pptx)");
        }

        try {
            ToolResult result;
            switch (type.toLowerCase()) {
                case "docx":
                    result = handleDocx(action, params);
                    break;
                case "xlsx":
                    result = handleXlsx(action, params);
                    break;
                case "pptx":
                    result = handlePptx(action, params);
                    break;
                default:
                    result = ToolResult.failure("Unsupported document type: " + type);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Office operation failed: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        String type = params.getString("type");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action parameter is required");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type parameter is required");
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

    private ToolResult handleDocx(String action, ToolParams params) throws Exception {
        switch (action.toLowerCase()) {
            case "read":
                return readDocx(params);
            case "create":
                return createDocx(params);
            case "edit":
                return editDocx(params);
            default:
                return ToolResult.failure("Unknown action for docx: " + action);
        }
    }

    private ToolResult handleXlsx(String action, ToolParams params) throws Exception {
        switch (action.toLowerCase()) {
            case "read":
                return readXlsx(params);
            case "create":
                return createXlsx(params);
            case "edit":
                return editXlsx(params);
            default:
                return ToolResult.failure("Unknown action for xlsx: " + action);
        }
    }

    private ToolResult handlePptx(String action, ToolParams params) throws Exception {
        switch (action.toLowerCase()) {
            case "read":
                return readPptx(params);
            case "create":
                return createPptx(params);
            default:
                return ToolResult.failure("Unknown action for pptx: " + action);
        }
    }

    private ToolResult readDocx(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with python-docx is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from docx import Document\n");
        script.append("import json\n");
        script.append("doc = Document('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("result = {'paragraphs': [], 'tables': []}\n");
        script.append("for para in doc.paragraphs:\n");
        script.append("    if para.text.strip():\n");
        script.append("        result['paragraphs'].append(para.text)\n");
        script.append("for table in doc.tables:\n");
        script.append("    table_data = []\n");
        script.append("    for row in table.rows:\n");
        script.append("        row_data = [cell.text for cell in row.cells]\n");
        script.append("        table_data.append(row_data)\n");
        script.append("    result['tables'].append(table_data)\n");
        script.append("print(json.dumps(result, ensure_ascii=False))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("content", output, "source", inputPath));
    }

    private ToolResult createDocx(ToolParams params) throws Exception {
        String outputPath = params.getString("output_path");
        String content = params.getString("content");
        
        if (outputPath == null) {
            return ToolResult.failure("output_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with python-docx is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from docx import Document\n");
        script.append("doc = Document()\n");
        
        if (content != null && !content.isBlank()) {
            String[] paragraphs = content.split("\n\n");
            for (String para : paragraphs) {
                String escaped = para.replace("\\", "\\\\").replace("'", "\\'");
                script.append("doc.add_paragraph('").append(escaped).append("')\n");
            }
        }
        
        script.append("doc.save('").append(outputPath.replace("\\", "/")).append("')\n");
        script.append("print('Document created successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Document created successfully", "output", outputPath));
    }

    private ToolResult editDocx(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        String content = params.getString("content");
        
        if (inputPath == null || outputPath == null) {
            return ToolResult.failure("input_path and output_path are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with python-docx is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from docx import Document\n");
        script.append("doc = Document('").append(inputPath.replace("\\", "/")).append("')\n");
        
        if (content != null && !content.isBlank()) {
            String[] paragraphs = content.split("\n\n");
            for (String para : paragraphs) {
                String escaped = para.replace("\\", "\\\\").replace("'", "\\'");
                script.append("doc.add_paragraph('").append(escaped).append("')\n");
            }
        }
        
        script.append("doc.save('").append(outputPath.replace("\\", "/")).append("')\n");
        script.append("print('Document edited successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Document edited successfully", "output", outputPath));
    }

    private ToolResult readXlsx(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String sheetName = params.getString("sheet_name");
        
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with openpyxl is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from openpyxl import load_workbook\n");
        script.append("import json\n");
        script.append("wb = load_workbook('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("result = {'sheets': {}, 'sheet_names': wb.sheetnames}\n");
        
        if (sheetName != null && !sheetName.isBlank()) {
            script.append("ws = wb['").append(sheetName).append("']\n");
            script.append("data = []\n");
            script.append("for row in ws.iter_rows(values_only=True):\n");
            script.append("    data.append(list(row))\n");
            script.append("result['sheets']['").append(sheetName).append("'] = data\n");
        } else {
            script.append("for sheet_name in wb.sheetnames:\n");
            script.append("    ws = wb[sheet_name]\n");
            script.append("    data = []\n");
            script.append("    for row in ws.iter_rows(values_only=True):\n");
            script.append("        data.append(list(row))\n");
            script.append("    result['sheets'][sheet_name] = data\n");
        }
        
        script.append("print(json.dumps(result, ensure_ascii=False, default=str))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("content", output, "source", inputPath));
    }

    private ToolResult createXlsx(ToolParams params) throws Exception {
        String outputPath = params.getString("output_path");
        String sheetName = params.getString("sheet_name");
        if (sheetName == null) sheetName = "Sheet1";
        
        if (outputPath == null) {
            return ToolResult.failure("output_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with openpyxl is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from openpyxl import Workbook\n");
        script.append("wb = Workbook()\n");
        script.append("ws = wb.active\n");
        script.append("ws.title = '").append(sheetName).append("'\n");
        
        @SuppressWarnings("unchecked")
        List<List<Object>> data = params.get("data");
        if (data != null && !data.isEmpty()) {
            int row = 1;
            for (List<Object> rowData : data) {
                int col = 1;
                for (Object cell : rowData) {
                    if (cell != null) {
                        script.append("ws.cell(row=").append(row).append(", column=").append(col).append(", value='").append(escapeString(cell.toString())).append("')\n");
                    }
                    col++;
                }
                row++;
            }
        }
        
        script.append("wb.save('").append(outputPath.replace("\\", "/")).append("')\n");
        script.append("print('Spreadsheet created successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Spreadsheet created successfully", "output", outputPath));
    }

    private ToolResult editXlsx(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        String outputPath = params.getString("output_path");
        
        if (inputPath == null || outputPath == null) {
            return ToolResult.failure("input_path and output_path are required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with openpyxl is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from openpyxl import load_workbook\n");
        script.append("wb = load_workbook('").append(inputPath.replace("\\", "/")).append("')\n");
        
        @SuppressWarnings("unchecked")
        List<List<Object>> data = params.get("data");
        if (data != null && !data.isEmpty()) {
            String sheetName = params.getString("sheet_name");
            if (sheetName == null) sheetName = "Sheet1";
            script.append("ws = wb['").append(sheetName).append("']\n");
            
            script.append("row = ws.max_row + 1\n");
            for (List<Object> rowData : data) {
                int col = 1;
                for (Object cell : rowData) {
                    if (cell != null) {
                        script.append("ws.cell(row=row, column=").append(col).append(", value='").append(escapeString(cell.toString())).append("')\n");
                    }
                    col++;
                }
                script.append("row += 1\n");
            }
        }
        
        script.append("wb.save('").append(outputPath.replace("\\", "/")).append("')\n");
        script.append("print('Spreadsheet edited successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Spreadsheet edited successfully", "output", outputPath));
    }

    private ToolResult readPptx(ToolParams params) throws Exception {
        String inputPath = params.getString("input_path");
        if (inputPath == null) {
            return ToolResult.failure("input_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with python-pptx is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pptx import Presentation\n");
        script.append("import json\n");
        script.append("prs = Presentation('").append(inputPath.replace("\\", "/")).append("')\n");
        script.append("result = {'slides': []}\n");
        script.append("for i, slide in enumerate(prs.slides):\n");
        script.append("    slide_info = {'slide_number': i+1, 'shapes': []}\n");
        script.append("    for shape in slide.shapes:\n");
        script.append("        if hasattr(shape, 'text') and shape.text.strip():\n");
        script.append("            slide_info['shapes'].append(shape.text)\n");
        script.append("    result['slides'].append(slide_info)\n");
        script.append("print(json.dumps(result, ensure_ascii=False))\n");

        String output = executePython(script.toString());
        return ToolResult.success(Map.of("content", output, "source", inputPath));
    }

    private ToolResult createPptx(ToolParams params) throws Exception {
        String outputPath = params.getString("output_path");
        String title = params.getString("slide_title");
        if (title == null) title = "New Presentation";
        String content = params.getString("content");
        
        if (outputPath == null) {
            return ToolResult.failure("output_path is required");
        }

        if (!isPythonAvailable()) {
            return ToolResult.failure("Python with python-pptx is required");
        }

        StringBuilder script = new StringBuilder();
        script.append("from pptx import Presentation\n");
        script.append("prs = Presentation()\n");
        
        script.append("slide_layout = prs.slide_layouts[0]\n");
        script.append("slide = prs.slides.add_slide(slide_layout)\n");
        script.append("title_shape = slide.shapes.title\n");
        script.append("title_shape.text = '").append(escapeString(title)).append("'\n");
        
        if (content != null && !content.isBlank()) {
            String[] slides = content.split("\n---\n");
            for (String slideContent : slides) {
                script.append("slide_layout = prs.slide_layouts[1]\n");
                script.append("slide = prs.slides.add_slide(slide_layout)\n");
                String[] lines = slideContent.split("\n", 2);
                if (lines.length > 0) {
                    script.append("slide.shapes.title.text = '").append(escapeString(lines[0])).append("'\n");
                }
                if (lines.length > 1) {
                    script.append("for shape in slide.placeholders:\n");
                    script.append("    if shape.placeholder_format.idx == 1:\n");
                    script.append("        shape.text = '").append(escapeString(lines[1])).append("'\n");
                    script.append("        break\n");
                }
            }
        }
        
        script.append("prs.save('").append(outputPath.replace("\\", "/")).append("')\n");
        script.append("print('Presentation created successfully')\n");

        executePython(script.toString());
        return ToolResult.success(Map.of("message", "Presentation created successfully", "output", outputPath));
    }

    private String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
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
        File tempFile = File.createTempFile("office_tool_", ".py");
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
