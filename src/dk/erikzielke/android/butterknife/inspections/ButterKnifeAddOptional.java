package dk.erikzielke.android.butterknife.inspections;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiModifierListOwner;

public class ButterKnifeAddOptional extends AddAnnotationFix {
    public ButterKnifeAddOptional(String s, PsiModifierListOwner psiModifierListOwner, String... strings) {
        super(s, psiModifierListOwner, strings);
    }
}
