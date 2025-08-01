package org.jeecg.modules.demo.video.util.event;



import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 阶段2：Image到Mat转换处理器
 * 目标：并行转换，准备AI处理
 * @author wggg
 * @date 2025/8/1 9:33
 */
@Slf4j
public  class ImageToMatHandler implements EventHandler<FrameProcessEvent> {
    private final AtomicLong convertedMats = new AtomicLong(0);

    @Override
    public void onEvent(FrameProcessEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getError() != null) {
            return; // 跳过有错误的事件
        }

        try {
            BufferedImage image = event.getImage();
            if (image == null) {
                event.setError(new IllegalArgumentException("BufferedImage为空"));
                return;
            }

            // 转换为Mat
            Mat mat = bufferedImageToMat(image);
            if (mat == null || mat.empty()) {
                event.setError(new IllegalArgumentException("Mat转换失败"));
                return;
            }

            event.setMat(mat);

            // 可以选择释放BufferedImage以节省内存
            // 但如果后续还需要使用则保留
            // event.setImage(null);

            convertedMats.incrementAndGet();

        } catch (Exception e) {
            log.error("[Mat转换异常] 流: {}", event.getStreamName(), e);
            event.setError(e);
        }
    }
}