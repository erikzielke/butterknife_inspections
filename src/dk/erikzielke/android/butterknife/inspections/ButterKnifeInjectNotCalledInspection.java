package dk.erikzielke.android.butterknife.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.CalleeMethodsTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static dk.erikzielke.android.butterknife.inspections.ButterKnifeUtils.*;

public class ButterKnifeInjectNotCalledInspection extends BaseJavaLocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass psiClass) {
                super.visitClass(psiClass);
                boolean isActivity = isActivity(psiClass);
                boolean isFragment = !isActivity && isFragment(psiClass);
                boolean isView = !isFragment && isView(psiClass);
                if (isActivity || isFragment || isView) {
                    if (hasButterKnifeAnnotations(psiClass)) {
                        if (isActivity) {
                            final PsiMethod[] onCreate = psiClass.findMethodsByName("onCreate", false);
                            if (onCreate.length != 0) {
                                final PsiMethod onCreateMethod = onCreate[0];
                                checkMethodHasInjectCall(psiClass.getProject(), onCreateMethod, holder);
                            }
                        } else if (isFragment) {
                            PsiMethod[] onCreateViews = psiClass.findMethodsByName("onCreateView", false);
                            if (onCreateViews.length != 0) {
                                PsiMethod onCreateViewMethod = onCreateViews[0];
                                checkMethodHasInjectCall(psiClass.getProject(), onCreateViewMethod, holder);
                            }
                        } else {
                            PsiMethod[] constructors = psiClass.findMethodsByName(psiClass.getName(), true);
                            for (PsiMethod constructor : constructors) {
                                checkMethodHasInjectCall(psiClass.getProject(), constructor, holder);
                            }
                        }
                    }
                }
            }
        };
    }

    private void checkMethodHasInjectCall(Project project, PsiMethod onCreateMethod, @NotNull ProblemsHolder holder) {

        PsiCodeBlock body = onCreateMethod.getBody();
        if (body != null) {
            PsiExpressionStatement[] childrenOfType = PsiTreeUtil.getChildrenOfType(body, PsiExpressionStatement.class);
            if (childrenOfType != null) {
                for (PsiExpressionStatement psiMethodCallExpression : childrenOfType) {
                    PsiExpression expression = psiMethodCallExpression.getExpression();
                    if (expression instanceof PsiMethodCallExpression) {
                        PsiReferenceExpression[] referenceExpressions = PsiTreeUtil.getChildrenOfType(expression, PsiReferenceExpression.class);
                        if (referenceExpressions != null) {
                            for (PsiReferenceExpression referenceExpression : referenceExpressions) {
                                if (referenceExpression.getCanonicalText().equals("ButterKnife.inject")) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }

        CalleeMethodsTreeStructure calleeMethodsTreeStructure = new CalleeMethodsTreeStructure(project, onCreateMethod, HierarchyBrowserBaseEx.SCOPE_ALL);
        final CallHierarchyNodeDescriptor rootElement = (CallHierarchyNodeDescriptor) calleeMethodsTreeStructure.getRootElement();
        if (!doesCallInjectView(calleeMethodsTreeStructure, rootElement, 0)) {
            holder.registerProblem(onCreateMethod, "ButterKnife.inject needs to be called");
        }

    }


    private boolean hasButterKnifeAnnotations(PsiClass psiClass) {
        final HasButterKnifeAnnotationsVisitor visitor = new HasButterKnifeAnnotationsVisitor();
        psiClass.accept(visitor);
        return visitor.hasButterKnifeAnnotations();
    }

    //TODO: Go breadth first
    public boolean doesCallInjectView(CalleeMethodsTreeStructure calleeMethodsTreeStructure, CallHierarchyNodeDescriptor rootElement, int level) {
        if (level < 3) {
            final PsiElement targetElement = rootElement.getTargetElement();
            if (targetElement instanceof PsiMethod) {
                final PsiMethod psiMethod = (PsiMethod) targetElement;
                final PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass != null) {
                    final String qualifiedName = containingClass.getQualifiedName();
                    if (qualifiedName != null) {
                        final boolean isInjectCall = qualifiedName.equals("butterknife.ButterKnife");
                        if (isInjectCall) {
                            return true;
                        }
                    }
                }
            }

            List<CallHierarchyNodeDescriptor> potentialChildrenToCheck = new ArrayList<CallHierarchyNodeDescriptor>();
            final Object[] childElements = calleeMethodsTreeStructure.getChildElements(rootElement);
            for (int index = childElements.length - 1; index >= 0; index--) {
                Object childElement = childElements[index];
                final CallHierarchyNodeDescriptor element = (CallHierarchyNodeDescriptor) childElement;
                final PsiElement method = element.getTargetElement();
                if (method instanceof PsiMethod) {
                    PsiMethod psiMethod = (PsiMethod) method;
                    final PsiClass containingClass = psiMethod.getContainingClass();
                    if (containingClass != null) {
                        final String qualifiedName = containingClass.getQualifiedName();
                        if (qualifiedName != null) {
                            if (!qualifiedName.startsWith("android"))
                                potentialChildrenToCheck.add(element);
                        }
                    }
                }
            }
            for (CallHierarchyNodeDescriptor element : potentialChildrenToCheck) {
                if (doesCallInjectView(calleeMethodsTreeStructure, element, level + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class HasButterKnifeAnnotationsVisitor extends JavaRecursiveElementWalkingVisitor {
        private boolean hasButterKnifeAnnotations;

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            final String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("butterknife")) {
                hasButterKnifeAnnotations = true;
            }
        }

        public boolean hasButterKnifeAnnotations() {
            return hasButterKnifeAnnotations;
        }
    }
}
