package edu.whut.clf.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 类别字典（P4 类别归一化）：自由文本类别 → 规范 category_code（两级 父类/子类）。
 * 字典为版本化资源文件 matching/category-dictionary.v1.txt，非工程师可评审；
 * 匹配 C 子分（同子类 1.0 / 同父类 0.5）与候选"同类目臂"都依赖它。
 * 匹配规则：先整串精确匹配（别名=原文），再最长别名包含匹配；命中排除词时禁用包含匹配。
 */
@Component
public class CategoryDictionary {

    private static final Logger log = LoggerFactory.getLogger(CategoryDictionary.class);
    private static final String RESOURCE = "/matching/category-dictionary.v1.txt";

    private final String version;
    private final Map<String, String> parentByCode = new HashMap<>();
    private final Map<String, String> labelByCode = new HashMap<>();
    /** 别名（小写化）→ 子类编码；按别名长度降序参与包含匹配。 */
    private final List<Map.Entry<String, String>> aliasesByLength = new ArrayList<>();
    private final List<String> exclusions = new ArrayList<>();

    public CategoryDictionary() {
        try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();
            String v = "unknown";
            for (String line : lines) {
                String[] parts = line.split("\\|");
                if (parts[0].equals("version")) {
                    v = parts[1];
                } else if (parts[0].equals("exclude")) {
                    exclusions.add(parts[1].toLowerCase());
                } else if (parts.length >= 4) {
                    String code = parts[0], parent = parts[1], label = parts[2];
                    parentByCode.put(code, parent);
                    labelByCode.put(code, label);
                    for (String alias : parts[3].split("、")) {
                        String a = alias.trim().toLowerCase();
                        if (!a.isEmpty()) {
                            aliasesByLength.add(Map.entry(a, code));
                        }
                    }
                }
            }
            this.version = v;
            aliasesByLength.sort((x, y) -> Integer.compare(y.getKey().length(), x.getKey().length()));
        } catch (IOException e) {
            throw new IllegalStateException("类别字典资源缺失或不可读: " + RESOURCE, e);
        }
        log.info("类别字典 {} 载入：子类 {} 个，别名 {} 条，排除词 {} 条",
                version, parentByCode.size(), aliasesByLength.size(), exclusions.size());
    }

    /** 自由文本 → 子类编码。先精确，后最长包含；无映射返回 empty（调用方回退原文比对）。 */
    public Optional<String> codeFor(String freeText) {
        if (freeText == null || freeText.isBlank()) {
            return Optional.empty();
        }
        String t = freeText.trim().toLowerCase();
        for (Map.Entry<String, String> e : aliasesByLength) {
            if (e.getKey().equals(t)) {
                return Optional.of(e.getValue());
            }
        }
        for (String ex : exclusions) {
            if (t.contains(ex)) {
                return Optional.empty();
            }
        }
        for (Map.Entry<String, String> e : aliasesByLength) {
            if (t.contains(e.getKey())) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    public String parentOf(String code) {
        return parentByCode.get(code);
    }

    public String labelOf(String code) {
        return labelByCode.getOrDefault(code, code);
    }

    public String version() {
        return version;
    }
}
