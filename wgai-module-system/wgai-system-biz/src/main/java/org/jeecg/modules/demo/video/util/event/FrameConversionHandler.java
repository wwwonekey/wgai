package org.jeecg.modules.demo.video.util.event;

import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 阶段1：Frame到Image转换处理器
 * 目标：快速转换，释放Frame资源
 *  * @author wggg
 *  * @date 2025/8/1 9:32
 */
@Slf4j
public  class FrameConversionHandler implements EventHandler<FrameProcessEvent> {
    // 使用ThreadLocal避免多线程并发问题
    private static final ThreadLocal<Java2DFrameConverter> converterLocal =
            ThreadLocal.withInitial(Java2DFrameConverter::new);

    private final AtomicLong convertedFrames = new AtomicLong(0);
    private final AtomicLong failedFrames = new AtomicLong(0);

    @Override
    public void onEvent(FrameProcessEvent event, long sequence, boolean endOfBatch) throws Exception {
        long startTime = System.nanoTime();

        try {
            Frame frame = event.getFrame();
            // 增强的Frame有效性检查
            if (!isValidFrame(frame)) {
                String reason = getInvalidFrameReason(frame);
                log.debug("[Frame无效] 流: {}, 原因: {}", event.getStreamName(), reason);
                event.setError(new IllegalArgumentException("Frame无效: " + reason));
                return;
            }
            // 获取当前线程的转换器实例
            Java2DFrameConverter converter = converterLocal.get();

            BufferedImage image = null;
            try {
                // 安全转换Frame到BufferedImage
                image = converter.getBufferedImage(frame);
            } catch (Exception e) {
                log.warn("[Frame转换失败] 流: {}, 错误: {}", event.getStreamName(), e.getMessage());
                failedFrames.incrementAndGet();
                event.setError(e);
                return;
            }

            event.setImage(image);

            // 立即释放Frame资源，减少内存占用
            // 但不清空引用，后续处理器可能需要检查
            try {
                frame.close();
                // 注意：不设置为null，避免影响其他处理器
                // event.setFrame(null);
            } catch (Exception e) {
                log.debug("[Frame释放异常] 流: {}, 忽略: {}", event.getStreamName(), e.getMessage());
            }


            // 性能统计
            long converted = convertedFrames.incrementAndGet();
            if (converted % 1000 == 0) {
                long processTime = System.nanoTime() - startTime;
                long failed = failedFrames.get();
                double successRate = (double) converted / (converted + failed) * 100;

                log.info("[Frame转换统计] 流: {}, 成功: {}, 失败: {}, 成功率: {:.2f}%, 耗时: {}微秒",
                        event.getStreamName(), converted, failed, successRate, processTime / 1000);
            }

        } catch (Exception e) {
            log.error("[Frame转换异常] 流: {}", event.getStreamName(), e);
            event.setError(e);
            failedFrames.incrementAndGet();
        }
    }

    /**
     * 检查Frame是否有效
     */
    private boolean isValidFrame(Frame frame) {
        if (frame == null) {
            return false;
        }

        // 检查Frame是否有图像数据
        if (frame.image == null && frame.samples == null) {
            return false;
        }

        // 如果有图像数据，检查Buffer是否有效
        if (frame.image != null) {
            if (frame.image.length == 0) {
                return false;
            }

            // 检查图像Buffer是否为空
            for (int i = 0; i < frame.image.length; i++) {
                if (frame.image[i] != null && frame.image[i].capacity() > 0) {
                    return true; // 至少有一个有效的图像平面
                }
            }
            return false;
        }

        // 如果有音频数据（虽然我们主要处理视频）
        if (frame.samples != null) {
            if (frame.samples.length == 0) {
                return false;
            }

            for (int i = 0; i < frame.samples.length; i++) {
                if (frame.samples[i] != null && frame.samples[i].capacity() > 0) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    /**
     * 获取Frame无效的具体原因
     */
    private String getInvalidFrameReason(Frame frame) {
        if (frame == null) {
            return "Frame为null";
        }
        if (frame.image == null && frame.samples == null) {
            return "Frame无图像和音频数据";
        }
        if (frame.image != null && frame.image.length == 0) {
            return "图像Buffer数组为空";
        }
        if (frame.image != null) {
            for (int i = 0; i < frame.image.length; i++) {
                if (frame.image[i] == null) {
                    return "图像Buffer[" + i + "]为null";
                }
                if (frame.image[i].capacity() == 0) {
                    return "图像Buffer[" + i + "]容量为0";
                }
            }
        }
        return "未知原因";
    }

    /**
     * 检查转换后的BufferedImage是否有效
     */
    private boolean isValidImage(BufferedImage image) {
        return image != null &&
                image.getWidth() > 0 &&
                image.getHeight() > 0 &&
                image.getWidth() <= 10000 &&  // 防止异常大的图像
                image.getHeight() <= 10000;
    }

    /**
     * 获取BufferedImage无效的具体原因
     */
    private String getInvalidImageReason(BufferedImage image) {
        if (image == null) {
            return "BufferedImage为null";
        }
        if (image.getWidth() <= 0) {
            return "图像宽度无效: " + image.getWidth();
        }
        if (image.getHeight() <= 0) {
            return "图像高度无效: " + image.getHeight();
        }
        if (image.getWidth() > 10000 || image.getHeight() > 10000) {
            return "图像尺寸过大: " + image.getWidth() + "x" + image.getHeight();
        }
        return "未知原因";
    }

    /**
     * 清理ThreadLocal资源
     */
    public static void cleanup() {
        converterLocal.remove();
    }
}
