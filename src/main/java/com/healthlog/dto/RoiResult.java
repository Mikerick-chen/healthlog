package com.healthlog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康投資回報率（ROI）結果（§2）。
 * 將「行為」（喝水、步數）對「結果」（體重、靜止心率）的影響具象化。
 */
public class RoiResult {

    public String thisWeekLabel;
    public String lastWeekLabel;
    public List<Item> behaviors = new ArrayList<>();  // 你投入的行為
    public List<Item> outcomes = new ArrayList<>();   // 帶來的結果
    public List<String> highlights = new ArrayList<>(); // 白話成就感敘述
    public boolean enoughData;

    public static class Item {
        public String name;
        public String unit;
        public Double thisWeek;
        public Double lastWeek;
        public Double delta;      // 本週 - 上週
        public Double deltaPct;   // 變化百分比
        public boolean betterWhenUp; // 此指標越高越好？
        public String status;     // good / warn / neutral

        public Item(String name, String unit, boolean betterWhenUp) {
            this.name = name; this.unit = unit; this.betterWhenUp = betterWhenUp;
        }
    }
}
