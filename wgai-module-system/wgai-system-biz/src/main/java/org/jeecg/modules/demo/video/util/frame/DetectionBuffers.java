package org.jeecg.modules.demo.video.util.frame;

/**
 * @author wggg
 * @date 2025/9/5 10:34
 */

import org.opencv.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 检测结果缓冲区 - 避免频繁内存分配
 */
public  class DetectionBuffers {
    final List<Rect2d> boxes2d = new ArrayList<>(200);
    final List<Float> confidences = new ArrayList<>(200);
    final List<Integer> classIds = new ArrayList<>(200);
    final List<Mat> results = new ArrayList<>(10);
    final MatOfRect2d boxesMat = new MatOfRect2d();
    MatOfFloat confidencesMat = new MatOfFloat();
    final MatOfInt indices = new MatOfInt();

    void clear() {
        boxes2d.clear();
        confidences.clear();
        classIds.clear();
        results.clear();
    }
}
