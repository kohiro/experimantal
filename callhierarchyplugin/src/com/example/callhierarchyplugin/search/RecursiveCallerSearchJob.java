package com.example.callhierarchyplugin.search;

import com.example.callhierarchyplugin.views.CallHierarchyView;
import com.example.callhierarchyplugin.utils.JDTUtils; // JDTUtils が必要

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.*; // IMethod のため

import java.util.List; // List のため

/**
 * 根本的な呼び出し元を検索し、結果をビューに通知する Job。
 * 実際の検索ロジックは RootCallerFinder に委譲する。
 */
public class RecursiveCallerSearchJob extends Job {

    private final IMethod initialTargetMethod;
    private final CallHierarchyView resultView;

    public RecursiveCallerSearchJob(IMethod targetMethod, CallHierarchyView view) {
        super("根本起点を含む呼び出し元検索: " + JDTUtils.getMethodQualifiedName(targetMethod));
        this.initialTargetMethod = targetMethod;
        this.resultView = view;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        if (initialTargetMethod == null || !initialTargetMethod.exists()) {
            return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "検索対象のメソッドが無効です。");
        }

        // RootCallerFinder を使って検索を実行
        RootCallerFinder finder = new RootCallerFinder(initialTargetMethod);
        try {
            // ファインダーを実行し、結果リストを取得
            // ★★★ モニターを渡すことを確認 ★★★
            List<CallInfo> results = finder.findRootCallers(monitor);

            // キャンセルされていなければビューを更新
            if (!monitor.isCanceled() && resultView != null) {
                // updateResults は内部で Display.asyncExec を呼ぶので、
                // このスレッドから直接呼び出して良い
                resultView.updateResults(results);
            }

            return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;

        } catch (CoreException e) {
            e.printStackTrace();
            setMessageInView("検索中にエラーが発生しました: " + e.getMessage());
            return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "呼び出し元の検索中にエラーが発生しました。", e);
        } catch (OperationCanceledException e) {
            setMessageInView("検索がキャンセルされました。");
            return Status.CANCEL_STATUS;
        } catch (Exception e) { // 予期せぬ実行時例外
             e.printStackTrace();
             setMessageInView("予期せぬエラーが発生しました: " + e.getMessage());
             return new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "予期せぬエラーが発生しました。", e);
        } finally {
            // Ensure monitor is done regardless of outcome
             // ★★★ モニターが null でないことを確認 ★★★
             if (monitor != null) {
                  monitor.done();
             }
        }
    }

    /** UI スレッドでビューにメッセージを設定するヘルパー */
    private void setMessageInView(String message) {
        if (resultView != null) {
            resultView.setMessage(message);
        }
    }
}
