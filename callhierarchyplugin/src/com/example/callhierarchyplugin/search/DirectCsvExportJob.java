package com.example.callhierarchyplugin.search;

import com.example.callhierarchyplugin.views.CallHierarchyView; // View クラスを参照するため
import com.example.callhierarchyplugin.utils.JDTUtils; // JDTUtils を使用

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 検索結果を表に表示せず、直接 CSV ファイルに出力する Job。
 */
public class DirectCsvExportJob extends Job {

    private final IMethod initialTargetMethod;
    private final Shell shell; // FileDialog と MessageDialog を表示するため

    public DirectCsvExportJob(IMethod targetMethod, Shell shell) {
        super("CSV直接出力: " + JDTUtils.getMethodQualifiedName(targetMethod));
        this.initialTargetMethod = targetMethod;
        this.shell = shell; // 親シェルを保持
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        if (initialTargetMethod == null || !initialTargetMethod.exists()) {
            showErrorMessage("検索対象のメソッドが無効です。");
            return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "検索対象のメソッドが無効です。");
        }

        // RootCallerFinder を使って検索を実行
        RootCallerFinder finder = new RootCallerFinder(initialTargetMethod);
        try {
            // ファインダーを実行し、結果リストを取得
            monitor.beginTask("呼び出し元を検索中...", IProgressMonitor.UNKNOWN);
            List<CallInfo> results = finder.findRootCallers(monitor);
            monitor.done();

            if (monitor.isCanceled()) {
                showInformationMessage("CSV出力がキャンセルされました。");
                return Status.CANCEL_STATUS;
            }

            if (results.isEmpty()) {
                showInformationMessage("呼び出し元が見つからなかったため、CSVファイルは出力されませんでした。");
                return Status.OK_STATUS;
            }

            // --- ファイルダイアログ表示と CSV 書き込み ---
            // UI 操作は UI スレッドで行う必要がある
            final String[] filePath = new String[1]; // 結果を格納する配列
            Display.getDefault().syncExec(() -> {
                FileDialog dialog = new FileDialog(shell, SWT.SAVE);
                dialog.setText("CSVファイルとして保存");
                dialog.setFilterNames(new String[] { "CSV ファイル (*.csv)", "すべてのファイル (*.*)" });
                dialog.setFilterExtensions(new String[] { "*.csv", "*.*" });
                dialog.setFileName(initialTargetMethod.getElementName() + "_callers.csv"); // デフォルトファイル名
                dialog.setOverwrite(true);
                filePath[0] = dialog.open();
            });

            if (filePath[0] == null) {
                showInformationMessage("CSV出力がキャンセルされました。");
                return Status.CANCEL_STATUS; // ユーザーキャンセル
            }

            // CSV ファイルへの書き込み
            try {
                // 書き込み処理自体はバックグラウンドでOK
                exportResultsToCsv(results, filePath[0]);
                showInformationMessage("CSVファイルへのエクスポートが完了しました。\n" + filePath[0]);
                return Status.OK_STATUS;
            } catch (IOException e) {
                e.printStackTrace();
                showErrorMessage("CSVファイルのエクスポート中にエラーが発生しました。\n" + e.getMessage());
                return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "CSV書き込みエラー", e);
            }

        } catch (CoreException e) {
            e.printStackTrace();
            showErrorMessage("検索中にエラーが発生しました: " + e.getMessage());
            return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "呼び出し元の検索中にエラーが発生しました。", e);
        } catch (OperationCanceledException e) {
            showInformationMessage("CSV出力がキャンセルされました。");
            return Status.CANCEL_STATUS;
        } catch (Exception e) { // 予期せぬ実行時例外
             e.printStackTrace();
             showErrorMessage("予期せぬエラーが発生しました: " + e.getMessage());
             return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "予期せぬエラーが発生しました。", e);
        }
    }

    // --- CSV 書き込みロジック (CallHierarchyView からコピー＆調整) ---
    private void exportResultsToCsv(List<CallInfo> results, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write('\uFEFF'); // BOM for Excel
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

    // --- UI スレッドでメッセージダイアログを表示するヘルパー ---
    private void showInformationMessage(String message) {
        Display.getDefault().asyncExec(() -> {
            if (shell != null && !shell.isDisposed()) {
                MessageDialog.openInformation(shell, "情報", message);
            }
        });
    }
    private void showErrorMessage(String message) {
        Display.getDefault().asyncExec(() -> {
            if (shell != null && !shell.isDisposed()) {
                MessageDialog.openError(shell, "エラー", message);
            }
        });
    }
}

