package com.example.callhierarchyplugin.search;

import com.example.callhierarchyplugin.utils.JDTUtils;

import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.search.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指定されたメソッドの根本的な呼び出し元を検索するコアロジックを提供します。
 */
public class RootCallerFinder {

    // DirectCallDetails は検索プロセス内部でのみ使用するため、ここに移動しても良い
    private static class DirectCallDetails {
        final IMethod directCaller;
        final int lineNumber;
        final List<String> arguments;
        final int offset;
        final int length;

        DirectCallDetails(IMethod directCaller, int lineNumber, List<String> arguments, int offset, int length) {
            this.directCaller = directCaller;
            this.lineNumber = lineNumber;
            this.arguments = arguments;
            this.offset = offset;
            this.length = length;
        }
    }

    private final IMethod initialTargetMethod;
    private final List<CallInfo> finalResults = new ArrayList<>();
    private final Set<IMethod> processedMethods = new HashSet<>();
    private final List<IMethod> methodsToSearchQueue = new LinkedList<>();
    private final Map<IMethod, Set<IMethod>> callersMap = new HashMap<>();
    private final List<DirectCallDetails> directCallsToInitialTarget = new ArrayList<>();

    public RootCallerFinder(IMethod initialTargetMethod) {
        if (initialTargetMethod == null) {
            throw new IllegalArgumentException("Initial target method cannot be null");
        }
        this.initialTargetMethod = initialTargetMethod;
    }

