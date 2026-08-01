package com.wintercogs.beyonddimensions.util;

import java.util.Locale;

import com.github.promeg.pinyinhelper.Pinyin;

/**
 * 拼音转换工具，封装 TinyPinyin 库。
 */
public final class TinyPinyinUtils {

    private TinyPinyinUtils() {}

    /**
     * 将中文字符串转换为拼音（无声调，小写）。
     *
     * @param input 中文输入
     * @return 拼音字符串，如果输入不含中文则返回原字符串
     */
    public static String toPinyin(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Pinyin.isChinese(c)) {
                sb.append(Pinyin.toPinyin(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
            .toLowerCase(Locale.ENGLISH);
    }

    /**
     * 获取字符串的拼音首字母。
     *
     * @param input 中文输入
     * @return 拼音首字母字符串
     */
    public static String toPinyinFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Pinyin.isChinese(c)) {
                String pinyin = Pinyin.toPinyin(c);
                if (pinyin != null && !pinyin.isEmpty()) {
                    sb.append(pinyin.charAt(0));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
            .toLowerCase(Locale.ENGLISH);
    }

    /**
     * 判断字符串是否包含中文。
     */
    public static boolean containsChinese(String input) {
        if (input == null || input.isEmpty()) return false;
        for (int i = 0; i < input.length(); i++) {
            if (Pinyin.isChinese(input.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
