package com.example.callhierarchyplugin.views;

import com.example.callhierarchyplugin.search.CallInfo;
import com.example.callhierarchyplugin.search.DirectCsvExportJob;
import com.example.callhierarchyplugin.search.RecursiveCallerSearchJob;
import com.example.callhierarchyplugin.utils.JDTUtils;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.IActionBars;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;


import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class CallHierarchyView extends ViewPart {

    public static final String ID = "com.example.callhierarchyplugin.views.CallHierarchyView";

    private Text methodInputText;
    private Button searchButton;
    private Button directExportCsvButton;
    private TableViewer viewer;
    // private Label countLabel; // ★★★ 件数表示用ラベルを削除 ★★★

    private List<CallInfo> lastFullResults = Collections.emptyList();

    // テーブル列のインデックス (変更なし)
    private static final int COL_ORIGINAL_CALLER = 0;
    private static final int COL_DIRECT_CALLER = 1;
    private static final int COL_LINE = 2;
    private static final int COL_ARGUMENTS = 3;


    @Override
    public void createPartControl(Composite parent) {
        GridLayout parentLayout = new GridLayout(1, false);
        parent.setLayout(parentLayout);

        // --- 上部入力エリア (変更なし) ---
        Composite topComposite = new Composite(parent, SWT.NONE);
        topComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout topLayout = new GridLayout(4, false);
        topComposite.setLayout(topLayout);

        Label methodLabel = new Label(topComposite, SWT.NONE);
        methodLabel.setText("メソッド:");
        methodLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

        methodInputText = new Text(topComposite, SWT.BORDER | SWT.SINGLE);
        methodInputText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        methodInputText.setToolTipText("例: com.example.MyClass#myMethod");

        searchButton = new Button(topComposite, SWT.PUSH);
        searchButton.setText("検索");
        searchButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

        directExportCsvButton = new Button(topComposite, SWT.PUSH);
        directExportCsvButton.setText("CSV出力");
        directExportCsvButton.setToolTipText("結果を表示せずに直接CSVファイルに出力します");
        directExportCsvButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));


        // --- 中央テーブルビューアエリア (変更なし) ---
        viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
        // ★★★ テーブルが利用可能な垂直スペース全体を占めるようにする ★★★
        viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createColumns(viewer);
        final Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        // --- 下部エリア (件数ラベルとボタンがあったコンポジットを削除) ---
        // Composite bottomComposite = new Composite(parent, SWT.NONE); // ★★★ 削除 ★★★
        // ... (bottomComposite と countLabel の設定コードを削除) ...

        // --- リスナー ---
        methodInputText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) { startSearchFromInput(); }
            }
        });
        searchButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { startSearchFromInput(); }
        });
        directExportCsvButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                startDirectCsvExport();
            }
        });

        addMouseListener(); // テーブルのダブルクリックリスナー

        getSite().setSelectionProvider(viewer);
    }

    /** テーブルビューアに列を作成 (変更なし) */
    private void createColumns(final TableViewer viewer) {
        String[] titles = { "起点クラス・メソッド", "直接の呼び出し元", "行", "引数" };
        int[] bounds = { 250, 250, 50, 400 };
        TableViewerColumn col;
        col = createTableViewerColumn(titles[COL_ORIGINAL_CALLER], bounds[COL_ORIGINAL_CALLER], COL_ORIGINAL_CALLER);
        col.setLabelProvider(new ColumnLabelProvider() { @Override public String getText(Object element) { return ((CallInfo) element).getOriginalCallerName(); } });
        col = createTableViewerColumn(titles[COL_DIRECT_CALLER], bounds[COL_DIRECT_CALLER], COL_DIRECT_CALLER);
        col.setLabelProvider(new ColumnLabelProvider() { @Override public String getText(Object element) { return ((CallInfo) element).getDirectCallerName(); } });
        col = createTableViewerColumn(titles[COL_LINE], bounds[COL_LINE], COL_LINE);
        col.setLabelProvider(new ColumnLabelProvider() { @Override public String getText(Object element) { int line = ((CallInfo) element).getLineNumber(); return line > 0 ? String.valueOf(line) : "N/A"; } });
        col = createTableViewerColumn(titles[COL_ARGUMENTS], bounds[COL_ARGUMENTS], COL_ARGUMENTS);
        col.setLabelProvider(new ColumnLabelProvider() { @Override public String getText(Object element) { return ((CallInfo) element).getArgumentsAsString(); } });
    }

    /** TableViewerColumn を作成 (変更なし) */
    private TableViewerColumn createTableViewerColumn(String title, int bound, final int colNumber) {
        final TableViewerColumn viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
        final TableColumn column = viewerColumn.getColumn();
        column.setText(title);
        column.setWidth(bound);
        column.setResizable(true);
        column.setMoveable(true);
        return viewerColumn;
    }

    /** テキスト入力フィールドの内容に基づいて検索を開始 (表示あり) */
    private void startSearchFromInput() {
        String inputText = methodInputText.getText().trim();
        if (!validateInput(inputText)) return;
        startSearch(inputText, false); // 通常検索
    }

    /** 直接CSV出力ボタンのアクション */
    private void startDirectCsvExport() {
        String inputText = methodInputText.getText().trim();
        if (!validateInput(inputText)) return;
        startSearch(inputText, true); // 直接CSV出力
    }

    /** 入力テキストのバリデーション */
    private boolean validateInput(String inputText) {
        if (inputText.isEmpty()) {
            setMessage("メソッド名を入力してください。");
            updateResults(Collections.emptyList()); // 結果クリア
            // countLabel.setText("0 件"); // ★★★ 削除 ★★★
            return false;
        }
        if (!inputText.contains("#")) {
             setMessage("エラー: 'クラス名#メソッド名' の形式で入力してください。");
             return false;
        }
        return true;
    }


    /**
     * 検索プロセスを開始します。
     * @param methodIdentifier 検索対象 (String or IMethod)
     * @param directExport CSVへ直接出力する場合は true
     */
    public void startSearch(Object methodIdentifier, boolean directExport) {
        IMethod targetMethod = null;
        String searchTargetDescription = "";
        try {
            // IMethod の特定
            if (methodIdentifier instanceof String) {
                String inputText = (String) methodIdentifier;
                searchTargetDescription = inputText;
                targetMethod = JDTUtils.findMethodFromQualifiedName(inputText);
                 if (targetMethod == null) {
                     setMessage("エラー: メソッドが見つかりません - " + inputText);
                     updateResults(Collections.emptyList());
                     // countLabel.setText("0 件"); // ★★★ 削除 ★★★
                     return;
                 }
            } else if (methodIdentifier instanceof IMethod) {
                targetMethod = (IMethod) methodIdentifier;
                searchTargetDescription = JDTUtils.getMethodQualifiedName(targetMethod);
                methodInputText.setText(searchTargetDescription);
            } else {
                 setMessage("エラー: 無効な検索対象です。");
                 updateResults(Collections.emptyList());
                 // countLabel.setText("0 件"); // ★★★ 削除 ★★★
                 return;
            }

            // 検索ジョブの起動
            if (targetMethod != null) {
                if (directExport) {
                    // 直接CSV出力ジョブを起動
                    setMessage("CSV出力のための検索を実行中: " + searchTargetDescription + " ...");
                    // countLabel.setText("検索中..."); // ★★★ 削除 ★★★
                    DirectCsvExportJob job = new DirectCsvExportJob(targetMethod, getSite().getShell());
                    job.setUser(true);
                    job.schedule();
                } else {
                    // 通常の検索ジョブを起動 (表示あり)
                    setMessage("検索中: " + searchTargetDescription + " ...");
                    updateResults(Collections.emptyList()); // 結果クリア
                    // countLabel.setText("検索中..."); // ★★★ 削除 ★★★
                    RecursiveCallerSearchJob job = new RecursiveCallerSearchJob(targetMethod, this);
                    job.setUser(true);
                    job.schedule();
                }
            }
        } catch (CoreException e) {
            setMessage("エラー: メソッドの検索中に問題が発生しました。");
            e.printStackTrace();
             updateResults(Collections.emptyList());
             // countLabel.setText("エラー"); // ★★★ 削除 ★★★
        }
    }


    /**
     * 検索結果でテーブルビューアを更新します。
     * 10件を超える場合はダイアログで確認します。
     * UI スレッドで呼び出す必要があります。
     */
    public void updateResults(final List<CallInfo> results) {
        this.lastFullResults = results != null ? new ArrayList<>(results) : Collections.emptyList();

        Display.getDefault().asyncExec(() -> {
            if (viewer == null || viewer.getControl().isDisposed()) {
                return;
            }

            int resultCount = lastFullResults.size();
            boolean displayResults = true;

            // 件数チェックと確認ダイアログ
            if (resultCount > 10) {
                boolean confirm = MessageDialog.openQuestion(
                    viewer.getControl().getShell(),
                    "検索結果多数",
                    "結果が " + resultCount + " 件見つかりました (10件を超えています)。\n表に表示しますか？\n\n" +
                    "「いいえ」を選択した場合、表への表示は行われません。"
                );
                if (!confirm) {
                    displayResults = false;
                }
            }

            // 結果を表示するかどうかに基づいて処理
            if (displayResults) {
                viewer.setInput(lastFullResults);
                setMessage(resultCount + " 件の結果を表示しました。");
            } else {
                viewer.setInput(Collections.emptyList());
                setMessage(resultCount + " 件の結果が見つかりました (表示中止)。");
            }

            // 件数ラベルを更新するコードは削除
            // countLabel.setText(resultCount + " 件"); // ★★★ 削除 ★★★
        });
    }


     /** メッセージを設定 (コンソール出力のみ) */
     public void setMessage(final String message) {
         Display.getDefault().asyncExec(() -> {
             if (message != null) {
                System.out.println("View Message: " + message);
             }
         });
     }


    @Override
    public void setFocus() {
        if (methodInputText != null && !methodInputText.isDisposed()) {
            methodInputText.setFocus();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    // --- CSV エクスポート関連メソッド (変更なし) ---
    public void exportResultsToCsv(List<CallInfo> results, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write('\uFEFF');
            String[] headers = { "起点クラス・メソッド", "直接の呼び出し元", "行", "引数" };
            writer.write(String.join(",", escapeCsvFields(headers)));
            writer.newLine();
            for (CallInfo info : results) {
                String[] data = {
                    info.getOriginalCallerName(), info.getDirectCallerName(),
                    info.getLineNumber() > 0 ? String.valueOf(info.getLineNumber()) : "",
                    info.getArgumentsAsString()
                };
                writer.write(String.join(",", escapeCsvFields(data)));
                writer.newLine();
            }
        }
    }
    private String[] escapeCsvFields(String[] fields) {
        String[] escapedFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            escapedFields[i] = escapeCsvField(fields[i]);
        }
        return escapedFields;
    }
    private String escapeCsvField(String data) {
        if (data == null) return "\"\"";
        String escapedData = data.replace("\"", "\"\"");
        return "\"" + escapedData + "\"";
    }

    // --- マウスリスナー (ダブルクリック処理) (変更なし) ---
    private void addMouseListener() {
        viewer.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                Point point = new Point(e.x, e.y);
                Table table = viewer.getTable();
                TableItem item = table.getItem(point);
                if (item == null) return;
                Object element = item.getData();
                if (!(element instanceof CallInfo)) return;
                CallInfo callInfo = (CallInfo) element;
                int columnIndex = -1;
                for (int i = 0; i < table.getColumnCount(); i++) {
                    Rectangle columnBounds = item.getBounds(i);
                    if (columnBounds.contains(point)) {
                        columnIndex = i;
                        break;
                    }
                }
                IMethod methodToOpen = null;
                boolean jumpToLine = false;
                int lineToJump = -1;
                if (columnIndex == COL_ORIGINAL_CALLER) {
                    methodToOpen = callInfo.getOriginalCaller();
                } else if (columnIndex == COL_LINE || columnIndex == COL_ARGUMENTS) {
                    methodToOpen = callInfo.getDirectCaller();
                    lineToJump = callInfo.getLineNumber();
                    jumpToLine = (lineToJump > 0);
                } else {
                    methodToOpen = callInfo.getDirectCaller();
                }
                if (methodToOpen != null && methodToOpen.exists()) {
                    try {
                        IEditorPart editorPart = JavaUI.openInEditor(methodToOpen);
                        if (jumpToLine && editorPart instanceof ITextEditor) {
                            ITextEditor textEditor = (ITextEditor) editorPart;
                            IDocumentProvider provider = textEditor.getDocumentProvider();
                            if (provider != null) {
                                IDocument document = provider.getDocument(textEditor.getEditorInput());
                                if (document != null) {
                                    try {
                                        int offset = document.getLineOffset(lineToJump - 1);
                                        textEditor.selectAndReveal(offset, 0);
                                    } catch (BadLocationException ble) {
                                        System.err.println("指定された行へのジャンプに失敗しました (行番号: " + lineToJump + "): " + ble.getMessage());
                                        MessageDialog.openWarning(viewer.getControl().getShell(), "ジャンプ失敗", "指定された行番号 ("+ lineToJump + ") は無効です。");
                                    }
                                } else { System.err.println("ドキュメントの取得に失敗しました。"); }
                            } else { System.err.println("ドキュメントプロバイダーの取得に失敗しました。"); }
                        }
                    } catch (PartInitException | JavaModelException ex) {
                        handleOpenEditorException(ex);
                    }
                } else {
                    String colName = "不明な列";
                    if(columnIndex == COL_ORIGINAL_CALLER) colName = "起点メソッド";
                    else if (columnIndex == COL_DIRECT_CALLER) colName = "直接の呼び出し元";
                    else if (columnIndex == COL_LINE) colName = "直接の呼び出し元(行ジャンプ用)";
                    else if (columnIndex == COL_ARGUMENTS) colName = "直接の呼び出し元(引数ジャンプ用)";
                    System.err.println("ジャンプ先のメソッドが見つからないか、存在しません。 Column: " + columnIndex + " ("+ colName +")");
                    MessageDialog.openWarning(viewer.getControl().getShell(), "ジャンプ不可",
                        (columnIndex == COL_ORIGINAL_CALLER ? "起点クラス・メソッド" : "直接の呼び出し元") + " が見つかりませんでした。");
                }
            }
        });
    }

    /** エディタを開く際の例外を処理するヘルパーメソッド (変更なし) */
    private void handleOpenEditorException(Exception e) {
         String message = "エディタを開けませんでした: ";
         if (e instanceof PartInitException) { message = "エディタの初期化中にエラーが発生しました: "; }
         else if (e instanceof JavaModelException) { message = "Java要素の取得中にエラーが発生しました: "; }
         else if (e instanceof BadLocationException) { message = "無効な場所へのアクセス中にエラーが発生しました: "; }
         System.err.println(message + e.getMessage());
         e.printStackTrace();
         MessageDialog.openError(viewer.getControl().getShell(), "エラー", message + "\n詳細はエラーログを確認してください。");
    }

}
