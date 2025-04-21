package com.example.callhierarchyplugin.utils;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*; // ASTノードのためにパッケージ全体をインポート
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream; // Stream のキャストのためにインポート


public class JDTUtils {

    /**
     * IMethod を表示用にフォーマットします (ClassName#methodName)。
     * 匿名クラスの場合は (BaseClassName#methodName) のように表示します。
     * メソッドが null の場合は "N/A" を返します。
     */
    public static String formatMethodName(IMethod method) {
        if (method == null) {
            return "N/A";
        }
        IType declaringType = method.getDeclaringType();
        String typeNamePortion = "[UnknownType]"; // デフォルトの型名部分
        String methodNamePortion = method.getElementName(); // デフォルトのメソッド名部分

        if (declaringType != null) {
            try {
                if (declaringType.isAnonymous()) {
                    // --- 匿名クラスの場合 ---
                    String baseName = null;
                    // スーパークラスの型シグネチャを取得
                    String superSig = declaringType.getSuperclassTypeSignature();
                    // スーパークラスが Object 以外なら、その単純名を使用
                    // 注意: Signature.toString() は '.' 区切り、シグネチャは '/' 区切りや '$' を含むことがある
                    // Signature.getTypeErasure() で型消去後のシグネチャを取得し、それを元に単純名を取得するのがより確実
                    if (superSig != null && !"Ljava/lang/Object;".equals(Signature.getTypeErasure(superSig))) {
                        // 型シグネチャから読みやすい型名文字列に変換し、そこから単純名を取得
                        baseName = Signature.getSimpleName(Signature.toString(Signature.getTypeErasure(superSig)));
                    } else {
                        // スーパークラスが Object か null の場合、実装しているインターフェースを確認
                        String[] interfaceSigs = declaringType.getSuperInterfaceTypeSignatures();
                        if (interfaceSigs != null && interfaceSigs.length > 0) {
                            // 最初のインターフェースの単純名を使用
                            baseName = Signature.getSimpleName(Signature.toString(Signature.getTypeErasure(interfaceSigs[0])));
                        }
                    }
                    // baseName が取得できればそれを使う、できなければフォールバック
                    typeNamePortion = (baseName != null && !baseName.isEmpty()) ? baseName : "[Anonymous]";
                    // 匿名クラスの場合、メソッド名はそのまま getElementName() で取得したものを使用
                    // (コンストラクタは匿名クラスにはないので考慮不要)

                } else {
                    // --- 通常のクラス、インターフェース、enum などの場合 ---
                    typeNamePortion = declaringType.getElementName();
                    // 要素名が空の場合 (例: ローカルクラスで名前がない?)、完全修飾名を試す
                    if (typeNamePortion == null || typeNamePortion.isEmpty()) {
                         typeNamePortion = declaringType.getTypeQualifiedName('.'); // 完全修飾名をフォールバックとして使用
                    }
                     // コンストラクタの場合、メソッド名はクラス名と同じにする
                     if (method.isConstructor()) {
                         methodNamePortion = typeNamePortion;
                     } else {
                         methodNamePortion = method.getElementName(); // 通常のメソッド名
                     }
                }
            } catch (JavaModelException e) {
                System.err.println("Error getting type name for method " + method.getElementName() + ": " + e.getMessage());
                typeNamePortion = "[Error]";
                try { // 例外発生時でもメソッド名は取得試行
                    methodNamePortion = method.getElementName();
                } catch (Exception ignored) {}
            }
        } else {
             // declaringType が null の場合 (通常はありえないはずだが念のため)
             try {
                  methodNamePortion = method.getElementName();
             } catch (Exception ignored) {
                  methodNamePortion = "[UnknownMethod]";
             }
        }

        // 最終的な文字列を組み立てる
        return typeNamePortion + "#" + methodNamePortion;
    }

     /**
     * 入力に適した完全修飾名を取得します (例: com.example.MyClass#myMethod)。
     * (変更なし)
     */
     public static String getMethodQualifiedName(IMethod method) {
         if (method == null) return "";
         IType declaringType = method.getDeclaringType();
         String typeName = declaringType.getFullyQualifiedName('.');
         return typeName + "#" + method.getElementName();
     }


