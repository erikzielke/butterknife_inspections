package dk.erikzielke.android.butterknife.inspections;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ButterKnifeOptionalNonNullableFieldInspection extends BaseJavaLocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                PsiModifierList modifierList = field.getModifierList();
                if (modifierList != null) {
                    PsiAnnotation optional = modifierList.findAnnotation("butterknife.Optional");
                    if (optional != null) {
                        PsiAnnotation nullable = modifierList.findAnnotation("android.support.annotation.Nullable");
                        if (nullable == null) {
                            holder.registerProblem(optional, "Optional, but not @Nullable", new AddAnnotationFix("android.support.annotation.Nullable", field));
                        }
                    }
                }
            }
        };
    }
}
