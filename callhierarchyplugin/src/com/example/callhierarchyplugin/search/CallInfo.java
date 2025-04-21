package com.example.callhierarchyplugin.search;

import com.example.callhierarchyplugin.utils.JDTUtils; // JDTUtils を使用
import org.eclipse.jdt.core.IMethod;
import java.util.List;
import java.util.stream.Collectors;


public class CallInfo {
    private final IMethod originalCaller; // 再帰の起点となったメソッド
    private final IMethod directCaller;   // 直接の呼び出し元メソッド
    private final int lineNumber;         // 呼び出し元の行番号
    private final List<String> argumentValues; // 呼び出し時の引数の文字列表現

    public CallInfo(IMethod originalCaller, IMethod directCaller, int lineNumber, List<String> argumentValues) {
        this.originalCaller = originalCaller;
        this.directCaller = directCaller;
        this.lineNumber = lineNumber;
        this.argumentValues = argumentValues != null ? argumentValues : List.of(); // Null check
    }

    // --- Getters ---

    public IMethod getOriginalCaller() {
        return originalCaller;
    }

    public IMethod getDirectCaller() {
        return directCaller;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public List<String> getArgumentValues() {
        return argumentValues;
    }

    // --- Helper methods for display ---

    public String getOriginalCallerName() {
        return JDTUtils.formatMethodName(originalCaller); // Use utility method
    }

    public String getDirectCallerName() {
        return JDTUtils.formatMethodName(directCaller); // Use utility method
    }

    public String getArgumentsAsString() {
        if (argumentValues.isEmpty() || argumentValues.stream().allMatch(String::isEmpty)) {
            return "なし";
        }
        // Filter out potential placeholder messages if needed
        List<String> validArgs = argumentValues.stream()
            .filter(arg -> arg != null && !arg.equals("AST解析エラー") && !arg.equals("呼び出し箇所特定不可") && !arg.equals("ソースなし") && !arg.equals("引数情報取得不可"))
            .collect(Collectors.toList());

        if (validArgs.isEmpty() && !argumentValues.isEmpty()) {
             // Only placeholder messages were present
             return argumentValues.get(0); // Show the first placeholder
        } else if (validArgs.isEmpty()) {
            return "なし";
        }

        return String.join(", ", validArgs);
    }


    @Override
    public String toString() {
        // Provides a basic string representation, mainly for debugging
        return String.format("起点: %s | 呼び出し元: %s (L%d) | 引数: %s",
                getOriginalCallerName(), getDirectCallerName(), lineNumber, getArgumentsAsString());
    }
}

