package org.jeecg.modules.demo.video.util.reture;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wggg
 * @date 2025/9/19 15:45
 */
@Slf4j
@Data
public class retureBoxInfo {
    //x坐标
    double x;
    //y坐标
    double y;
    //宽度
    double width;
    //长度
    double height;
    //是否通过 ture， false
    boolean isFlag;

    List<retureBoxInfo> infoList;

    String name;

    /***
     *
     * @param infoList 前置模型识别到的x坐标-原始图片坐标 非640*640
     * @param infoList 前置模型识别到的x坐标
     * @param infoList 前置模型识别到的宽度
     * @param infoList 前置模型识别到的高度
     * @param xin  后置识别的x坐标
     * @param yin 后置识别的y坐标
     * @param fw 允许的差距范围
     * @return
     */
    public static boolean getLocalhost(List<retureBoxInfo> infoList, double xin, double yin, int fw) {
        if (infoList == null || infoList.isEmpty()) {
            return false;
        }

        for (retureBoxInfo box : infoList) {
            double x = box.getX();
            double y = box.getY();
            double width = box.getWidth();
            double height = box.getHeight();

            // 扩展前置框
            double left   = x - fw;
            double top    = y - fw;
            double right  = x + width  + fw;
            double bottom = y + height + fw;

            boolean inside = xin >= left && xin <= right && yin >= top && yin <= bottom;

            // 调试打印
            log.info(
                    "前置框(x={}, y={}, w={}, h={})，误差={}，扩展后区域=[left={}, top={}, right={}, bottom={}]，" +
                            "后置点=({}, {})，结果={}",
                    x, y, width, height, fw, left, top, right, bottom,
                    xin, yin, inside ? "在范围内" : "不在范围内"
            );

            if (inside) {
                return true; // 只要命中一个框，就返回 true
            }
        }

        return false; // 没有任何一个框包含点
    }

    public static void main(String[] args) {
        // 创建前置框列表
        // 创建前置框列表
        List<retureBoxInfo> infoList = new ArrayList<>();

        retureBoxInfo box1 = new retureBoxInfo();
        box1.setX(100);
        box1.setY(100);
        box1.setWidth(50);
        box1.setHeight(50);


        retureBoxInfo box2 = new retureBoxInfo();
        box2.setX(200);
        box2.setY(200);
        box2.setWidth(80);
        box2.setHeight(80);


        infoList.add(box1);
        infoList.add(box2);
        // 后置点
        double xin = 205;
        double yin = 210;
        int fw = 10;

        boolean result = getLocalhost(infoList, xin, yin, fw);
        System.out.println("最终结果: " + (result ? " 在某个前置框范围内" : " 不在任何前置框范围内"));

    }


}
