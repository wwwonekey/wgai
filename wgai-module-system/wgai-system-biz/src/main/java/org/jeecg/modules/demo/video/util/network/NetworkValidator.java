package org.jeecg.modules.demo.video.util.network;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络带宽和延迟验证工具
 * 用于诊断RTSP视频流网络问题
 *
 * @author wggg
 * @date 2025/9/3
 */
@Slf4j
@Component
public class NetworkValidator {

    private static final int DEFAULT_TIMEOUT = 10000; // 10秒超时
    private static final int PING_COUNT = 10; // ping次数
    private static final int BANDWIDTH_TEST_DURATION = 10; // 带宽测试持续时间(秒)

    /**
     * 网络诊断结果
     */
    public static class NetworkDiagnosticResult {
        private String host;
        private int port;
        private boolean isReachable;
        private double avgLatency; // 平均延迟(ms)
        private double minLatency; // 最小延迟(ms)
        private double maxLatency; // 最大延迟(ms)
        private double packetLoss; // 丢包率(%)
        private double estimatedBandwidth; // 估算带宽(Mbps)
        private long tcpConnectTime; // TCP连接时间(ms)
        private boolean isRtspAccessible; // RTSP是否可访问
        private String diagnosis; // 诊断建议

        // getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isReachable() { return isReachable; }
        public void setReachable(boolean reachable) { isReachable = reachable; }
        public double getAvgLatency() { return avgLatency; }
        public void setAvgLatency(double avgLatency) { this.avgLatency = avgLatency; }
        public double getMinLatency() { return minLatency; }
        public void setMinLatency(double minLatency) { this.minLatency = minLatency; }
        public double getMaxLatency() { return maxLatency; }
        public void setMaxLatency(double maxLatency) { this.maxLatency = maxLatency; }
        public double getPacketLoss() { return packetLoss; }
        public void setPacketLoss(double packetLoss) { this.packetLoss = packetLoss; }
        public double getEstimatedBandwidth() { return estimatedBandwidth; }
        public void setEstimatedBandwidth(double estimatedBandwidth) { this.estimatedBandwidth = estimatedBandwidth; }
        public long getTcpConnectTime() { return tcpConnectTime; }
        public void setTcpConnectTime(long tcpConnectTime) { this.tcpConnectTime = tcpConnectTime; }
        public boolean isRtspAccessible() { return isRtspAccessible; }
        public void setRtspAccessible(boolean rtspAccessible) { isRtspAccessible = rtspAccessible; }
        public String getDiagnosis() { return diagnosis; }
        public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

        @Override
        public String toString() {
            return String.format(
                    "网络诊断结果:\n" +
                            "主机: %s:%d\n" +
                            "连通性: %s\n" +
                            "平均延迟: %.2f ms\n" +
                            "延迟范围: %.2f - %.2f ms\n" +
                            "丢包率: %.2f%%\n" +
                            "估算带宽: %.2f Mbps\n" +
                            "TCP连接时间: %d ms\n" +
                            "RTSP可访问性: %s\n" +
                            "诊断建议: %s",
                    host, port, isReachable ? "正常" : "异常",
                    avgLatency, minLatency, maxLatency, packetLoss,
                    estimatedBandwidth, tcpConnectTime,
                    isRtspAccessible ? "可访问" : "不可访问",
                    diagnosis
            );
        }
    }

    /**
     * 从RTSP URL提取主机和端口
     */
    private String[] parseRtspUrl(String rtspUrl) {
        try {
            // 示例: rtsp://192.168.1.100:554/stream
            if (!rtspUrl.toLowerCase().startsWith("rtsp://")) {
                return new String[]{rtspUrl, "554"}; // 默认RTSP端口
            }

            String urlPart = rtspUrl.substring(7); // 移除 "rtsp://"
            int portIndex = urlPart.indexOf(':');
            int pathIndex = urlPart.indexOf('/');

            String host;
            String port = "554"; // 默认RTSP端口

            if (portIndex > 0) {
                host = urlPart.substring(0, portIndex);
                int endIndex = pathIndex > 0 ? pathIndex : urlPart.length();
                port = urlPart.substring(portIndex + 1, endIndex);
            } else {
                int endIndex = pathIndex > 0 ? pathIndex : urlPart.length();
                host = urlPart.substring(0, endIndex);
            }

            return new String[]{host, port};
        } catch (Exception e) {
            log.error("[解析RTSP URL失败] URL: {}", rtspUrl, e);
            return new String[]{rtspUrl, "554"};
        }
    }

