package edu.whut.clf.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 中文友好的文本归一化 + token 化（P3）。
 * 流程：归一化（全角→半角、小写、去标点空白）→ 同义词规整（最长变体替换为规范词）
 * → 输出 unigram 集（CJK 单字去停用字 + ASCII 词）与 bigram 集（CJK 连续段内相邻二字，
 * 在停用词剔除前生成、仅由停用字构成的 bigram 丢弃）。
 * 配置为版本化资源文件 matching/text-normalizer.v1.txt，非工程师可评审；
 * K 子分 = wUnigram·J(uni) + wBigram·J(bi)（权重可配置，默认 0.5/0.5）。
 */
@Component
public class TextTokenizer {

    private static final Logger log = LoggerFactory.getLogger(TextTokenizer.class);
    private static final String RESOURCE = "/matching/text-normalizer.v1.txt";

    private final String version;
    /** 变体（小写）→ 规范词，按变体长度降序做替换。 */
    private final List<Map.Entry<String, String>> synonymsByLength = new ArrayList<>();
    private final Set<String> stopwords = new HashSet<>();

    public TextTokenizer() {
        try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
            String v = "unknown";
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList()) {
                String[] parts = line.split("\\|");
                if (parts[0].equals("version")) {
                    v = parts[1];
                } else if (parts[0].equals("synonym") && parts.length >= 3) {
                    String canonical = normalizeText(parts[1]);
                    for (String variant : parts[2].split("、")) {
                        String n = normalizeText(variant);
                        if (!n.isEmpty() && !n.equals(canonical)) {
                            synonymsByLength.add(Map.entry(n, canonical));
                        }
                    }
                } else if (parts[0].equals("stopword") && parts.length >= 2) {
                    for (char ch : parts[1].toCharArray()) {
                        if (isCjk(ch)) stopwords.add(String.valueOf(ch));
                    }
                }
            }
            this.version = v;
            synonymsByLength.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
            log.info("文本归一化配置 {} 载入：同义变体 {} 条，停用字 {} 个",
                    version, synonymsByLength.size(), stopwords.size());
        } catch (IOException e) {
            throw new IllegalStateException("文本归一化资源缺失或不可读: " + RESOURCE, e);
        }
    }

    public String version() {
        return version;
    }

    /** 归一化后的文本（全角→半角、小写、去标点/空白，保留 CJK 与字母数字）。 */
    public String normalize(String text) {
        if (text == null) return "";
        return normalizeText(text);
    }

    /** 单字（unigram）token 集：CJK 停用字剔除 + ASCII 词。 */
    public Set<String> unigrams(String text) {
        String norm = normalizeText(text);
        norm = applySynonyms(norm);
        Set<String> out = new HashSet<>();
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < norm.length(); i++) {
            char ch = norm.charAt(i);
            if (ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z') {
                ascii.append(ch);
            } else {
                if (ascii.length() > 0) {
                    out.add(ascii.toString());
                    ascii.setLength(0);
                }
                if (isCjk(ch) && !stopwords.contains(String.valueOf(ch))) {
                    out.add(String.valueOf(ch));
                }
            }
        }
        if (ascii.length() > 0) out.add(ascii.toString());
        return out;
    }

    /** 相邻二字（bigram）token 集：CJK 连续段内生成，仅停用字组成的丢弃。 */
    public Set<String> bigrams(String text) {
        String norm = applySynonyms(normalizeText(text));
        Set<String> out = new HashSet<>();
        List<String> runs = cjkRuns(norm);
        for (String run : runs) {
            for (int i = 0; i + 1 < run.length(); i++) {
                String a = String.valueOf(run.charAt(i));
                String b = String.valueOf(run.charAt(i + 1));
                if (stopwords.contains(a) && stopwords.contains(b)) {
                    continue; // 纯停用字 bigram 无判别价值
                }
                out.add(run.substring(i, i + 2));
            }
        }
        return out;
    }

    // ---- 内部 ----

    private static String normalizeText(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                ch = (char) (ch - 0xFEE0); // 全角 ASCII 区 → 半角
            } else if (ch == 0x3000) {
                ch = ' ';
            }
            if (ch >= 'A' && ch <= 'Z') {
                ch = (char) (ch + 32);
            }
            if (isCjk(ch) || ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z') {
                sb.append(ch);
            }
            // 其余（标点/空白/emoji）丢弃
        }
        return sb.toString();
    }

    private String applySynonyms(String norm) {
        String out = norm;
        for (Map.Entry<String, String> e : synonymsByLength) {
            if (out.contains(e.getKey())) {
                out = out.replace(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static List<String> cjkRuns(String norm) {
        List<String> runs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < norm.length(); i++) {
            char ch = norm.charAt(i);
            if (isCjk(ch)) {
                cur.append(ch);
            } else if (cur.length() > 0) {
                runs.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) runs.add(cur.toString());
        return runs;
    }

    private static boolean isCjk(char ch) {
        return ch >= '一' && ch <= '鿿';
    }

    /** 调试/报告用：归一化摘要。 */
    public Map<String, Object> describe() {
        Map<String, Object> m = new TreeMap<>();
        m.put("version", version);
        m.put("synonymVariants", synonymsByLength.size());
        m.put("stopwords", stopwords.size());
        return m;
    }
}
