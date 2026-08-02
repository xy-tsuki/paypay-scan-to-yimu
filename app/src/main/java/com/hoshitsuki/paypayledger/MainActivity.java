package com.hoshitsuki.paypayledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int MAX_TEXT_IMPORT_BYTES = 10 * 1024 * 1024;
    private static final int REQUEST_IMAGES = 41;
    private static final int REQUEST_SAVE_BILLS = 42;
    private static final int REQUEST_IMPORT_CONFIG = 43;
    private static final int REQUEST_SAVE_CONFIG = 44;
    private static final int REQUEST_IMPORT_RULE_GROUPS = 45;
    private static final int REQUEST_SAVE_RULE_GROUPS = 46;
    private static final int REQUEST_PAYPAY_CSV = 47;
    private static final String MIME_EXCEL = "application/vnd.ms-excel";
    private static final String MIME_CSV = "text/csv";

    private final int paper = Color.rgb(244, 241, 236);
    private final int card = Color.rgb(253, 251, 247);
    private final int ink = Color.rgb(70, 67, 63);
    private final int muted = Color.rgb(135, 130, 122);
    private final int sage = Color.rgb(142, 167, 160);
    private final int sand = Color.rgb(222, 211, 194);

    private final ArrayList<BillItem> bills = new ArrayList<BillItem>();
    private final Set<String> seenKeys = new HashSet<String>();
    private final ArrayList<Uri> pendingUris = new ArrayList<Uri>();

    private CategoryRuleStore rules;
    private TextRecognizer recognizer;
    private LinearLayout listArea;
    private TextView statusText;
    private TextView countText;
    private ProgressBar progressBar;
    private Button importButton;
    private Button csvImportButton;
    private Button yimuExportButton;
    private Button exportButton;
    private int processedCount = 0;
    private byte[] pendingCustomWorkbook;
    private String pendingCustomWorkbookName;
    private byte[] pendingCsvExport;
    private String currentScreen = "home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rules = new CategoryRuleStore(this);
        recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        buildUi();
        if (!rules.hasUserMappings()) {
            statusText.setText("还没有设置分类规则，可从商户合集进入设置。");
            showMissingMappingPrompt();
        } else if (rules.consumeMigrationNotice()) {
            showMappingMigrationNotice();
        }
    }

    private void buildUi() {
        currentScreen = "home";
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(paper);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("PayPay 自动记账", 30, ink, true));

        TextView subtitle = text("导入 PayPay 交易履历 CSV 或截图，自动分类并导出一木记账表。", 15, muted, false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        LinearLayout hero = panel(dp(26));
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hero, matchWrap());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.paypay_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setBackground(round(Color.WHITE, dp(20), 0));
        logo.setPadding(dp(8), dp(8), dp(8), dp(8));
        hero.addView(logo, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView heroTitle = text("导入 PayPay 账单", 24, ink, true);
        heroTitle.setGravity(Gravity.CENTER);
        heroTitle.setPadding(0, dp(20), 0, dp(4));
        hero.addView(heroTitle);

        TextView heroSub = text("推荐使用官方 CSV；截图支持多选和自动去重。", 14, muted, false);
        heroSub.setGravity(Gravity.CENTER);
        hero.addView(heroSub);

        csvImportButton = actionButton("导入 PayPay CSV", sage, Color.WHITE, 18);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        importLp.setMargins(0, dp(22), 0, 0);
        hero.addView(csvImportButton, importLp);
        csvImportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCsvPicker(REQUEST_PAYPAY_CSV);
            }
        });

        importButton = actionButton("选择 PayPay 截图", sand, ink, 16);
        LinearLayout.LayoutParams imageImportLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        imageImportLp.setMargins(0, dp(10), 0, 0);
        hero.addView(importButton, imageImportLp);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openImagePicker();
            }
        });

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.setMargins(0, dp(16), 0, 0);
        root.addView(stats, statsLp);

        countText = statCard("0", "已识别账单");
        stats.addView(countText, new LinearLayout.LayoutParams(0, dp(92), 1));
        TextView groupText = statCard(String.valueOf(rules.getEffectiveRuleGroups().size()), "商户合集");
        LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(0, dp(92), 1);
        groupLp.setMargins(dp(12), 0, 0, 0);
        stats.addView(groupText, groupLp);
        groupText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCategoryRulesPage();
            }
        });

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.setMargins(0, dp(16), 0, 0);
        root.addView(progressBar, progressLp);

        statusText = text("等待导入 PayPay CSV 或截图", 14, muted, false);
        statusText.setPadding(0, dp(12), 0, dp(16));
        root.addView(statusText);

        yimuExportButton = actionButton("导出至一木记账", sand, ink, 16);
        yimuExportButton.setEnabled(false);
        root.addView(yimuExportButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        yimuExportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportTempWorkbookToYimu();
            }
        });

        exportButton = actionButton("自定义导出表格", sand, ink, 16);
        exportButton.setEnabled(false);
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        exportLp.setMargins(0, dp(10), 0, 0);
        root.addView(exportButton, exportLp);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCustomExport();
            }
        });

        TextView section = text("识别结果", 20, ink, true);
        section.setPadding(0, dp(24), 0, dp(10));
        root.addView(section);

        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(listArea, matchWrap());
        renderBills();

        setContentView(scrollView);
    }

    private void addSettingsButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = actionButton(label, sand, ink, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.setMargins(0, parent.getChildCount() == 0 ? 0 : dp(8), 0, 0);
        parent.addView(button, lp);
        button.setOnClickListener(listener);
    }

    private void showMissingMappingPrompt() {
        new AlertDialog.Builder(this)
                .setTitle("还没有设置分类规则")
                .setMessage("可以从首页的“商户合集”进入分类规则设置。现在不设置也可以先导入截图，App 会先使用推荐映射。")
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showCategoryRulesPage();
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void showMappingMigrationNotice() {
        new AlertDialog.Builder(this)
                .setTitle("分类规则已兼容迁移")
                .setMessage("旧版“快餐”和“正餐”已经合并为“餐饮”。如果原来两项分类不同，已保留最后一条映射，你可以在分类规则设置中调整。")
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showCategoryRulesPage();
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private LinearLayout pageRoot(String title, boolean showMenu) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(paper);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, matchWrap());

        Button back = actionButton("‹", card, ink, 28);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigateBack();
            }
        });

        TextView titleView = text(title, 22, ink, true);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        top.addView(titleView, titleLp);

        if (showMenu) {
            Button menu = actionButton("⋮", card, ink, 24);
            top.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
            menu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showCategoryMenu();
                }
            });
        } else {
            View spacer = new View(this);
            top.addView(spacer, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        setContentView(scrollView);
        return root;
    }

    private void showCategoryMenu() {
        final String[] items = new String[]{"查看商户识别规则", "规则导入导出", "管理单个商户修正规则"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        if (which == 0) {
                            showRuleGroupsPage();
                        } else if (which == 1) {
                            showRuleTransferPage();
                        } else {
                            showOverridesDialog();
                        }
                    }
                })
                .show();
    }

    private void navigateBack() {
        if ("keywords".equals(currentScreen)) {
            showRuleGroupsPage();
        } else if ("rules".equals(currentScreen) || "transfer".equals(currentScreen)) {
            showCategoryRulesPage();
        } else {
            buildUi();
        }
    }

    @Override
    public void onBackPressed() {
        if ("home".equals(currentScreen)) {
            super.onBackPressed();
        } else {
            navigateBack();
        }
    }

    private void showRuleTransferPage() {
        currentScreen = "transfer";
        LinearLayout root = pageRoot("规则导入导出", false);
        TextView intro = text("这里导入导出账本分类映射和单个商户修正规则，不包含商户识别关键词。", 14, muted, false);
        intro.setPadding(0, dp(14), 0, dp(12));
        root.addView(intro);

        LinearLayout box = panel(dp(14));
        root.addView(box, matchWrap());
        addSettingsButton(box, "导出分类配置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportConfig();
            }
        });
        addSettingsButton(box, "导入分类配置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCsvPicker(REQUEST_IMPORT_CONFIG);
            }
        });
    }

    private void showRuleGroupsPage() {
        currentScreen = "rules";
        LinearLayout root = pageRoot("商户识别规则", false);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, dp(12));
        root.addView(actions, matchWrap());
        Button export = actionButton("导出关键词规则", sand, ink, 14);
        Button importButton = actionButton("导入关键词规则", sand, ink, 14);
        actions.addView(export, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        importLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(importButton, importLp);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportRuleGroups();
            }
        });
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCsvPicker(REQUEST_IMPORT_RULE_GROUPS);
            }
        });

        for (final CategoryRuleStore.RuleGroup group : rules.getEffectiveRuleGroups()) {
            LinearLayout row = panel(dp(14));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            root.addView(row, rowLp);
            row.addView(text(group.groupName, 16, ink, true));
            row.addView(text(group.keywords.size() + " 个关键词" + (group.imported ? "，已自定义" : "，内置规则"), 13, muted, false));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showRuleGroupKeywordsPage(group.groupId);
                }
            });
        }
    }

    private void showRuleGroupKeywordsPage(String groupId) {
        currentScreen = "keywords";
        final CategoryRuleStore.RuleGroup group = rules.getEffectiveRuleGroup(groupId);
        if (group == null) {
            Toast.makeText(this, "没有找到该商户合集", Toast.LENGTH_SHORT).show();
            showRuleGroupsPage();
            return;
        }
        LinearLayout root = pageRoot(group.groupName, false);
        String source = group.imported ? "来源：自定义关键词规则" : "来源：内置文件 app/src/main/assets/builtin_rule_groups.csv";
        TextView intro = text(source + "\n每行一个关键词。保存后会用于后续识别分类。", 14, muted, false);
        intro.setPadding(0, dp(14), 0, dp(8));
        root.addView(intro);

        final EditText keywords = editText("每行一个关键词", joinKeywords(group.keywords));
        keywords.setSingleLine(false);
        keywords.setMinLines(12);
        keywords.setGravity(Gravity.TOP);
        root.addView(keywords, matchWrap());

        Button save = actionButton("保存关键词", sage, Color.WHITE, 16);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        saveLp.setMargins(0, dp(12), 0, 0);
        root.addView(save, saveLp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rules.saveRuleGroupKeywords(group.groupId, group.groupName, splitKeywords(keywords.getText().toString()));
                recategorizeBills();
                Toast.makeText(MainActivity.this, "关键词已保存", Toast.LENGTH_SHORT).show();
                showRuleGroupsPage();
            }
        });
    }

    private TextView statCard(String number, String label) {
        TextView tv = text(number + "\n" + label, 15, ink, true);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(dp(3), 1.0f);
        tv.setBackground(round(card, dp(18), Color.argb(45, 70, 67, 63)));
        return tv;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGES);
    }

    private void openCsvPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_SAVE_BILLS || requestCode == REQUEST_SAVE_CONFIG || requestCode == REQUEST_SAVE_RULE_GROUPS) {
                Toast.makeText(this, "已取消导出", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQUEST_IMAGES) {
            handleImageSelection(data);
            return;
        }
        if (requestCode == REQUEST_PAYPAY_CSV) {
            handlePayPayCsvImport(data.getData());
            return;
        }
        if (requestCode == REQUEST_SAVE_BILLS) {
            handleCustomExport(data.getData());
            return;
        }
        if (requestCode == REQUEST_SAVE_CONFIG || requestCode == REQUEST_SAVE_RULE_GROUPS) {
            handleCsvExport(data.getData());
            return;
        }
        if (requestCode == REQUEST_IMPORT_CONFIG || requestCode == REQUEST_IMPORT_RULE_GROUPS) {
            handleCsvImport(requestCode, data.getData());
        }
    }

    private void handleImageSelection(Intent data) {
        pendingUris.clear();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                pendingUris.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            pendingUris.add(data.getData());
        }
        for (Uri uri : pendingUris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
        processImages();
    }

    private void handleCsvImport(int requestCode, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            String csv = readText(uri);
            String result;
            if (requestCode == REQUEST_IMPORT_CONFIG) {
                result = rules.importCombinedConfigCsv(csv);
            } else {
                result = rules.importRuleGroupsCsvAppend(csv);
            }
            recategorizeBills();
            if ("home".equals(currentScreen)) {
                renderBills();
                statusText.setText(result + "。当前识别结果已重新分类。");
            } else if (requestCode == REQUEST_IMPORT_RULE_GROUPS) {
                showRuleGroupsPage();
            }
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePayPayCsvImport(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            PayPayCsvParser.Result result = PayPayCsvParser.parse(readText(uri));
            bills.clear();
            seenKeys.clear();
            for (PayPayCsvParser.Record record : result.records) {
                CategoryRuleStore.CategoryResult category = rules.classify(record.merchant);
                BillItem item = new BillItem(
                        record.merchant,
                        record.date,
                        record.time,
                        record.amount,
                        record.transactionId,
                        category);
                if (seenKeys.add(item.key())) {
                    bills.add(item);
                }
            }
            finishImportedBills();
            String summary = "已导入 " + bills.size() + " 条卡片／信用支付账单";
            if (result.ignoredNonExpense > 0 || result.invalid > 0) {
                summary += "，跳过 " + (result.ignoredNonExpense + result.invalid) + " 条无有效支出的记录";
            }
            statusText.setText(summary);
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            statusText.setText("PayPay CSV 导入失败：" + e.getMessage());
            Toast.makeText(this, "PayPay CSV 导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void finishImportedBills() {
        Collections.sort(bills, new Comparator<BillItem>() {
            @Override
            public int compare(BillItem a, BillItem b) {
                return (a.date + " " + a.time).compareTo(b.date + " " + b.time);
            }
        });
        progressBar.setProgress(100);
        renderBills();
        countText.setText(bills.size() + "\n已识别账单");
        boolean hasBills = !bills.isEmpty();
        yimuExportButton.setEnabled(hasBills);
        exportButton.setEnabled(hasBills);
    }

    private void processImages() {
        bills.clear();
        seenKeys.clear();
        processedCount = 0;
        listArea.removeAllViews();
        yimuExportButton.setEnabled(false);
        exportButton.setEnabled(false);
        importButton.setEnabled(false);
        csvImportButton.setEnabled(false);
        statusText.setText("正在识别 0/" + pendingUris.size());
        progressBar.setProgress(0);
        processNextImage();
    }

    private void processNextImage() {
        if (processedCount >= pendingUris.size()) {
            finishProcessing();
            return;
        }
        final Uri uri = pendingUris.get(processedCount);
        final ArrayList<BillItem> parsedBills = new ArrayList<BillItem>();
        final Runnable completeImage = new Runnable() {
            @Override
            public void run() {
                addParsedBills(parsedBills);
                processedCount++;
                int progress = pendingUris.isEmpty() ? 0 : (processedCount * 100 / pendingUris.size());
                progressBar.setProgress(progress);
                statusText.setText("正在识别 " + processedCount + "/" + pendingUris.size());
                processNextImage();
            }
        };
        try {
            final InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            parsedBills.addAll(parsePayPayText(text, image.getWidth(), image.getHeight()));
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Toast.makeText(MainActivity.this, "整图识别失败，正在尝试分区识别", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<Text>() {
                        @Override
                        public void onComplete(com.google.android.gms.tasks.Task<Text> task) {
                            processCroppedPasses(uri, parsedBills, completeImage);
                        }
                    });
        } catch (IOException e) {
            completeImage.run();
        }
    }

    private void processCroppedPasses(Uri uri, final List<BillItem> parsedBills, Runnable completion) {
        try {
            Bitmap source = loadOcrBitmap(uri, 2200);
            if (source == null || source.getWidth() < 200 || source.getHeight() < 400) {
                if (source != null) {
                    source.recycle();
                }
                completion.run();
                return;
            }
            processCroppedPass(source, 0, parsedBills, completion);
        } catch (Exception ignored) {
            completion.run();
        }
    }

    private Bitmap loadOcrBitmap(Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = getContentResolver().openInputStream(uri);
        if (boundsStream == null) {
            throw new IOException("无法读取截图");
        }
        try {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } finally {
            boundsStream.close();
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("截图格式无法解码");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (options.inSampleSize * 2) >= maxDimension) {
            options.inSampleSize *= 2;
        }
        InputStream imageStream = getContentResolver().openInputStream(uri);
        if (imageStream == null) {
            throw new IOException("无法读取截图");
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
            if (bitmap == null) {
                throw new IOException("截图格式无法解码");
            }
            return bitmap;
        } finally {
            imageStream.close();
        }
    }

    private void processCroppedPass(
            final Bitmap source,
            final int pass,
            final List<BillItem> parsedBills,
            final Runnable completion) {
        final float[][] ranges = new float[][]{
                {0.14f, 0.53f},
                {0.40f, 0.79f},
                {0.66f, 0.99f}
        };
        if (pass >= ranges.length) {
            source.recycle();
            completion.run();
            return;
        }

        int left = Math.max(0, Math.round(source.getWidth() * 0.08f));
        int right = Math.min(source.getWidth(), Math.round(source.getWidth() * 0.995f));
        int top = Math.max(0, Math.round(source.getHeight() * ranges[pass][0]));
        int bottom = Math.min(source.getHeight(), Math.round(source.getHeight() * ranges[pass][1]));
        if (right <= left || bottom <= top) {
            processCroppedPass(source, pass + 1, parsedBills, completion);
            return;
        }

        final Bitmap crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
        final InputImage cropImage = InputImage.fromBitmap(crop, 0);
        recognizer.process(cropImage)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        parsedBills.addAll(parsePayPayText(text, crop.getWidth(), crop.getHeight()));
                    }
                })
                .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<Text>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<Text> task) {
                        crop.recycle();
                        processCroppedPass(source, pass + 1, parsedBills, completion);
                    }
                });
    }

    private void addParsedBills(List<BillItem> parsed) {
        for (BillItem item : parsed) {
            int duplicateIndex = findDuplicateBill(item);
            if (duplicateIndex >= 0) {
                mergeDuplicateBill(bills.get(duplicateIndex), item);
            } else if (seenKeys.add(item.key())) {
                bills.add(item);
            }
        }
        countText.setText(bills.size() + "\n已识别账单");
        renderBills();
    }

    private int findDuplicateBill(BillItem item) {
        for (int i = 0; i < bills.size(); i++) {
            BillItem existing = bills.get(i);
            if (existing.amount == item.amount
                    && existing.date.equals(item.date)
                    && existing.time.equals(item.time)
                    && isLikelySameMerchant(existing.merchant, item.merchant)) {
                return i;
            }
        }
        return -1;
    }

    private void mergeDuplicateBill(BillItem existing, BillItem incoming) {
        if (merchantCompletenessScore(incoming.merchant) > merchantCompletenessScore(existing.merchant)) {
            existing.merchant = incoming.merchant;
            existing.applyCategory(rules.classify(existing.merchant));
        }
        seenKeys.add(existing.key());
    }

    private boolean isLikelySameMerchant(String left, String right) {
        return MerchantDeduplicator.isLikelySame(left, right);
    }

    private String dedupeMerchant(String value) {
        return MerchantDeduplicator.normalize(value);
    }

    private int merchantCompletenessScore(String merchant) {
        return dedupeMerchant(merchant).length();
    }

    private void finishProcessing() {
        importButton.setEnabled(true);
        csvImportButton.setEnabled(true);
        Collections.sort(bills, new Comparator<BillItem>() {
            @Override
            public int compare(BillItem a, BillItem b) {
                return (a.date + " " + a.time).compareTo(b.date + " " + b.time);
            }
        });
        renderBills();
        countText.setText(bills.size() + "\n已识别账单");
        if (bills.isEmpty()) {
            statusText.setText("没有识别到可导入的账单，请确认截图是 PayPay 交易履历列表。");
            return;
        }
        yimuExportButton.setEnabled(true);
        exportButton.setEnabled(true);
        statusText.setText("识别完成，正在生成一木记账导入表格。");
        prepareTempExportAndPrompt();
    }

    private List<BillItem> parsePayPayText(Text text, int imageWidth, int imageHeight) {
        ArrayList<LineItem> lines = new ArrayList<LineItem>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                String value = clean(line.getText());
                if (box != null && value.length() > 0) {
                    lines.add(new LineItem(value, box));
                }
            }
        }
        Collections.sort(lines, new Comparator<LineItem>() {
            @Override
            public int compare(LineItem a, LineItem b) {
                if (Math.abs(a.box.top - b.box.top) > 10) {
                    return a.box.top - b.box.top;
                }
                return a.box.left - b.box.left;
            }
        });

        ArrayList<BillItem> result = new ArrayList<BillItem>();
        for (LineItem amountLine : lines) {
            Integer amount = extractAmount(amountLine.text);
            if (amount == null || amountLine.centerX() < imageWidth * 0.42f) {
                continue;
            }
            LineItem dateLine = findDateLine(lines, amountLine, imageWidth, imageHeight);
            if (dateLine == null) {
                continue;
            }
            String[] dateTime = extractDateTime(dateLine.text);
            if (dateTime == null) {
                continue;
            }
            String merchant = findMerchant(lines, amountLine, dateLine, imageWidth, imageHeight);
            if (merchant.length() == 0) {
                merchant = removeAmount(amountLine.text);
            }
            if (merchant.length() == 0 || isNoise(merchant)) {
                continue;
            }
            CategoryRuleStore.CategoryResult category = rules.classify(merchant);
            result.add(new BillItem(merchant, dateTime[0], dateTime[1], amount, category));
        }
        return result;
    }

    private LineItem findDateLine(List<LineItem> lines, LineItem amountLine, int imageWidth, int imageHeight) {
        LineItem best = null;
        int bestScore = Integer.MAX_VALUE;
        for (LineItem line : lines) {
            if (line.centerX() > imageWidth * 0.82f) {
                continue;
            }
            LineItem candidate = line;
            if (extractDateTime(candidate.text) == null) {
                candidate = combineDateAndTimeLine(lines, line, imageWidth);
                if (candidate == null) {
                    continue;
                }
            }
            int maxDistance = Math.max(210, imageHeight / 9);
            int dy = Math.abs(candidate.centerY() - amountLine.centerY());
            if (dy > maxDistance || candidate.centerY() < amountLine.centerY() - Math.max(30, amountLine.box.height())) {
                continue;
            }
            int score = dy + Math.abs(candidate.box.left - amountLine.box.left) / 6;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private LineItem combineDateAndTimeLine(List<LineItem> lines, LineItem first, int imageWidth) {
        for (LineItem second : lines) {
            if (second == first || second.centerX() > imageWidth * 0.82f) {
                continue;
            }
            int verticalGap = Math.max(0,
                    Math.max(first.box.top, second.box.top) - Math.min(first.box.bottom, second.box.bottom));
            int allowedGap = Math.max(55, Math.max(first.box.height(), second.box.height()) * 2);
            if (verticalGap > allowedGap || Math.abs(first.box.left - second.box.left) > imageWidth * 0.35f) {
                continue;
            }
            LineItem upper = first.box.top <= second.box.top ? first : second;
            LineItem lower = upper == first ? second : first;
            String combinedText = upper.text + " " + lower.text;
            if (extractDateTime(combinedText) != null) {
                Rect box = new Rect(upper.box);
                box.union(lower.box);
                return new LineItem(combinedText, box);
            }
        }
        return null;
    }

    private String findMerchant(
            List<LineItem> lines,
            LineItem amountLine,
            LineItem dateLine,
            int imageWidth,
            int imageHeight) {
        ArrayList<LineItem> candidates = new ArrayList<LineItem>();
        int merchantBand = Math.max(110, imageHeight / 18);
        for (LineItem line : lines) {
            if (line.centerX() > imageWidth * 0.78f) {
                continue;
            }
            if (line.box.left < imageWidth * 0.14f) {
                continue;
            }
            if (line.box.top < amountLine.box.top - merchantBand || line.box.top > dateLine.box.top - 2) {
                continue;
            }
            if (line == amountLine || line == dateLine || isNoise(line.text)
                    || extractAmount(line.text) != null || extractDateTime(line.text) != null
                    || isDateTimeFragment(line.text)) {
                continue;
            }
            candidates.add(line);
        }
        Collections.sort(candidates, new Comparator<LineItem>() {
            @Override
            public int compare(LineItem a, LineItem b) {
                if (Math.abs(a.box.top - b.box.top) > 10) {
                    return a.box.top - b.box.top;
                }
                return a.box.left - b.box.left;
            }
        });
        StringBuilder merchant = new StringBuilder();
        String amountSideText = removeAmount(amountLine.text);
        if (amountSideText.length() > 0 && !isNoise(amountSideText)) {
            merchant.append(amountSideText);
        }
        int used = 0;
        int lastBottom = -1;
        for (LineItem candidate : candidates) {
            if (used >= 3) {
                break;
            }
            int allowedGap = Math.max(48, candidate.box.height() * 2);
            if (lastBottom > 0 && candidate.box.top - lastBottom > allowedGap) {
                break;
            }
            if (merchant.length() > 0) {
                merchant.append(" ");
            }
            merchant.append(candidate.text);
            lastBottom = candidate.box.bottom;
            used++;
        }
        return polishMerchantName(merchant.toString());
    }

    private String removeAmount(String text) {
        return clean(text.replaceAll("[0-9０-９Oo〇○][0-9０-９Oo〇○,，.．\\s]*\\s*[円¥￥]", ""));
    }

    private String polishMerchantName(String value) {
        String merchant = clean(value)
                .replaceAll("\\s+([店駅丁目])$", "$1")
                .replaceAll("(?i)\\bS\\s+u\\s+i\\s+c\\s+a\\b", "Suica")
                .replaceAll("(?i)\\bA\\s+p\\s+p\\s+l\\s+e\\b", "Apple");
        return merchant;
    }

    private Integer extractAmount(String text) {
        return OcrTextParser.extractAmount(text);
    }

    private String[] extractDateTime(String text) {
        OcrTextParser.DateTime result = OcrTextParser.extractDateTime(text);
        return result == null ? null : new String[]{result.date, result.time};
    }

    private boolean isDateTimeFragment(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC);
        return value.matches(".*20[0-9Oo〇○]{2}\\s*年.*")
                || value.matches(".*[0-9Oo〇○]{1,2}\\s*月\\s*[0-9Oo〇○]{1,2}\\s*日.*")
                || value.matches(".*[0-9Oo〇○]{1,2}\\s*(?:時|峙|:)\\s*[0-9Oo〇○]{1,2}\\s*分?.*");
    }

    private String clean(String text) {
        return normalize(text).replaceAll("\\s+", " ").trim();
    }

    private String normalize(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '０' && c <= '９') {
                out.append((char) ('0' + c - '０'));
            } else if (c == '，') {
                out.append(',');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean isNoise(String text) {
        String m = normalize(text).toLowerCase(Locale.ROOT);
        return m.length() < 2
                || m.equals("r")
                || m.contains("取引履歴")
                || m.contains("paypayカード")
                || m.contains("paypay残高")
                || m.contains("クレジット")
                || m.contains("支払い")
                || m.contains("visa")
                || m.contains("すべて")
                || m.contains("kb/s")
                || m.matches("20[0-9]{2}年[0-9]{1,2}月")
                || m.matches("[0-9:]+")
                || m.matches("[0-9]+");
    }

    private void renderBills() {
        listArea.removeAllViews();
        if (bills.isEmpty()) {
            TextView empty = text("导入后会在这里看到每一条识别出的账单。", 14, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(22), 0, dp(22));
            empty.setBackground(round(card, dp(16), 0));
            listArea.addView(empty, matchWrap());
            return;
        }
        for (final BillItem bill : bills) {
            LinearLayout row = panel(dp(16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(10));
            listArea.addView(row, lp);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(top, matchWrap());

            TextView name = text(bill.merchant, 17, ink, true);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView amount = text(String.format(Locale.US, "%,d円", bill.amount), 18, ink, true);
        amount.setGravity(Gravity.END);
            top.addView(amount);

            String minor = bill.minor.length() == 0 ? "无二级分类" : bill.minor;
            String group = bill.groupId.length() == 0 ? bill.categorySource : bill.groupId + " / " + bill.categorySource;
            TextView meta = text(bill.date + " " + bill.time + "  ·  " + bill.major + " / " + minor + "  ·  " + group, 13, muted, false);
            meta.setPadding(0, dp(8), 0, 0);
            row.addView(meta);

            TextView hint = text("点击修正分类，并记住为该商户规则", 12, sage, false);
            hint.setPadding(0, dp(6), 0, 0);
            row.addView(hint);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showBillCategoryDialog(bill);
                }
            });
        }
    }

    private void showBillCategoryDialog(final BillItem bill) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), 0);
        final EditText merchantName = editText("商户名称", bill.merchant);
        final EditText major = editText("一级分类", bill.major);
        final EditText minor = editText("二级分类，可为空", bill.minor);
        form.addView(text("商户名称", 13, muted, false));
        form.addView(merchantName);
        form.addView(major);
        form.addView(minor);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修正分类")
                .setView(form)
                .create();
        Button saveRule = actionButton("保存为商户规则", sage, Color.WHITE, 15);
        Button currentOnly = actionButton("仅修正当前账单", sand, ink, 15);
        Button cancel = actionButton("取消", card, ink, 15);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        actionLp.setMargins(0, dp(12), 0, 0);
        form.addView(saveRule, actionLp);
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        currentLp.setMargins(0, dp(8), 0, 0);
        form.addView(currentOnly, currentLp);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        cancelLp.setMargins(0, dp(8), 0, 0);
        form.addView(cancel, cancelLp);
        currentOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String merchantValue = merchantName.getText().toString().trim();
                String majorValue = major.getText().toString().trim();
                String minorValue = minor.getText().toString().trim();
                if (merchantValue.length() == 0) {
                    Toast.makeText(MainActivity.this, "商户名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (majorValue.length() == 0) {
                    Toast.makeText(MainActivity.this, "一级分类不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                bill.merchant = merchantValue;
                bill.major = majorValue;
                bill.minor = minorValue;
                bill.groupId = "";
                bill.categorySource = "当前账单修正";
                renderBills();
                statusText.setText("已仅修正当前账单。");
                dialog.dismiss();
            }
        });
        saveRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String merchantValue = merchantName.getText().toString().trim();
                String majorValue = major.getText().toString().trim();
                String minorValue = minor.getText().toString().trim();
                if (merchantValue.length() == 0) {
                    Toast.makeText(MainActivity.this, "商户名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (majorValue.length() == 0) {
                    Toast.makeText(MainActivity.this, "一级分类不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                bill.merchant = merchantValue;
                rules.saveMerchantOverride(merchantValue, majorValue, minorValue);
                recategorizeBills();
                renderBills();
                statusText.setText("已记住 " + merchantValue + " 的商户修正规则。");
                dialog.dismiss();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void showCategoryRulesPage() {
        currentScreen = "category";
        final List<CategoryRuleStore.RuleGroup> groups = rules.getEffectiveRuleGroups();
        final Map<String, CategoryRuleStore.CategoryMapping> mappings = rules.loadMappings();
        final ArrayList<EditText> majors = new ArrayList<EditText>();
        final ArrayList<EditText> minors = new ArrayList<EditText>();

        LinearLayout form = pageRoot("编辑账本分类映射", true);

        TextView intro = text("为每个商户合集设置账本分类。二级分类可以留空。", 14, muted, false);
        intro.setPadding(0, dp(14), 0, dp(10));
        form.addView(intro);

        for (CategoryRuleStore.RuleGroup group : groups) {
            CategoryRuleStore.CategoryMapping mapping = mappings.get(group.groupId);
            if (mapping == null) {
                mapping = rules.recommendedMapping(group.groupId);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            form.addView(row, matchWrap());

            TextView label = text(group.groupName, 14, ink, true);
            label.setMaxLines(2);
            row.addView(label, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout fields = new LinearLayout(this);
            fields.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(fields, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            EditText major = editText("一级分类，必填", mapping.major);
            EditText minor = editText("二级分类，可为空", mapping.minor);
            majors.add(major);
            minors.add(minor);
            fields.addView(major, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout.LayoutParams minorLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            minorLp.setMargins(dp(8), 0, 0, 0);
            fields.addView(minor, minorLp);
        }

        Button save = actionButton("保存分类映射", sage, Color.WHITE, 16);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        saveLp.setMargins(0, dp(16), 0, 0);
        form.addView(save, saveLp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayList<CategoryRuleStore.CategoryMapping> newMappings = new ArrayList<CategoryRuleStore.CategoryMapping>();
                for (int i = 0; i < groups.size(); i++) {
                    String major = majors.get(i).getText().toString().trim();
                    String minor = minors.get(i).getText().toString().trim();
                    if (major.length() == 0) {
                        Toast.makeText(MainActivity.this, groups.get(i).groupName + " 的一级分类不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    newMappings.add(new CategoryRuleStore.CategoryMapping(groups.get(i).groupId, major, minor));
                }
                rules.saveMappings(newMappings);
                recategorizeBills();
                Toast.makeText(MainActivity.this, "分类规则已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOverridesDialog() {
        final ArrayList<CategoryRuleStore.MerchantOverride> overrides = new ArrayList<CategoryRuleStore.MerchantOverride>(rules.loadOverrides().values());
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), 0);
        scroll.addView(list);

        if (overrides.isEmpty()) {
            list.addView(text("还没有商户单独修正规则。你可以在识别结果中点击某条账单来创建。", 14, muted, false));
        } else {
            for (final CategoryRuleStore.MerchantOverride override : overrides) {
                LinearLayout row = panel(dp(12));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(8));
                list.addView(row, rowLp);
                row.addView(text(override.displayName.length() == 0 ? override.merchantKey : override.displayName, 15, ink, true));
                row.addView(text(override.major + (override.minor.length() == 0 ? "" : " / " + override.minor), 13, muted, false));

                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setPadding(0, dp(8), 0, 0);
                row.addView(actions);
                Button edit = actionButton("编辑", sand, ink, 13);
                Button delete = actionButton("删除", sand, ink, 13);
                actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1));
                LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(42), 1);
                deleteLp.setMargins(dp(8), 0, 0, 0);
                actions.addView(delete, deleteLp);
                edit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showOverrideEditDialog(override);
                    }
                });
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        rules.deleteMerchantOverride(override.merchantKey);
                        recategorizeBills();
                        renderBills();
                        Toast.makeText(MainActivity.this, "已删除商户修正规则", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("商户单独修正规则")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .show();
    }

    private void showOverrideEditDialog(final CategoryRuleStore.MerchantOverride override) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), 0);
        form.addView(text(override.displayName.length() == 0 ? override.merchantKey : override.displayName, 16, ink, true));
        final EditText major = editText("一级分类", override.major);
        final EditText minor = editText("二级分类，可为空", override.minor);
        form.addView(major);
        form.addView(minor);

        new AlertDialog.Builder(this)
                .setTitle("编辑商户规则")
                .setView(form)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String majorValue = major.getText().toString().trim();
                        if (majorValue.length() == 0) {
                            Toast.makeText(MainActivity.this, "一级分类不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        rules.saveMerchantOverride(override.displayName.length() == 0 ? override.merchantKey : override.displayName, majorValue, minor.getText().toString().trim());
                        recategorizeBills();
                        renderBills();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void recategorizeBills() {
        for (BillItem bill : bills) {
            bill.applyCategory(rules.classify(bill.merchant));
        }
    }

    private void prepareTempExportAndPrompt() {
        if (bills.isEmpty()) {
            Toast.makeText(this, "没有可导出的账单", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File tempFile = writeTempWorkbook();
            final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
            statusText.setText("表格已生成，可以直接分享至一木记账 App 快速导入。");
            showTempShareDialog(uri);
        } catch (Exception e) {
            statusText.setText("生成表格失败：" + e.getMessage());
            Toast.makeText(this, "生成表格失败", Toast.LENGTH_LONG).show();
        }
    }

    private File writeTempWorkbook() throws Exception {
        File output = new File(getCacheDir(), "paypay_yimu_latest.xls");
        FileOutputStream outputStream = new FileOutputStream(output, false);
        try {
            outputStream.write(XlsExporter.create(bills));
        } finally {
            outputStream.close();
        }
        return output;
    }

    private void exportTempWorkbookToYimu() {
        if (bills.isEmpty()) {
            Toast.makeText(this, "没有可导出的账单", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File tempFile = writeTempWorkbook();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
            statusText.setText("表格已生成，可以直接分享至一木记账 App 快速导入。");
            shareWorkbook(uri);
        } catch (Exception e) {
            statusText.setText("生成表格失败：" + e.getMessage());
            Toast.makeText(this, "生成表格失败", Toast.LENGTH_LONG).show();
        }
    }

    private void showTempShareDialog(final Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("表格已生成")
                .setMessage("表格已生成，可以直接分享至一木记账 App 快速导入。")
                .setPositiveButton("分享至一木记账 App", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        shareWorkbook(uri);
                    }
                })
                .setNegativeButton("自定义导出表格", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startCustomExport();
                    }
                })
                .setNeutralButton("稍后", null)
                .show();
    }

    private void shareWorkbook(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(MIME_EXCEL);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TITLE, "PayPay_一木记账.xls");
        intent.setClipData(ClipData.newUri(getContentResolver(), "一木记账导入表格", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "分享至一木记账 App"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开分享面板：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCustomExport() {
        if (bills.isEmpty()) {
            Toast.makeText(this, "没有可导出的账单", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pendingCustomWorkbook = XlsExporter.create(bills);
            pendingCustomWorkbookName = createWorkbookFileName();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(MIME_EXCEL);
            intent.putExtra(Intent.EXTRA_TITLE, pendingCustomWorkbookName);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_SAVE_BILLS);
        } catch (Exception e) {
            statusText.setText("导出失败：" + e.getMessage());
            Toast.makeText(this, "导出失败", Toast.LENGTH_LONG).show();
        }
    }

    private void handleCustomExport(Uri uri) {
        if (uri == null || pendingCustomWorkbook == null) {
            return;
        }
        try {
            writeUri(uri, pendingCustomWorkbook);
            statusText.setText("已导出至 " + uri.toString());
            Toast.makeText(this, "已导出至所选路径", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            statusText.setText("导出失败：" + e.getMessage());
            Toast.makeText(this, "导出失败", Toast.LENGTH_LONG).show();
        } finally {
            pendingCustomWorkbook = null;
            pendingCustomWorkbookName = null;
        }
    }

    private String createWorkbookFileName() {
        return "PayPay_一木记账_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".xls";
    }

    private void exportConfig() {
        try {
            pendingCsvExport = rules.exportCombinedConfigCsv().getBytes("UTF-8");
            startCsvSave("paypay_yimu_category_config.csv", REQUEST_SAVE_CONFIG);
        } catch (Exception e) {
            Toast.makeText(this, "导出配置失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportRuleGroups() {
        try {
            pendingCsvExport = rules.exportRuleGroupsCsv().getBytes("UTF-8");
            startCsvSave("paypay_yimu_merchant_keywords.csv", REQUEST_SAVE_RULE_GROUPS);
        } catch (Exception e) {
            Toast.makeText(this, "导出关键词规则失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCsvSave(String fileName, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MIME_CSV);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void handleCsvExport(Uri uri) {
        if (uri == null || pendingCsvExport == null) {
            return;
        }
        try {
            writeUri(uri, pendingCsvExport);
            Toast.makeText(this, "已导出至所选路径", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingCsvExport = null;
        }
    }

    private void writeUri(Uri uri, byte[] data) throws IOException {
        OutputStream outputStream = getContentResolver().openOutputStream(uri, "w");
        if (outputStream == null) {
            throw new IOException("无法写入文件");
        }
        try {
            outputStream.write(data);
        } finally {
            outputStream.close();
        }
    }

    private String readText(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("无法读取文件");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAX_TEXT_IMPORT_BYTES) {
                    throw new IOException("导入文件超过 10 MB");
                }
            }
            return bytes.toString("UTF-8");
        } finally {
            inputStream.close();
        }
    }

    private String joinKeywords(Set<String> values) {
        ArrayList<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (String value : sorted) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(value);
        }
        return out.toString();
    }

    private List<String> splitKeywords(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        String[] lines = text.split("[\\r\\n]+");
        for (String line : lines) {
            String value = line.trim();
            if (value.length() > 0) {
                out.add(value);
            }
        }
        return new ArrayList<String>(out);
    }

    private Button actionButton(String label, int color, int textColor, int sp) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(sp);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(round(color, dp(14), 0));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private EditText editText(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setTextSize(15);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private LinearLayout panel(int padding) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackground(round(card, dp(18), Color.argb(35, 70, 67, 63)));
        return panel;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(true);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.close();
    }

    private static class LineItem {
        final String text;
        final Rect box;

        LineItem(String text, Rect box) {
            this.text = text;
            this.box = box;
        }

        int centerX() {
            return box.left + box.width() / 2;
        }

        int centerY() {
            return box.top + box.height() / 2;
        }
    }

    private static class BillItem {
        String merchant;
        final String date;
        final String time;
        final int amount;
        String major;
        String minor;
        String groupId;
        String categorySource;

        final String sourceId;

        BillItem(String merchant, String date, String time, int amount, CategoryRuleStore.CategoryResult category) {
            this(merchant, date, time, amount, "", category);
        }

        BillItem(String merchant, String date, String time, int amount, String sourceId, CategoryRuleStore.CategoryResult category) {
            this.merchant = merchant;
            this.date = date;
            this.time = time;
            this.amount = amount;
            this.sourceId = sourceId == null ? "" : sourceId.trim();
            applyCategory(category);
        }

        void applyCategory(CategoryRuleStore.CategoryResult category) {
            major = category.major;
            minor = category.minor;
            groupId = category.groupId;
            categorySource = category.source;
        }

        String key() {
            return sourceId.length() > 0
                    ? "paypay|" + sourceId
                    : merchant + "|" + date + "|" + time + "|" + amount;
        }
    }

    private static class XlsExporter {
        static byte[] create(List<BillItem> bills) throws Exception {
            ArrayList<YimuXlsExporter.Entry> entries =
                    new ArrayList<YimuXlsExporter.Entry>();
            for (BillItem bill : bills) {
                entries.add(new YimuXlsExporter.Entry(
                        bill.merchant,
                        bill.date,
                        bill.time,
                        bill.amount,
                        bill.major,
                        bill.minor));
            }
            return YimuXlsExporter.create(entries);
        }
    }
}
