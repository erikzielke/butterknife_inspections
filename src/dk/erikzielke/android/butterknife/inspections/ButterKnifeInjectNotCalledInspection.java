package dk.erikzielke.android.butterknife.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.CalleeMethodsTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import static dk.erikzielke.android.butterknife.inspections.ButterKnifeUtils.*;
import static dk.erikzielke.android.butterknife.inspections.ButterKnifeUtils.isView;

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
        CalleeMethodsTreeStructure calleeMethodsTreeStructure = new CalleeMethodsTreeStructure(project, onCreateMethod, HierarchyBrowserBaseEx.SCOPE_ALL);
        final CallHierarchyNodeDescriptor rootElement = (CallHierarchyNodeDescriptor) calleeMethodsTreeStructure.getRootElement();
        if (!doesCallInjectView(calleeMethodsTreeStructure, rootElement)) {
            holder.registerProblem(onCreateMethod, "ButterKnife.inject needs to be called");
        }
    }



    private boolean hasButterKnifeAnnotations(PsiClass psiClass) {
        final HasButterKnifeAnnotationsVisitor visitor = new HasButterKnifeAnnotationsVisitor();
        psiClass.accept(visitor);
        return visitor.hasButterKnifeAnnotations();
    }

    public boolean doesCallInjectView(CalleeMethodsTreeStructure calleeMethodsTreeStructure, CallHierarchyNodeDescriptor rootElement) {
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
                            if (doesCallInjectView(calleeMethodsTreeStructure, element)) {
                                return true;
                            }
                    }
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
