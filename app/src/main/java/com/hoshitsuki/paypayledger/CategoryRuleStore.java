package com.hoshitsuki.paypayledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CategoryRuleStore {
    private static final String TAG = "CategoryRuleStore";
    private static final String PREFS = "category_rules";
    private static final String KEY_MAPPINGS = "category_mapping_json";
    private static final String KEY_OVERRIDES = "merchant_override_json";
    private static final String KEY_IMPORTED_GROUPS = "imported_rule_groups_json";
    private static final String KEY_MIGRATION_NOTICE = "migration_notice";

    private final Context context;
    private final SharedPreferences prefs;
    private final LinkedHashMap<String, RuleGroup> builtInGroups = new LinkedHashMap<String, RuleGroup>();

    public CategoryRuleStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildBuiltInGroups();
        migrateImportedGroupStorage();
    }

    public boolean hasUserMappings() {
        return prefs.contains(KEY_MAPPINGS) && !loadMappings().isEmpty();
    }

    public boolean consumeMigrationNotice() {
        boolean shouldShow = prefs.getBoolean(KEY_MIGRATION_NOTICE, false);
        if (shouldShow) {
            prefs.edit().remove(KEY_MIGRATION_NOTICE).apply();
        }
        return shouldShow;
    }

    public List<RuleGroup> getEffectiveRuleGroups() {
        LinkedHashMap<String, RuleGroup> merged = new LinkedHashMap<String, RuleGroup>();
        for (RuleGroup group : builtInGroups.values()) {
            merged.put(group.groupId, group.copy());
        }
        for (RuleGroup imported : loadImportedGroups().values()) {
            String targetId = migrateGroupId(imported.groupId);
            RuleGroup target = merged.get(targetId);
            if (target == null) {
                imported.groupId = targetId;
                merged.put(targetId, imported.copy());
            } else {
                target.keywords.clear();
                target.keywords.addAll(imported.keywords);
                target.imported = true;
            }
        }
        ArrayList<RuleGroup> groups = new ArrayList<RuleGroup>(merged.values());
        Collections.sort(groups, new Comparator<RuleGroup>() {
            @Override
            public int compare(RuleGroup a, RuleGroup b) {
                return a.order - b.order;
            }
        });
        return groups;
    }

    public RuleGroup getEffectiveRuleGroup(String groupId) {
        groupId = migrateGroupId(groupId);
        for (RuleGroup group : getEffectiveRuleGroups()) {
            if (group.groupId.equals(groupId)) {
                return group;
            }
        }
        return null;
    }

    public Map<String, CategoryMapping> loadMappings() {
        LinkedHashMap<String, CategoryMapping> mappings = new LinkedHashMap<String, CategoryMapping>();
        String raw = prefs.getString(KEY_MAPPINGS, "");
        if (raw.length() == 0) {
            return mappings;
        }
        try {
            JSONArray array = new JSONArray(raw);
            boolean changed = false;
            boolean conflict = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String originalGroupId = item.optString("group_id");
                String groupId = migrateGroupId(originalGroupId);
                CategoryMapping mapping = new CategoryMapping(
                        groupId,
                        item.optString("major"),
                        item.optString("minor"));
                if (mapping.groupId.length() > 0 && mapping.major.length() > 0) {
                    CategoryMapping previous = mappings.get(mapping.groupId);
                    if (!groupId.equals(originalGroupId)) {
                        changed = true;
                    }
                    if (previous != null && (!previous.major.equals(mapping.major) || !previous.minor.equals(mapping.minor))) {
                        conflict = true;
                        changed = true;
                    }
                    mappings.put(mapping.groupId, mapping);
                }
            }
            if (changed) {
                saveMappings(new ArrayList<CategoryMapping>(mappings.values()));
            }
            if (conflict) {
                prefs.edit().putBoolean(KEY_MIGRATION_NOTICE, true).apply();
            }
        } catch (JSONException ignored) {
        }
        return mappings;
    }

    public Map<String, MerchantOverride> loadOverrides() {
        LinkedHashMap<String, MerchantOverride> overrides = new LinkedHashMap<String, MerchantOverride>();
        String raw = prefs.getString(KEY_OVERRIDES, "");
        if (raw.length() == 0) {
            return overrides;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                MerchantOverride override = new MerchantOverride(
                        item.optString("merchant_key"),
                        item.optString("display_name"),
                        item.optString("major"),
                        item.optString("minor"));
                if (override.merchantKey.length() > 0 && override.major.length() > 0) {
                    overrides.put(override.merchantKey, override);
                }
            }
        } catch (JSONException ignored) {
        }
        return overrides;
    }

    public CategoryMapping recommendedMapping(String groupId) {
        groupId = migrateGroupId(groupId);
        if ("restaurant".equals(groupId)) return new CategoryMapping(groupId, "食品餐饮", "外出吃饭");
        if ("retail_convenience".equals(groupId)) return new CategoryMapping(groupId, "食品餐饮", "便利店");
        if ("retail_supermarket".equals(groupId)) return new CategoryMapping(groupId, "食品餐饮", "食材采购");
        if ("retail_drugstore".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "药妆日化");
        if ("retail_home_center".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "日用百货");
        if ("retail_electronics".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "大额数码");
        if ("retail_bookstore".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "书籍文具");
        if ("retail_cosmetics".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "美妆护肤");
        if ("retail_100yen".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "日用百货");
        if ("retail_sports".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "运动户外");
        if ("retail_baby_pet".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "母婴宠物");
        if ("retail_furniture".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "家具家饰");
        if ("clothing_store".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "服饰鞋包");
        if ("online_shopping".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "日用百货");
        if ("delivery_parcel".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "运费其他");
        if ("fee_service".equals(groupId)) return new CategoryMapping(groupId, "购物消费", "运费其他");
        if ("food_delivery".equals(groupId)) return new CategoryMapping(groupId, "食品餐饮", "外卖");
        if ("transport_train".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "公共交通");
        if ("transport_shinkansen".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "火车");
        if ("taxi".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "打车租车");
        if ("parking".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "停车路费");
        if ("gas_station".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "加油充电");
        if ("flight".equals(groupId)) return new CategoryMapping(groupId, "出行交通", "飞机");
        if ("hotel".equals(groupId)) return new CategoryMapping(groupId, "休闲娱乐", "住宿");
        if ("attraction_ticket".equals(groupId)) return new CategoryMapping(groupId, "休闲娱乐", "门票演出");
        if ("entertainment_movie".equals(groupId)) return new CategoryMapping(groupId, "休闲娱乐", "电影娱乐");
        if ("entertainment_show".equals(groupId)) return new CategoryMapping(groupId, "休闲娱乐", "门票演出");
        if ("entertainment_place".equals(groupId)) return new CategoryMapping(groupId, "休闲娱乐", "其他");
        if ("subscription".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "App会员");
        if ("rent".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "房租");
        if ("telecom".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "话费宽带");
        if ("utility_electric_water".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "水费");
        if ("utility_gas".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "燃气费");
        if ("public_service".equals(groupId)) return new CategoryMapping(groupId, "居家生活", "公共服务");
        if ("education_admin".equals(groupId)) return new CategoryMapping(groupId, "文化教育", "教务缴费");
        if ("medical".equals(groupId)) return new CategoryMapping(groupId, "健康医疗", "医疗");
        return new CategoryMapping(groupId, "待分类", "");
    }

    public void saveMappings(List<CategoryMapping> mappings) {
        JSONArray array = new JSONArray();
        for (CategoryMapping mapping : mappings) {
            if (mapping.groupId.length() == 0 || mapping.major.trim().length() == 0) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("group_id", migrateGroupId(mapping.groupId));
                item.put("major", mapping.major.trim());
                item.put("minor", mapping.minor.trim());
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_MAPPINGS, array.toString()).apply();
    }

    public void saveMerchantOverride(String merchant, String major, String minor) {
        String key = merchantKey(merchant);
        LinkedHashMap<String, MerchantOverride> overrides = new LinkedHashMap<String, MerchantOverride>(loadOverrides());
        overrides.put(key, new MerchantOverride(key, merchant, major.trim(), minor.trim()));
        saveOverrides(new ArrayList<MerchantOverride>(overrides.values()));
    }

    public void deleteMerchantOverride(String merchantKey) {
        LinkedHashMap<String, MerchantOverride> overrides = new LinkedHashMap<String, MerchantOverride>(loadOverrides());
        overrides.remove(merchantKey);
        saveOverrides(new ArrayList<MerchantOverride>(overrides.values()));
    }

    public CategoryResult classify(String merchant) {
        String key = merchantKey(merchant);
        MerchantOverride override = loadOverrides().get(key);
        if (override != null) {
            return new CategoryResult(override.major, override.minor, "", "商户修正");
        }

        RuleGroup group = matchRuleGroup(merchant);
        if (group == null) {
            return new CategoryResult("待分类", "", "", "未匹配");
        }

        CategoryMapping mapping = loadMappings().get(group.groupId);
        if (mapping == null) {
            mapping = recommendedMapping(group.groupId);
            return new CategoryResult(mapping.major, mapping.minor, group.groupId, "推荐映射");
        }
        return new CategoryResult(mapping.major, mapping.minor, group.groupId, "用户映射");
    }

    public RuleGroup matchRuleGroup(String merchant) {
        return MerchantRuleMatcher.match(getEffectiveRuleGroups(), merchant);
    }

    public String importCategoryMappingsCsv(String csv) throws Exception {
        List<String[]> rows = parseCsv(csv);
        requireHeader(rows, "group_id", "major", "minor");
        Set<String> knownIds = new LinkedHashSet<String>();
        for (RuleGroup group : getEffectiveRuleGroups()) {
            knownIds.add(group.groupId);
        }
        LinkedHashMap<String, CategoryMapping> current = new LinkedHashMap<String, CategoryMapping>(loadMappings());
        int updated = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            String groupId = migrateGroupId(cell(rows.get(i), 0).trim());
            String major = cell(rows.get(i), 1).trim();
            String minor = cell(rows.get(i), 2).trim();
            if (groupId.length() == 0 || major.length() == 0 || !knownIds.contains(groupId)) {
                skipped++;
                continue;
            }
            current.put(groupId, new CategoryMapping(groupId, major, minor));
            updated++;
        }
        saveMappings(new ArrayList<CategoryMapping>(current.values()));
        return "导入账本分类映射 " + updated + " 条，跳过 " + skipped + " 条";
    }

    public String importMerchantOverridesCsv(String csv) throws Exception {
        List<String[]> rows = parseCsv(csv);
        requireHeader(rows, "merchant_key", "display_name", "major", "minor");
        LinkedHashMap<String, MerchantOverride> current = new LinkedHashMap<String, MerchantOverride>(loadOverrides());
        int updated = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            String key = cell(rows.get(i), 0).trim();
            String display = cell(rows.get(i), 1).trim();
            String major = cell(rows.get(i), 2).trim();
            String minor = cell(rows.get(i), 3).trim();
            if (key.length() == 0 && display.length() > 0) {
                key = merchantKey(display);
            }
            if (key.length() == 0 || major.length() == 0) {
                skipped++;
                continue;
            }
            current.put(key, new MerchantOverride(key, display, major, minor));
            updated++;
        }
        saveOverrides(new ArrayList<MerchantOverride>(current.values()));
        return "导入单个商户修正规则 " + updated + " 条，跳过 " + skipped + " 条";
    }

    public String importCombinedConfigCsv(String csv) throws Exception {
        List<String[]> rows = parseCsv(csv);
        requireHeader(rows, "record_type", "group_id", "major", "minor", "merchant_key", "display_name");
        Set<String> knownIds = new LinkedHashSet<String>();
        for (RuleGroup group : getEffectiveRuleGroups()) {
            knownIds.add(group.groupId);
        }
        LinkedHashMap<String, CategoryMapping> mappings = new LinkedHashMap<String, CategoryMapping>(loadMappings());
        LinkedHashMap<String, MerchantOverride> overrides = new LinkedHashMap<String, MerchantOverride>(loadOverrides());
        int mappingCount = 0;
        int overrideCount = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            String type = cell(rows.get(i), 0).trim();
            String groupId = migrateGroupId(cell(rows.get(i), 1).trim());
            String major = cell(rows.get(i), 2).trim();
            String minor = cell(rows.get(i), 3).trim();
            String key = cell(rows.get(i), 4).trim();
            String display = cell(rows.get(i), 5).trim();
            if ("mapping".equals(type)) {
                if (groupId.length() == 0 || major.length() == 0 || !knownIds.contains(groupId)) {
                    skipped++;
                    continue;
                }
                mappings.put(groupId, new CategoryMapping(groupId, major, minor));
                mappingCount++;
            } else if ("override".equals(type)) {
                if (key.length() == 0 && display.length() > 0) {
                    key = merchantKey(display);
                }
                if (key.length() == 0 || major.length() == 0) {
                    skipped++;
                    continue;
                }
                overrides.put(key, new MerchantOverride(key, display, major, minor));
                overrideCount++;
            } else {
                skipped++;
            }
        }
        saveMappings(new ArrayList<CategoryMapping>(mappings.values()));
        saveOverrides(new ArrayList<MerchantOverride>(overrides.values()));
        return "导入分类配置：账本映射 " + mappingCount + " 条，商户修正 " + overrideCount + " 条，跳过 " + skipped + " 条";
    }

    public String importRuleGroupsCsvAppend(String csv) throws Exception {
        List<String[]> rows = parseCsv(csv);
        requireHeader(rows, "group_id", "group_name", "keywords");
        LinkedHashMap<String, RuleGroup> imported = new LinkedHashMap<String, RuleGroup>(loadImportedGroups());
        int updated = 0;
        int risk = 0;
        for (int i = 1; i < rows.size(); i++) {
            String groupId = migrateGroupId(cell(rows.get(i), 0).trim());
            String groupName = cell(rows.get(i), 1).trim();
            String keywords = cell(rows.get(i), 2).trim();
            if (groupId.length() == 0 || groupName.length() == 0 || keywords.length() == 0) {
                continue;
            }
            RuleGroup target = imported.get(groupId);
            if (target == null) {
                target = new RuleGroup(groupId, groupName, 1000 + imported.size(), true);
                imported.put(groupId, target);
            } else {
                target.keywords.clear();
            }
            for (String keyword : keywords.split("\\|")) {
                String cleaned = keyword.trim();
                if (cleaned.length() > 0) {
                    target.keywords.add(cleaned);
                }
            }
            if (target.keywords.size() < 100) {
                risk++;
            }
            updated++;
        }
        saveImportedGroups(new ArrayList<RuleGroup>(imported.values()));
        return "导入商户识别规则 " + updated + " 组" + (risk > 0 ? "，其中 " + risk + " 组少于 100 个关键词" : "");
    }

    public void saveRuleGroupKeywords(String groupId, String groupName, List<String> keywords) {
        groupId = migrateGroupId(groupId);
        LinkedHashMap<String, RuleGroup> imported = new LinkedHashMap<String, RuleGroup>(loadImportedGroups());
        RuleGroup target = imported.get(groupId);
        if (target == null) {
            target = new RuleGroup(groupId, groupName, 1000 + imported.size(), true);
            imported.put(groupId, target);
        }
        target.keywords.clear();
        LinkedHashSet<String> cleaned = new LinkedHashSet<String>();
        for (String keyword : keywords) {
            String value = keyword.trim();
            if (value.length() > 0) {
                cleaned.add(value);
            }
        }
        target.keywords.addAll(cleaned);
        saveImportedGroups(new ArrayList<RuleGroup>(imported.values()));
    }

    public String exportCategoryMappingsCsv() {
        StringBuilder out = new StringBuilder("\uFEFFgroup_id,major,minor\n");
        Map<String, CategoryMapping> mappings = loadMappings();
        for (RuleGroup group : getEffectiveRuleGroups()) {
            CategoryMapping mapping = mappings.get(group.groupId);
            if (mapping == null) {
                mapping = recommendedMapping(group.groupId);
            }
            out.append(csv(group.groupId)).append(',').append(csv(mapping.major)).append(',').append(csv(mapping.minor)).append('\n');
        }
        return out.toString();
    }

    public String exportMerchantOverridesCsv() {
        StringBuilder out = new StringBuilder("\uFEFFmerchant_key,display_name,major,minor\n");
        for (MerchantOverride override : loadOverrides().values()) {
            out.append(csv(override.merchantKey)).append(',').append(csv(override.displayName)).append(',').append(csv(override.major)).append(',').append(csv(override.minor)).append('\n');
        }
        return out.toString();
    }

    public String exportRuleGroupsCsv() {
        StringBuilder out = new StringBuilder("\uFEFFgroup_id,group_name,keywords\n");
        for (RuleGroup group : getEffectiveRuleGroups()) {
            ArrayList<String> sorted = new ArrayList<String>(group.keywords);
            Collections.sort(sorted);
            out.append(csv(group.groupId)).append(',').append(csv(group.groupName)).append(',').append(csv(join(sorted, "|"))).append('\n');
        }
        return out.toString();
    }

    public String exportCombinedConfigCsv() {
        StringBuilder out = new StringBuilder("\uFEFFrecord_type,group_id,major,minor,merchant_key,display_name\n");
        Map<String, CategoryMapping> mappings = loadMappings();
        for (RuleGroup group : getEffectiveRuleGroups()) {
            CategoryMapping mapping = mappings.get(group.groupId);
            if (mapping == null) {
                mapping = recommendedMapping(group.groupId);
            }
            out.append("mapping,")
                    .append(csv(group.groupId)).append(',')
                    .append(csv(mapping.major)).append(',')
                    .append(csv(mapping.minor)).append(",,\n");
        }
        for (MerchantOverride override : loadOverrides().values()) {
            out.append("override,,")
                    .append(csv(override.major)).append(',')
                    .append(csv(override.minor)).append(',')
                    .append(csv(override.merchantKey)).append(',')
                    .append(csv(override.displayName)).append('\n');
        }
        return out.toString();
    }

    public static String merchantKey(String merchant) {
        return normalizeMerchant(merchant).replaceAll("[^a-z0-9\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    public static String normalizeMerchant(String merchant) {
        String normalized = Normalizer.normalize(merchant == null ? "" : merchant, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[・＊*／/\\\\()（）\\[\\]【】.,，。:：]", " ")
                .replaceAll("\\bpaypay\\b", " ")
                .replaceAll("\\bvisa\\b", " ")
                .replaceAll("(?<=[a-z])\\s+(?=[a-z])", "")
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized.replaceAll("\\s+[0-9]{3,}$", "");
        return normalized;
    }

    private String migrateGroupId(String groupId) {
        if ("restaurant_fast".equals(groupId) || "restaurant_dining".equals(groupId)) return "restaurant";
        if ("convenience_store".equals(groupId)) return "retail_convenience";
        if ("supermarket".equals(groupId)) return "retail_supermarket";
        return groupId == null ? "" : groupId;
    }

    private Map<String, RuleGroup> loadImportedGroups() {
        LinkedHashMap<String, RuleGroup> groups = new LinkedHashMap<String, RuleGroup>();
        String raw = prefs.getString(KEY_IMPORTED_GROUPS, "");
        if (raw.length() == 0) {
            return groups;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String groupId = migrateGroupId(item.optString("group_id"));
                RuleGroup builtIn = builtInGroups.get(groupId);
                RuleGroup group = new RuleGroup(
                        groupId,
                        item.optString("group_name", builtIn == null ? "" : builtIn.groupName),
                        builtIn == null ? 1000 + i : builtIn.order,
                        true);
                if (item.has("added_keywords") || item.has("removed_keywords")) {
                    if (builtIn != null) {
                        group.keywords.addAll(builtIn.keywords);
                    }
                    removeJsonKeywords(group.keywords, item.optJSONArray("removed_keywords"));
                    addJsonKeywords(group.keywords, item.optJSONArray("added_keywords"));
                } else {
                    addJsonKeywords(group.keywords, item.optJSONArray("keywords"));
                }
                if (group.groupId.length() > 0 && group.groupName.length() > 0) {
                    groups.put(group.groupId, group);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "无法读取商户识别规则", e);
        }
        return groups;
    }

    private void saveImportedGroups(List<RuleGroup> groups) {
        JSONArray array = new JSONArray();
        for (RuleGroup group : groups) {
            JSONObject item = new JSONObject();
            try {
                item.put("group_id", group.groupId);
                item.put("group_name", group.groupName);
                RuleGroup builtIn = builtInGroups.get(group.groupId);
                if (builtIn == null) {
                    item.put("keywords", jsonKeywords(group.keywords));
                } else {
                    LinkedHashSet<String> added = new LinkedHashSet<String>(group.keywords);
                    added.removeAll(builtIn.keywords);
                    LinkedHashSet<String> removed = new LinkedHashSet<String>(builtIn.keywords);
                    removed.removeAll(group.keywords);
                    item.put("added_keywords", jsonKeywords(added));
                    item.put("removed_keywords", jsonKeywords(removed));
                }
                array.put(item);
            } catch (JSONException e) {
                Log.e(TAG, "无法保存商户识别规则", e);
            }
        }
        prefs.edit().putString(KEY_IMPORTED_GROUPS, array.toString()).apply();
    }

    private void migrateImportedGroupStorage() {
        String raw = prefs.getString(KEY_IMPORTED_GROUPS, "");
        if (raw.contains("\"keywords\"")) {
            saveImportedGroups(new ArrayList<RuleGroup>(loadImportedGroups().values()));
        }
    }

    private static void addJsonKeywords(Set<String> target, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            String keyword = values.optString(i).trim();
            if (keyword.length() > 0) {
                target.add(keyword);
            }
        }
    }

    private static void removeJsonKeywords(Set<String> target, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            target.remove(values.optString(i).trim());
        }
    }

    private static JSONArray jsonKeywords(Set<String> keywords) {
        JSONArray array = new JSONArray();
        ArrayList<String> sorted = new ArrayList<String>(keywords);
        Collections.sort(sorted);
        for (String keyword : sorted) {
            array.put(keyword);
        }
        return array;
    }

    private void saveOverrides(List<MerchantOverride> overrides) {
        JSONArray array = new JSONArray();
        for (MerchantOverride override : overrides) {
            if (override.merchantKey.length() == 0 || override.major.trim().length() == 0) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("merchant_key", override.merchantKey);
                item.put("display_name", override.displayName);
                item.put("major", override.major.trim());
                item.put("minor", override.minor.trim());
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_OVERRIDES, array.toString()).apply();
    }

    private void buildBuiltInGroups() {
        try {
            List<String[]> rows = parseCsv(readAssetText("builtin_rule_groups.csv"));
            requireHeader(rows, "group_id", "group_name", "keywords");
            for (int i = 1; i < rows.size(); i++) {
                String groupId = cell(rows.get(i), 0).trim();
                String groupName = cell(rows.get(i), 1).trim();
                String keywords = cell(rows.get(i), 2).trim();
                if (groupId.length() > 0 && groupName.length() > 0 && keywords.length() > 0) {
                    addGroup(i - 1, groupId, groupName, keywords);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "无法加载内置商户识别规则", e);
        }
    }

    private void addGroup(int order, String groupId, String groupName, String keywords) {
        RuleGroup group = builtInGroups.get(groupId);
        if (group == null) {
            group = new RuleGroup(groupId, groupName, order, false);
            builtInGroups.put(groupId, group);
        }
        for (String keyword : keywords.split("\\|")) {
            String value = keyword.trim();
            if (value.length() > 0) {
                group.keywords.add(value);
            }
        }
    }

    private String readAssetText(String name) throws IOException {
        InputStream inputStream = context.getAssets().open(name);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toString("UTF-8");
        } finally {
            inputStream.close();
        }
    }
    private static void requireHeader(List<String[]> rows, String... expected) throws Exception {
        if (rows.isEmpty()) {
            throw new Exception("CSV 为空");
        }
        String[] header = rows.get(0);
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].equals(cell(header, i).trim())) {
                throw new Exception("CSV 表头不符合预期");
            }
        }
    }

    private static List<String[]> parseCsv(String text) throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        ArrayList<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String source = text.replace("\uFEFF", "");
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row.toArray(new String[row.size()]));
                row = new ArrayList<String>();
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        if (quoted) {
            throw new Exception("CSV 引号没有正确闭合");
        }
        row.add(cell.toString());
        boolean hasData = false;
        for (String value : row) {
            if (value.length() > 0) {
                hasData = true;
                break;
            }
        }
        if (hasData) {
            rows.add(row.toArray(new String[row.size()]));
        }
        return rows;
    }

    private static String cell(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(delimiter);
            }
            out.append(values.get(i));
        }
        return out.toString();
    }

    public static class RuleGroup {
        public String groupId;
        public final String groupName;
        public final int order;
        public boolean imported;
        public final LinkedHashSet<String> keywords = new LinkedHashSet<String>();

        RuleGroup(String groupId, String groupName, int order, boolean imported) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.order = order;
            this.imported = imported;
        }

        RuleGroup copy() {
            RuleGroup copy = new RuleGroup(groupId, groupName, order, imported);
            copy.keywords.addAll(keywords);
            return copy;
        }
    }

    public static class CategoryMapping {
        public final String groupId;
        public final String major;
        public final String minor;

        public CategoryMapping(String groupId, String major, String minor) {
            this.groupId = groupId == null ? "" : groupId;
            this.major = major == null ? "" : major;
            this.minor = minor == null ? "" : minor;
        }
    }

    public static class MerchantOverride {
        public final String merchantKey;
        public final String displayName;
        public final String major;
        public final String minor;

        MerchantOverride(String merchantKey, String displayName, String major, String minor) {
            this.merchantKey = merchantKey == null ? "" : merchantKey;
            this.displayName = displayName == null ? "" : displayName;
            this.major = major == null ? "" : major;
            this.minor = minor == null ? "" : minor;
        }
    }

    public static class CategoryResult {
        public final String major;
        public final String minor;
        public final String groupId;
        public final String source;

        CategoryResult(String major, String minor, String groupId, String source) {
            this.major = major;
            this.minor = minor;
            this.groupId = groupId;
            this.source = source;
        }
    }
}