    /**
     * 完整的网络诊断
     */
    public NetworkDiagnosticResult diagnoseNetwork(String rtspUrl) {
        String[] hostPort = parseRtspUrl(rtspUrl);
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        log.info("[开始网络诊断] 目标: {}:{}", host, port);

        NetworkDiagnosticResult result = new NetworkDiagnosticResult();
        result.setHost(host);
        result.setPort(port);

        // 1. 基本连通性测试
        result.setReachable(testReachability(host));

        // 2. 延迟测试
        LatencyResult latencyResult = testLatency(host);
        result.setAvgLatency(latencyResult.avgLatency);
        result.setMinLatency(latencyResult.minLatency);
        result.setMaxLatency(latencyResult.maxLatency);
        result.setPacketLoss(latencyResult.packetLoss);

        // 3. TCP连接测试
        result.setTcpConnectTime(testTcpConnection(host, port));

        // 4. 带宽估算
        result.setEstimatedBandwidth(estimateBandwidth(host, port));

        // 5. RTSP协议测试
        result.setRtspAccessible(testRtspAccess(rtspUrl));

        // 6. 生成诊断建议
        result.setDiagnosis(generateDiagnosis(result));

        log.info("[网络诊断完成]\n{}", result);
        return result;
    }

    /**
     * 测试基本连通性
     */
    private boolean testReachability(String host) {
        try {
            log.info("[测试连通性] 目标: {}", host);
            InetAddress address = InetAddress.getByName(host);
            boolean reachable = address.isReachable(5000);
            log.info("[连通性测试结果] {}: {}", host, reachable ? "可达" : "不可达");
            return reachable;
        } catch (Exception e) {
            log.error("[连通性测试失败] 目标: {}", host, e);
            return false;
        }
    }

    /**
     * 延迟结果
     */
    private static class LatencyResult {
        double avgLatency;
        double minLatency = Double.MAX_VALUE;
        double maxLatency = 0;
        double packetLoss;
    }

    /**
     * 测试网络延迟
     */
    private LatencyResult testLatency(String host) {
        log.info("[测试延迟] 目标: {}, 次数: {}", host, PING_COUNT);

        LatencyResult result = new LatencyResult();
        List<Double> latencies = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < PING_COUNT; i++) {
            long startTime = System.nanoTime();
            try {
                boolean reachable = InetAddress.getByName(host).isReachable(3000);
                long endTime = System.nanoTime();

                if (reachable) {
                    double latency = (endTime - startTime) / 1_000_000.0; // 转换为毫秒
                    latencies.add(latency);
                    successCount++;

                    result.minLatency = Math.min(result.minLatency, latency);
                    result.maxLatency = Math.max(result.maxLatency, latency);

                    log.debug("[延迟测试] 第{}次: {:.2f}ms", i + 1, latency);
                }
            } catch (Exception e) {
                log.debug("[延迟测试失败] 第{}次", i + 1);
            }

            // 间隔100ms
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (successCount > 0) {
            result.avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            result.packetLoss = ((double) (PING_COUNT - successCount) / PING_COUNT) * 100;
        } else {
            result.avgLatency = -1;
            result.minLatency = -1;
            result.maxLatency = -1;
            result.packetLoss = 100;
        }

        log.info("[延迟测试结果] 平均: {:.2f}ms, 范围: {:.2f}-{:.2f}ms, 丢包率: {:.2f}%",
                result.avgLatency, result.minLatency, result.maxLatency, result.packetLoss);

        return result;
    }

