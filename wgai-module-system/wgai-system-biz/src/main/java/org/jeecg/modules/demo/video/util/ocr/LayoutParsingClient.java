package org.jeecg.modules.demo.video.util.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * PaddleOCR布局解析客户端 - 修复版
 * @author wggg
 * @date 2025/11/13
 */
public class LayoutParsingClient {

    private static final String API_URL = "https://j2gcqdb6l2w9i826.aistudio-app.com/layout-parsing";
    private static final String TOKEN = "ecc7c6727e38b817f617ecab994a52465b111163";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public LayoutParsingClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static class ParseResult {
        private List<PageResult> pages;
        private DataInfo dataInfo;

        public List<PageResult> getPages() { return pages; }
        public void setPages(List<PageResult> pages) { this.pages = pages; }
        public DataInfo getDataInfo() { return dataInfo; }
        public void setDataInfo(DataInfo dataInfo) { this.dataInfo = dataInfo; }
    }

    public static class PageResult {
        private int pageIndex;
        private JsonNode prunedResult;
        private MarkdownResult markdown;
        private Map<String, String> outputImages;
        private String inputImage;

        public int getPageIndex() { return pageIndex; }
        public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
        public JsonNode getPrunedResult() { return prunedResult; }
        public void setPrunedResult(JsonNode prunedResult) { this.prunedResult = prunedResult; }
        public MarkdownResult getMarkdown() { return markdown; }
        public void setMarkdown(MarkdownResult markdown) { this.markdown = markdown; }
        public Map<String, String> getOutputImages() { return outputImages; }
        public void setOutputImages(Map<String, String> outputImages) { this.outputImages = outputImages; }
        public String getInputImage() { return inputImage; }
        public void setInputImage(String inputImage) { this.inputImage = inputImage; }
    }

    public static class MarkdownResult {
        private String text;
        private Map<String, String> images;
        private boolean isStart;
        private boolean isEnd;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Map<String, String> getImages() { return images; }
        public void setImages(Map<String, String> images) { this.images = images; }
        public boolean isStart() { return isStart; }
        public void setStart(boolean start) { isStart = start; }
        public boolean isEnd() { return isEnd; }
        public void setEnd(boolean end) { isEnd = end; }
    }

    public static class DataInfo {
        private String inputType;
        private int totalPages;

        public String getInputType() { return inputType; }
        public void setInputType(String inputType) { this.inputType = inputType; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }

