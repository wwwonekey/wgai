package org.jeecg.modules.demo.video.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.jeecg.modules.demo.tab.entity.PushInfo;
import org.jeecg.modules.demo.video.entity.TabAiModelNew;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.jeecg.modules.tab.AIModel.identify.identifyTypeAll;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.opencv.core.Mat;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * @author wggg
 * @date 2025/5/20 17:41
 */
@Slf4j
public class VideoReadPicNew implements Runnable {

    private static final ThreadLocal<TabAiSubscriptionNew> threadLocalPushInfo = new ThreadLocal<>();
    TabAiSubscriptionNew tabAiSubscriptionNew;


    RedisTemplate redisTemplate;

    public VideoReadPicNew(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run() {
        threadLocalPushInfo.set(tabAiSubscriptionNew);
        tabAiSubscriptionNew = threadLocalPushInfo.get();

        List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();


        if (tabAiSubscriptionNew.getPyType().equals("5")) { // pytype字典 FFMPEG的值
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());
            try {
                if (tabAiSubscriptionNew.getEventTypes().equals("1")) { //gpu
                    grabber.setOption("hwaccel", "cuda"); // NVIDIA CUDA 硬解码
                    grabber.setOption("hwaccel_device", "0");  // 如果你有多个 GPU，设置 GPU id
                    grabber.setOption("hwaccel_output_format", "cuda");  // 尝试启用硬解输出格式
                    log.info("[使用GPU_CUDA加速解码]");
                } else {
                    log.info("[使用CPU加速解码]");
                }

                grabber.setOption("threads", "auto"); // 自动选择线程数
                grabber.setOption("preset", "ultrafast"); // 快速解码
                grabber.setVideoOption("tune", "zerolatency"); // 低延迟模式
                grabber.setOption("rtsp_transport", "tcp");  // 强制使用 TCP
                grabber.setOption("max_delay", "500000");    // 设置最大延迟
                grabber.setOption("buffer_size", "10485760"); // 设置10MB的缓冲区
                grabber.setOption("fflags", "nobuffer"); // 不缓存旧帧，尽量读取最新帧
                grabber.setOption("flags", "low_delay"); // 降低延迟
                grabber.setOption("framedrop", "1"); // 在解码压力大时丢帧
                grabber.setOption("analyzeduration", "0"); // 减少分析时间
                grabber.setOption("probesize", "32"); // 降低探测数据大小，快速锁定流
                grabber.setOption("stimeout", "3000000");    // 3秒超时
                grabber.setOption("skip_frame", "nokey"); // 只解码关键帧（I帧）
                grabber.setOption("hwaccel", "auto"); // 硬件加速
                grabber.setOption("pixel_format", "yuv420p"); // 像素格式
                grabber.setOption("an", "1"); // 禁用音频
                grabber.setOption("skip_frame", "nokey");// 跳过损坏帧
                grabber.setOption("strict", "experimental");// 设置更严格的解码
                grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);
                grabber.start(); // 开始读取视频

                int width = grabber.getImageWidth();
                int height = grabber.getImageHeight();
                double fps = grabber.getFrameRate();
                // 2. 初始化录像器（可选）
//                FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height);
//                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
//                recorder.setFormat("mp4");
//                recorder.setFrameRate(fps);
//                // recorder.setOption("vcodec", "h264_nvenc"); // GPU编码（可选）
//                recorder.start();

                // 3. 转换器
                Java2DFrameConverter converter = new Java2DFrameConverter();
                Frame frame;
                while (true) {

                    // 将Frame转换为OpenCV的Mat对象
                    boolean flag = (boolean) redisTemplate.opsForValue().get(tabAiSubscriptionNew.getId() + "newRunPush");
                    if (!flag) {
                        log.warn("[结束推送]{}", tabAiSubscriptionNew.getName());
                        break;
                    }

                    frame = grabber.grabImage();
                    if (frame == null) {
                        log.info("[当前没数据重新读取]");
                        grabber.stop();
                        grabber.release();
                        if (tabAiSubscriptionNew.getEventTypes().equals("1")) { //gpu
                            grabber.setOption("hwaccel", "cuda"); // NVIDIA CUDA 硬解码
                            grabber.setOption("hwaccel_device", "0");  // 如果你有多个 GPU，设置 GPU id
                            grabber.setOption("hwaccel_output_format", "cuda");  // 尝试启用硬解输出格式
                            log.info("[使用GPU_CUDA加速解码]");
                        } else {
                            log.info("[使用CPU加速解码]");
                        }

                        grabber.setOption("threads", "auto"); // 自动选择线程数
                        grabber.setOption("preset", "ultrafast"); // 快速解码
                        grabber.setVideoOption("tune", "zerolatency"); // 低延迟模式
                        grabber.setOption("rtsp_transport", "tcp");  // 强制使用 TCP
                        grabber.setOption("max_delay", "500000");    // 设置最大延迟
                        grabber.setOption("buffer_size", "10485760"); // 设置10MB的缓冲区
                        grabber.setOption("fflags", "nobuffer"); // 不缓存旧帧，尽量读取最新帧
                        grabber.setOption("flags", "low_delay"); // 降低延迟
                        grabber.setOption("framedrop", "1"); // 在解码压力大时丢帧
                        grabber.setOption("analyzeduration", "0"); // 减少分析时间
                        grabber.setOption("probesize", "32"); // 降低探测数据大小，快速锁定流
                        grabber.setOption("stimeout", "3000000");    // 3秒超时
                        grabber.setOption("skip_frame", "nokey"); // 只解码关键帧（I帧）
                        grabber.setOption("hwaccel", "auto"); // 硬件加速
                        grabber.setOption("pixel_format", "yuv420p"); // 像素格式
                        grabber.setOption("an", "1"); // 禁用音频
                        grabber.setOption("skip_frame", "nokey");// 跳过损坏帧
                        grabber.setOption("strict", "experimental");// 设置更严格的解码
                        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);
                        grabber.start(); // 开始读取视频

                        continue;
                    }
                    Mat matInfo = bufferedImageToMat(converter.getBufferedImage(frame));

                    if (matInfo == null) continue;
                    log.info("[开始推理]{}", tabAiSubscriptionNew.getBeginEventTypes());

                    //      ExecutorService executor = Executors.newFixedThreadPool(netPushList.size()*2); // 限制为4线程

                    for (NetPush netPush : netPushList) {
                        Mat mat = matInfo.clone();
                        //                    executor.submit(() -> {
                        try {


                            identifyTypeNew identifyTypeAll = new identifyTypeNew();
                            if (netPush.getIsBefor() == 1) { //有前置
                                log.info("[进入有前置]");
                                List<NetPush> before = netPush.getListNetPush();
                                for (int i = 0; i < before.size(); i++) {
                                    boolean flagYes = false;
                                    if (i == 0) {

                                        if (before.get(i).getModelType().equals("1")) {
                                            if (!identifyTypeAll.detectObjects(tabAiSubscriptionNew, mat, before.get(i).getNet(), before.get(i).getClaseeNames(), before.get(i))) {
                                                log.warn("验证不通过");
                                                break;
                                            }

                                        } else {
                                            if (!identifyTypeAll.detectObjectsV5(tabAiSubscriptionNew, mat, before.get(i).getNet(), before.get(i).getClaseeNames(), before.get(i))) {
                                                log.warn("验证不通过");
                                                break;
                                            }

                                        }

                                    }

                                    if (before.get(i).getModelType().equals("1")) {
                                        identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, before.get(i),redisTemplate);
                                    } else {
                                        identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, before.get(i),redisTemplate);
                                    }

                                }

                            } else { //无前置
                                log.info("[进入无前置]");
                                if (netPush.getModelType().equals("1")) {  //V3
                                    identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, netPush,redisTemplate);
                                } else {
                                    identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush,redisTemplate);
                                }

                            }
                        } finally {
                            mat.release();
                        }
                        //               });
                    }

                }

            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                log.info("[无论如何都要结束释放]");
                try {
                    grabber.stop();
                    grabber.release();
                } catch (FFmpegFrameGrabber.Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


}