    /**
     * 测试TCP连接时间
     */
    private long testTcpConnection(String host, int port) {
        log.info("[测试TCP连接] 目标: {}:{}", host, port);

        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000);
            long connectTime = System.currentTimeMillis() - startTime;
            log.info("[TCP连接成功] 耗时: {}ms", connectTime);
            return connectTime;
        } catch (Exception e) {
            long connectTime = System.currentTimeMillis() - startTime;
            log.error("[TCP连接失败] 耗时: {}ms", connectTime, e);
            return -1;
        }
    }

    /**
     * 估算网络带宽
     */
    private double estimateBandwidth(String host, int port) {
        log.info("[估算带宽] 目标: {}:{}", host, port);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000);
            socket.setSoTimeout(5000);

            // 发送测试数据
            byte[] testData = new byte[1024 * 8]; // 8KB测试数据
            Arrays.fill(testData, (byte) 0x55);

            long startTime = System.currentTimeMillis();
            long totalBytes = 0;
            int iterations = 0;

            try (OutputStream out = socket.getOutputStream()) {
                // 发送数据5秒
                while (System.currentTimeMillis() - startTime < 5000 && iterations < 100) {
                    out.write(testData);
                    out.flush();
                    totalBytes += testData.length;
                    iterations++;
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                log.debug("[带宽测试发送异常]", e);
            }

            long duration = System.currentTimeMillis() - startTime;
            if (duration > 0 && totalBytes > 0) {
                double bandwidth = (totalBytes * 8.0 / 1024 / 1024) / (duration / 1000.0); // Mbps
                log.info("[带宽估算结果] {:.2f} Mbps (发送 {} bytes 耗时 {} ms)", bandwidth, totalBytes, duration);
                return bandwidth;
            }

        } catch (Exception e) {
            log.error("[带宽估算失败]", e);
        }

        return -1;
    }

    /**
     * 测试RTSP协议访问
     */
    private boolean testRtspAccess(String rtspUrl) {
        log.info("[测试RTSP访问] URL: {}", rtspUrl);

        String[] hostPort = parseRtspUrl(rtspUrl);
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000);
            socket.setSoTimeout(5000);

            // 发送RTSP OPTIONS请求
            String optionsRequest = String.format(
                    "OPTIONS %s RTSP/1.0\r\n" +
                            "CSeq: 1\r\n" +
                            "User-Agent: NetworkValidator/1.0\r\n" +
                            "\r\n", rtspUrl);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.print(optionsRequest);
                out.flush();

                String response = in.readLine();
                if (response != null && response.contains("RTSP/1.0")) {
                    log.info("[RTSP访问测试] 成功收到响应: {}", response);
                    return response.contains("200") || response.contains("401"); // 200 OK 或 401 需要认证都表示可访问
                } else {
                    log.warn("[RTSP访问测试] 无效响应: {}", response);
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("[RTSP访问测试失败]", e);
            return false;
        }
    }

    /**
     * 生成诊断建议
     */
    private String generateDiagnosis(NetworkDiagnosticResult result) {
        StringBuilder diagnosis = new StringBuilder();
        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // 连通性问题
        if (!result.isReachable()) {
            issues.add("主机不可达");
            recommendations.add("检查网络连接和防火墙设置");
        }

        // 延迟问题
        if (result.getAvgLatency() > 100) {
            issues.add("网络延迟过高(" + String.format("%.2f", result.getAvgLatency()) + "ms)");
            recommendations.add("优化网络路由或考虑使用更近的服务器");
        }

        // 丢包问题
        if (result.getPacketLoss() > 5) {
            issues.add("丢包率过高(" + String.format("%.2f", result.getPacketLoss()) + "%)");
            recommendations.add("检查网络稳定性，考虑使用TCP传输");
        }

        // TCP连接问题
        if (result.getTcpConnectTime() > 3000 || result.getTcpConnectTime() == -1) {
            issues.add("TCP连接时间过长或失败");
            recommendations.add("检查目标端口是否开放，增加连接超时时间");
        }

        // 带宽问题
        if (result.getEstimatedBandwidth() < 2) {
            issues.add("可用带宽不足(" + String.format("%.2f", result.getEstimatedBandwidth()) + "Mbps)");
            recommendations.add("降低视频码率或升级网络带宽");
        }

        // RTSP协议问题
        if (!result.isRtspAccessible()) {
            issues.add("RTSP协议不可访问");
            recommendations.add("检查RTSP服务状态和认证信息");
        }

        // 生成诊断报告
        if (issues.isEmpty()) {
            diagnosis.append("网络状况良好");
        } else {
            diagnosis.append("发现以下问题: ");
            diagnosis.append(String.join("; ", issues));
            diagnosis.append("\n建议: ");
            diagnosis.append(String.join("; ", recommendations));
        }

        // 针对RTSP视频流的特殊建议
        if (result.getAvgLatency() > 50 || result.getPacketLoss() > 2) {
            diagnosis.append("\n视频流优化建议: ");
            diagnosis.append("使用TCP传输模式; 增大接收缓冲区; 启用自动重连; 考虑降低视频分辨率");
        }

        return diagnosis.toString();
    }

    /**
     * 快速网络检查（用于实时监控）
     */
    public boolean quickNetworkCheck(String rtspUrl) {
        String[] hostPort = parseRtspUrl(rtspUrl);
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        // 简单的连通性和TCP连接测试
        try {
            long startTime = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
                long connectTime = System.currentTimeMillis() - startTime;

                boolean result = connectTime < 2000; // 2秒内连接成功
                log.debug("[快速网络检查] {}:{} - {} ({}ms)", host, port,
                        result ? "正常" : "异常", connectTime);
                return result;
            }
        } catch (Exception e) {
            log.debug("[快速网络检查失败] {}:{}", host, port);
            return false;
        }
    }

    /**
     * 网络质量持续监控
     */
    public void startNetworkMonitoring(String rtspUrl, int intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean networkOk = quickNetworkCheck(rtspUrl);
                if (!networkOk) {
                    log.warn("[网络监控] 网络质量下降: {}", rtspUrl);
                }
            } catch (Exception e) {
                log.error("[网络监控异常]", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
}