    public ParseResult parseDocument(String filePath, int fileType) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + filePath);
        }

        long fileSize = file.length();
        System.out.println("文件大小: " + (fileSize / 1024) + " KB");

        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        String fileData = Base64.getEncoder().encodeToString(fileBytes);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("file", fileData);
        payload.put("fileType", fileType);
        payload.put("useDocOrientationClassify", false);
        payload.put("useDocUnwarping", false);
        payload.put("useChartRecognition", false);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "token " + TOKEN)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonPayload, JSON))
                .build();

        System.out.println("发送解析请求...");

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            System.out.println("响应状态码: " + statusCode);

            String responseBody = response.body() != null ? response.body().string() : "";

            if (statusCode != 200) {
                System.err.println("错误响应: " + responseBody);
                throw new RuntimeException("请求失败 [" + statusCode + "]");
            }

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (!jsonResponse.has("result")) {
                throw new RuntimeException("响应中缺少result字段");
            }

            return parseResult(jsonResponse.get("result"));
        }
    }

    private ParseResult parseResult(JsonNode result) {
        ParseResult parseResult = new ParseResult();

        if (result.has("layoutParsingResults")) {
            JsonNode layoutResults = result.get("layoutParsingResults");
            List<PageResult> pages = new ArrayList<>();

            for (int i = 0; i < layoutResults.size(); i++) {
                JsonNode pageNode = layoutResults.get(i);
                PageResult page = new PageResult();
                page.setPageIndex(i);

                if (pageNode.has("prunedResult")) {
                    page.setPrunedResult(pageNode.get("prunedResult"));
                }

                if (pageNode.has("markdown")) {
                    page.setMarkdown(parseMarkdown(pageNode.get("markdown")));
                }

                if (pageNode.has("outputImages") && !pageNode.get("outputImages").isNull()) {
                    page.setOutputImages(parseImages(pageNode.get("outputImages")));
                }

                if (pageNode.has("inputImage") && !pageNode.get("inputImage").isNull()) {
                    page.setInputImage(pageNode.get("inputImage").asText());
                }

                pages.add(page);
            }

            parseResult.setPages(pages);
        }

        if (result.has("dataInfo")) {
            DataInfo dataInfo = new DataInfo();
            JsonNode dataInfoNode = result.get("dataInfo");

            if (dataInfoNode.has("inputType")) {
                dataInfo.setInputType(dataInfoNode.get("inputType").asText());
            }
            if (dataInfoNode.has("totalPages")) {
                dataInfo.setTotalPages(dataInfoNode.get("totalPages").asInt());
            }

            parseResult.setDataInfo(dataInfo);
        }

        return parseResult;
    }

    private MarkdownResult parseMarkdown(JsonNode markdownNode) {
        MarkdownResult markdown = new MarkdownResult();

        if (markdownNode.has("text")) {
            markdown.setText(markdownNode.get("text").asText());
        }

        if (markdownNode.has("images") && !markdownNode.get("images").isNull()) {
            markdown.setImages(parseImages(markdownNode.get("images")));
        }

        if (markdownNode.has("isStart")) {
            markdown.setStart(markdownNode.get("isStart").asBoolean());
        }

        if (markdownNode.has("isEnd")) {
            markdown.setEnd(markdownNode.get("isEnd").asBoolean());
        }

        return markdown;
    }

    private Map<String, String> parseImages(JsonNode imagesNode) {
        Map<String, String> images = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = imagesNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            images.put(entry.getKey(), entry.getValue().asText());
        }

        return images;
    }

    public void saveResults(ParseResult parseResult, String outputDir) throws Exception {
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        System.out.println("\n========================================");
        System.out.println("开始保存结果...");
        System.out.println("========================================");

        List<PageResult> pages = parseResult.getPages();

        for (int i = 0; i < pages.size(); i++) {
            PageResult page = pages.get(i);
            System.out.println("\n处理第 " + (i + 1) + "/" + pages.size() + " 页");

            // 1. 保存Markdown文本
            if (page.getMarkdown() != null && page.getMarkdown().getText() != null) {
                String mdPath = outputDir + File.separator + "page_" + i + ".md";
                writeStringToFile(mdPath, page.getMarkdown().getText());
                System.out.println("  ✓ Markdown已保存: " + mdPath);

                // 2. 保存Markdown中的图片
                if (page.getMarkdown().getImages() != null && !page.getMarkdown().getImages().isEmpty()) {
                    System.out.println("  保存Markdown图片...");
                    for (Map.Entry<String, String> entry : page.getMarkdown().getImages().entrySet()) {
                        String imgPath = entry.getKey();
                        String imgData = entry.getValue();

                        String fullPath = outputDir + File.separator + imgPath;
                        Path imgFilePath = Paths.get(fullPath);
                        if (imgFilePath.getParent() != null) {
                            Files.createDirectories(imgFilePath.getParent());
                        }

                        // 判断是URL还是Base64
                        if (isUrl(imgData)) {
                            downloadImage(imgData, fullPath);
                        } else {
                            saveBase64Image(imgData, fullPath);
                        }
                        System.out.println("    ✓ " + imgPath);
                    }
                }
            }

            // 3. 保存输出图片 (通常是URL)
            if (page.getOutputImages() != null && !page.getOutputImages().isEmpty()) {
                System.out.println("  保存输出图片...");
                for (Map.Entry<String, String> entry : page.getOutputImages().entrySet()) {
                    String imgName = entry.getKey();
                    String imgData = entry.getValue();

                    String filename = outputDir + File.separator + imgName + "_page" + i + ".jpg";

                    try {
                        // 判断是URL还是Base64
                        if (isUrl(imgData)) {
                            downloadImage(imgData, filename);
                            System.out.println("    ✓ " + imgName + " (从URL下载)");
                        } else {
                            saveBase64Image(imgData, filename);
                            System.out.println("    ✓ " + imgName + " (Base64解码)");
                        }
                    } catch (Exception e) {
                        System.err.println("    ✗ " + imgName + " 保存失败: " + e.getMessage());
                    }
                }
            }

            // 4. 保存输入图片 (通常是Base64)
            if (page.getInputImage() != null) {
                String inputPath = outputDir + File.separator + "input_page" + i + ".jpg";
                try {
                    if (isUrl(page.getInputImage())) {
                        downloadImage(page.getInputImage(), inputPath);
                    } else {
                        saveBase64Image(page.getInputImage(), inputPath);
                    }
                    System.out.println("  ✓ 输入图片已保存: input_page" + i + ".jpg");
                } catch (Exception e) {
                    System.err.println("  ✗ 输入图片保存失败: " + e.getMessage());
                }
            }

            // 5. 保存prunedResult JSON
            if (page.getPrunedResult() != null) {
                String jsonPath = outputDir + File.separator + "pruned_page" + i + ".json";
                writeStringToFile(jsonPath, page.getPrunedResult().toPrettyString());
                System.out.println("  ✓ JSON结果已保存: pruned_page" + i + ".json");
            }
        }

        // 6. 保存汇总信息
        String summaryPath = outputDir + File.separator + "summary.txt";
        StringBuilder summary = new StringBuilder();
        summary.append("解析汇总\n");
        summary.append("========================================\n");

        if (parseResult.getDataInfo() != null) {
            summary.append("输入类型: ").append(parseResult.getDataInfo().getInputType()).append("\n");
            summary.append("总页数: ").append(parseResult.getDataInfo().getTotalPages()).append("\n");
        }

        summary.append("实际处理页数: ").append(pages.size()).append("\n");

        writeStringToFile(summaryPath, summary.toString());
        System.out.println("\n✓ 汇总信息已保存: " + summaryPath);

        System.out.println("\n========================================");
        System.out.println("✓ 所有结果保存完成!");
        System.out.println("========================================");
    }

    /**
     * 判断是否为URL
     */
    private boolean isUrl(String str) {
        return str != null && (str.startsWith("http://") || str.startsWith("https://"));
    }

    /**
     * 从URL下载图片
     */
    private boolean downloadImage(String url, String savePath) {
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                byte[] imageBytes = response.body().bytes();
                Files.write(Paths.get(savePath), imageBytes);
                return true;
            } else {
                System.err.println("下载失败,状态码: " + response.code());
                return false;
            }
        } catch (Exception e) {
            System.err.println("下载异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存Base64图片
     */
    private void saveBase64Image(String base64Data, String savePath) throws IOException {
        // 移除可能的data URL前缀
        String cleanBase64 = base64Data;
        if (base64Data.contains(",")) {
            cleanBase64 = base64Data.split(",")[1];
        }

        byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
        Files.write(Paths.get(savePath), imageBytes);
    }

    /**
     * 写入字符串到文件
     */
    private void writeStringToFile(String filename, String content) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * 打印解析结果概览
     */
    public void printSummary(ParseResult parseResult) {
        System.out.println("\n========================================");
        System.out.println("解析结果概览");
        System.out.println("========================================");

        if (parseResult.getDataInfo() != null) {
            System.out.println("输入类型: " + parseResult.getDataInfo().getInputType());
            System.out.println("总页数: " + parseResult.getDataInfo().getTotalPages());
        }

        List<PageResult> pages = parseResult.getPages();
        System.out.println("实际处理页数: " + pages.size());
        System.out.println();

        for (int i = 0; i < pages.size(); i++) {
            PageResult page = pages.get(i);
            System.out.println("第 " + (i + 1) + " 页:");

            if (page.getMarkdown() != null) {
                MarkdownResult md = page.getMarkdown();
                System.out.println("  Markdown长度: " + (md.getText() != null ? md.getText().length() : 0) + " 字符");
                System.out.println("  Markdown图片数: " + (md.getImages() != null ? md.getImages().size() : 0));
                System.out.println("  段落开始: " + md.isStart());
                System.out.println("  段落结束: " + md.isEnd());
            }

            if (page.getOutputImages() != null) {
                System.out.println("  输出图片数: " + page.getOutputImages().size());
            }

            if (page.getInputImage() != null) {
                System.out.println("  包含输入图片: 是");
            }

            System.out.println();
        }

        System.out.println("========================================");
    }

    public static void main(String[] args) {
        try {
            LayoutParsingClient client = new LayoutParsingClient();

            String filePath = "F:\\JAVAAI\\test_image.jpg";
            int fileType = 1;
            String outputDir = "F:\\JAVAAI\\layout_output";

            System.out.println("========================================");
            System.out.println("PaddleOCR 布局解析");
            System.out.println("========================================");
            System.out.println("文件: " + filePath);
            System.out.println("类型: " + (fileType == 0 ? "PDF" : "图片"));
            System.out.println("输出: " + outputDir);
            System.out.println("========================================");

            ParseResult result = client.parseDocument(filePath, fileType);
            client.printSummary(result);
            client.saveResults(result, outputDir);

            System.out.println("\n✓ 完成!");

        } catch (Exception e) {
            System.err.println("✗ 发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}