    /**
     * 根本的な呼び出し元の検索を実行します。
     *
     * @param monitor プログレスモニター
     * @return CallInfo のリスト
     * @throws CoreException 検索中にエラーが発生した場合
     */
    public List<CallInfo> findRootCallers(IProgressMonitor monitor) throws CoreException {
        if (!initialTargetMethod.exists()) {
             throw new CoreException(new Status(IStatus.ERROR, "com.example.callhierarchyplugin", "Target method does not exist."));
        }

        String taskName = JDTUtils.getMethodQualifiedName(initialTargetMethod) + " の呼び出し階層を構築中";
        // サブモニターを使用するか、全体の作業量を推定する
        SubMonitor subMonitor = SubMonitor.convert(monitor, taskName, 100);

        try {
            // フェーズ 1: 呼び出し関係グラフの構築
            initializeSearchState();
            // 作業量の 80% をグラフ構築に割り当て
            buildCallGraph(subMonitor.newChild(80, SubMonitor.SUPPRESS_SUBTASK));

            if (subMonitor.isCanceled()) throw new OperationCanceledException();

            // フェーズ 2: 根本的な起点の特定と最終結果の生成
            subMonitor.setTaskName("根本的な起点を特定中...");
            // 作業量の 20% を起点特定に割り当て
            generateFinalResults(subMonitor.newChild(20, SubMonitor.SUPPRESS_SUBTASK));

            return new ArrayList<>(finalResults); // 結果のコピーを返す

        } catch (OperationCanceledException e) {
            return Collections.emptyList(); // キャンセルされた場合は空リスト
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }


    /** 検索状態を初期化 */
    private void initializeSearchState() {
        methodsToSearchQueue.clear();
        processedMethods.clear();
        finalResults.clear();
        callersMap.clear();
        directCallsToInitialTarget.clear();
        methodsToSearchQueue.add(initialTargetMethod);
    }

    /** フェーズ 1: SearchEngine を使って呼び出し関係を探索し、マップに格納 */
    private void buildCallGraph(IProgressMonitor monitor) throws CoreException {
        SearchEngine searchEngine = new SearchEngine();
        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();

        // 作業量をキューの初期サイズに基づいて大まかに設定 (動的に増えるため不正確)
        // monitor.beginTask("呼び出し関係を探索中...", methodsToSearchQueue.size());

        while (!methodsToSearchQueue.isEmpty()) {
            if (monitor.isCanceled()) throw new OperationCanceledException();

            IMethod currentMethodToSearch = methodsToSearchQueue.remove(0);

            if (!processedMethods.add(currentMethodToSearch)) continue;

            if (currentMethodToSearch == null || !currentMethodToSearch.exists()) {
                System.err.println("警告(Finder): キュー内のメソッドが存在しません: " + JDTUtils.formatMethodName(currentMethodToSearch));
                continue;
            }

            monitor.subTask(JDTUtils.formatMethodName(currentMethodToSearch) + " の呼び出し元を検索中...");

            SearchPattern pattern;
            try {
                 pattern = SearchPattern.createPattern(currentMethodToSearch, IJavaSearchConstants.REFERENCES);
                 if (pattern == null) {
                      System.err.println("警告(Finder): 検索パターンの作成に失敗しました: " + JDTUtils.formatMethodName(currentMethodToSearch));
                      continue;
                 }
            } catch (Exception e) {
                 System.err.println("エラー(Finder): 検索パターンの作成中に例外が発生しました: " + JDTUtils.formatMethodName(currentMethodToSearch));
                 e.printStackTrace();
                 continue;
            }

            SearchRequestor requestor = new SearchRequestor() {
                @Override
                public void acceptSearchMatch(SearchMatch match) throws CoreException {
                    if (monitor.isCanceled()) throw new OperationCanceledException();
                    if (match.getAccuracy() != SearchMatch.A_ACCURATE) return;

                    Object element = match.getElement();
                    IMethod directCallerMethod = findEnclosingMethod(element);

                    if (directCallerMethod != null && directCallerMethod.exists()) {
                        callersMap.computeIfAbsent(currentMethodToSearch, k -> new HashSet<>()).add(directCallerMethod);

                        if (currentMethodToSearch.equals(initialTargetMethod)) {
                            boolean alreadyRecorded = directCallsToInitialTarget.stream()
                                .anyMatch(d -> d.directCaller.equals(directCallerMethod) && d.offset == match.getOffset());
                            if (!alreadyRecorded) {
                                // 引数解析は時間がかかる可能性があるため、モニターを渡す
                                List<String> arguments = parseArgumentsFromMatch(directCallerMethod, match.getOffset(), match.getLength(), monitor);
                                int lineNumber = JDTUtils.getLineNumber(directCallerMethod.getCompilationUnit(), match.getOffset());
                                directCallsToInitialTarget.add(
                                    new DirectCallDetails(directCallerMethod, lineNumber, arguments, match.getOffset(), match.getLength())
                                );
                            }
                        }

                        if (!isInJar(directCallerMethod) && !processedMethods.contains(directCallerMethod) && !methodsToSearchQueue.contains(directCallerMethod)) {
                            methodsToSearchQueue.add(directCallerMethod);
                            // monitor. Kicsit növelni a totalWork-ot? Vagy ismeretlenként hagyni.
                        }
                    }
                }
            };

            try {
                searchEngine.search(pattern,
                                    new SearchParticipant[]{SearchEngine.getDefaultSearchParticipant()},
                                    scope,
                                    requestor,
                                    monitor); // Pass monitor for cancellation
            } catch (CoreException ce) {
                 System.err.println("エラー(Finder): SearchEngine.search 中に CoreException (メソッド: " + JDTUtils.formatMethodName(currentMethodToSearch) + ")");
                 ce.printStackTrace();
                 // Stop or continue? Let's continue for now.
                 continue;
            } catch (Exception ex) {
                 System.err.println("エラー(Finder): SearchEngine.search 中に予期せぬ例外 (メソッド: " + JDTUtils.formatMethodName(currentMethodToSearch) + ")");
                 ex.printStackTrace();
                 continue;
            }
            // monitor.worked(1); // 1メソッド分の探索完了
        }
        // monitor.done();
    }

    /** フェーズ 2: directCallsToInitialTarget リストを元に、各呼び出しの根本起点を探し、最終結果を生成 */
    private void generateFinalResults(IProgressMonitor monitor) {
        finalResults.clear();
        int total = directCallsToInitialTarget.size();
        monitor.beginTask("根本的な起点を特定中...", total > 0 ? total : 1);

        if (total == 0) {
             monitor.done();
             return;
        }

        for (DirectCallDetails details : directCallsToInitialTarget) {
            if (monitor.isCanceled()) throw new OperationCanceledException();
            monitor.subTask("起点を確認中: " + JDTUtils.formatMethodName(details.directCaller));

            Set<IMethod> rootCallers = findAllRootCallers(details.directCaller, this.callersMap);

            if (rootCallers.isEmpty()) {
                 System.err.println("警告(Finder): 根本的な呼び出し元が見つかりませんでした: " + JDTUtils.formatMethodName(details.directCaller));
                 finalResults.add(new CallInfo(details.directCaller, details.directCaller, details.lineNumber, details.arguments));
            } else {
                for (IMethod rootCaller : rootCallers) {
                    finalResults.add(new CallInfo(rootCaller, details.directCaller, details.lineNumber, details.arguments));
                }
            }
            monitor.worked(1);
        }
        monitor.done();
    }

    /** 指定されたメソッドから呼び出し元マップを遡り、考えられる全ての根本的な起点を探します */
    private Set<IMethod> findAllRootCallers(IMethod startMethod, Map<IMethod, Set<IMethod>> callersMap) {
        Set<IMethod> roots = new HashSet<>();
        findAllRootCallersRecursive(startMethod, callersMap, roots, new HashSet<>());
        return roots;
    }

    /** findAllRootCallers のための再帰ヘルパーメソッド */
    private void findAllRootCallersRecursive(IMethod currentMethod,
                                             Map<IMethod, Set<IMethod>> callersMap,
                                             Set<IMethod> roots,
                                             Set<IMethod> visitedInPath) {
        if (!visitedInPath.add(currentMethod)) {
             System.err.println("findAllRootCallersRecursive: 循環呼び出しを検出しました: " + JDTUtils.formatMethodName(currentMethod));
            return;
        }
        Set<IMethod> callers = callersMap.get(currentMethod);
        if (callers == null || callers.isEmpty()) {
            if (!isInJar(currentMethod)) { // JAR由来はルートとしない
                 roots.add(currentMethod);
            }
        } else {
            boolean canRecurseFurther = false;
            for (IMethod caller : callers) {
                 if (caller != null && caller.exists()) {
                    if (!isInJar(caller)) { // JAR由来でない場合のみ再帰
                        canRecurseFurther = true;
                        findAllRootCallersRecursive(caller, callersMap, roots, visitedInPath);
                    }
                 } else {
                     System.err.println("findAllRootCallersRecursive: 存在しない、または不正な呼び出し元をスキップします。");
                     // このパスはここで止まるが、currentMethodがルートとは限らない
                 }
            }
            // 非JARの呼び出し元が一つもなかった場合、現在のメソッドが実質的なルート
            if (!canRecurseFurther && !isInJar(currentMethod)) {
                 roots.add(currentMethod);
            }
        }
        visitedInPath.remove(currentMethod);
    }

    /** 指定された IMethod が JAR アーカイブファイルに含まれているかどうかを判定 */
    private boolean isInJar(IMethod method) {
        if (method == null) return false;
        IPackageFragmentRoot root = (IPackageFragmentRoot) method.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
        return root != null && root.isArchive();
    }

    /** SearchMatch で見つかった要素から、それを含む IMethod を見つける */
    private IMethod findEnclosingMethod(Object element) {
        if (element instanceof IMethod) return (IMethod) element;
        if (element instanceof IJavaElement) {
            IJavaElement current = (IJavaElement) element;
            while (current != null) {
                if (current instanceof IMethod) return (IMethod) current;
                if (current instanceof ICompilationUnit || current instanceof IType) return null;
                current = current.getParent();
            }
        }
        return null;
    }

    /** SearchMatch の位置情報を使用して、呼び出し箇所の引数を AST を使って解析 */
    private List<String> parseArgumentsFromMatch(IMethod callerMethod, int matchOffset, int matchLength, IProgressMonitor monitor) {
        // このメソッドは JDTUtils に移しても良いかもしれない
        ICompilationUnit cu = callerMethod.getCompilationUnit();
        if (cu == null || !cu.exists()) return List.of("ソースなし");

        try {
            ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
            parser.setSource(cu);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true);
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(monitor); // Pass monitor

            if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();

            NodeFinder nodeFinder = new NodeFinder(astRoot, matchOffset, matchLength);
            ASTNode node = nodeFinder.getCoveringNode();
            MethodInvocation invocation = null;
            List<String> argStrings = new ArrayList<>();

            // Find MethodInvocation or SuperMethodInvocation
            if (node instanceof SimpleName) {
                ASTNode parent = node.getParent();
                if (parent instanceof MethodInvocation) invocation = (MethodInvocation) parent;
                else if (parent instanceof QualifiedName && parent.getParent() instanceof MethodInvocation) invocation = (MethodInvocation) parent.getParent();
                else if (parent instanceof SuperMethodInvocation) {
                     SuperMethodInvocation superInv = (SuperMethodInvocation) parent;
                     IMethodBinding binding = superInv.resolveMethodBinding();
                     if (binding != null && binding.getJavaElement() != null && binding.getJavaElement().equals(this.initialTargetMethod)) {
                         @SuppressWarnings("unchecked") List<Expression> arguments = superInv.arguments();
                         for (Expression arg : arguments) argStrings.add(JDTUtils.expressionToString(arg));
                         return argStrings; // Return early for SuperMethodInvocation
                     }
                }
            } else if (node instanceof MethodInvocation) invocation = (MethodInvocation) node;

            // Process found MethodInvocation
            if (invocation != null) {
                IMethodBinding methodBinding = invocation.resolveMethodBinding();
                if (methodBinding != null) {
                    IJavaElement resolvedElement = methodBinding.getJavaElement();
                    if (resolvedElement != null && resolvedElement.equals(this.initialTargetMethod)) {
                        @SuppressWarnings("unchecked") List<Expression> arguments = invocation.arguments();
                        for (Expression arg : arguments) argStrings.add(JDTUtils.expressionToString(arg));
                        return argStrings; // Return arguments
                    }
                }
            }
            // If we reach here, invocation/arguments were not found or didn't match
            return List.of("呼び出し箇所特定不可");

        } catch (OperationCanceledException e) {
             throw e;
        } catch (Exception e) {
             System.err.println("エラー(Finder): AST解析中に例外 (" + JDTUtils.formatMethodName(callerMethod) + "): " + e.getMessage());
             // e.printStackTrace();
            return List.of("AST解析エラー");
        }
    }
}