    /**
     * "com.example.MyClass#myMethod" のような修飾名文字列に基づいて IMethod を検索します。
     * (変更なし)
     */
    public static IMethod findMethodFromQualifiedName(String qualifiedName) throws CoreException {
        if (qualifiedName == null || !qualifiedName.contains("#")) {
            return null; // 無効な形式
        }
        String[] parts = qualifiedName.split("#", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null; // 無効な形式
        }
        String typeName = parts[0];
        String methodName = parts[1];
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IJavaModel javaModel = JavaCore.create(workspaceRoot);
        if (!javaModel.isOpen()) {
             System.err.println("Java Model is not open.");
             return null;
        }
        IJavaProject[] javaProjects = javaModel.getJavaProjects();
        for (IJavaProject project : javaProjects) {
            if (!project.isOpen()) continue;
            IType type = project.findType(typeName, new NullProgressMonitor());
            if (type != null && type.exists()) {
                for (IMethod method : type.getMethods()) {
                    if (method.getElementName().equals(methodName)) {
                        return method;
                    }
                }
                 if (type.getElementName().equals(methodName)) {
                     for (IMethod method : type.getMethods()) {
                         if (method.isConstructor()) {
                              return method;
                         }
                     }
                 }
            }
        }
        return null; // 見つからない
    }


     /**
     * アクティブな Java エディタの現在の選択範囲に対応する IMethod を検索しようとします。
     * (変更なし)
     */
     public static IMethod findMethodFromEditorSelection(ISelection selection) {
        if (!(selection instanceof ITextSelection)) {
            return null;
        }
        ITextSelection textSelection = (ITextSelection) selection;
        IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        ITextEditor textEditor = null;
        if (editorPart instanceof ITextEditor) {
             textEditor = (ITextEditor) editorPart;
        } else if (editorPart != null) {
             textEditor = editorPart.getAdapter(ITextEditor.class);
        }
        if (textEditor == null) {
             System.err.println("Active editor is not a text editor or adaptable.");
             return null;
        }
        ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(textEditor.getEditorInput());
        if (typeRoot == null) {
             System.err.println("Could not get ITypeRoot from editor input.");
             return null;
        }
        try {
            IJavaElement[] elements = typeRoot.codeSelect(textSelection.getOffset(), textSelection.getLength());
            if (elements != null && elements.length > 0) {
                for (IJavaElement element : elements) {
                    if (element instanceof IMethod) {
                        return (IMethod) element;
                    }
                     IJavaElement parent = element;
                     while (parent != null && !(parent instanceof IMethod) && !(parent instanceof ITypeRoot)) {
                         parent = parent.getParent();
                     }
                     if (parent instanceof IMethod) {
                         return (IMethod) parent;
                     }
                }
            }
             IJavaElement elementAtOffset = typeRoot.getElementAt(textSelection.getOffset());
             if (elementAtOffset != null) {
                  IJavaElement parent = elementAtOffset;
                  while (parent != null && !(parent instanceof IMethod) && !(parent instanceof ITypeRoot)) {
                      parent = parent.getParent();
                  }
                  if (parent instanceof IMethod) {
                      return (IMethod) parent;
                  }
             }
        } catch (JavaModelException e) {
            System.err.println("JavaModelException during code select/getElementAt:");
            e.printStackTrace();
        }
        System.err.println("Could not resolve selection to an IMethod.");
        return null;
    }


