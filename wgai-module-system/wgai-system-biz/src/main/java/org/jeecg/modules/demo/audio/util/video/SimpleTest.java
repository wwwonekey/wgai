package org.jeecg.modules.demo.audio.util.video;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.Arrays;

/**
 * @author wggg
 * @date 2025/12/8 13:49
 */
public class SimpleTest {

    public static void main(String[] args) {
        try {
            System.out.println("开始检测...");

            // 1. 加载图片
            Mat image = Imgcodecs.imread("D:/test.jpg");
            if (image.empty()) {
                System.out.println("错误：图片加载失败！");
                System.out.println("请确保 D:/test.jpg 文件存在");
                return;
            }
            System.out.println("✓ 图片加载成功: " + image.cols() + "x" + image.rows());

            // 2. 加载模型
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession session = env.createSession("xxxx.onnx");
            System.out.println("✓ 模型加载成功");

            // 3. 配置参数
            NetPush netPush = new NetPush();
            netPush.setSession(session);
            netPush.setEnv(env);
            netPush.setClaseeNames(Arrays.asList(
                    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck"

            ));
            netPush.setUploadPath("D:/output/");
            netPush.setId("test");
            netPush.setIsBy(1);      // 不使用区域过滤
            netPush.setIsFollow(1);  // 不跟随前置区域
            netPush.setIsBefor(1);   // 没有前置模型

            // 4. 执行检测
            videoIdentifyTypeNewOnnx detector = new videoIdentifyTypeNewOnnx();
            System.out.println("正在检测...");

            long start = System.currentTimeMillis();
            retureBoxInfo result = detector.detectObjectsV5Onnx(image, netPush, null);
            long time = System.currentTimeMillis() - start;

            // 5. 显示结果
            System.out.println("✓ 检测完成，耗时: " + time + "ms");

            if (result != null && result.isFlag()) {
                int count = result.getInfoList() != null ? result.getInfoList().size() : 0;
                System.out.println("✓ 检测到 " + count + " 个目标");

                if (result.getInfoList() != null) {
                    System.out.println("\n检测详情：");
                    for (Object info : result.getInfoList()) {
                        System.out.println("  - " + info);
                    }
                }

//                if (result.getImagePath() != null) {
//                    System.out.println("\n结果图片: " + result.getImagePath());
//                }
            } else {
                System.out.println("✗ 未检测到目标");
            }

            // 6. 清理
            image.release();
            session.close();
            System.out.println("\n完成！");

        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
