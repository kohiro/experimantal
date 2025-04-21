package com.example.callhierarchyplugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.text.ITextSelection; // TextSelection のインポート
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.IWorkbenchPage; // IWorkbenchPage のインポート
import org.eclipse.ui.IWorkbenchWindow; // IWorkbenchWindow のインポート

import com.example.callhierarchyplugin.utils.JDTUtils; // JDTUtils を使用
import com.example.callhierarchyplugin.views.CallHierarchyView; // View を使用

/**
 * "Find Root Callers" コマンドを実行するハンドラ。
 */
public class FindCallersHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // 現在の選択範囲を取得
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        IMethod targetMethod = null;

        // 選択の種類に応じて IMethod を特定
        if (selection instanceof IStructuredSelection) {
            // パッケージエクスプローラーなどからの選択
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            if (firstElement instanceof IMethod) {
                targetMethod = (IMethod) firstElement;
            } else if (firstElement instanceof IJavaElement) {
                 // 他の Java 要素の場合、親を辿ってメソッドを探す試み (オプション)
                 IJavaElement element = (IJavaElement) firstElement;
                 IMethod enclosingMethod = (IMethod) element.getAncestor(IJavaElement.METHOD);
                 if (enclosingMethod != null) {
                      targetMethod = enclosingMethod;
                 }
            }
        } else if (selection instanceof ITextSelection) {
            // Java エディタからのテキスト選択
            // JDTUtils を使って選択位置から IMethod を解決
            targetMethod = JDTUtils.findMethodFromEditorSelection(selection);
        }

        // IMethod が特定できたら検索を開始
        if (targetMethod != null) {
            // ビューを開いて検索を開始
            IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
            if (window != null) {
                IWorkbenchPage page = window.getActivePage();
                if (page != null) {
                    try {
                        CallHierarchyView view = (CallHierarchyView) page.showView(CallHierarchyView.ID);
                        if (view != null) {
                            // ★★★ 第2引数に false を指定して、常に表示モードで検索を開始 ★★★
                            view.startSearch(targetMethod, false);
                        }
                    } catch (PartInitException e) {
                        System.err.println("ビューの表示中にエラーが発生しました: " + CallHierarchyView.ID);
                        e.printStackTrace();
                        // 必要であればユーザーにエラーダイアログを表示
                        throw new ExecutionException("ビューの表示に失敗しました。", e);
                    }
                }
            }
        } else {
            System.out.println("選択範囲から検索対象のメソッドを特定できませんでした。");
            // 必要であればユーザーにメッセージを表示
        }

        return null; // AbstractHandler#execute は通常 null を返す
    }

    /*
    // 必要であれば、コマンドの有効/無効状態を動的に制御するために isEnabled/setBaseEnabled をオーバーライド
    @Override
    public boolean isEnabled() {
        // ここで選択状態などをチェックして有効/無効を返す
        // plugin.xml の <enabledWhen> で定義する方が一般的
        return super.isEnabled();
    }
    */
}