    /**
     * AST の Expression ノードを文字列表現に変換します。
     * (変更なし)
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // AST API からの raw List 型と Stream キャストのため
    public static String expressionToString(Expression expression) {
        // (変更なし: 前回のコードを維持)
        if (expression instanceof StringLiteral) {
            return "\"" + ((StringLiteral) expression).getLiteralValue() + "\"";
        } else if (expression instanceof NumberLiteral) {
            return ((NumberLiteral) expression).getToken();
        } else if (expression instanceof BooleanLiteral) {
            return String.valueOf(((BooleanLiteral) expression).booleanValue());
        } else if (expression instanceof CharacterLiteral) {
            return "'" + ((CharacterLiteral) expression).charValue() + "'";
        } else if (expression instanceof NullLiteral) {
            return "null";
        } else if (expression instanceof TypeLiteral) {
             return expression.toString();
        }
        else if (expression instanceof Name) {
             IBinding binding = ((Name) expression).resolveBinding();
             return ((Name) expression).getFullyQualifiedName();
        }
        else if (expression instanceof MethodInvocation) {
             MethodInvocation inv = (MethodInvocation) expression;
             String receiver = inv.getExpression() != null ? expressionToString(inv.getExpression()) + "." : "";
             List<Expression> argsList = inv.arguments();
             String args = argsList.stream()
                             .map(JDTUtils::expressionToString)
                             .collect(Collectors.joining(", "));
             return receiver + inv.getName().getIdentifier() + "(" + args + ")";
        }
        else if (expression instanceof ClassInstanceCreation) {
             ClassInstanceCreation cic = (ClassInstanceCreation) expression;
             List<Expression> argsList = cic.arguments();
             String args = argsList.stream()
                             .map(JDTUtils::expressionToString)
                             .collect(Collectors.joining(", "));
             String anonymousBody = cic.getAnonymousClassDeclaration() != null ? " { ... }" : "";
             return "new " + cic.getType().toString() + "(" + args + ")" + anonymousBody;
        }
         else if (expression instanceof ArrayCreation) {
             ArrayCreation ac = (ArrayCreation) expression;
             String initializer = "";
             if (ac.getInitializer() != null) {
                  List<Expression> initializers = ac.getInitializer().expressions();
                  initializer = "{" + initializers.stream().map(JDTUtils::expressionToString).limit(3).collect(Collectors.joining(", ")) + (initializers.size() > 3 ? ", ..." : "") + "}";
             }
             String dimensions = ((Stream<Expression>)ac.dimensions().stream()).map(JDTUtils::expressionToString).collect(Collectors.joining("][", "[", "]"));
             return "new " + ac.getType().getElementType().toString() + dimensions + (initializer.isEmpty() ? "" : " " + initializer);
         }
         else if (expression instanceof ArrayAccess) {
              return expressionToString(((ArrayAccess) expression).getArray()) + "[" + expressionToString(((ArrayAccess) expression).getIndex()) + "]";
         }
         else if (expression instanceof FieldAccess) {
              return expressionToString(((FieldAccess) expression).getExpression()) + "." + ((FieldAccess) expression).getName().getIdentifier();
         }
         else if (expression instanceof QualifiedName) {
              QualifiedName qn = (QualifiedName) expression;
              return qn.getFullyQualifiedName();
         }
         else if (expression instanceof ThisExpression) {
             return (((ThisExpression) expression).getQualifier() != null ? ((ThisExpression) expression).getQualifier().toString() + "." : "") + "this";
         }
         else if (expression instanceof CastExpression) {
             return "(" + ((CastExpression) expression).getType().toString() + ") " + expressionToString(((CastExpression) expression).getExpression());
         }
         else if (expression instanceof ParenthesizedExpression) {
             return "(" + expressionToString(((ParenthesizedExpression) expression).getExpression()) + ")";
         }
         else if (expression instanceof LambdaExpression) {
             LambdaExpression lambda = (LambdaExpression) expression;
             String params = (String) lambda.parameters().stream()
                                  .map(Object::toString)
                                  .collect(Collectors.joining(", "));
             boolean needsParentheses = true;
             try {
                 Boolean hasParens = lambda.hasParentheses();
                 if (hasParens != null && !hasParens) {
                     needsParentheses = false;
                 }
             } catch (UnsupportedOperationException e) {
                 if (lambda.parameters().size() == 1) {
                     Object param = lambda.parameters().get(0);
                     if (!(param instanceof SingleVariableDeclaration)) {
                         needsParentheses = false;
                     }
                 }
             }
             if (needsParentheses) {
                 params = "(" + params + ")";
             }
             ASTNode body = lambda.getBody();
             String bodyStr = (body instanceof Block && ((Block)body).statements().size() > 1) ? "{ ... }" : body.toString();
             return params + " -> " + bodyStr;
         }
         else if (expression instanceof MethodReference) {
             MethodReference mr = (MethodReference) expression;
             String typeArgs = "";
             if (!mr.typeArguments().isEmpty()) {
                  List<Type> typeArgsList = mr.typeArguments();
                  typeArgs = "<" + typeArgsList.stream().map(Object::toString).collect(Collectors.joining(", ")) + ">";
             }
             String qualifier = "";
             String methodName = "";
             if (mr instanceof CreationReference) {
                  qualifier = ((CreationReference) mr).getType().toString() + "::";
                  methodName = "new";
             } else if (mr instanceof TypeMethodReference) {
                  qualifier = ((TypeMethodReference) mr).getType().toString() + "::";
                  methodName = ((TypeMethodReference) mr).getName().getIdentifier();
             } else if (mr instanceof SuperMethodReference) {
                  qualifier = (((SuperMethodReference) mr).getQualifier() != null ? ((SuperMethodReference) mr).getQualifier().toString() + "." : "") + "super::";
                  methodName = ((SuperMethodReference) mr).getName().getIdentifier();
             } else if (mr instanceof ExpressionMethodReference) {
                  qualifier = expressionToString(((ExpressionMethodReference) mr).getExpression()) + "::";
                  methodName = ((ExpressionMethodReference) mr).getName().getIdentifier();
             }
             else {
                  qualifier = mr.toString().split("::")[0] + "::";
                  String[] parts = mr.toString().split("::");
                  methodName = parts.length > 1 ? parts[1] : "[unknown]";
             }
             return qualifier + typeArgs + methodName;
         }
         else if (expression instanceof InstanceofExpression) {
              return expressionToString(((InstanceofExpression) expression).getLeftOperand()) + " instanceof " + ((InstanceofExpression) expression).getRightOperand().toString();
         }
         else if (expression instanceof ConditionalExpression) {
              ConditionalExpression ce = (ConditionalExpression) expression;
              return expressionToString(ce.getExpression()) + " ? " + expressionToString(ce.getThenExpression()) + " : " + expressionToString(ce.getElseExpression());
         }
         else if (expression instanceof Assignment) {
              Assignment assign = (Assignment) expression;
              return expressionToString(assign.getLeftHandSide()) + " " + assign.getOperator().toString() + " " + expressionToString(assign.getRightHandSide());
         }
         else if (expression instanceof PostfixExpression) {
              return expressionToString(((PostfixExpression) expression).getOperand()) + ((PostfixExpression) expression).getOperator().toString();
         }
         else if (expression instanceof PrefixExpression) {
              return ((PrefixExpression) expression).getOperator().toString() + expressionToString(((PrefixExpression) expression).getOperand());
         }
        System.err.println("Warning: Unhandled expression type in expressionToString: " + expression.getClass().getName() + " | Node: " + expression.toString());
        return expression.toString();
    }

    /**
     * コンパイルユニット内の指定されたオフセットに対する 1 ベースの行番号を取得します。
     * (変更なし)
     */
    public static int getLineNumber(ICompilationUnit cu, int offset) {
        if (cu == null || offset < 0) {
             return -1;
        }
        try {
            ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
            parser.setSource(cu);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
            int line = astRoot.getLineNumber(offset);
            if (line <= 0) {
                 try {
                     String source = cu.getSource();
                     if (source != null && offset == source.length()) {
                          int lastCharOffset = offset - 1;
                          if (lastCharOffset >= 0) {
                              line = astRoot.getLineNumber(lastCharOffset);
                          }
                     }
                 } catch (JavaModelException e) {
                 }
            }
            return line > 0 ? line : -1;
        } catch (Exception e) {
            System.err.println("Error getting line number via AST for offset " + offset + " in " + cu.getElementName());
            return -1;
        }
    }
}
